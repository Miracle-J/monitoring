package com.baidu.monitoring.task;

import com.alibaba.fastjson.JSONObject;
import com.baidu.monitoring.webscoket.StatusChangeWebsocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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


    /**
     * 每秒扫描一次 serverLinkInfo，找出 value 为 "0" 的空闲端口 分配给排队用户
     */
    @Scheduled(fixedDelay = 1000)
    public void scanIdlePorts() throws IOException {
        List<Integer> idlePorts = serverLinkInfo.entrySet().stream()
                .filter(entry -> "0".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (!idlePorts.isEmpty()) {
            logger.info("发现空闲端口: {}", idlePorts);
            if (!queueLinkInfo.isEmpty()) {
                // 从队列中取出第一个元素，并广播消息
                String sessionKey = queueLinkInfo.remove(0);
                for (Integer port : serverLinkInfo.keySet()) {
                    if(serverLinkInfo.get(port).equals("0")){
                        // 修改端口状态
                        serverLinkInfo.put(port, "1");
                        // 向正在使用的用户队列中添加信息（给用户分配端口）
                        useLinkInfo.put(sessionKey, port);
                        // 找到一个以后就结束遍历
                        break;
                    }
                }
                //广播消息告诉别人第N个用户使用了哪个端口
                statusChangeWebsocketHandler.broadcast(statusChangeWebsocketHandler.buildMessage().toJSONString());
            }
        } else {
            logger.debug("当前无空闲端口");
        }
    }

    /**
     * 每 20 秒检查一次 WebSocketSession 的心跳状态
     */
    @Scheduled(fixedRate = 20000)
    public void checkTimeoutSessions() {
        long now = System.currentTimeMillis();
        for (WebSocketSession session : sessions.values()) {
            Long lastTime = lastHeartbeat.get(session.getId());
            if (lastTime != null && (now - lastTime > 30000)) {
                System.out.println("发现超时连接，主动关闭: " + session.getId());
                try {
                    session.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
