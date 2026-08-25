package com.farmbroker.farmbroker.chat.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

// 티켓 인터셉터가 놓친 연결 방식이나 이후 프레임에서 Principal이 없으면 broker에 도달하기 전에 막습니다.
// 인증과 인가를 별도 계층으로 두어 CONNECT 검사 하나가 실수로 빠져도 구독·전송이 열리지 않게 합니다.
@Component
public class AuthenticatedWebSocketMessageInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, SimpMessageHeaderAccessor.class);
        if (accessor == null || accessor.getUser() == null) {
            throw new AccessDeniedException("인증된 WebSocket 연결이 필요합니다.");
        }
        return message;
    }
}
