package ru.mephi.hotelbooking.booking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    HotelClient(@LoadBalanced RestClient.Builder builder, TokenService tokens) {
        this.client = builder.baseUrl("http://hotel-service").build();
        this.tokens = tokens;
    }

    void reserve(Booking booking) {
        send("/api/rooms/{roomId}/confirm-availability", booking,
                new ReserveRequest(booking.id, booking.startDate, booking.endDate));
    }

    void release(Booking booking) {
        send("/api/rooms/{roomId}/release", booking, new ReleaseRequest(booking.id));
    }

    private void send(String path, Booking booking, Object request) {
        for (int attempt = 1; ; attempt++) {
            try {
                client.post().uri(path, booking.roomId)
                        .headers(headers -> headers.setBearerAuth(tokens.forHotelCall()))
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException | IllegalStateException failure) {
                if (attempt == 3 || failure instanceof RestClientResponseException response
                        && response.getStatusCode().is4xxClientError()) {
                    throw failure;
                }
                log.warn("hotel_retry bookingId={} operation={} attempt={}", booking.id, path, attempt);
                waitBeforeRetry(attempt);
            }
        }
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
