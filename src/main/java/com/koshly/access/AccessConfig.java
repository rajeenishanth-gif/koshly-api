package com.koshly.access;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the phone and subscription check in front of the money endpoints. */
@Configuration
public class AccessConfig implements WebMvcConfigurer {

    private final AccessService access;

    public AccessConfig(AccessService access) {
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessInterceptor(access))
                .addPathPatterns("/api/**")
                // Still needed while blocked: who am I and why (access), the profile, confirming
                // the phone, deleting the account (all under users/current), and adding a country,
                // which checks the plan itself.
                .excludePathPatterns("/api/access", "/api/users/current", "/api/users/current/**",
                        "/api/account-links", "/api/account-links/**");
    }
}
