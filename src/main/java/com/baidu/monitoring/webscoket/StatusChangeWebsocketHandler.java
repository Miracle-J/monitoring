package com.baidu.monitoring.webscoket;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baidu.monitoring.init.ServerInitializer;
import com.baidu.monitoring.util.CommonUtils;
import com.baidu.monitoring.util.IPUtil;
import com.baidu.monitoring.util.R;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

import static com.baidu.monitoring.util.SessionManager.*;


@Component
public class StatusChangeWebsocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerInitializer.class);

    // 下载目录通过配置文件传入
    @Value("${DownloadDir}")
    private String downloadDir;

    // 自定义 OkHttpClient：连接池配置
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES)) // 最多20个连接，5分钟空闲回收
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)  // 支持大文件
            .build();

    // 下载线程池：限并发 + 防止系统负载
    private final ExecutorService downloadExecutor = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdownDownloadExecutor() throws InterruptedException {
        downloadExecutor.shutdown();
        if (!downloadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
            downloadExecutor.shutdownNow();
        }
        logger.info("下载模型线程池线程池已关闭");
    }


    /**
     * 创建websocket连接
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("连接已建立：" + session.getId() + ";+" + IPUtil.getLocalIpAddress());
        String pathQuery = session.getUri().getQuery();
        Map<String, String> paramMap = CommonUtils.formatUrlParams(pathQuery);

        String ts = paramMap.get("ts");
        String realName = paramMap.get("realName");
        String userName = paramMap.get("userName");
        //生成sessionKey 以前的命名规则 我也很懵逼为啥用^^^？？？
        String sessionKey = realName + "^^^" + userName + "^^^" + ts;
        String clientIp = (String) session.getAttributes().get("clientIp");
        useIpInfo.put(sessionKey, clientIp);
        if (sessions.get(sessionKey) == null) {
            sessions.put(sessionKey, session);
        }
        //此处去掉了启动一个空闲UE逻辑
        //如果用户没有使用，则加入排队队列，等待启动ue
        if (useLinkInfo.get(sessionKey) == null) {
            queueLinkInfo.add(sessionKey);
            broadcast(buildMessage().toJSONString());
        }
    }


    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 这里可以根据 message 内容响应客户端
        String payload = message.getPayload();
        JSONObject wsMessage = JSONObject.parseObject(payload);
        // 假设收到 ping，就回 pong
        if (wsMessage.getString("type").equals("ping")) {
            session.sendMessage(new TextMessage("{\"type\": \"pong\"}"));
            lastHeartbeat.put(session.getId(), System.currentTimeMillis());
        }
    }

    public void changeUeStatus(String input) throws IOException {
        Integer uePort = Integer.valueOf(input) + 10;
        logger.info("ue启动完成，端口：" + uePort);
        serverLinkInfo.put(uePort, "0");
    }

    /**
     * 断开连接后
     *
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String pathQuery = session.getUri().getQuery();
        Map<String, String> paramMap = CommonUtils.formatUrlParams(pathQuery);
        /**
         * 为什么不重session.getAttributes()中提前绑定参数？ 健壮性不行
         */
        String ts = paramMap.get("ts");
        String realName = paramMap.get("realName");
        String userName = paramMap.get("userName");

        String sessionKey = realName + "^^^" + userName + "^^^" + ts;
        logger.info("断开长连接sessionKey :" + sessionKey);

        sessions.remove(sessionKey);
        useIpInfo.remove(sessionKey);
        queueLinkInfo.remove(sessionKey);

        //执行断开逻辑
        Integer port = useLinkInfo.get(sessionKey);
        if (port != null) {
            logger.info("断开链接:" + port);
            useLinkInfo.remove(sessionKey);
            killPortServer(port);
        }
        broadcast(buildMessage().toJSONString());
    }

    /**
     * 下载模型文件
     *
     * @param input
     * @return
     */
    public R ueForward(JSONObject input) {
        logger.info("进入接口："+input);
        // 创建下载目录（如果不存在）
        File dir = new File(downloadDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                logger.info("下载目录不存在，已创建：" + downloadDir);
            } else {
                logger.error("下载目录创建失败：" + downloadDir);
                return R.fail("无法创建下载目录，请检查权限");
            }
        }

        List<String> fileUrls = input.getJSONArray("list").toJavaList(String.class);
        String callUrl = input.getString("callUrl").replaceAll(" ", "");

        CountDownLatch latch = new CountDownLatch(fileUrls.size());
        boolean allExist = true;

        for (String fileUrl : fileUrls) {
            String fileName = extractFileName(fileUrl);
            File outFile = new File(downloadDir, fileName);

            if (outFile.exists()) {
                logger.info("文件已存在，跳过下载：" + fileName);
                latch.countDown();
            } else {
                allExist = false;
                downloadExecutor.submit(() -> {
                    try {
                        downloadFile(fileUrl, outFile);
                    } catch (Exception e) {
                        logger.error("下载失败: " + fileUrl);
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        if (allExist) {
            // 所有文件已存在，延迟500ms后回调
            downloadExecutor.submit(() -> {
                try {
                    Thread.sleep(500);  // 不阻塞主线程
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                notifyCallback(callUrl + "1");
            });
        } else {
            // 有文件需下载，下载后再延迟500ms后回调
            downloadExecutor.submit(() -> {
                try {
                    latch.await(); // 等所有下载完成
                    Thread.sleep(500); // 延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                notifyCallback(callUrl + "1");
            });
        }
        return R.ok("下载任务已提交");
    }

    private void downloadFile(String fileUrl, File outFile) throws IOException {
        Request request = new Request.Builder().url(fileUrl.trim()).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("下载失败: " + response);
            }

            try (InputStream in = response.body().byteStream();
                 FileOutputStream fos = new FileOutputStream(outFile)) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }
        }
    }

    private void notifyCallback(String callbackUrl) {
        Request request = new Request.Builder().url(callbackUrl).get().build();
        try (Response response = client.newCall(request).execute()) {
            logger.info("回调成功: " + callbackUrl + "，状态码: " + response.code());
        } catch (IOException e) {
            logger.error("回调失败: " + callbackUrl);
            e.printStackTrace();
        }
    }

    private String extractFileName(String url) {
        try {
            URL uri = new URL(url.trim());
            String[] parts = uri.getPath().split("/");
            if (parts.length >= 2) {
                // 倒数第二段：文件指纹
                String idPart = parts[parts.length - 2];
                // 倒数第一段：原始文件名
                String lastName = parts[parts.length - 1];
                int dot = lastName.lastIndexOf('.');
                if (dot > 0 && dot < lastName.length() - 1) {
                    // 包含点的后缀，例如 ".fbx"
                    String ext = lastName.substring(dot).toLowerCase();
                    return idPart + ext;
                } else {
                    // 无后缀则只返回 ID
                    return idPart;
                }
            }
            // 回退：直接按原逻辑
            String name = new File(uri.getPath()).getName();
            int dot = name.lastIndexOf('.');
            return (dot > 0
                    ? name.substring(0, dot) + name.substring(dot)
                    : name);
        } catch (MalformedURLException e) {
            throw new RuntimeException("解析文件名失败：" + url, e);
        }
    }



    /**
     * 关闭指定端口UE+signalling（回话断开链接后执行）
     */
    public Integer killPortServer(Integer port) {
        try {
            // 容器名
            String containerName = "signalling-" + port;

            // Step 1: 优雅停止容器
            List<String> stopCmd = Arrays.asList("docker", "stop", containerName);
            ProcessBuilder stopBuilder = new ProcessBuilder(stopCmd);
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor(); // 等待执行完成

            // Step 2: 删除容器
            List<String> rmCmd = Arrays.asList("docker", "rm", "-f", containerName);
            ProcessBuilder rmBuilder = new ProcessBuilder(rmCmd);
            rmBuilder.start();

            // Step 3: 杀掉仍然存在的进程（兜底）
            Long pid = portPidInfo.get(port);
            if (pid != null) {
                Process killProcess = new ProcessBuilder("kill", "-9", pid.toString()).start();
                int killExitCode = killProcess.waitFor();

                if (killExitCode == 0) {
                    logger.info("成功关闭UE，进程 " + pid + "   端口：" + port);
                } else {
                    logger.error("关闭UE失败，进程 " + pid + ", exit code: " + killExitCode + "   ，端口：" + port);
                }
            } else {
                System.err.println("没有找到port对应的pid " + port);
            }

            // Step 4: 清理缓存
            portPidInfo.remove(port);
            serverLinkInfo.remove(port);
            postSet.add(port);

            // 移除正在使用该端口的连接
            for (Map.Entry<String, Integer> entry : useLinkInfo.entrySet()) {
                String s = entry.getKey();
                Integer assignedPort = entry.getValue();

                if (port.equals(assignedPort)) {
                    // 原子操作：只在当前映射仍然是目标端口时才移除
                    useLinkInfo.remove(s, port);
                }
            }
            //广播消息
            broadcast(buildMessage().toJSONString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return port;
    }

    /**
     * 关闭指定端口signalling 关闭信令
     */
    public Integer killSignallingPortServer(Integer port) {
        try {
            // 容器名
            String containerName = "signalling-" + port;

            // Step 1: 优雅停止容器
            List<String> stopCmd = Arrays.asList("docker", "stop", containerName);
            ProcessBuilder stopBuilder = new ProcessBuilder(stopCmd);
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor(); // 等待执行完成

            // Step 2: 删除容器
            List<String> rmCmd = Arrays.asList("docker", "rm", "-f", containerName);
            ProcessBuilder rmBuilder = new ProcessBuilder(rmCmd);
            rmBuilder.start();


            // Step 4: 清理缓存
            portPidInfo.remove(port);
            serverLinkInfo.remove(port);
            postSet.add(port);

            // 移除正在使用该端口的连接
            for (Map.Entry<String, Integer> entry : useLinkInfo.entrySet()) {
                String s = entry.getKey();
                Integer assignedPort = entry.getValue();

                if (port.equals(assignedPort)) {
                    // 原子操作：只在当前映射仍然是目标端口时才移除
                    useLinkInfo.remove(s, port);
                    startedSessions.remove(s);
                    queueLinkInfo.add(0, s);
                }
            }
            //广播消息
            broadcast(buildMessage().toJSONString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return port;
    }

    /**
     * 广播消息
     *
     * @param message
     * @throws IOException
     */
    public void broadcast(String message) throws IOException {
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        }
    }

    /**
     * 构建消息
     *
     * @return
     */
    public JSONObject buildMessage() {
        JSONObject message = new JSONObject();

        List<JSONObject> useLinkList = new ArrayList<>();
        List<JSONObject> queueLinkList = new ArrayList<>();

        for (String s : useLinkInfo.keySet()) {
            JSONObject json = new JSONObject();
            json.put("sessionKey", s);
            json.put("ueHttpPort", useLinkInfo.get(s) - offsetValue - 10);
            json.put("webSocketPort", useLinkInfo.get(s) - offsetValue + 10);
            json.put("signallingPort", useLinkInfo.get(s) - offsetValue + 20);
            json.put("clientIp", useIpInfo.get(s));

            String[] strings = s.split("\\^\\^\\^");
            String realName = strings.length >= 2 ? strings[0] : null;
            String userName = strings.length >= 2 ? strings[1] : null;

            json.put("realName", realName);
            json.put("userName", userName);
            json.put("ts", Long.valueOf(s.substring(s.lastIndexOf("^^^") + 3)));

            useLinkList.add(json);
        }

        for (String s : queueLinkInfo) {
            JSONObject json = new JSONObject();
            json.put("sessionKey", s);
            json.put("ueHttpPort", -1);
            json.put("webSocketPort", -1);
            json.put("signallingPort", -1);
            json.put("clientIp", useIpInfo.get(s));

            String[] strings = s.split("\\^\\^\\^");
            String realName = strings.length >= 2 ? strings[0] : null;
            String userName = strings.length >= 2 ? strings[1] : null;

            json.put("realName", realName);
            json.put("userName", userName);
            json.put("ts", Long.valueOf(s.substring(s.lastIndexOf("^^^") + 3)));

            queueLinkList.add(json);
        }

        message.put("useLinkList", useLinkList);
        message.put("queueLinkList", queueLinkList);
        message.put("maxLink", maxLink);
        return message;
    }

    /**
     * UE 崩溃后自动重启逻辑：
     * - 遍历所有记录中的端口
     * - 若端口可用（空闲），则重启对应 UE 实例
     */
    public void restartUe() {
        //循环UEpid 看崩溃的是哪个端口
        for (Map.Entry<Integer, Long> entry : portPidInfo.entrySet()) {
            Integer port = entry.getKey();
            Long pid = entry.getValue();
            logger.info("渲染===Port: {} -> PID: {}", port, pid);
            boolean processAlive = isProcessAlive(pid);
            if(!processAlive){
                logger.warn("崩溃UE为PID：{}", pid);
                //当前崩溃用户应该排在第一个等待用户，只需等待UE重启即可，不要再排在末尾了
                useLinkInfo.forEach((sessionKey, uPort) -> {
                    // 在这里对 sessionKey 和 port 做你需要的操作
                    logger.info("sessionKey=" + sessionKey + ", port=" + uPort);
                    //因为已经崩溃，从使用队列中移除，添加到第一个排队队列中
                    if (uPort.equals(port)) {
//                        原子操作删除
//                        useLinkInfo.remove(sessionKey, uPort);
                        //添加到队列（插队）
                        queueLinkInfo.add(0, sessionKey);
                    }
                });
                killPortServer(port);
            }

        }
    }


    private final SystemInfo si = new SystemInfo();
    private final OperatingSystem os = si.getOperatingSystem();
    /**
     * 判断给定 pid 的进程目录是否存在
     * @param pid 进程号
     * @return 存在返回 true，否则 false
     */
    public boolean isProcessAlive(long pid) {
        OSProcess process = os.getProcess((int) pid);
        return process != null && process.getState() != OSProcess.State.INVALID;
    }

    /**
     * 等待端口完全释放（即可用），最大等待 maxWaitMs 毫秒
     *
     * @return true = 端口空闲；false = 超时仍被占用
     */
    private boolean waitForPortRelease(int port, int maxWaitMs) {
        int interval = 500;
        int waited = 0;

        while (waited < maxWaitMs) {
            if (isPortFree(port)) {
                return true;
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            waited += interval;
        }
        return false;
    }

    /**
     * 检查端口是否可用（未被占用），通过尝试绑定 ServerSocket 实现
     *
     * @return true = 可以绑定（空闲）；false = 绑定失败（被占用）
     */
    private boolean isPortFree(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }


}
