package com.best_reader.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"users",  "progress"})
@EqualsAndHashCode(exclude = {"users", "progress"})
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Le titre est obligatoire")
    @Column(nullable = false)
    private String title;

    private String author;

    private String coverImagePath;

    @Transient
    private Double progressPercentage = 0.0;

    @Transient
    private Integer currentPage = 1;

    @Transient
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileFormat format;

    @Column(nullable = false)
    private String filePath;

    private Integer totalPages;

    private LocalDateTime uploadedAt = LocalDateTime.now();

    private boolean favorite = false;

    @Column(unique = true)
    private String shareToken;

    @JsonIgnore
    @ManyToMany(mappedBy = "books")
    private Set<User> users = new HashSet<>();

    @JsonIgnore
    @OneToOne(mappedBy = "book", cascade = CascadeType.ALL)
    private ReadingProgress progress;

    public enum FileFormat {
        PDF, WORD, TXT, EPUB
    }
}