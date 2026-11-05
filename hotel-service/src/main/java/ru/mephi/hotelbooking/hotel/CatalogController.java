package ru.mephi.hotelbooking.hotel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/hotels/{id}")
    RoomInventory.HotelView hotel(@PathVariable long id) {
        return catalog.hotel(id);
    }

    @PostMapping("/api/hotels")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    RoomInventory.HotelView createHotel(@Valid @RequestBody HotelInput input) {
        return catalog.createHotel(input.name(), input.address());
    }

    @PutMapping("/api/hotels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    RoomInventory.HotelView updateHotel(@PathVariable long id, @Valid @RequestBody HotelInput input) {
        return catalog.updateHotel(id, input.name(), input.address());
    }

    @DeleteMapping("/api/hotels/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteHotel(@PathVariable long id) {
        catalog.deleteHotel(id);
    }

    @GetMapping("/api/rooms/{id}")
    RoomInventory.RoomView room(@PathVariable long id) {
        return catalog.room(id);
    }

    @PostMapping("/api/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    RoomInventory.RoomView createRoom(@Valid @RequestBody NewRoom input) {
        return catalog.createRoom(input.hotelId(), input.number(), input.available());
    }

    @PutMapping("/api/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    RoomInventory.RoomView updateRoom(@PathVariable long id, @Valid @RequestBody RoomInput input) {
        return catalog.updateRoom(id, input.number(), input.available());
    }

    @DeleteMapping("/api/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoom(@PathVariable long id) {
        catalog.deleteRoom(id);
    }

    @GetMapping("/api/rooms/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    List<CatalogService.Occupancy> occupancy(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
                                             @RequestParam(required = false) Long hotelId) {
        return catalog.occupancy(startDate, endDate, hotelId);
    }

    record HotelInput(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 250) String address) {
    }

    record NewRoom(@Positive long hotelId, @NotBlank @Size(max = 30) String number, @NotNull Boolean available) {
    }

    record RoomInput(@NotBlank @Size(max = 30) String number, @NotNull Boolean available) {
    }
}
