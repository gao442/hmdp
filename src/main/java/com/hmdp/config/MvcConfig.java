package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private LoginInterceptor loginInterceptor;
    @Resource
    private RefreshTokenInterceptor refreshTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(refreshTokenInterceptor)
                .order(0);

        registry.addInterceptor(loginInterceptor)
                .excludePathPatterns(
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                )
                .order(1);



    }
}
