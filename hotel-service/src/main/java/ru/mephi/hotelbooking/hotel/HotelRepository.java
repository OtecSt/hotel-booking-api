package ru.mephi.hotelbooking.hotel;

import org.springframework.data.jpa.repository.JpaRepository;

interface HotelRepository extends JpaRepository<Hotel, Long> {
}
