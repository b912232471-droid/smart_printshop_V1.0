package com.example.scheduleservice.common;

public class ApiException extends RuntimeException {
    private final int status;

    private ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, message);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(503, message);
    }
}
