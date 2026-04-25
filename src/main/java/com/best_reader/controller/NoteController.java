package com.best_reader.controller;

import com.best_reader.model.Note;
import com.best_reader.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping("/user/{userId}")
    public ResponseEntity<Note> save(
            @PathVariable Long userId,
            @RequestParam(required = false) Long bookId,
            @Valid @RequestBody Note note) {
        return ResponseEntity.ok(noteService.save(userId, bookId, note));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Note>> getAllByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(noteService.findAllByUser(userId));
    }

    @GetMapping("/user/{userId}/book/{bookId}")
    public ResponseEntity<List<Note>> getByUserAndBook(
            @PathVariable Long userId,
            @PathVariable Long bookId) {
        return ResponseEntity.ok(noteService.findByUserAndBook(userId, bookId));
    }

    @PutMapping("/{noteId}/user/{userId}")
    public ResponseEntity<Note> update(
            @PathVariable Long noteId,
            @PathVariable Long userId,
            @Valid @RequestBody Note updatedNote) {
        return ResponseEntity.ok(noteService.update(noteId, userId, updatedNote));
    }

    @DeleteMapping("/{noteId}/user/{userId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long noteId,
            @PathVariable Long userId) {
        noteService.delete(noteId, userId);
        return ResponseEntity.noContent().build();
    }
}