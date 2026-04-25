package com.best_reader.service;

import com.best_reader.model.User;
import com.best_reader.model.UserPreference;
import com.best_reader.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserService userService;

    @Transactional
    public UserPreference findByUserId(Long userId) {
        return userPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));
    }

    @Transactional
    public UserPreference updateTheme(Long userId, String theme) {
        UserPreference preference = findByUserId(userId);
        preference.setTheme(normalizeTheme(theme));
        return userPreferenceRepository.save(preference);
    }

    @Transactional
    public UserPreference updateFontSize(Long userId, int fontSize) {
        UserPreference preference = findByUserId(userId);
        preference.setFontSize(clampFontSize(fontSize));
        return userPreferenceRepository.save(preference);
    }

    @Transactional
    public UserPreference updatePreferences(Long userId, String theme, int fontSize) {
        UserPreference preference = findByUserId(userId);
        preference.setTheme(normalizeTheme(theme));
        preference.setFontSize(clampFontSize(fontSize));
        return userPreferenceRepository.save(preference);
    }

    private UserPreference createDefaultPreference(Long userId) {
        User user = userService.findById(userId);
        UserPreference preference = new UserPreference();
        preference.setUser(user);
        preference.setTheme("clair");
        preference.setFontSize(16);
        return userPreferenceRepository.save(preference);
    }

    private int clampFontSize(int fontSize) {
        return Math.max(10, Math.min(36, fontSize));
    }

    private String normalizeTheme(String theme) {
        if (theme == null || theme.isBlank()) return "clair";
        return switch (theme) {
            case "light" -> "clair";
            case "dark" -> "sombre";
            case "clair", "sepia", "sombre" -> theme;
            default -> "clair";
        };
    }
}
