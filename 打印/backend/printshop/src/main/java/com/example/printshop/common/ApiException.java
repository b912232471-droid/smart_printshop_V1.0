package com.example.printshop.common;

public class ApiException extends RuntimeException {
    private final int status;
    private final int code;

    private ApiException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public int getCode() {
        return code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, 400, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, 401, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(403, 403, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(429, 429, message);
    }

    public static ApiException serviceUnavailable(String message) {
        return new ApiException(503, 503, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, 404, message);
    }
}
