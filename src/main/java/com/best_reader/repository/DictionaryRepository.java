package com.best_reader.repository;

import com.best_reader.model.DictionaryWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DictionaryRepository extends JpaRepository<DictionaryWord, Long> {

    Optional<DictionaryWord> findByWordAndLanguage(String word, String language);

    List<DictionaryWord> findByWordIgnoreCaseAndLanguageIgnoreCaseOrderByIdAsc(String word, String language);

    List<DictionaryWord> findByWordContainingIgnoreCaseAndLanguage(String word, String language);

    boolean existsByWordAndLanguage(String word, String language);
}
