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

    @Test
    @DisplayName("Cấu hình Message Broker hỗ trợ /topic và /app")
    void configureMessageBroker_ConfiguresBrokerAndPrefixes() {
        WebSocketConfig config = new WebSocketConfig(jwtTokenProvider, userDetailsService);

        config.configureMessageBroker(messageBrokerRegistry);

        verify(messageBrokerRegistry).enableSimpleBroker("/topic");
        verify(messageBrokerRegistry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    @DisplayName("ChannelInterceptor xác thực JWT trong CONNECT frame và set user principal")
    void channelInterceptor_AuthenticatesUser_OnConnect() {
        WebSocketConfig config = new WebSocketConfig(jwtTokenProvider, userDetailsService);

        ArgumentCaptor<ChannelInterceptor> captor = ArgumentCaptor.forClass(ChannelInterceptor.class);
        config.configureClientInboundChannel(channelRegistration);
        verify(channelRegistration).interceptors(captor.capture());

        ChannelInterceptor interceptor = captor.getValue();
        assertNotNull(interceptor);

        String token = "valid-token";
        String email = "test@example.com";
        User user = User.builder()
                .id(2L)
                .email(email)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(jwtTokenProvider.parseClaims(token))
                .thenReturn(new JwtClaims(2L, UserRole.USER, 0, Instant.now().plusSeconds(3600)));
        when(userDetailsService.loadUserById(2L)).thenReturn(userDetails);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> processed = interceptor.preSend(message, messageChannel);
        assertNotNull(processed);

        StompHeaderAccessor processedAccessor = StompHeaderAccessor.wrap(processed);
        Authentication auth = (Authentication) processedAccessor.getUser();
        assertNotNull(auth);
        assertEquals(email, auth.getName());
    }
}
