package ru.mephi.hotelbooking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bookings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "requestId"}),
        indexes = @Index(columnList = "userId,createdAt"))
class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID userId;

    @Column(nullable = false, length = 100)
    String requestId;

    long roomId;
    LocalDate startDate;
    LocalDate endDate;
    Instant createdAt = Instant.now();
    boolean releasePending;

    @Enumerated(EnumType.STRING)
    Status status = Status.PENDING;

    protected Booking() {
    }

    Booking(UUID userId, String requestId, long roomId, LocalDate startDate, LocalDate endDate) {
        this.userId = userId;
        this.requestId = requestId;
        this.roomId = roomId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    enum Status {
        PENDING, CONFIRMED, CANCELLED
    }
}
