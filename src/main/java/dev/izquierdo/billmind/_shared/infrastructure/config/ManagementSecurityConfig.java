package dev.izquierdo.billmind._shared.infrastructure.config;

import dev.izquierdo.billmind._shared.domain.port.ExternalAuthPort;
import dev.izquierdo.billmind._shared.infrastructure.auth.ManagementHealthAuthFilter;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registered into the Actuator management child context (separate internal port) via
 * {@code META-INF/spring/...ManagementContextConfiguration.imports}. Only active when the
 * management port differs from the application port, so it never touches the public app port.
 */
@ManagementContextConfiguration(ManagementContextType.CHILD)
public class ManagementSecurityConfig {

    @Bean
    public FilterRegistrationBean<ManagementHealthAuthFilter> managementHealthAuthFilter(
            ExternalAuthPort externalAuthPort) {
        FilterRegistrationBean<ManagementHealthAuthFilter> registration =
                new FilterRegistrationBean<>(new ManagementHealthAuthFilter(externalAuthPort));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}