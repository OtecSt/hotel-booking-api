package ru.mephi.hotelbooking.hotel;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "app.jwt-secret=hotel-integration-test-secret-not-used-in-production"
})
@AutoConfigureMockMvc
class RoomInventoryTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    HotelRepository hotels;

    @Autowired
    RoomRepository rooms;

    @Test
    void releaseBeforeConfirmationPreventsLateReservation() throws Exception {
        Hotel hotel = hotels.save(new Hotel("Test hotel", "Test address"));
        Room room = rooms.save(new Room(hotel, "001"));
        UUID bookingId = UUID.randomUUID();
        String endpoint = "/api/rooms/" + room.id;

        mvc.perform(post(endpoint + "/release")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + bookingId + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post(endpoint + "/confirm-availability")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId":"%s","startDate":"2035-04-01","endDate":"2035-04-03"}
                                """.formatted(bookingId)))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/rooms")
                        .param("startDate", "2035-04-01").param("endDate", "2035-04-03")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(room.id))
                .andExpect(jsonPath("$[0].timesBooked").value(0));
    }

    @Test
    void userCannotCallServiceEndpointEvenDirectly() throws Exception {
        mvc.perform(post("/api/rooms/1/release")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
