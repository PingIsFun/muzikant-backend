package io.github.pingisfun.muzikant.controller;

import io.github.pingisfun.muzikant.service.SpotifyRateLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(SpotifyRateLimitException.class)
  public ResponseEntity<String> handleSpotifyRateLimit(SpotifyRateLimitException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
  }
}
