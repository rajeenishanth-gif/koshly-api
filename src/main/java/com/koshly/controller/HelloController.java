package com.koshly.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    // A record is a small, read-only data class. Spring turns it into JSON.
    record Hello(String message, Instant time) {
    }

    @GetMapping("/api/hello")
    public Hello hello() {
        return new Hello("Hello from the Koshly API", Instant.now());
    }
}