package com.best_reader.service;

import com.best_reader.exception.EmailExistsException;
import com.best_reader.exception.NotFoundException;
import com.best_reader.exception.UsernameExistsException;
import com.best_reader.exception.AccessDeniedException;
import com.best_reader.model.User;
import com.best_reader.model.UserPreference;
import com.best_reader.repository.UserRepository;
import com.best_reader.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new EmailExistsException("Cet email est déjà utilisé");

        }
             if (userRepository.existsByUsername(user.getUsername())){
            throw new UsernameExistsException("Ce nom d'utilisateur est déjà utilisé");
 
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        createDefaultPreference(savedUser);
        return savedUser;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable"));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Utilisateur n'existe pas"));
    }

    @Transactional
    public User updateProfile(Long id, User updatedUser) {
        User user = findById(id);
        user.setFullName(updatedUser.getFullName());
        return userRepository.save(user);
    }

    @Transactional
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        User user = findById(id);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new AccessDeniedException("Ancien mot de passe incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(Long id) {
        User user = findById(id);
        userRepository.delete(user);
    }

    private void createDefaultPreference(User user) {
        UserPreference preference = new UserPreference();
        preference.setUser(user);
        preference.setTheme("light");
        preference.setFontSize(16);
        userPreferenceRepository.save(preference);
    }
}