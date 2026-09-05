package com.example.scheduleservice.model;

public record JwAccount(
        long id,
        long userId,
        String studentId,
        String jwPassword
) {
}
