package com.example.thumbnailer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.Map;

/**
 * Turns the failures a caller can cause into JSON rather than a stack trace. Handled here rather
 * than in the controller because an oversized upload fails while the request is being parsed,
 * before any controller method is chosen.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({IOException.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(error(e.getMessage(), "That request could not be processed."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "That image is larger than the 5 MB limit."));
    }

    private Map<String, String> error(String message, String fallback) {
        return Map.of("error", message == null || message.isBlank() ? fallback : message);
    }
}
