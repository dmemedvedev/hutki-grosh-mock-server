package by.hgrosh.mockserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;

@SpringBootApplication
public class MockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

    @Bean
    public org.springframework.web.client.RestTemplate restTemplate() {
        return new org.springframework.web.client.RestTemplate();
    }

    @Component
    public static class DiagnosticFilter implements Filter {
        private static final Logger log = LoggerFactory.getLogger("DIAGNOSTIC");

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            
            log.info(">>>> [SYSTEM-DIAGNOSTIC] INCOMING: {} {} from {}", 
                req.getMethod(), req.getRequestURI(), req.getRemoteAddr());
            
            // Log headers
            Collections.list(req.getHeaderNames()).forEach(h -> 
                log.info(">>>> [SYSTEM-DIAGNOSTIC] Header: {} = {}", h, req.getHeader(h)));
            
            chain.doFilter(request, response);
        }
    }
}
