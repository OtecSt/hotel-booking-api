package ru.mephi.hotelbooking.hotel;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
class DemoCatalog {

    @Bean
    ApplicationRunner seedCatalog(HotelRepository hotels, RoomRepository rooms) {
        return arguments -> {
            Hotel hotel = hotels.save(new Hotel("Космос", "Москва, Каширское шоссе"));
            rooms.save(new Room(hotel, "101"));
            rooms.save(new Room(hotel, "102"));
        };
    }
}
