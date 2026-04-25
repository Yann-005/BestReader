package com.best_reader.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> gererIntrouvable(NotFoundException ex) {
        return construireReponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailExistsException.class)
    public ResponseEntity<Map<String, Object>> gererEmailDejaUtilise(EmailExistsException ex) {
        return construireReponse(HttpStatus.CONFLICT, ex.getMessage());
    }

     @ExceptionHandler(UsernameExistsException.class)
    public ResponseEntity<Map<String, Object>> gererUsernameDejaUtilise(UsernameExistsException ex) {
        return construireReponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> gererAccesRefuse(AccessDeniedException ex) {
        return construireReponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(FileException.class)
    public ResponseEntity<Map<String, Object>> gererErreurFichier(FileException ex) {
        return construireReponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> gererErreurGenerale(Exception ex) {
        return construireReponse(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur inattendue s'est produite");
    }

    private ResponseEntity<Map<String, Object>> construireReponse(HttpStatus status, String message) {
        Map<String, Object> reponse = new HashMap<>();
        reponse.put("status", status.value());
        reponse.put("message", message);
        reponse.put("timestamp", LocalDateTime.now());
        return new ResponseEntity<>(reponse, status);
    }
}
