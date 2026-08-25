package com.farmbroker.farmbroker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// JWT 토큰의 생성 · 파싱 · 검증을 전담하는 컴포넌트.
// subject에 userId만 담아 발급하고, 필터에서 토큰을 받으면 userId를 꺼내
// SecurityContext에 인증 정보를 세팅한다.
// role은 claim에 넣지 않는다 — 역할이 활동에 따라 늘어나는 가변 값이라
// 토큰에 박아두면 발급 직후부터 실제 값과 어긋난다. 권한 판단은 매번 DB의 User를 읽어서 한다.
// secret과 expiration은 application.yml에서 주입받아 하드코딩을 방지한다.
@Component
public class JwtTokenProvider {

    private static final String TOKEN_USE_CLAIM = "token_use";
    private static final String WEBSOCKET_TOKEN_USE = "websocket";

    private final SecretKey signingKey;
    private final long expiration;
    private final long websocketTicketExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.websocket-ticket-expiration:60000}") long websocketTicketExpiration
    ) {
        // jjwt 0.12.x: Keys.hmacShaKeyFor()로 SecretKey 생성
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.websocketTicketExpiration = websocketTicketExpiration;
    }

    // 토큰 생성 — subject: userId(String)
    public String generateToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(signingKey)
                .compact();
    }

    // 브라우저가 Render WebSocket에 직접 연결할 때 STOMP CONNECT에서만 쓰는 단기 티켓입니다.
    // token_use를 분리해 이 값이 일반 REST Bearer 토큰으로 재사용되지 않게 합니다.
    public String generateWebSocketTicket(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TOKEN_USE_CLAIM, WEBSOCKET_TOKEN_USE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + websocketTicketExpiration))
                .signWith(signingKey)
                .compact();
    }

    public int getWebSocketTicketExpiresInSeconds() {
        return Math.toIntExact(websocketTicketExpiration / 1000);
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    // 토큰 유효성 검증 — 만료 · 위변조 · 형식 오류 모두 false 반환
    public boolean validateToken(String token) {
        try {
            return getClaims(token).get(TOKEN_USE_CLAIM) == null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateWebSocketTicket(String token) {
        try {
            return WEBSOCKET_TOKEN_USE.equals(getClaims(token).get(TOKEN_USE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        // jjwt 0.12.x 신 API: parser().verifyWith(key).build().parseSignedClaims()
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
