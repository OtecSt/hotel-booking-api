package ru.mephi.hotelbooking.booking;

import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class BookingStore {

    private static final Logger log = LoggerFactory.getLogger(BookingStore.class);

    private final BookingRepository bookings;
    private final UserAccountRepository users;

    BookingStore(BookingRepository bookings, UserAccountRepository users) {
        this.bookings = bookings;
        this.users = users;
    }

    @Transactional
    public Creation begin(UUID userId, String requestId, long roomId, LocalDate startDate, LocalDate endDate) {
        users.lockById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        Booking existing = bookings.findByUserIdAndRequestId(userId, requestId).orElse(null);
        if (existing != null) {
            if (existing.requestedRoomId != roomId || !startDate.equals(existing.startDate) || !endDate.equals(existing.endDate)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used for different dates or room");
            }
            return new Creation(existing, false);
        }
        Booking booking = bookings.save(new Booking(userId, requestId, roomId, startDate, endDate));
        log.info("booking_pending bookingId={} roomId={}", booking.id, roomId);
        return new Creation(booking, true);
    }

    @Transactional
    public Booking selectRoom(UUID id, long roomId) {
        Booking booking = lock(id);
        booking.roomId = roomId;
        log.info("booking_room_selected bookingId={} roomId={}", id, roomId);
        return booking;
    }

    @Transactional
    public Booking confirm(UUID id) {
        Booking booking = lock(id);
        if (booking.status == Booking.Status.PENDING) {
            booking.status = Booking.Status.CONFIRMED;
            log.info("booking_confirmed bookingId={}", id);
        }
        return booking;
    }

    @Transactional
    public Booking cancel(UUID id, UUID owner, boolean onFailure, boolean releaseRequired) {
        Booking booking = lock(id);
        if (!booking.userId.equals(owner)) {
            throw notFound();
        }
        if (booking.status == Booking.Status.PENDING && !onFailure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking is still pending");
        }
        if (booking.status != Booking.Status.CANCELLED) {
            booking.status = Booking.Status.CANCELLED;
            booking.releasePending = releaseRequired;
            booking.releaseTraceId = MDC.get("traceId");
            log.info("booking_cancelled bookingId={} releasePending={}", id, releaseRequired);
        }
        return booking;
    }

    @Transactional
    public void released(UUID id) {
        lock(id).releasePending = false;
    }

    private Booking lock(UUID id) {
        return bookings.lockById(id).orElseThrow(BookingStore::notFound);
    }

    static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
    }

    record Creation(Booking booking, boolean created) {
    }
}
