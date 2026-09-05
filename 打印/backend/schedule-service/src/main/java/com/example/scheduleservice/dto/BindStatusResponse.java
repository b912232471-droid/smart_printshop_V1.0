package com.example.scheduleservice.dto;

public record BindStatusResponse(
        boolean bound,
        String studentId
) {
}
