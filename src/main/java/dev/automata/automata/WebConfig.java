package dev.automata.automata;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(new MappingJackson2HttpMessageConverter());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // MCP endpoints — allow any origin (Claude Desktop, AI clients)
        registry.addMapping("/sse")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
        registry.addMapping("/mcp/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
        // Existing frontend CORS
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:5173", "http://raspberry.local:8010", "https://automata.realsubhamgupta.in", "http://automata.realsubhamgupta.in")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

