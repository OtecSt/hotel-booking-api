package ru.mephi.hotelbooking.booking;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class HotelClient {

    private static final Logger log = LoggerFactory.getLogger(HotelClient.class);

    private final RestClient client;
    private final TokenService tokens;

    private final CircuitBreaker reservationCircuit = CircuitBreaker.of("hotel-reservations",
            CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(10))
                    .ignoreException(HotelClient::isClientError)
                    .build());

    HotelClient(@LoadBalanced RestClient.Builder builder, TokenService tokens) {
        this.client = builder.baseUrl("http://hotel-service").build();
        this.tokens = tokens;
    }

    void reserve(Booking booking) {
        reservationCircuit.executeRunnable(() -> send("/api/rooms/{roomId}/confirm-availability", booking,
                new ReserveRequest(booking.id, booking.startDate, booking.endDate)));
    }

    List<Long> recommend(Booking booking) {
        return reservationCircuit.executeSupplier(() -> retry("/api/rooms/recommend", booking.id, () -> {
            Candidate[] candidates = client.get().uri(builder -> builder.path("/api/rooms/recommend")
                            .queryParam("startDate", booking.startDate)
                            .queryParam("endDate", booking.endDate)
                            .build())
                    .headers(headers -> serviceHeaders(headers, booking.id))
                    .retrieve().body(Candidate[].class);
            if (candidates == null) {
                throw new RestClientException("Hotel returned an empty candidate response");
            }
            return Arrays.stream(candidates).map(Candidate::id).toList();
        }));
    }

    void release(Booking booking) {
        send("/api/rooms/{roomId}/release", booking, new ReleaseRequest(booking.id));
    }

    private void send(String path, Booking booking, Object request) {
        retry(path, booking.id, () -> client.post().uri(path, booking.roomId)
                .headers(headers -> serviceHeaders(headers, booking.id))
                .body(request)
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T retry(String path, UUID bookingId, Supplier<T> operation) {
        for (int attempt = 1; ; attempt++) {
            try {
                return operation.get();
            } catch (RestClientException | IllegalStateException failure) {
                if (attempt == 3 || isClientError(failure)) {
                    throw failure;
                }
                log.warn("hotel_retry bookingId={} operation={} attempt={}", bookingId, path, attempt);
                waitBeforeRetry(attempt);
            }
        }
    }

    private static boolean isClientError(Throwable failure) {
        return failure instanceof RestClientResponseException response && response.getStatusCode().is4xxClientError();
    }

    private record Candidate(long id) {
    }

    private void serviceHeaders(HttpHeaders headers, UUID bookingId) {
        headers.setBearerAuth(tokens.forHotelCall());
        String traceId = MDC.get("traceId");
        headers.set(RequestTraceFilter.HEADER, traceId == null ? bookingId.toString() : traceId);
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(100L << (attempt - 1));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Hotel call interrupted", interrupted);
        }
    }

    private record ReserveRequest(UUID bookingId, LocalDate startDate, LocalDate endDate) {
    }

    private record ReleaseRequest(UUID bookingId) {
    }

    @Configuration
    static class ClientConfiguration {

        @Bean
        @LoadBalanced
        RestClient.Builder hotelRestClient(RestClientBuilderConfigurer configurer) {
            var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            var factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(2));
            return configurer.configure(RestClient.builder()).requestFactory(factory);
        }
    }
}
