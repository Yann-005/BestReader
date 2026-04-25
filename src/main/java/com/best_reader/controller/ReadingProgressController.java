package com.best_reader.controller;

import com.best_reader.model.ReadingProgress;
import com.best_reader.service.ReadingProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ReadingProgressController {

    private final ReadingProgressService readingProgressService;

    @GetMapping("/book/{bookId}")
    public ResponseEntity<ReadingProgress> getProgress(
            @PathVariable Long bookId,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(readingProgressService.findOrCreateProgress(userId, bookId));
    }

    @PutMapping("/book/{bookId}/page/{currentPage}")
    public ResponseEntity<ReadingProgress> updateProgress(
            @PathVariable Long bookId,
            @PathVariable int currentPage,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(readingProgressService.saveOrUpdateProgress(userId, bookId, currentPage));
    }

    @PostMapping("/book/{bookId}/page/{page}")
    public ResponseEntity<ReadingProgress> saveProgress(
            @PathVariable Long bookId,
            @PathVariable int page,
            @RequestParam(required = false) Long userId) {
        return ResponseEntity.ok(readingProgressService.saveOrUpdateProgress(userId, bookId, page));
    }

    @DeleteMapping("/book/{bookId}")
    public ResponseEntity<Void> deleteProgress(@PathVariable Long bookId) {
        readingProgressService.deleteProgress(bookId);
        return ResponseEntity.noContent().build();
    }
}
