package com.best_reader.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "redirect:/auth/Login";
    }

    @GetMapping("/auth/Login")
    public String login() {
        return "auth/Login";
    }

    @GetMapping("/auth/Inscription")
    public String inscription() {
        return "auth/Register";
    }

    @GetMapping("/Library")
    public String library() {
        return "Library/Index";
    }

    @GetMapping({"/Reader/{bookId:[0-9]+}", "/reader/{bookId:[0-9]+}"})
    public String reader(@PathVariable Long bookId) {
        return "Reader";
    }

    @GetMapping({"/Notes", "/notes"})
    public String notes() {
        return "Notes";
    }

     @GetMapping({"/Annotations", "/annotations"})
    public String annotations() {
        return "Annotations";
    }
}