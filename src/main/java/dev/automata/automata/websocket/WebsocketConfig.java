package dev.automata.automata.websocket;

import dev.automata.automata.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(
                        "http://localhost:5173",
                        "http://localhost:8010",
                        "http://192.168.1.54:8010",
                        "http://raspberry.local:8010",
                        "https://automata.realsubhamgupta.in",
                        "http://automata.realsubhamgupta.in"
                )
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app", "/automata");
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    // ✅ This is the missing piece — validates JWT on every STOMP CONNECT frame
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    // ✅ Log what's arriving to debug
                    System.out.println("WS CONNECT - Auth header: " + authHeader);

                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new org.springframework.messaging.MessageDeliveryException(
                                message, "Missing or malformed Authorization header"
                        );
                    }

                    String token = authHeader.substring(7);

                    String username = jwtService.extractUsername(token);
                    System.out.println("WS CONNECT - Extracted username: " + username);

                    if (username == null) {
                        throw new org.springframework.messaging.MessageDeliveryException(
                                message, "Could not extract username from token"
                        );
                    }

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                    if (!jwtService.isTokenValid(token, userDetails)) {
                        throw new org.springframework.messaging.MessageDeliveryException(
                                message, "Invalid or expired JWT token"
                        );
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    accessor.setUser(authentication);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    System.out.println("WS CONNECT - Auth successful for: " + username);
                }

                return message;
            }
        });
    }
}