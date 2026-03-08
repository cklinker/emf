package io.kelta.worker.config;

import io.kelta.worker.interceptor.SpanIdCaptureInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
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
