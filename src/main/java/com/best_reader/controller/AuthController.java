package com.best_reader.controller;

import com.best_reader.model.User;
import com.best_reader.security.service.UserDetailsServiceImpl;
import com.best_reader.security.util.JwtUtil;
import com.best_reader.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/Register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody User user) {
        User savedUser = userService.register(user);
        
        // Générer un token JWT après l'inscription
        String token = jwtUtil.generateToken(savedUser.getEmail());
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Inscription réussie");
        response.put("userId", savedUser.getId());
        response.put("token", token);
        response.put("username", savedUser.getUsername());
        response.put("email", savedUser.getEmail());
        return ResponseEntity.ok(response);
    }

   @PostMapping("/Login")
    public ResponseEntity<Map<String, Object>> login(
        @RequestBody Map<String, String> request) {
    String identifier = request.get("identifier");
    String password = request.get("password");

    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                    identifier, password)
    );

    UserDetails userDetails = userDetailsService
            .loadUserByUsername(identifier);
    String token = jwtUtil.generateToken(
            userDetails.getUsername());

    User user = userService.findByEmail(
            userDetails.getUsername());

    Map<String, Object> response = new HashMap<>();
    response.put("token", token);
    response.put("userId", user.getId());
    response.put("email", user.getEmail());
    response.put("username", user.getUsername());
    return ResponseEntity.ok(response);
 }

    @PostMapping("/Logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Déconnexion réussie");
        return ResponseEntity.ok(response);
    }
}