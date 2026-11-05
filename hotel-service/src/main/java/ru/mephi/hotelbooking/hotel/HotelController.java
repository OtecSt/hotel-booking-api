package ru.mephi.hotelbooking.hotel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HotelController {

    private final RoomInventory inventory;

    HotelController(RoomInventory inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/api/hotels")
    List<RoomInventory.HotelView> hotels() {
        return inventory.hotels();
    }

    @GetMapping("/api/rooms")
    List<RoomInventory.RoomView> available(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return inventory.available(startDate, endDate, false);
    }

    @GetMapping("/api/rooms/recommend")
    List<RoomInventory.RoomView> recommend(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return inventory.available(startDate, endDate, true);
    }

    @PostMapping("/api/rooms/{roomId}/confirm-availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reserve(@PathVariable long roomId, @Valid @RequestBody ReserveRequest request) {
        inventory.reserve(roomId, request.bookingId(), request.startDate(), request.endDate());
    }

    @PostMapping("/api/rooms/{roomId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable long roomId, @Valid @RequestBody ReleaseRequest request) {
        inventory.release(roomId, request.bookingId());
    }

    record ReserveRequest(@NotNull UUID bookingId, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    record ReleaseRequest(@NotNull UUID bookingId) {
    }
}
