package com.koshly.ratelimit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the per-person rate limit in front of every /api endpoint. /actuator/health is not limited. */
@Configuration
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor interceptor;

    public RateLimitConfig(RateLimitSettings settings) {
        this.interceptor = new RateLimitInterceptor(
                new RateLimiter(settings.readsPerMinute()),
                new RateLimiter(settings.writesPerMinute()));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
