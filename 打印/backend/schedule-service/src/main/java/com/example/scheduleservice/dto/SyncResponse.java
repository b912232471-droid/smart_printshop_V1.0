package com.example.scheduleservice.dto;

public record SyncResponse(
        int imported,
        String xnm,
        String xqm
) {
}
