package io.github.pingisfun.muzikant.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class SpotifyTokenService {
  private final RestTemplate restTemplate;
  private final String clientId;
  private final String clientSecret;

  private String accessToken;
  private Instant expiresAt;
  private String refreshToken;

  public SpotifyTokenService(
    RestTemplate restTemplate,
    @Value("${SPOTIFY_CLIENT_ID}") String clientId,
    @Value("${SPOTIFY_CLIENT_SECRET}") String clientSecret,
    @Value("${SPOTIFY_REFRESH_TOKEN:}") String refreshToken
  ) {
    this.restTemplate = restTemplate;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    if (refreshToken != null && !refreshToken.isBlank()) {
      this.refreshToken = refreshToken.trim();
    }
  }

  public synchronized void updateFromAuthorization(SpotifyTokenResponse tokenResponse) {
    this.accessToken = tokenResponse.getAccessToken();
    this.expiresAt = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
    if (tokenResponse.getRefreshToken() != null && !tokenResponse.getRefreshToken().isBlank()) {
      this.refreshToken = tokenResponse.getRefreshToken();
    }
  }

  public synchronized String getValidAccessToken() {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalStateException("SPOTIFY_REFRESH_TOKEN is not set. Complete /oauth/login first.");
    }
    if (accessToken == null || expiresAt == null || Instant.now().isAfter(expiresAt.minusSeconds(60))) {
      refreshAccessToken();
    }
    return accessToken;
  }

  public synchronized String getRefreshToken() {
    return refreshToken;
  }

  private void refreshAccessToken() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("Authorization", "Basic " + basicAuthHeader());

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "refresh_token");
    body.add("refresh_token", refreshToken);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
    SpotifyTokenResponse response = restTemplate.postForObject(
      "https://accounts.spotify.com/api/token",
      request,
      SpotifyTokenResponse.class
    );
    if (response == null || response.getAccessToken() == null) {
      throw new IllegalStateException("Failed to refresh Spotify access token.");
    }
    updateFromAuthorization(response);
  }

  private String basicAuthHeader() {
    String creds = clientId + ":" + clientSecret;
    return Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
  }
}
