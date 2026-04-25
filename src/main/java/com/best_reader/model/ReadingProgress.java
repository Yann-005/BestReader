package com.best_reader.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reading_progress", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "book_id"})
})
public class ReadingProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int currentPage = 0;

    private double progressPercentage = 0.0;

    private LocalDateTime lastReadAt = LocalDateTime.now();

    @ManyToOne 
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "user_id") 
    private Long userId;
}