package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/user/(\\d+)/(balance|orders)$");
    private final UserRepository userRepository;

    @Value("${app.frontend-origin:https://trading-frontend-lolc.onrender.com}")
    private String frontendOrigin;

    public WebSocketConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-trading")
                .setAllowedOrigins(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        frontendOrigin)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                Principal principal = accessor.getUser();
                if (accessor.getCommand() == StompCommand.CONNECT && principal == null) {
                    throw new AccessDeniedException("Authentication is required.");
                }

                if (accessor.getCommand() == StompCommand.SEND) {
                    throw new AccessDeniedException("Client messages are not accepted.");
                }

                if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    String destination = accessor.getDestination();
                    if ("/topic/ticks".equals(destination)) {
                        return message;
                    }

                    Matcher matcher = destination == null ? null : USER_TOPIC.matcher(destination);
                    if (principal == null || matcher == null || !matcher.matches()) {
                        throw new AccessDeniedException("Subscription is not allowed.");
                    }

                    User user = userRepository.findByUsername(principal.getName()).orElse(null);
                    if (user == null || !user.getId().toString().equals(matcher.group(1))) {
                        throw new AccessDeniedException("Subscription is not allowed.");
                    }
                }
                return message;
            }
        });
    }
}