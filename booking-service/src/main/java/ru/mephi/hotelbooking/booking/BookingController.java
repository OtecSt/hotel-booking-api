package ru.mephi.hotelbooking.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
class BookingController {

    private final BookingWorkflow workflow;
    private final BookingRepository bookings;

    BookingController(BookingWorkflow workflow, BookingRepository bookings) {
        this.workflow = workflow;
        this.bookings = bookings;
    }

    @PostMapping
    ResponseEntity<BookingView> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String requestId,
            @Valid @RequestBody CreateBooking request) {
        Booking booking = workflow.create(userId(jwt), requestId, request.roomId(), request.startDate(), request.endDate());
        HttpStatus status = switch (booking.status) {
            case PENDING -> HttpStatus.ACCEPTED;
            case CONFIRMED -> HttpStatus.CREATED;
            case CANCELLED -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(BookingView.from(booking));
    }

    @GetMapping
    BookingPage history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = bookings.findByUserIdOrderByCreatedAtDescIdDesc(userId(jwt), PageRequest.of(page, size));
        return new BookingPage(result.map(BookingView::from).getContent(), page, size, result.getTotalElements());
    }

    @GetMapping("/{id}")
    BookingView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return bookings.findByIdAndUserId(id, userId(jwt)).map(BookingView::from)
                .orElseThrow(BookingStore::notFound);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        workflow.cancel(id, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record CreateBooking(@Positive long roomId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    record BookingView(UUID id, long roomId, LocalDate startDate, LocalDate endDate,
                       Booking.Status status, Instant createdAt) {

        static BookingView from(Booking booking) {
            return new BookingView(booking.id, booking.roomId, booking.startDate, booking.endDate,
                    booking.status, booking.createdAt);
        }
    }

    record BookingPage(List<BookingView> items, int page, int size, long total) {
    }
}
