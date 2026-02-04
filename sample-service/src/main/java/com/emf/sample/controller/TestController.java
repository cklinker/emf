package com.emf.sample.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of("message", "Hello from sample service!"));
    }
    
    @GetMapping("/echo/{name}")
    public ResponseEntity<Map<String, String>> echo(@PathVariable("name") String name) {
        return ResponseEntity.ok(Map.of("echo", name));
    }
}
