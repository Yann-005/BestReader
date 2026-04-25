package com.best_reader.repository;

import com.best_reader.model.ReadingProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    Optional<ReadingProgress> findByBookId(Long bookId);

    void deleteByBookId(Long bookId);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    Optional<ReadingProgress> findByUserIdAndBookId(Long userId, Long bookId);
}