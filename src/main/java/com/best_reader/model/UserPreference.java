package com.best_reader.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String theme = "light";

    @Column(nullable = false)
    private int fontSize = 16;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}