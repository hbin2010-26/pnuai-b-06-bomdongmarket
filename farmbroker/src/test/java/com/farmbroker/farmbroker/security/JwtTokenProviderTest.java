package com.farmbroker.farmbroker.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-for-token-purpose-0123456789";

    @Test
    @DisplayName("Access Token과 WebSocket 티켓은 서로의 용도로 검증되지 않는다")
    void separatesAccessTokenAndWebSocketTicket() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86_400_000, 60_000);

        String accessToken = provider.generateToken(7L);
        String websocketTicket = provider.generateWebSocketTicket(7L);

        assertThat(provider.validateToken(accessToken)).isTrue();
        assertThat(provider.validateWebSocketTicket(accessToken)).isFalse();
        assertThat(provider.validateToken(websocketTicket)).isFalse();
        assertThat(provider.validateWebSocketTicket(websocketTicket)).isTrue();
        assertThat(provider.getUserId(websocketTicket)).isEqualTo(7L);
        assertThat(provider.getWebSocketTicketExpiresInSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("만료된 WebSocket 티켓은 거절한다")
    void rejectsExpiredWebSocketTicket() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86_400_000, -1_000);

        assertThat(provider.validateWebSocketTicket(provider.generateWebSocketTicket(7L)))
                .isFalse();
    }
}
