package com.best_reader.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "dictionary_words")
public class DictionaryWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    @Column(columnDefinition = "TEXT")
    private String shortDefinition;

    @Column(columnDefinition = "TEXT")
    private String synonyms;

    @Column(nullable = false, length = 5)
    private String language;
}