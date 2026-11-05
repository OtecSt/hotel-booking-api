package ru.mephi.hotelbooking.hotel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hotels")
class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 120)
    String name;
    @Column(nullable = false, length = 250)
    String address;

    protected Hotel() {
    }

    public Long getId() {
        return id;
    }

    Hotel(String name, String address) {
        this.name = name;
        this.address = address;
    }
}
