package com.baidu.monitoring.conf;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.Map;

public class CustomHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        String clientIp = getClientIp(request);
        attributes.put("clientIp", clientIp);  // 将 IP 存入 attributes 中
        return true; // 返回 true 表示允许握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 可选：握手后逻辑
    }

    /**
     * 提取客户端 IP 地址
     */
    private String getClientIp(ServerHttpRequest request) {
        String xffHeader = request.getHeaders().getFirst("X-Forwarded-For");
        if (xffHeader != null && !xffHeader.isEmpty()) {
            return xffHeader.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return "UNKNOWN";
    }
}
