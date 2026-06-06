package dev.automata.automata.security;

import dev.automata.automata.model.Role;
import dev.automata.automata.model.Users;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * GuestAccessFilter - Checks if guest users are trying to access protected endpoints
 * Blocks all non-GET requests for GUEST role users
 * Allows GET requests for read-only access
 * <p>
 * This filter works in conjunction with JwtAuthenticationFilter which sets the Authentication
 */
@Component
@RequiredArgsConstructor
public class GuestAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Get the current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            // Check if principal is a Users object (guest user check)
            if (principal instanceof Users user) {

                // Block guests from write operations (non-GET requests)
                if (user.getRole() == Role.GUEST) {
                    String method = request.getMethod();

                    // Allow GET requests for read-only access
                    if (!method.equals("GET")) {
                        // Send 403 Forbidden response
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\":\"Guests cannot perform write operations\"}");
                        return;
                    }
                }
            }
        }

        // Continue with the filter chain
        filterChain.doFilter(request, response);
    }
}
