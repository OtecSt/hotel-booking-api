package ru.mephi.hotelbooking.hotel;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomSlotRepository extends JpaRepository<RoomSlot, UUID> {

    boolean existsByRoomIdAndReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
            long roomId, LocalDate endDate, LocalDate startDate);
}
