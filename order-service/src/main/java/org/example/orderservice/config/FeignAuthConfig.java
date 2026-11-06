
package org.example.orderservice.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignAuthConfig {

    @Bean
    public RequestInterceptor jwtForwardingInterceptor() {
        return template -> {
            var attributes =
                    RequestContextHolder.getRequestAttributes();

            if (!(attributes instanceof ServletRequestAttributes servlet)) {
                return;
            }

            HttpServletRequest request = servlet.getRequest();

            String authorization =
                    request.getHeader("Authorization");

            if (authorization != null
                    && authorization.startsWith("Bearer ")) {
                template.header("Authorization", authorization);
            }
        };
    }
}
