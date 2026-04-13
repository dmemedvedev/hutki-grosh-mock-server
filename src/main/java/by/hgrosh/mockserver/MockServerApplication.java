package by.hgrosh.mockserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

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
            HttpServletResponse res = (HttpServletResponse) response;
            
            String uri = req.getRequestURI();
            String method = req.getMethod();
            
            log.info(">>>> [SYSTEM-DIAGNOSTIC] INCOMING: {} {} from {}", method, uri, req.getRemoteAddr());
            
            // Universal CORS support
            res.setHeader("Access-Control-Allow-Origin", "*");
            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            res.setHeader("Access-Control-Allow-Headers", "*");
            res.setHeader("Access-Control-Allow-Credentials", "true");

            if ("OPTIONS".equalsIgnoreCase(method)) {
                log.info(">>>> [SYSTEM-DIAGNOSTIC] Responding to OPTIONS pre-flight for {}", uri);
                res.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            // Log headers
            Collections.list(req.getHeaderNames()).forEach(h -> 
                log.info(">>>> [SYSTEM-DIAGNOSTIC] Header: {} = {}", h, req.getHeader(h)));
            
            chain.doFilter(request, response);
        }
    }
}
