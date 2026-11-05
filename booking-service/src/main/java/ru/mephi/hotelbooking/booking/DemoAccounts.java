package ru.mephi.hotelbooking.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
class DemoAccounts {

    @Bean
    ApplicationRunner seedAdmin(AccountService accounts,
                                @Value("${HOTEL_BOOKING_DEMO_ADMIN_PASSWORD}") String password) {
        return arguments -> accounts.register("demo-admin", password, AccountService.Role.ADMIN);
    }
}
