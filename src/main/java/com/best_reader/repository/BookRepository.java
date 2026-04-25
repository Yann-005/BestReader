package com.best_reader.repository;

import com.best_reader.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByUsersIdOrderByTitleAsc(Long userId);

    List<Book> findByUsersIdOrderByUploadedAtDesc(Long userId);

    List<Book> findByUsersIdAndFavoriteTrue(Long userId);

    List<Book> findByUsersId(Long userId);

    Optional<Book> findByShareToken(String shareToken);

     @Query("SELECT b FROM Book b JOIN b.users u JOIN b.progress p WHERE u.id = :userId ORDER BY p.lastReadAt DESC")
    List<Book> findByUsersIdOrderByLastReadDesc(@Param("userId") Long userId);

    @Query("SELECT b FROM Book b JOIN b.users u WHERE u.id = :userId AND (LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(b.author) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Book> searchByTitleOrAuthor(@Param("userId") Long userId, @Param("search") String search);
}