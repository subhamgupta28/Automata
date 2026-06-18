package dev.automata.automata.websocket;

import dev.automata.automata.model.Users;
import dev.automata.automata.service.HomeAuthzService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomeTopicSubscriptionInterceptor implements ChannelInterceptor {

    private final HomeAuthzService authzService;

    // Matches: /topic/home/{homeId}/data  or  /topic/home/{homeId}/live  etc.
    private static final Pattern HOME_TOPIC =
            Pattern.compile("^/topic/home/([a-zA-Z0-9_-]+)/.*$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            if (dest == null) return message;

            Matcher m = HOME_TOPIC.matcher(dest);
            if (m.matches()) {
                String homeId = m.group(1);
                Users user = extractUser(accessor);
                if (user == null) {
                    log.warn("SUBSCRIBE rejected — no auth principal for {}", dest);
                    throw new MessageDeliveryException(message,
                            "Not authenticated");
                }
                // Throws 403 if not a member → STOMP subscription is rejected
                authzService.requireAccess(homeId, user.getId());
                log.debug("SUBSCRIBE allowed: user={} homeId={}", user.getId(), homeId);
            }
        }
        return message;
    }

    private Users extractUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof Users u) {
            return u;
        }
        return null;
    }
}
