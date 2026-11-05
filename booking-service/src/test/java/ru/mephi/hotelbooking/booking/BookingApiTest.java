package ru.mephi.hotelbooking.booking;

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
    "app.jwt-secret=booking-integration-test-secret-not-used-in-production"
})
@AutoConfigureMockMvc
class BookingApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void registrationIssuesTokenAndDuplicateUsernameReturnsConflict() throws Exception {
        String credentials = """
                {"username":"integration-user","password":"integration-password"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void anonymousCannotReadHistoryAndUserCannotListAccounts() throws Exception {
        mvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void reversedDatesAreRejectedBeforeContactingHotel() throws Exception {
        mvc.perform(post("/api/bookings")
                        .with(jwt().jwt(token -> token.subject("ad80f093-6614-40b3-a57d-050e8190cf26"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .header("Idempotency-Key", "invalid-dates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"startDate":"2035-04-03","endDate":"2035-04-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
