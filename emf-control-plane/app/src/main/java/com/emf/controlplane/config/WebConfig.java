package com.emf.controlplane.config;

import com.emf.controlplane.tenant.TenantFilterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 * Registers the TenantFilterInterceptor for automatic Hibernate tenant filtering.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;

    public WebConfig(TenantFilterInterceptor tenantFilterInterceptor) {
        this.tenantFilterInterceptor = tenantFilterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor)
                .addPathPatterns("/control/**")
                .excludePathPatterns(
                        "/control/bootstrap",
                        "/control/tenants/slug-map",
                        "/control/ui-bootstrap",
                        "/control/tenants/**",
                        "/control/workers/**",
                        "/control/metrics/**",
                        "/internal/**"
                );
    }
}
