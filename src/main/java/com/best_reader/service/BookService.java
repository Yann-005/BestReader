package com.best_reader.service;

import com.best_reader.exception.AccessDeniedException;
import com.best_reader.exception.FileException;
import com.best_reader.exception.NotFoundException;
import com.best_reader.model.Book;
import com.best_reader.model.ReadingProgress;
import com.best_reader.model.User;
import com.best_reader.repository.AnnotationRepository;
import com.best_reader.repository.BookRepository;
import com.best_reader.repository.NoteRepository;
import com.best_reader.repository.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final UserService userService;
    private final AnnotationRepository annotationRepository;
    private final NoteRepository noteRepository;
    private final ReadingProgressRepository readingProgressRepository;

    private static final String UPLOAD_DIR = "uploads/books/";
    private static final String COVER_DIR = "uploads/covers/";

    @Transactional
    public Book uploadBook(MultipartFile file, String title, String author, Long userId) {
        User user = userService.findById(userId);
        String filePath = saveFile(file);
        Book.FileFormat format = detectFormat(file.getOriginalFilename());

        Book book = new Book();
        book.setTitle((title == null || title.isBlank()) ? cleanTitle(file.getOriginalFilename()) : title.trim());
        book.setAuthor(normalizeAuthor(author));
        book.setFilePath(filePath);
        book.setFormat(format);
        book.setShareToken(UUID.randomUUID().toString());
        book.setUsers(new HashSet<>());
        book.setTotalPages(calculerTotalPages(filePath, format));

        enrichMetadata(book);
        book.setCoverImagePath(extractCoverImage(book));

        book.getUsers().add(user);
        user.getBooks().add(book);

        Book saved = bookRepository.save(book);
        hydrateBookForUser(saved, userId);
        return saved;
    }

    public List<Book> findAllByUser(Long userId) {
        return hydrateBooks(bookRepository.findByUsersId(userId), userId);
    }

    public List<Book> findByUserSortedByTitle(Long userId) {
        return hydrateBooks(bookRepository.findByUsersIdOrderByTitleAsc(userId), userId);
    }

    public List<Book> findByUserSortedByDate(Long userId) {
        return hydrateBooks(bookRepository.findByUsersIdOrderByUploadedAtDesc(userId), userId);
    }

    public List<Book> findByUserSortedByLastRead(Long userId) {
        return hydrateBooks(bookRepository.findByUsersIdOrderByLastReadDesc(userId), userId);
    }

    public List<Book> searchBooks(Long userId, String search) {
        return hydrateBooks(bookRepository.searchByTitleOrAuthor(userId, search == null ? "" : search), userId);
    }

    public List<Book> findFavoritesByUser(Long userId) {
        return hydrateBooks(bookRepository.findByUsersIdAndFavoriteTrue(userId), userId);
    }

    public Book findById(Long bookId) {
        return bookRepository.findById(bookId).orElseThrow(() -> new NotFoundException("Livre introuvable"));
    }

    public Book findByIdForUser(Long bookId, Long userId) {
        Book book = findById(bookId);
        if (userId != null) hydrateBookForUser(book, userId);
        return book;
    }

    public Optional<Book> findByIdOptional(Long id) {
        return bookRepository.findById(id);
    }

    @Transactional
    public void toggleFavorite(Long bookId, Long userId) {
        Book book = findById(bookId);
        verifyUserOwnsBook(book, userId);
        book.setFavorite(!book.isFavorite());
        bookRepository.save(book);
    }

    @Transactional
    public void removeFromLibrary(Long bookId, Long userId) {
        Book book = findById(bookId);
        User user = userService.findById(userId);
        verifyUserOwnsBook(book, userId);

        annotationRepository.deleteByBookIdAndUserId(bookId, userId);
        noteRepository.deleteByBookIdAndUserId(bookId, userId);
        readingProgressRepository.deleteByBookIdAndUserId(bookId, userId);

        book.getUsers().remove(user);
        user.getBooks().remove(book);

        if (book.getUsers().isEmpty()) {
            annotationRepository.deleteByBookId(bookId);
            noteRepository.deleteByBookId(bookId);
            readingProgressRepository.deleteByBookId(bookId);
            deleteFile(book.getCoverImagePath());
            deleteFile(book.getFilePath());
            bookRepository.delete(book);
        } else {
            bookRepository.save(book);
        }
    }

    @Transactional
    public void deleteCompletely(Long bookId, Long userId) {
        Book book = findById(bookId);
        verifyUserOwnsBook(book, userId);
        annotationRepository.deleteByBookId(bookId);
        noteRepository.deleteByBookId(bookId);
        readingProgressRepository.deleteByBookId(bookId);
        for (User u : new HashSet<>(book.getUsers())) u.getBooks().remove(book);
        book.getUsers().clear();
        deleteFile(book.getCoverImagePath());
        deleteFile(book.getFilePath());
        bookRepository.delete(book);
    }

    public Book findByShareToken(String token) {
        return bookRepository.findByShareToken(token).orElseThrow(() -> new NotFoundException("Livre introuvable"));
    }

    public String extractPageContent(Book book, int pageNumber) {
        try {
            Path filePath = Paths.get(book.getFilePath());
            return switch (book.getFormat()) {
                case PDF -> extractPdfPage(filePath, pageNumber);
                case WORD -> extractWordHtml(filePath, pageNumber);
                case TXT -> extractTxtPage(filePath, pageNumber);
                case EPUB -> extractEpubHtml(filePath, pageNumber, book.getId());
            };
        } catch (Exception e) {
            throw new FileException("Erreur lecture");
        }
    }

    public void hydrateBookForUser(Book book, Long userId) {
        if (book == null || userId == null) return;
        Optional<ReadingProgress> p = readingProgressRepository.findByUserIdAndBookId(userId, book.getId());
        book.setProgressPercentage(p.map(ReadingProgress::getProgressPercentage).orElse(0.0));
        book.setCurrentPage(p.map(ReadingProgress::getCurrentPage).orElse(1));
        book.setCoverUrl(book.getCoverImagePath() == null || book.getCoverImagePath().isBlank() ? null : "/api/books/" + book.getId() + "/cover");
        if (book.getAuthor() == null || book.getAuthor().isBlank()) book.setAuthor("Auteur inconnu");
    }

    private List<Book> hydrateBooks(List<Book> books, Long userId) {
        books.forEach(b -> hydrateBookForUser(b, userId));
        return books;
    }

    private String saveFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadPath);
            String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = System.currentTimeMillis() + "_" + original;
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);
            return filePath.toString();
        } catch (IOException e) {
            throw new FileException("Erreur lors de l'upload du fichier");
        }
    }

    private void deleteFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        try { Files.deleteIfExists(Paths.get(filePath)); }
        catch (IOException e) { throw new FileException("Erreur lors de la suppression du fichier"); }
    }

    private Book.FileFormat detectFormat(String fileName) {
        if (fileName == null) throw new FileException("Fichier invalide");
        String ext = fileName.toLowerCase();
        if (ext.endsWith(".pdf")) return Book.FileFormat.PDF;
        if (ext.endsWith(".docx") || ext.endsWith(".doc")) return Book.FileFormat.WORD;
        if (ext.endsWith(".txt")) return Book.FileFormat.TXT;
        if (ext.endsWith(".epub")) return Book.FileFormat.EPUB;
        throw new FileException("Format non supporté");
    }

    private int calculerTotalPages(String filePath, Book.FileFormat format) {
        try {
            Path path = Paths.get(filePath);
            if (format == Book.FileFormat.PDF) {
                try (PDDocument doc = Loader.loadPDF(path.toFile())) { return doc.getNumberOfPages(); }
            }
            if (format == Book.FileFormat.WORD) {
                if (filePath.toLowerCase().endsWith(".doc")) {
                    try (FileInputStream fis = new FileInputStream(path.toFile()); HWPFDocument doc = new HWPFDocument(fis); WordExtractor extractor = new WordExtractor(doc)) {
                        long count = java.util.Arrays.stream(extractor.getParagraphText()).filter(t -> !t.trim().isEmpty()).count();
                        return (int) Math.max(1, (count + 3) / 4);
                    }
                }
                try (FileInputStream fis = new FileInputStream(path.toFile()); XWPFDocument doc = new XWPFDocument(fis)) {
                    long count = doc.getParagraphs().stream().filter(p -> !p.getText().trim().isEmpty()).count();
                    return (int) Math.max(1, (count + 3) / 4);
                }
            }
            if (format == Book.FileFormat.TXT) {
                String content = Files.readString(path);
                return Math.max(1, (content.length() + 2499) / 2500);
            }
            if (format == Book.FileFormat.EPUB) {
                try (ZipFile zipFile = new ZipFile(path.toFile())) {
                    long count = zipFile.stream().filter(e -> e.getName().endsWith(".html") || e.getName().endsWith(".xhtml")).count();
                    return (int) Math.max(1, count);
                }
            }
        } catch (Exception ignored) { return 1; }
        return 1;
    }

    private String extractPdfPage(Path filePath, int pageNumber) throws Exception {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            String text = stripper.getText(document);
            return text.isEmpty() ? "Page vide." : text;
        }
    }

    private String extractWordHtml(Path filePath, int pageNumber) throws Exception {
        int paragraphsPerPage = 4;
        int start = Math.max(0, (pageNumber - 1) * paragraphsPerPage);
        int end = start + paragraphsPerPage;
        if (filePath.toString().toLowerCase().endsWith(".doc")) {
            try (FileInputStream fis = new FileInputStream(filePath.toFile()); HWPFDocument doc = new HWPFDocument(fis); WordExtractor extractor = new WordExtractor(doc)) {
                StringBuilder html = new StringBuilder("<div class='word-doc'>");
                String[] paragraphs = extractor.getParagraphText();
                for (int i = start; i < Math.min(end, paragraphs.length); i++) {
                    String text = paragraphs[i] == null ? "" : paragraphs[i].trim();
                    if (!text.isBlank()) html.append("<p>").append(escapeHtml(text)).append("</p>");
                }
                html.append("</div>");
                return html.toString();
            }
        }
        try (FileInputStream fis = new FileInputStream(filePath.toFile()); XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder html = new StringBuilder("<div class='word-doc'>");
            List<XWPFParagraph> paragraphs = doc.getParagraphs().stream().filter(p -> !p.getText().trim().isBlank()).toList();
            for (int i = start; i < Math.min(end, paragraphs.size()); i++) {
                String text = paragraphs.get(i).getText();
                if (!text.isBlank()) html.append("<p>").append(escapeHtml(text)).append("</p>");
            }
            if (pageNumber == 1) {
                for (XWPFPictureData pic : doc.getAllPictures()) {
                    String mime = pic.getPackagePart().getContentType();
                    String base64 = Base64.getEncoder().encodeToString(pic.getData());
                    html.append("<img src='data:").append(mime).append(";base64,").append(base64).append("'/>");
                }
            }
            html.append("</div>");
            return html.toString();
        }
    }

    private String extractTxtPage(Path filePath, int pageNumber) throws Exception {
        String content = Files.readString(filePath);
        int charsPerPage = 2500;
        int debut = Math.max(0, (pageNumber - 1) * charsPerPage);
        int fin = Math.min(debut + charsPerPage, content.length());
        if (debut >= content.length()) return "";
        return escapeHtml(content.substring(debut, fin));
    }

    private String extractEpubHtml(Path filePath, int pageNumber, Long bookId) throws Exception {
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            List<? extends ZipEntry> entries = zipFile.stream()
                    .filter(e -> e.getName().endsWith(".html") || e.getName().endsWith(".xhtml"))
                    .sorted((a, b) -> a.getName().compareTo(b.getName()))
                    .toList();
            if (entries.isEmpty()) return "Contenu EPUB introuvable";
            ZipEntry entry = entries.get(Math.min(pageNumber - 1, entries.size() - 1));
            String baseDir = entry.getName().contains("/") ? entry.getName().substring(0, entry.getName().lastIndexOf('/') + 1) : "";
            try (InputStream is = zipFile.getInputStream(entry)) {
                String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
                html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");
                html = html.replaceAll("(?i)src=['\"]([^'\"]+)['\"]", "src=\"/api/books/" + bookId + "/epub/resource?path=" + baseDir + "$1\"");
                return html;
            }
        }
    }

    private void enrichMetadata(Book book) {
        if (book.getAuthor() != null && !book.getAuthor().isBlank() && !"Auteur inconnu".equalsIgnoreCase(book.getAuthor())) return;
        String author = null;
        try {
            Path path = Paths.get(book.getFilePath());
            if (book.getFormat() == Book.FileFormat.PDF) {
                try (PDDocument doc = Loader.loadPDF(path.toFile())) { author = doc.getDocumentInformation().getAuthor(); }
            } else if (book.getFormat() == Book.FileFormat.WORD) {
                try (FileInputStream fis = new FileInputStream(path.toFile()); XWPFDocument doc = new XWPFDocument(fis)) { author = doc.getProperties().getCoreProperties().getCreator(); }
            } else if (book.getFormat() == Book.FileFormat.EPUB) {
                author = extractEpubCreator(path);
            }
        } catch (Exception ignored) {}
        book.setAuthor(normalizeAuthor(author));
    }

    private String extractEpubCreator(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (entry.getName().endsWith(".opf")) {
                    String opf = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                    Matcher m = Pattern.compile("<[^:>]*:?creator[^>]*>(.*?)</[^:>]*:?creator>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(opf);
                    if (m.find()) return stripTags(m.group(1)).trim();
                }
            }
        }
        return null;
    }

    private String extractCoverImage(Book book) {
        try {
            Path coverDir = Paths.get(COVER_DIR);
            Files.createDirectories(coverDir);
            Path file = Paths.get(book.getFilePath());
            Path output = coverDir.resolve("cover_" + System.nanoTime() + ".png");
            if (book.getFormat() == Book.FileFormat.PDF) {
                try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                    if (doc.getNumberOfPages() == 0) return null;
                    BufferedImage img = new PDFRenderer(doc).renderImageWithDPI(0, 120);
                    ImageIO.write(img, "png", output.toFile());
                    return output.toString();
                }
            }
            if (book.getFormat() == Book.FileFormat.WORD) {
                try (FileInputStream fis = new FileInputStream(file.toFile()); XWPFDocument doc = new XWPFDocument(fis)) {
                    if (doc.getAllPictures().isEmpty()) return null;
                    XWPFPictureData pic = doc.getAllPictures().get(0);
                    Files.write(output, pic.getData());
                    return output.toString();
                }
            }
            if (book.getFormat() == Book.FileFormat.EPUB) {
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    Optional<? extends ZipEntry> img = zip.stream().filter(e -> {
                        String n = e.getName().toLowerCase();
                        return !e.isDirectory() && (n.contains("cover") || n.contains("couverture")) && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"));
                    }).findFirst();
                    if (img.isPresent()) {
                        Files.write(output, zip.getInputStream(img.get()).readAllBytes());
                        return output.toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String cleanTitle(String filename) {
        if (filename == null) return "Sans titre";
        return filename.replaceFirst("\\.[^.]+$", "");
    }

    private String normalizeAuthor(String author) {
        return (author == null || author.isBlank()) ? "Auteur inconnu" : author.trim();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#039;");
    }

    private String stripTags(String html) {
        return html == null ? "" : html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    private void verifyUserOwnsBook(Book book, Long userId) {
        boolean owns = book.getUsers().stream().anyMatch(u -> u.getId().equals(userId));
        if (!owns) throw new AccessDeniedException("Accès refusé à ce livre");
    }
}
