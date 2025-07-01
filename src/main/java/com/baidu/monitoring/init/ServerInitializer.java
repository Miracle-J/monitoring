package com.baidu.monitoring.init;

import com.baidu.monitoring.conf.LocalProperties;
import static com.baidu.monitoring.util.SessionManager.*;

import com.baidu.monitoring.webscoket.StatusChangeWebsocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * 启动服务初始化
 */
@Component
public class ServerInitializer {

	@Autowired
	@Lazy
	private StatusChangeWebsocketHandler statusChangeWebsocketHandler;

	private static final Logger logger = LoggerFactory.getLogger(ServerInitializer.class);


	// 容器名称
	private static final String CONTAINER_NAME = "mycoturn";
	// 镜像名称
	private static final String IMAGE_NAME = "docker.1ms.run/coturn/coturn";
	// 配置文件挂载路径
	private static final String UE_VOLUME_BASE = "/mnt/baidu/app/";
	private static final String CONFIG_MOUNT = UE_VOLUME_BASE+"turnserver.conf:/etc/coturn/turnserver.conf";
	private static final String UE_IMAGE_NAME = "signalling:ue";


	// 固定线程池，用于日志输出处理（最大 8 个线程，对应 3 个 UE 的 stdout/stderr,多给两个 容错）
	private static final ExecutorService logThreadPool = Executors.newFixedThreadPool(8);


	@Autowired
	private LocalProperties localProperties;

	@Value("${local.host}")
	private String localHost;

	@PostConstruct
	public void init() {
		startTurn(); // 启动 TURN 服务
//		startServer(); // 启动第一个UE服务器
	}

	public void startTurn() {
		logger.info("初始化turn服务");
		nowServerIp.set(localHost);
		try {
			runCommand(Arrays.asList("docker", "stop", CONTAINER_NAME), true);
			runCommand(Arrays.asList("docker", "rm", CONTAINER_NAME), true);


			runCommand(
				Arrays.asList("docker", "run", "-d",
				"-p", "15050:3478",
				"-p", "15050:3478/udp",
				"-p", "15051-15099:15051-15099/udp",
				"-v", CONFIG_MOUNT,
				"--network=host",
				"--name=" + CONTAINER_NAME,
				IMAGE_NAME),true);

			logger.info("容器状态检查：");
			runCommand(Arrays.asList("docker", "ps", "-a"),true);
			logger.info("初始化turn服务成功");
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("turn服务执行异常");
		}
	}

	/**
	 * 启动UE、signalling服务
	 * @return
	 */
	public Integer startServer() {
		logger.info("启动UE服务器");
		if(useLinkInfo.size()>=maxLink.get()){
			logger.warn("当前资源数已满，不再启动新实例！");
			return -1;
		}
		Integer port = null;
		try {
			synchronized (postSet) {
				if (postSet.isEmpty()) {
					return null;
				}
				Iterator<Integer> iterator = postSet.iterator();
				port = iterator.next();
				iterator.remove();
			}

			List<String> dockerCmd = Arrays.asList(
				"docker", "run", "-d",
				"--name", "signalling-" + port,
				"-p", port + ":9416",
				"-p", (port + 20) + ":19416",
				"-p", (port + 30) + ":19516",
				"-v", UE_VOLUME_BASE+"uestart.sh:/SignallingWebServer/SignallingWebServer/platform_scripts/bash/start_with_turn.sh",
				"-v", UE_VOLUME_BASE+"common.sh:/SignallingWebServer/SignallingWebServer/platform_scripts/bash/common.sh",
				"-v", UE_VOLUME_BASE+"config.json:/SignallingWebServer/SignallingWebServer/config.json",
				UE_IMAGE_NAME
			);
			runCommand(dockerCmd, true);

			String logFileName = "./log_" + port + "_" + System.currentTimeMillis() + ".log";
			String uePath = localProperties.getPath() + "TwinBaseGH55-Linux-Shipping";
			System.out.println("UE路径:"+uePath);
			logger.info("UE路径:"+uePath);
			List<String> ueCommand = Arrays.asList(
				uePath,
				"-RenderOffScreen",
				"-PixelStreamingURL=ws://" + nowServerIp.get() + ":" + port,
				"-PixelStreamingKeyFilter=F5",
				"-MyWebsocketPort=" + (port + 10),
				"-MyHttpPort=" + (port - 10),
				"-Log=./" + logFileName
			);
			//不实用封装方法runCommand，不适用常驻型进程
			Process ueProcess = new ProcessBuilder(ueCommand).start();
			InputStream inputStream = ueProcess.getInputStream();
			InputStream errorStream = ueProcess.getErrorStream();
			if (inputStream != null) consumeStream(inputStream, "[UE OUT]");
			if (errorStream != null) consumeStream(errorStream, "[UE ERR]");

			long pid = getPidFromProcess(ueProcess);
			portPidInfo.put(port, pid);
			serverLinkInfo.put(port, "2");
			return port;
		} catch (Exception e) {
			if (port != null) {
				synchronized (postSet) {
					postSet.add(port);
				}
				logger.warn("UE 启动失败，端口 {} 已回滚至 postSet", port);
			}
			logger.error("启动 UE 服务失败，port = {}", port, e);
		}
		return port;
	}

	/**
	 * 异步读取 UE 启动进程的输出流（标准输出或错误流）并检测崩溃关键字
	 * @param inputStream UE 进程的输出流（stdout / stderr）
	 * @param prefix 日志前缀标识，例如 "[UE OUT]" 或 "[UE ERR]"
	 */
	private void consumeStream(InputStream inputStream, String prefix) {
		logThreadPool.submit(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				String line;
				while ((line = reader.readLine()) != null) {
					logger.info(prefix + " " + line);

					// 崩溃日志检测（精确匹配标准输出关键字）
					if (line.contains("Engine crash handling finished")) {
						handleCrash();
					}
				}
			} catch (IOException e) {
				logger.error("读取进程输出流异常：" + prefix, e);
			}
		});
	}

	/**
	 * UE 崩溃处理逻辑：触发重启
	 */
	private void handleCrash() {
		logger.warn("检测到 UE 崩溃日志，执行重启逻辑...");
		try {
			statusChangeWebsocketHandler.restartUe();
		} catch (Exception e) {
			logger.error("UE 重启失败", e);
		}
	}


	@PreDestroy
	public void shutdownLogThreadPool() throws InterruptedException {
		logThreadPool.shutdown();
		if (!logThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
			logThreadPool.shutdownNow();
		}
		logger.info("UE日志线程池已关闭");
	}


	private void runCommand(List<String> command, boolean wait) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).start();

		if (wait) {
			int code = process.waitFor();
			String stdout = readStream(process.getInputStream());
			String stderr = readStream(process.getErrorStream());

			if (code != 0) {
				logger.warn("命令执行失败：{}，退出码：{}\nSTDOUT:\n{}\nSTDERR:\n{}", command, code, stdout, stderr);
			} else {
				logger.debug("命令执行成功：{}\nSTDOUT:\n{}", command, stdout);
			}
		}
	}
	private String readStream(InputStream input) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append("\n");
			}
			return sb.toString();
		}
	}


	private long getPidFromProcess(Process process) {
		try {
			// Java 9+ 可直接获取 pid
//			return process.pid();
			Field pidField = process.getClass().getDeclaredField("pid");
			pidField.setAccessible(true);
			return pidField.getLong(process);
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
}
