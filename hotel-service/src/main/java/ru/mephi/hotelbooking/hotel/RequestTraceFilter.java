package ru.mephi.hotelbooking.hotel;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestTraceFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = traceId(request.getHeader(HEADER));
        response.setHeader(HEADER, traceId);
        try (var context = MDC.putCloseable("traceId", traceId)) {
            chain.doFilter(request, response);
        }
    }

    private String traceId(String header) {
        if (header != null && header.matches("[0-9a-fA-F-]{36}")) {
            return header;
        }
        return UUID.randomUUID().toString();
    }
}
