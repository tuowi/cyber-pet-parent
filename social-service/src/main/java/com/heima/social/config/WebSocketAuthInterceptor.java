package com.heima.social.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器——在 HTTP→WS 升级握手时读取用户身份
 *
 * <h3>为什么用 HandshakeInterceptor 而不是 ChannelInterceptor？</h3>
 *
 * 你的项目中 Gateway 先校验 JWT，然后把 userId 写入 HTTP Header {@code User-Id}，
 * 下游服务通过 UserInfoInterceptor（REST）或本类（WebSocket）直接信任这个 Header。
 * 不需要在 social-service 再解析一次 JWT。
 *
 * HandshakeInterceptor 正好能拿到握手时的 HTTP Header；
 * ChannelInterceptor 拦截的是 STOMP CONNECT 帧，拿 HTTP Header 比较麻烦。
 *
 * <h3>和 UserInfoInterceptor 的关系</h3>
 *
 * <pre>
 * REST 请求:  Gateway → User-Id Header → UserInfoInterceptor(HandlerInterceptor)
 *                                          → UserContextHolder.setUserId()
 *
 * WebSocket:  Gateway → User-Id Header → WebSocketAuthInterceptor(HandshakeInterceptor)
 *                                          → attributes["userId"]
 *                                          → Principal.getName()
 * </pre>
 *
 * 之后 convertAndSendToUser("userId", ...) 内部通过 Principal.getName() 匹配。
 *
 * @author lucy
 * @since 2026-07-24
 */
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    /**
     * 握手之前：从 HTTP Header 读 Gateway 注入的 User-Id 并存到 attributes
     *
     * @param attributes WebSocket session attributes（跨握手和后续通信）
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // Gateway 校验 JWT 后把 userId 放在 User-Id 头里（和给 REST 的一样）
        String userId = request.getHeaders().getFirst("User-Id");

        if (userId != null) {
            attributes.put("userId", userId);
            log.debug("WebSocket 握手: userId={}", userId);
        } else {
            log.warn("WebSocket 握手: User-Id 头缺失，来源IP={}", request.getRemoteAddress());
            // 不拒绝连接（如果后续需要严格校验，这里 return false）
        }

        // 始终返回 true，身份验证由 Gateway 保证
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手完成后无需额外操作
    }
}
