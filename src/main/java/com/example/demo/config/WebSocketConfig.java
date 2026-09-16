package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. Стандартна SockJS точка (за фолбек)
        registry.addEndpoint("/ws-trading")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 2. Чиста WebSocket точка (без SockJS overhead)
        registry.addEndpoint("/ws-trading")
                .setAllowedOriginPatterns("*");
    }
}