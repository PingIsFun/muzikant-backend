package io.github.pingisfun.muzikant.util;

public final class SpotifyUrlParser {
  private SpotifyUrlParser() {}

  public static String extractPlaylistId(String input) {
    if (input == null || input.isBlank()) {
      return input;
    }
    String trimmed = input.trim();
    if (!trimmed.contains("spotify.com")) {
      return trimmed;
    }
    String[] parts = trimmed.split("/");
    for (int i = 0; i < parts.length; i++) {
      if ("playlist".equals(parts[i]) && i + 1 < parts.length) {
        String idPart = parts[i + 1];
        int queryIndex = idPart.indexOf('?');
        return queryIndex > -1 ? idPart.substring(0, queryIndex) : idPart;
      }
    }
    return trimmed;
  }
}
