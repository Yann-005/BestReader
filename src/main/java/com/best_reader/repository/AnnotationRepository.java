package com.best_reader.repository;

import com.best_reader.model.Annotation;
import com.best_reader.model.Annotation.HighlightColor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    List<Annotation> findByBookIdAndUserId(Long bookId, Long userId);

    List<Annotation> findByBookIdAndUserIdAndColor(Long bookId, Long userId, HighlightColor color);

    void deleteByBookIdAndUserId(Long bookId, Long userId);

    void deleteByBookId(Long bookId);
}