package com.best_reader.controller;

import com.best_reader.model.DictionaryWord;
import com.best_reader.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary")
@RequiredArgsConstructor
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/search")
    public ResponseEntity<DictionaryWord> searchWord(
            @RequestParam String word,
            @RequestParam(defaultValue = "fr") String language) {
        return ResponseEntity.ok(dictionaryService.findWord(word, language));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<DictionaryWord>> getSuggestions(
            @RequestParam String word,
            @RequestParam(defaultValue = "fr") String language) {
        return ResponseEntity.ok(dictionaryService.searchWords(word, language));
    }

    @PostMapping("/save")
    public ResponseEntity<DictionaryWord> saveWord(@RequestBody Map<String, Object> body) {
        DictionaryWord word = new DictionaryWord();
        word.setWord(asString(body.get("word")));
        word.setLanguage(asStringOrDefault(body.get("language"), "fr"));
        word.setShortDefinition(asString(body.get("shortDefinition")));
        word.setDefinition(asString(body.get("definition")));
        word.setSynonyms(asString(body.get("synonyms")));
        // Important : on ne renvoie plus jamais 500 au lecteur pour une simple sauvegarde dictionnaire.
        // Si la base refuse l'écriture, le service renvoie quand même un objet propre au navigateur.
        return ResponseEntity.ok(dictionaryService.saveOfflineSafe(word));
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> wordExists(
            @RequestParam String word,
            @RequestParam(defaultValue = "fr") String language) {
        return ResponseEntity.ok(dictionaryService.wordExists(word, language));
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String asStringOrDefault(Object value, String defaultValue) {
        String text = asString(value).trim();
        return text.isBlank() ? defaultValue : text;
    }
}
