package com.omnibid.config;

import com.omnibid.entity.User;
import com.omnibid.enums.UserRole;
import com.omnibid.enums.UserStatus;
import com.omnibid.security.CustomUserDetails;
import com.omnibid.security.JwtClaims;
import com.omnibid.security.JwtTokenProvider;
import com.omnibid.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private MessageBrokerRegistry messageBrokerRegistry;

    @Mock
    private ChannelRegistration channelRegistration;

    @Mock
    private MessageChannel messageChannel;

    private final AppProperties appProperties = new AppProperties();

    @Test
    @DisplayName("Cấu hình Message Broker v4 chỉ mở /topic")
    void configureMessageBroker_ConfiguresTopicOnly() {
        WebSocketConfig config = new WebSocketConfig(jwtTokenProvider, userDetailsService, appProperties);

        config.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/topic");
    }

    @Test
    @DisplayName("ChannelInterceptor xác thực JWT (userId + ver) khi CONNECT và chặn mọi SEND")
    void channelInterceptor_AuthenticatesOnConnectAndBlocksSend() {
        WebSocketConfig config = new WebSocketConfig(jwtTokenProvider, userDetailsService, appProperties);

        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        config.configureClientInboundChannel(channelRegistration);
        verify(channelRegistration).interceptors(captor.capture());

        ChannelInterceptor interceptor = captor.getValue();
        assertNotNull(interceptor);

        String token = "valid-token";
        User user = User.builder()
                .id(2L)
                .email("test@example.com")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(1)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(jwtTokenProvider.parse(token)).thenReturn(new JwtClaims(2L, UserRole.USER, 1, Instant.now().plusSeconds(3600)));
        when(userDetailsService.loadUserById(2L)).thenReturn(userDetails);

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.addNativeHeader("Authorization", "Bearer " + token);
        connectAccessor.setLeaveMutable(true);
        Message<byte[]> connectMsg = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        Message<?> processed = interceptor.preSend(connectMsg, messageChannel);
        assertNotNull(processed);

        StompHeaderAccessor processedAccessor = StompHeaderAccessor.wrap(processed);
        Authentication auth = (Authentication) processedAccessor.getUser();
        assertNotNull(auth);
        assertEquals("test@example.com", auth.getName());

        // Chặn mọi khung SEND (WebSocket chỉ đọc)
        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setDestination("/app/bid");
        Message<byte[]> sendMsg = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());
        assertThrows(MessageDeliveryException.class, () -> interceptor.preSend(sendMsg, messageChannel));
    }
}
