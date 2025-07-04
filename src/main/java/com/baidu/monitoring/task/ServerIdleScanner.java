package com.baidu.monitoring.task;

import com.alibaba.fastjson.JSONObject;
import com.baidu.monitoring.init.ServerInitializer;
import com.baidu.monitoring.webscoket.StatusChangeWebsocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.baidu.monitoring.util.SessionManager.*;

@Component
public class ServerIdleScanner {

    private static final Logger logger = LoggerFactory.getLogger(ServerIdleScanner.class);


    @Autowired
    private StatusChangeWebsocketHandler statusChangeWebsocketHandler;
    @Autowired
    @Lazy
    private ServerInitializer startServer;

    /**
     * 每秒扫描一次 serverLinkInfo：
     * - 有排队用户时，把空闲端口分配给用户
     * - 队列为空 & 端口空闲超过5秒时，自动回收
     */
    @Scheduled(fixedDelay = 1_000)
    public void scanIdlePorts() throws IOException {
        long now = System.currentTimeMillis();

        // 收集所有当前空闲的端口
        List<Integer> idlePorts = serverLinkInfo.entrySet().stream()
                .filter(e -> "0".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!idlePorts.isEmpty()) {
            logger.info("发现空闲端口: {}", idlePorts);

            if (!queueLinkInfo.isEmpty()) {
                // 有排队用户，正常分配
                String sessionKey = queueLinkInfo.remove(0);
                for (Integer port : idlePorts) {
                    // 分配第一个空闲端口
                    serverLinkInfo.put(port, "1");
                    useLinkInfo.put(sessionKey, port);
                    //已分配端口移除
                    startedSessions.remove(sessionKey);
                    idleSince.remove(port);
                    break;
                }
                statusChangeWebsocketHandler.broadcast(statusChangeWebsocketHandler.buildMessage().toJSONString());
            } else {
                // 无排队用户，检查空闲时长，超过20秒就回收
                for (Integer port : idlePorts) {
                    idleSince.compute(port, (p, startTs) -> {
                        if (startTs == null) {
                            // 首次检测到空闲，记下时间
                            return now;
                        } else if (now - startTs > 3_000) {
                            // 已空闲超过5秒，回收
                            logger.info("端口 {} 空闲超过3秒，自动回收", p);
                            statusChangeWebsocketHandler.killPortServer(p);
                            // 从 idleSince 中移除，并在 serverLinkInfo 中也清理
                            return null;
                        }
                        // 仍在等待期内，保留原时间
                        return startTs;
                    });
                }
            }
        } else {
            logger.debug("当前无空闲端口");
            // 同时清理 idleSince 中已不在 serverLinkInfo 的端口
            idleSince.keySet().removeIf(p -> !serverLinkInfo.containsKey(p));
        }
    }

    /**
     * 每秒扫描一次 serverLinkInfo：
     * - 有排队用户时，把空闲端口分配给用户
     * - 队列为空 & 端口空闲超过5秒时，自动回收
     */
    @Scheduled(fixedDelay = 1_000)
    public void scanCollapsePorts() throws IOException {
        long now = System.currentTimeMillis();

        // 收集所有当前空闲的端口
        List<Integer> idlePorts = serverLinkInfo.entrySet().stream()
                .filter(e -> "2".equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!idlePorts.isEmpty()) {
            logger.info("发现崩溃端口: {}", idlePorts);

            for (Integer port : idlePorts) {
                idleSince.compute(port, (p, startTs) -> {
                    if (startTs == null) {
                        // 首次检测到启动，记下时间
                        return now;
                    } else if (now - startTs > 10_000) {
                        // 启动超过10秒，回收
                        logger.info("端口 {} 启动超过10秒，自动回收", p);
                        statusChangeWebsocketHandler.killPortServer(p);
                        startedSessions.pollLast();  // 删除并返回最后一个
                        // 从 idleSince 中移除，并在 serverLinkInfo 中也清理
                        return null;
                    }
                    // 仍在等待期内，保留原时间
                    return startTs;
                });
            }
        }
    }


    /**
     * 每 2 秒检查一次 排队队列 有人排队2秒创建一个UE
     */
    @Scheduled(fixedRate = 2_000)
    public void createUe() {
        int availableSlots = maxLink.get() - serverLinkInfo.size();
        if (availableSlots <= 0 || queueLinkInfo.isEmpty()) {
            logger.debug("无排队用户或无空闲端口");
            return;
        }
        for (String session : queueLinkInfo) {
            // 如果已经启动过，跳过 //TODO
            if (startedSessions.contains(session)) {
                continue;
            }
            // 标记为已启动
            startedSessions.addLast(session);
            // 启动 UE
            logger.info("为排队用户 {} 启动 UE", session);
            startServer.startServer();
            break;  // 每个周期只启动一个
        }
    }

    /**
     * 每 3 秒检查一次 WebSocketSession 的心跳状态
     */
    @Scheduled(fixedRate = 3_000)
    public void checkTimeoutSessions() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : sessions.values()) {
            Long lastTime = lastHeartbeat.get(session.getId());
            if (lastTime != null && (now - lastTime > 30000)) {
                logger.warn("发现超时连接，主动关闭: " + session.getId());
                try {
                    lastHeartbeat.remove(session.getId());
                    session.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
