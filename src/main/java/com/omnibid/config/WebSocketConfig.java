package com.omnibid.config;

import com.omnibid.security.CustomUserDetails;
import com.omnibid.security.JwtClaims;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.security.JwtValidationException;
import com.omnibid.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Pattern ALLOWED_SUBSCRIBE_PATTERN = Pattern.compile("^/topic/auctions/\\d+$");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final AppProperties appProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(appProperties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) {
                    return message;
                }

                StompCommand command = accessor.getCommand();
                if (StompCommand.CONNECT.equals(command)) {
                    handleConnect(message, accessor);
                } else if (StompCommand.SUBSCRIBE.equals(command)) {
                    handleSubscribe(message, accessor);
                } else if (StompCommand.SEND.equals(command)) {
                    throw new MessageDeliveryException(message, "Client SEND frames are not allowed");
                }
                return message;
            }
        });
    }

    private void handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader)) {
            return;
        }
        if (!authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException(message, "Invalid Authorization header");
        }
        String token = authHeader.substring(7).trim();
        try {
            JwtClaims claims = jwtTokenProvider.parse(token);
            CustomUserDetails userDetails = userDetailsService.loadUserById(claims.userId());
            if (claims.ver() != userDetails.getTokenVersion()) {
                throw new MessageDeliveryException(message, "JWT token has been invalidated");
            }
            if (userDetails.isBanned()) {
                throw new MessageDeliveryException(message, "Account is banned");
            }
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
        } catch (JwtValidationException | UsernameNotFoundException ex) {
            throw new MessageDeliveryException(message, "Invalid JWT token", ex);
        }
    }

    private void handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !ALLOWED_SUBSCRIBE_PATTERN.matcher(destination).matches()) {
            throw new MessageDeliveryException(message, "Subscription destination not allowed: " + destination);
        }
    }
}
