package com.best_reader.repository;

import com.best_reader.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserId(Long userId);

    List<Note> findByUserIdAndBookId(Long userId, Long bookId);

    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookId(Long bookId);
}