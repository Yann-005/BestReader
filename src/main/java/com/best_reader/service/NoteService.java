package com.best_reader.service;

import com.best_reader.exception.NotFoundException;
import com.best_reader.exception.AccessDeniedException;
import com.best_reader.model.Book;
import com.best_reader.model.Note;
import com.best_reader.model.User;
import com.best_reader.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserService userService;
    private final BookService bookService;

    @Transactional
    public Note save(Long userId, Long bookId, Note note) {
        User user = userService.findById(userId);
        note.setUser(user);
        if (bookId != null) {
            Book book = bookService.findById(bookId);
            note.setBook(book);
        }
        return noteRepository.save(note);
    }

    public List<Note> findAllByUser(Long userId) {
        return noteRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Note> findByUserAndBook(Long userId, Long bookId) {
        return noteRepository.findByUserIdAndBookId(userId, bookId);
    }

    public Note findById(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note introuvable"));
    }

    @Transactional
    public Note update(Long noteId, Long userId, Note updatedNote) {
        Note note = findById(noteId);
        verifyOwnership(note, userId);
        note.setTitle(updatedNote.getTitle());
        note.setContent(updatedNote.getContent());
        return noteRepository.save(note);
    }

    @Transactional
    public void delete(Long noteId, Long userId) {
        Note note = findById(noteId);
        verifyOwnership(note, userId);
        noteRepository.delete(note);
    }

    private void verifyOwnership(Note note, Long userId) {
        if (!note.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé à cette note");
        }
    }
}