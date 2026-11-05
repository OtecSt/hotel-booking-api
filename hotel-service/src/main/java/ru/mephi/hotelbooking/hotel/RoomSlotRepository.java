package ru.mephi.hotelbooking.hotel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomSlotRepository extends JpaRepository<RoomSlot, UUID> {

    boolean existsByRoomIdAndReleasedFalse(long roomId);

    List<RoomSlot> findByReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
            LocalDate endDate, LocalDate startDate);

    boolean existsByRoomIdAndReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
            long roomId, LocalDate endDate, LocalDate startDate);
}
