package ru.mephi.hotelbooking.hotel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_slots", indexes = @Index(columnList = "roomId,released,startDate,endDate"))
class RoomSlot {

    @Id
    UUID bookingId;

    long roomId;
    LocalDate startDate;
    LocalDate endDate;
    boolean released;

    protected RoomSlot() {
    }

    RoomSlot(UUID bookingId, long roomId, LocalDate startDate, LocalDate endDate, boolean released) {
        this.bookingId = bookingId;
        this.roomId = roomId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.released = released;
    }
}
