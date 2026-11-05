package ru.mephi.hotelbooking.hotel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CatalogService {

    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final RoomSlotRepository slots;

    CatalogService(HotelRepository hotels, RoomRepository rooms, RoomSlotRepository slots) {
        this.hotels = hotels;
        this.rooms = rooms;
        this.slots = slots;
    }

    @Transactional(readOnly = true)
    public RoomInventory.HotelView hotel(long id) {
        return hotelView(hotels.findById(id).orElseThrow(CatalogService::hotelNotFound));
    }

    @Transactional
    public RoomInventory.HotelView createHotel(String name, String address) {
        return hotelView(hotels.save(new Hotel(name, address)));
    }

    @Transactional
    public RoomInventory.HotelView updateHotel(long id, String name, String address) {
        Hotel hotel = lockHotel(id);
        hotel.name = name;
        hotel.address = address;
        return hotelView(hotel);
    }

    @Transactional
    public void deleteHotel(long id) {
        Hotel hotel = lockHotel(id);
        if (rooms.existsByHotelId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remove the hotel's rooms first");
        }
        hotels.delete(hotel);
    }

    @Transactional(readOnly = true)
    public RoomInventory.RoomView room(long id) {
        Room room = rooms.findById(id).orElseThrow(CatalogService::roomNotFound);
        return RoomInventory.RoomView.from(room);
    }

    @Transactional
    public RoomInventory.RoomView createRoom(long hotelId, String number, boolean available) {
        Room room = new Room(lockHotel(hotelId), number);
        room.available = available;
        return RoomInventory.RoomView.from(rooms.save(room));
    }

    @Transactional
    public RoomInventory.RoomView updateRoom(long id, String number, boolean available) {
        Room room = lockRoom(id);
        room.number = number;
        room.available = available;
        return RoomInventory.RoomView.from(room);
    }

    @Transactional
    public void deleteRoom(long id) {
        Room room = lockRoom(id);
        if (slots.existsByRoomIdAndReleasedFalse(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room has active bookings");
        }
        rooms.delete(room);
    }

    @Transactional(readOnly = true)
    public List<Occupancy> occupancy(LocalDate start, LocalDate end, Long hotelId) {
        RoomInventory.validateDates(start, end);
        long nights = ChronoUnit.DAYS.between(start, end);
        var reservedByRoom = slots.findByReleasedFalseAndStartDateLessThanAndEndDateGreaterThan(end, start)
                .stream()
                .collect(Collectors.groupingBy(slot -> slot.roomId,
                        Collectors.summarizingLong(slot -> occupiedNights(slot, start, end))));
        return rooms.findForHotel(hotelId).stream().map(room -> {
            var summary = reservedByRoom.get(room.id);
            long occupied = summary == null ? 0 : summary.getSum();
            long bookings = summary == null ? 0 : summary.getCount();
            return new Occupancy(room.id, room.hotel.getId(), room.number, bookings, occupied, nights,
                    Math.round(10000.0 * occupied / nights) / 100.0);
        }).toList();
    }

    private static long occupiedNights(RoomSlot slot, LocalDate start, LocalDate end) {
        LocalDate first = slot.startDate.isBefore(start) ? start : slot.startDate;
        LocalDate last = slot.endDate.isAfter(end) ? end : slot.endDate;
        return ChronoUnit.DAYS.between(first, last);
    }

    private Hotel lockHotel(long id) {
        return hotels.lockById(id).orElseThrow(CatalogService::hotelNotFound);
    }

    private Room lockRoom(long id) {
        return rooms.lockById(id).orElseThrow(CatalogService::roomNotFound);
    }

    private static RoomInventory.HotelView hotelView(Hotel hotel) {
        return new RoomInventory.HotelView(hotel.id, hotel.name, hotel.address);
    }

    private static ResponseStatusException hotelNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found");
    }

    private static ResponseStatusException roomNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
    }

    record Occupancy(long roomId, long hotelId, String roomNumber, long bookings,
                     long occupiedNights, long nights, double occupancyPercent) {
    }
}
