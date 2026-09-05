package com.example.scheduleservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindRequest(
        @NotBlank @Size(max = 64) String studentId,
        @NotBlank @Size(min = 6, max = 128) String jwPassword
) {
}
