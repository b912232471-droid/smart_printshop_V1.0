package com.example.scheduleservice.dto;

import jakarta.validation.constraints.Size;

public record SyncRequest(
        @Size(max = 16) String xnm,
        @Size(max = 16) String xqm
) {
}
