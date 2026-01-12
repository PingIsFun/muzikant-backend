package io.github.pingisfun.muzikant.service;

public class SpotifyRateLimitException extends RuntimeException {
  public SpotifyRateLimitException(String message) {
    super(message);
  }
}
