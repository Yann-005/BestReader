package com.best_reader.controller;

import com.best_reader.model.UserPreference;
import com.best_reader.service.UserPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<UserPreference> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(userPreferenceService.findByUserId(userId));
    }

    @PutMapping("/user/{userId}/theme")
    public ResponseEntity<UserPreference> updateTheme(
            @PathVariable Long userId,
            @RequestParam String theme) {
        return ResponseEntity.ok(userPreferenceService.updateTheme(userId, theme));
    }

    @PutMapping("/user/{userId}/fontsize")
    public ResponseEntity<UserPreference> updateFontSize(
            @PathVariable Long userId,
            @RequestParam int fontSize) {
        return ResponseEntity.ok(userPreferenceService.updateFontSize(userId, fontSize));
    }

    @PutMapping("/user/{userId}")
    public ResponseEntity<UserPreference> updatePreferences(
            @PathVariable Long userId,
            @RequestParam String theme,
            @RequestParam int fontSize) {
        return ResponseEntity.ok(userPreferenceService.updatePreferences(userId, theme, fontSize));
    }
}
