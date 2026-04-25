package com.best_reader.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "annotations")
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le texte annoté est obligatoire")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String highlightedText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HighlightColor color = HighlightColor.YELLOW;

    private int pageNumber;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum HighlightColor {
        YELLOW,
        GREEN,
        BLUE,
        PINK,
        ORANGE,
        PURPLE,
        BROWN,
        RED
    }
}