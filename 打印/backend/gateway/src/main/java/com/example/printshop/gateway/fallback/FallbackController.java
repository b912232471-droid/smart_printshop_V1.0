package com.example.printshop.gateway.fallback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {
    @RequestMapping("/print-service")
    public Mono<ResponseEntity<Map<String, Object>>> printService() {
        return unavailable("print service unavailable");
    }

    @RequestMapping("/photo-service")
    public Mono<ResponseEntity<Map<String, Object>>> photoService() {
        return unavailable("photo service unavailable");
    }

    @RequestMapping("/schedule-service")
    public Mono<ResponseEntity<Map<String, Object>>> scheduleService() {
        return unavailable("schedule service unavailable");
    }

    @RequestMapping("/chat-service")
    public Mono<ResponseEntity<Map<String, Object>>> chatService() {
        return unavailable("chat service unavailable");
    }

    private Mono<ResponseEntity<Map<String, Object>>> unavailable(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 503);
        body.put("message", message);
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
