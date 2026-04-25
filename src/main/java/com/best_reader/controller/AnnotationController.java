package com.best_reader.controller;

import com.best_reader.model.Annotation;
import com.best_reader.service.AnnotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @PostMapping("/book/{bookId}/user/{userId}")
    public ResponseEntity<Annotation> save(
            @PathVariable Long bookId,
            @PathVariable Long userId,
            @Valid @RequestBody Annotation annotation) {
        return ResponseEntity.ok(annotationService.save(bookId, userId, annotation));
    }

    @GetMapping("/book/{bookId}/user/{userId}")
    public ResponseEntity<List<Annotation>> getAllByBookAndUser(
            @PathVariable Long bookId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(annotationService.findAllByBookAndUser(bookId, userId));
    }

    @GetMapping("/book/{bookId}/user/{userId}/color/{color}")
    public ResponseEntity<List<Annotation>> getByColor(
            @PathVariable Long bookId,
            @PathVariable Long userId,
            @PathVariable Annotation.HighlightColor color) {
        return ResponseEntity.ok(annotationService.findByColor(bookId, userId, color));
    }

    @PutMapping("/{annotationId}/user/{userId}")
    public ResponseEntity<Annotation> update(
            @PathVariable Long annotationId,
            @PathVariable Long userId,
            @Valid @RequestBody Annotation annotation) {
        return ResponseEntity.ok(annotationService.update(annotationId, userId, annotation));
    }

    @DeleteMapping("/{annotationId}/user/{userId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long annotationId,
            @PathVariable Long userId) {
        annotationService.delete(annotationId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/book/{bookId}/user/{userId}")
    public ResponseEntity<Void> deleteAll(
            @PathVariable Long bookId,
            @PathVariable Long userId) {
        annotationService.deleteAllByBookAndUser(bookId, userId);
        return ResponseEntity.noContent().build();
    }
}