package ru.mephi.hotelbooking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true, length = 64)
    String username;

    @Column(nullable = false)
    String password;

    @Column(nullable = false)
    String role = "USER";

    protected UserAccount() {
    }

    UserAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
