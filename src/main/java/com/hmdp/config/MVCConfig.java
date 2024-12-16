package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MVCConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // token刷新拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).excludePathPatterns(
                "/user/login",
                "/user/code"
        ).order(0);

        // 登录拦截器
        // 添加拦截器并对不需要用到拦截器的页面进行放行
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns(
                "/user/login",
                "/user/code",
                "/shop/**",
                "/blog/hot",
                "/shop-type/**",
                "/voucher/**"
        ).order(1);
    }
}
