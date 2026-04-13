package io.kelta.worker.config;

import io.kelta.worker.interceptor.SpanIdCaptureInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("!migrate")   // excluded from the migrate profile — no web server runs in that mode
public class WebMvcConfig implements WebMvcConfigurer {

    private final SpanIdCaptureInterceptor spanIdCaptureInterceptor;

    public WebMvcConfig(SpanIdCaptureInterceptor spanIdCaptureInterceptor) {
        this.spanIdCaptureInterceptor = spanIdCaptureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(spanIdCaptureInterceptor);
    }
}
