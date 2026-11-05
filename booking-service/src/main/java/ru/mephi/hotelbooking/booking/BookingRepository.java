package ru.mephi.hotelbooking.booking;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface BookingRepository extends JpaRepository<Booking, UUID> {

    boolean existsByUserId(UUID userId);

    Optional<Booking> findByUserIdAndRequestId(UUID userId, String requestId);

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    Page<Booking> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable page);

    List<Booking> findFirst50ByReleasePendingTrueOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(UUID id);
}
