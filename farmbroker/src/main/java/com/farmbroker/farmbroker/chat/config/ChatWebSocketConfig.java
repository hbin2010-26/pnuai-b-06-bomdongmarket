package com.farmbroker.farmbroker.chat.config;

import com.farmbroker.farmbroker.common.config.AllowedOrigins;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AllowedOrigins allowedOrigins;
    private final WebSocketTicketAuthenticationInterceptor authenticationInterceptor;

    public ChatWebSocketConfig(
            @Value("${cors.allowed-origins:" + AllowedOrigins.LOCAL_DEFAULT + "}") String allowedOrigins,
            WebSocketTicketAuthenticationInterceptor authenticationInterceptor) {
        this.allowedOrigins = AllowedOrigins.parse(allowedOrigins);
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }

    // 핸드셰이크는 쿠키 없이 열되 허용 Origin은 REST의 CORS 목록을 그대로 쓴다.
    // 실제 사용자 인증은 첫 STOMP CONNECT 프레임에서 단기 티켓으로 수행한다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOrigins(allowedOrigins.asArray());
    }
}
