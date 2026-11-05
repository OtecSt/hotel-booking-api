package ru.mephi.hotelbooking.hotel;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RoomInventory {

    private static final Logger log = LoggerFactory.getLogger(RoomInventory.class);

    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final RoomSlotRepository slots;

    RoomInventory(HotelRepository hotels, RoomRepository rooms, RoomSlotRepository slots) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public List<HotelView> hotels() {
        return hotels.findAll().stream().map(hotel -> new HotelView(hotel.id, hotel.name, hotel.address)).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomView> available(LocalDate startDate, LocalDate endDate, Long hotelId, boolean recommend) {
        validateDates(startDate, endDate);
        var candidates = rooms.findAvailable(startDate, endDate, hotelId).stream()
                .map(room -> RoomView.from(room));
        if (recommend) {
            candidates = candidates.sorted(Comparator.comparingInt(RoomView::timesBooked).thenComparingLong(RoomView::id));
        }
        return candidates.toList();
    }

    @Transactional
    public void reserve(long roomId, UUID bookingId, LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        Room room = lockRoom(roomId);
        RoomSlot existing = slots.findById(bookingId).orElse(null);
        if (existing != null) {
            if (existing.released || existing.roomId != roomId
                    || !startDate.equals(existing.startDate) || !endDate.equals(existing.endDate)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking cannot reserve this interval");
            }
            return;
        }
        if (!room.available || slots.existsByRoomIdAndReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(
                roomId, endDate, startDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is unavailable for the requested dates");
        }
        slots.save(new RoomSlot(bookingId, roomId, startDate, endDate, false));
        room.timesBooked++;
        log.info("room_reserved bookingId={} roomId={}", bookingId, roomId);
    }

    @Transactional
    public void release(long roomId, UUID bookingId) {
        Room room = lockRoom(roomId);
        RoomSlot existing = slots.findById(bookingId).orElse(null);
        if (existing == null) {
            // A timeout can deliver the original reservation after this cancellation.
            slots.save(new RoomSlot(bookingId, roomId, null, null, true));
        } else if (existing.roomId != roomId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking belongs to another room");
        } else if (!existing.released) {
            existing.released = true;
            room.timesBooked--;
        }
        log.info("room_released bookingId={} roomId={}", bookingId, roomId);
    }

    private Room lockRoom(long roomId) {
        return rooms.lockById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
    }

    static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
    }

    record HotelView(long id, String name, String address) {
    }

    record RoomView(long id, long hotelId, String number, boolean available, int timesBooked) {

        static RoomView from(Room room) {
            return new RoomView(room.id, room.hotel.getId(), room.number, room.available, room.timesBooked);
        }
    }
}
