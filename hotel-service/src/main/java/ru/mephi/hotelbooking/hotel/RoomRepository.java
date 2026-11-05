package ru.mephi.hotelbooking.hotel;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface RoomRepository extends JpaRepository<Room, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(long id);

    @Query("""
            select r from Room r join fetch r.hotel
            where r.available = true and not exists (
                select s from RoomSlot s where s.roomId = r.id and s.released = false
                    and s.startDate < :endDate and s.endDate > :startDate
            )
            order by r.id
            """)
    List<Room> findAvailable(LocalDate startDate, LocalDate endDate);
}
