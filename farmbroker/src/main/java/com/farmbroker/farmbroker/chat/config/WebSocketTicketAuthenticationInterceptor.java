package com.farmbroker.farmbroker.chat.config;

import com.farmbroker.farmbroker.security.JwtTokenProvider;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;

// WebSocket 핸드셰이크는 쿠키 없이 Render에 직접 연결하고, 실제 사용자 인증은
// 첫 STOMP CONNECT/STOMP 프레임의 짧은 수명 티켓으로 완료합니다.
@Component
@RequiredArgsConstructor
public class WebSocketTicketAuthenticationInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command != StompCommand.CONNECT && command != StompCommand.STOMP) {
            return message;
        }

        String ticket = resolveBearerTicket(accessor);
        if (!StringUtils.hasText(ticket) || !jwtTokenProvider.validateWebSocketTicket(ticket)) {
            throw new AccessDeniedException("유효한 WebSocket 연결 티켓이 필요합니다.");
        }

        Long userId = jwtTokenProvider.getUserId(ticket);
        if (!userRepository.existsByIdAndWithdrawnAtIsNull(userId)) {
            throw new AccessDeniedException("유효한 WebSocket 연결 티켓이 필요합니다.");
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                userId, null, Collections.emptyList()));
        return message;
    }

    private String resolveBearerTicket(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
