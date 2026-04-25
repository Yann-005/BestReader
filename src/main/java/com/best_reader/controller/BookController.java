package com.best_reader.controller;

import com.best_reader.model.Book;
import com.best_reader.service.BookService;
import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;

    @PostMapping("/upload")
    public ResponseEntity<Book> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("author") String author,
            @RequestParam("userId") Long userId) {
        return ResponseEntity.ok(bookService.uploadBook(file, title, author, userId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Book>> getAllByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(bookService.findAllByUser(userId));
    }

    @GetMapping("/user/{userId}/alphabetical")
    public ResponseEntity<List<Book>> getByUserSortedByTitle(@PathVariable Long userId) {
        return ResponseEntity.ok(bookService.findByUserSortedByTitle(userId));
    }

    @GetMapping("/user/{userId}/recent")
    public ResponseEntity<List<Book>> getByUserSortedByDate(@PathVariable Long userId) {
        return ResponseEntity.ok(bookService.findByUserSortedByDate(userId));
    }

    @GetMapping("/user/{userId}/last-read")
    public ResponseEntity<List<Book>> getByUserSortedByLastRead(@PathVariable Long userId) {
        return ResponseEntity.ok(bookService.findByUserSortedByLastRead(userId));
    }

    @GetMapping("/user/{userId}/search")
    public ResponseEntity<List<Book>> searchBooks(@PathVariable Long userId, @RequestParam String query) {
        return ResponseEntity.ok(bookService.searchBooks(userId, query));
    }

    @GetMapping("/user/{userId}/favorites")
    public ResponseEntity<List<Book>> getFavorites(@PathVariable Long userId) {
        return ResponseEntity.ok(bookService.findFavoritesByUser(userId));
    }

    @PutMapping("/{bookId}/favorite/{userId}")
    public ResponseEntity<Void> toggleFavorite(@PathVariable Long bookId, @PathVariable Long userId) {
        bookService.toggleFavorite(bookId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{bookId}/user/{userId}")
    public ResponseEntity<Void> removeFromLibrary(@PathVariable Long bookId, @PathVariable Long userId) {
        bookService.removeFromLibrary(bookId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{bookId}/user/{userId}/complete")
    public ResponseEntity<Void> deleteCompletely(@PathVariable Long bookId, @PathVariable Long userId) {
        bookService.deleteCompletely(bookId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/share/{token}")
    public ResponseEntity<Book> getByShareToken(@PathVariable String token) {
        return ResponseEntity.ok(bookService.findByShareToken(token));
    }

    // Gardé seulement comme fallback pour TXT/anciens appels. PDF/DOCX/EPUB sont rendus depuis /file côté navigateur.
    @GetMapping("/{bookId}/content")
    public ResponseEntity<Map<String, Object>> getBookContent(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "1") int page) {
        try {
            Book book = bookService.findById(bookId);
            String content = bookService.extractPageContent(book, page);
            Map<String, Object> response = new HashMap<>();
            response.put("content", content);
            response.put("totalPages", book.getTotalPages());
            response.put("currentPage", page);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{bookId}/file")
    public ResponseEntity<Resource> getBookFile(@PathVariable Long bookId) throws Exception {
        Book book = bookService.findById(bookId);
        Path path = Paths.get(book.getFilePath()).toAbsolutePath().normalize();

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new UrlResource(path.toUri());

        MediaType mediaType = switch (book.getFormat()) {
            case PDF -> MediaType.APPLICATION_PDF;
            case WORD -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case TXT -> MediaType.TEXT_PLAIN;
            case EPUB -> MediaType.parseMediaType("application/epub+zip");
        };

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .body(resource);
    }
    @GetMapping("/{bookId}")
    public ResponseEntity<Book> getBook(@PathVariable Long bookId, @RequestParam(required = false) Long userId) {
        return bookService.findByIdOptional(bookId)
                .map(book -> {
                    if (userId != null) bookService.hydrateBookForUser(book, userId);
                    return ResponseEntity.ok(book);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{bookId}/cover")
    public ResponseEntity<Resource> getBookCover(@PathVariable Long bookId) throws Exception {
        Book book = bookService.findById(bookId);
        if (book.getCoverImagePath() == null || book.getCoverImagePath().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(book.getCoverImagePath()).toAbsolutePath().normalize();
        if (!Files.exists(path) || !Files.isRegularFile(path)) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(path.toUri());
        MediaType type = MediaType.IMAGE_PNG;
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) type = MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).contentLength(Files.size(path)).body(resource);
    }

    // Utile seulement si tu veux servir une ressource précise extraite d'un EPUB.
    // Le lecteur principal utilise epub.js directement sur le fichier .epub complet.
    @GetMapping("/{bookId}/epub/resource")
    public ResponseEntity<byte[]> getEpubResource(@PathVariable Long bookId, @RequestParam String path) throws Exception {
        Book book = bookService.findById(bookId);
        try (ZipFile zip = new ZipFile(book.getFilePath())) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) return ResponseEntity.notFound().build();
            try (InputStream is = zip.getInputStream(entry)) {
                return ResponseEntity.ok(is.readAllBytes());
            }
        }
    }
}
