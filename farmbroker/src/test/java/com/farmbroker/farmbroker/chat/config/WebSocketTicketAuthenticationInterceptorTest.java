package com.farmbroker.farmbroker.chat.config;

import com.farmbroker.farmbroker.security.JwtTokenProvider;
import com.farmbroker.farmbroker.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class WebSocketTicketAuthenticationInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;
    private WebSocketTicketAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userRepository = mock(UserRepository.class);
        interceptor = new WebSocketTicketAuthenticationInterceptor(jwtTokenProvider, userRepository);
    }

    @Test
    @DisplayName("유효한 티켓으로 STOMP CONNECT하면 사용자 Principal을 설정한다")
    void authenticatesConnectWithValidTicket() {
        given(jwtTokenProvider.validateWebSocketTicket("ticket-value")).willReturn(true);
        given(jwtTokenProvider.getUserId("ticket-value")).willReturn(42L);
        given(userRepository.existsByIdAndWithdrawnAtIsNull(42L)).willReturn(true);
        StompHeaderAccessor accessor = connectAccessor("Bearer ticket-value");

        interceptor.preSend(message(accessor), mock(MessageChannel.class));

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("티켓이 없는 STOMP CONNECT는 거절한다")
    void rejectsConnectWithoutTicket() {
        StompHeaderAccessor accessor = connectAccessor(null);

        assertThatThrownBy(() -> interceptor.preSend(
                message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("탈퇴한 사용자의 WebSocket 티켓은 거절한다")
    void rejectsWithdrawnUserTicket() {
        given(jwtTokenProvider.validateWebSocketTicket("ticket-value")).willReturn(true);
        given(jwtTokenProvider.getUserId("ticket-value")).willReturn(42L);
        StompHeaderAccessor accessor = connectAccessor("Bearer ticket-value");

        assertThatThrownBy(() -> interceptor.preSend(
                message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static StompHeaderAccessor connectAccessor(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
