package dev.automata.automata.configs;

import dev.automata.automata.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
//@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;


    private static final String[] WHITE_LIST_URL = {
            "/api/v1/auth/**",
            "/api/v1/auth/register/**",
            "/api/v1/auth/authenticate/**",
            "/api/v1/auth/refresh-token/**",
            "/assets/**",
            "/index.html",
            "/favicon.ico",
            "/logo.svg",
            "/vite.svg",
            "/marker-icon-2x.png",
            "/marker-shadow.png",
            "/", // for root
            "/api/automation/validate/**", // for root
            "/devices/**", // for root
            "/signin/**", // for root
            "/signup/**", // for root
            "/dashboard/**", // for root
            "/virtual/**", // for root
            "/configure/**", // for root
            "/actions/**", // for root
            "/analytics/**", // for root
            "/spotify/**", // for root
            "/actuator/metrics/**", // for root
            "/ws/**",
            "/ws",
            "/webhook/**",
            "/exp",
//            "/api/v1/virtual/**",
            "/api/v1/main/updateDevice",
//            "/api/v1/main/automations/**",
            "/api/v2/automations/**",
            "/api/v1/action/**",
            "/api/v1/main/masterList",
            "/api/v1/action/send/**",
            "/audio",
            "/app/**",
            "/topic/**",
            "/queue/**",
            "/user/**",
            "/api/v1/main/register/**",
            "/api/v1/main/serverCreds/**",
            "/api/v1/action/sendAction/**",
            "/api/v1/utils/test/**",
            "/api/v1/main/healthCheck/**",
            // MCP (Model Context Protocol) endpoints
            "/sse",
            "/mcp/**",
    };

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final LogoutHandler logoutHandler;

    private static final String[] LOCAL_NETWORK_IPS = {
            "localhost", "127.0.0.1", "192.168.*.*" // Include your local network CIDR
    };

    private static AuthorizationManager<RequestAuthorizationContext> hasIpAddress() {
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            for (String ipAddress : LOCAL_NETWORK_IPS) {
                IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ipAddress);
                if (ipAddressMatcher.matches(request)) {
                    return new AuthorizationDecision(true);
                }
            }
            return new AuthorizationDecision(false);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req ->
                        req.requestMatchers(WHITE_LIST_URL)
                                .permitAll()
                                .anyRequest()
                                .authenticated()

                )

                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout ->
                        logout.logoutUrl("/api/v1/auth/logout")
                                .addLogoutHandler(logoutHandler)
                                .logoutSuccessHandler((request, response, authentication) -> SecurityContextHolder.clearContext())
                )
        ;

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:8010",
                "http://192.168.1.54:8010",
                "http://raspberry.local:8010",
                "https://automata.realsubhamgupta.in",
                "http://automata.realsubhamgupta.in"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
//    @Bean
//    CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//
//        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
//        configuration.setAllowedMethods(List.of("GET","POST"));
//        configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//
//        source.registerCorsConfiguration("/**",configuration);
//
//        return source;
//    }
}