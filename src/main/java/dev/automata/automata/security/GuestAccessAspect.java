package dev.automata.automata.security;

import dev.automata.automata.model.Role;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * GuestAccessAspect - Intercepts methods with @DenyGuest annotation
 * Blocks GUEST role users from accessing protected operations
 */
@Aspect
@Component
@RequiredArgsConstructor
public class GuestAccessAspect {

    /**
     * Intercepts methods annotated with @DenyGuest
     * Throws AccessDeniedException if user has GUEST role
     */
    @Before("@annotation(denyGuest)")
    public void checkGuestAccess(JoinPoint joinPoint, DenyGuest denyGuest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.isAuthenticated()) {
            // Check if user principal contains role information
            Object principal = authentication.getPrincipal();
            
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                org.springframework.security.core.userdetails.UserDetails userDetails = 
                    (org.springframework.security.core.userdetails.UserDetails) principal;
                
                // Check if guest role is in authorities
                boolean isGuest = userDetails.getAuthorities().stream()
                        .anyMatch(auth -> auth.getAuthority().equals("ROLE_GUEST"));
                
                if (isGuest) {
                    throw new AccessDeniedException(denyGuest.message());
                }
            }
        }
    }
}
