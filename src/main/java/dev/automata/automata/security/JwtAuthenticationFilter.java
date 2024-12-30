package dev.automata.automata.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
//    private final RequestInfoRepository requestInfoRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

//        String remoteHost = request.getRemoteHost();
//        if (remoteHost.startsWith("192.167.") || remoteHost.startsWith("10.") || remoteHost.startsWith("172.")) {
//            System.err.println("Request from allowed IP range: " + remoteHost);
//        } else {
//            System.err.println("Authentication required for IP: " + remoteHost);
//            response.setHeader("WWW-Authenticate", "Basic realm=\"Restricted Access\"");
//            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
//            return;
//        }


//        var requestInfo = RequestInfo.builder()
//                .requestURI(request.getRequestURI())
//                .remoteAddr(request.getRemoteAddr())
//                .requestURL(request.getRequestURL().toString())
//                .host(request.getRemoteHost())
//                .method(request.getMethod())
//                .queryString(request.getQueryString())
//                .build();
//        requestInfoRepository.save(requestInfo);

        if (request.getServletPath().contains("/api/v1/auth")) {
            filterChain.doFilter(request, response);
            return;
        }
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        jwt = authHeader.substring(7);
        userEmail = jwtService.extractUsername(jwt);
        System.err.println("userEmail: " + userEmail);
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}