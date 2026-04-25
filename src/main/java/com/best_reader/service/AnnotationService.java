package com.best_reader.service;

import com.best_reader.exception.NotFoundException;
import com.best_reader.exception.AccessDeniedException;
import com.best_reader.model.Annotation;
import com.best_reader.model.Book;
import com.best_reader.model.User;
import com.best_reader.repository.AnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final BookService bookService;
    private final UserService userService;

    @Transactional
    public Annotation save(Long bookId, Long userId, Annotation annotation) {
        Book book = bookService.findById(bookId);
        User user = userService.findById(userId);
        annotation.setBook(book);
        annotation.setUser(user);
        return annotationRepository.save(annotation);
    }

    public List<Annotation> findAllByBookAndUser(Long bookId, Long userId) {
        return annotationRepository.findByBookIdAndUserId(bookId, userId);
    }

    public List<Annotation> findByColor(Long bookId, Long userId, Annotation.HighlightColor color) {
        return annotationRepository.findByBookIdAndUserIdAndColor(bookId, userId, color);
    }

    @Transactional
    public Annotation update(Long annotationId, Long userId, Annotation updated) {
        Annotation annotation = findById(annotationId);
        verifyOwnership(annotation, userId);
        annotation.setHighlightedText(updated.getHighlightedText());
        annotation.setColor(updated.getColor());
        return annotationRepository.save(annotation);
    }

    @Transactional
    public void delete(Long annotationId, Long userId) {
        Annotation annotation = findById(annotationId);
        verifyOwnership(annotation, userId);
        annotationRepository.delete(annotation);
    }

    @Transactional
    public void deleteAllByBookAndUser(Long bookId, Long userId) {
        annotationRepository.deleteByBookIdAndUserId(bookId, userId);
    }

    private Annotation findById(Long id) {
        return annotationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Annotation introuvable"));
    }

    private void verifyOwnership(Annotation annotation, Long userId) {
        if (!annotation.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé à cette annotation");
        }
    }
}