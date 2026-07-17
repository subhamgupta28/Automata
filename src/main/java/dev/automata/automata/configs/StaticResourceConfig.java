package dev.automata.automata.configs;

import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.EncodedResourceResolver;

import java.util.concurrent.TimeUnit;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                .resourceChain(true)
                .addResolver(new CachingResourceResolver(new ConcurrentMapCache("static-resources")))
                .addResolver(new EncodedResourceResolver());

        registry.addResourceHandler("/index.html", "/")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache());

    }
}
