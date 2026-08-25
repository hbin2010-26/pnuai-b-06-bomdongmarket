package com.farmbroker.farmbroker.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AuthenticatedWebSocketMessageInterceptorTest {

    private final AuthenticatedWebSocketMessageInterceptor interceptor =
            new AuthenticatedWebSocketMessageInterceptor();

    @Test
    @DisplayName("Principal이 있는 WebSocket 프레임은 broker로 전달한다")
    void allowsAuthenticatedMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                42L, null, Collections.emptyList()));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = message(accessor);

        assertThat(interceptor.preSend(message, mock(MessageChannel.class)))
                .isSameAs(message);
    }

    @Test
    @DisplayName("Principal이 없는 WebSocket 프레임은 broker 전에 거절한다")
    void rejectsUnauthenticatedMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);

        assertThatThrownBy(() -> interceptor.preSend(
                message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
