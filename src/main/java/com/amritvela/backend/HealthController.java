package com.amritvela.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String root() {
        return "Backend Running Successfully 🚀";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}