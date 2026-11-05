package ru.mephi.hotelbooking.gateway;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestTraceFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString();
        var request = exchange.getRequest().mutate().headers(headers -> headers.set(HEADER, traceId)).build();
        exchange.getResponse().getHeaders().set(HEADER, traceId);
        log.info("gateway_request traceId={} method={} path={}", traceId, request.getMethod(), request.getPath());
        return chain.filter(exchange.mutate().request(request).build());
    }
}
