package dev.izquierdo.billmind._shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/chat/index.html");
        registry.addRedirectViewController("/chat", "/chat/index.html");
        registry.addRedirectViewController("/chat/", "/chat/index.html");
        registry.addRedirectViewController("/market-rates", "/market-rates.html");
    }
}