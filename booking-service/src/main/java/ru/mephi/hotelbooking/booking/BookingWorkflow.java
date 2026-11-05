package ru.mephi.hotelbooking.booking;

import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
class BookingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(BookingWorkflow.class);

    private final BookingStore store;
    private final BookingRepository bookings;
    private final HotelClient hotel;

    BookingWorkflow(BookingStore store, BookingRepository bookings, HotelClient hotel) {
        this.store = store;
        this.bookings = bookings;
        this.hotel = hotel;
    }

    Booking create(UUID userId, String requestId, long roomId, LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
        BookingStore.Creation creation = store.begin(userId, requestId, roomId, startDate, endDate);
        Booking booking = creation.booking();
        if (!creation.created()) {
            return booking;
        }

        try {
            hotel.reserve(booking);
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 409 || failure.getStatusCode().value() == 404) {
                store.cancel(booking.id, userId, true, false);
                throw new ResponseStatusException(failure.getStatusCode(), "Hotel could not reserve the room");
            }
            throw unavailable(booking, failure);
        } catch (RestClientException | IllegalStateException failure) {
            throw unavailable(booking, failure);
        }
        return store.confirm(booking.id);
    }

    void cancel(UUID id, UUID userId) {
        Booking booking = store.cancel(id, userId, false, true);
        if (booking.releasePending && !release(booking)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Room release will be retried");
        }
    }

    private ResponseStatusException unavailable(Booking booking, RuntimeException failure) {
        log.warn("hotel_unavailable bookingId={} cause={}", booking.id, failure.toString());
        release(store.cancel(booking.id, booking.userId, true, true));
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hotel could not confirm the booking", failure);
    }

    @Scheduled(fixedDelayString = "${app.release-retry-delay:PT15S}")
    public void retryReleases() {
        bookings.findFirst50ByReleasePendingTrueOrderByCreatedAtAsc().forEach(this::release);
    }

    private boolean release(Booking booking) {
        try {
            hotel.release(booking);
        } catch (RestClientException | IllegalStateException failure) {
            log.warn("booking_release_pending bookingId={} cause={}", booking.id, failure.toString());
            return false;
        }
        store.released(booking.id);
        log.info("booking_released bookingId={}", booking.id);
        return true;
    }
}
