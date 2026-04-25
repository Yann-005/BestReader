package com.best_reader.service;

import com.best_reader.model.Book;
import com.best_reader.model.ReadingProgress;
import com.best_reader.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingProgressService {

    private final ReadingProgressRepository readingProgressRepository;
    private final BookService bookService;

    public ReadingProgress findByBookId(Long bookId) {
        return findOrCreateProgress(null, bookId);
    }

    @Transactional
    public ReadingProgress findOrCreateProgress(Long userId, Long bookId) {
        if (userId != null) {
            return readingProgressRepository.findByUserIdAndBookId(userId, bookId)
                    .orElseGet(() -> createDefaultProgress(userId, bookId));
        }
        return readingProgressRepository.findByBookId(bookId)
                .orElseGet(() -> createDefaultProgress(null, bookId));
    }

    @Transactional
    public ReadingProgress updateProgress(Long bookId, int currentPage) {
        return saveOrUpdateProgress(null, bookId, currentPage);
    }

    @Transactional
    public void deleteProgress(Long bookId) {
        readingProgressRepository.deleteByBookId(bookId);
    }

    @Transactional
    public ReadingProgress saveOrUpdateProgress(Long userId, Long bookId, int page) {
        ReadingProgress progress;
        if (userId != null) {
            progress = readingProgressRepository.findByUserIdAndBookId(userId, bookId)
                    .orElseGet(() -> new ReadingProgress());
            progress.setUserId(userId);
        } else {
            progress = readingProgressRepository.findByBookId(bookId)
                    .orElseGet(ReadingProgress::new);
        }

        Book book = bookService.findById(bookId);
        progress.setBook(book);
        progress.setCurrentPage(Math.max(1, page));
        progress.setProgressPercentage(calculatePercentage(Math.max(1, page), getSafeTotalPages(book)));
        progress.setLastReadAt(java.time.LocalDateTime.now());
        return readingProgressRepository.save(progress);
    }

    private ReadingProgress createDefaultProgress(Long userId, Long bookId) {
        Book book = bookService.findById(bookId);
        ReadingProgress progress = new ReadingProgress();
        progress.setUserId(userId);
        progress.setBook(book);
        progress.setCurrentPage(1);
        progress.setProgressPercentage(0.0);
        progress.setLastReadAt(java.time.LocalDateTime.now());
        return readingProgressRepository.save(progress);
    }

    private int getSafeTotalPages(Book book) {
        return book.getTotalPages() != null && book.getTotalPages() > 0 ? book.getTotalPages() : 100;
    }

    private double calculatePercentage(int currentPage, int totalPages) {
        if (totalPages <= 0) return 0.0;
        return Math.min(100.0, Math.round((currentPage * 100.0 / totalPages) * 10.0) / 10.0);
    }
}
