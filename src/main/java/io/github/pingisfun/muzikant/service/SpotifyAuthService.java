package io.github.pingisfun.muzikant.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SpotifyAuthService {
  private static final String SCOPES = "";

  private final RestTemplate restTemplate;
  private final SpotifyTokenService tokenService;
  private final String clientId;
  private final String clientSecret;
  private final String redirectUri;
  private String lastState;

  // This backend uses a single host Spotify account for personal use and does not allow third-party user authentication.
  public SpotifyAuthService(
    RestTemplate restTemplate,
    SpotifyTokenService tokenService,
    @Value("${spotify.client.id}") String clientId,
    @Value("${spotify.client.secret}") String clientSecret,
    @Value("${spotify.redirect.uri}") String redirectUri
  ) {
    this.restTemplate = restTemplate;
    this.tokenService = tokenService;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.redirectUri = redirectUri;
  }

  public synchronized String buildLoginUrl() {
    lastState = UUID.randomUUID().toString();
    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://accounts.spotify.com/authorize")
      .queryParam("response_type", "code")
      .queryParam("client_id", clientId)
      .queryParam("redirect_uri", redirectUri)
      .queryParam("state", lastState);
    if (SCOPES != null && !SCOPES.isBlank()) {
      builder.queryParam("scope", SCOPES);
    }
    return builder.build().encode().toUriString();
  }

  public synchronized boolean isStateValid(String state) {
    return lastState != null && lastState.equals(state);
  }

  public SpotifyTokenResponse exchangeCodeForToken(String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    headers.set("Authorization", "Basic " + basicAuthHeader());

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("grant_type", "authorization_code");
    body.add("code", code);
    body.add("redirect_uri", redirectUri);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
    SpotifyTokenResponse response = restTemplate.postForObject(
      "https://accounts.spotify.com/api/token",
      request,
      SpotifyTokenResponse.class
    );
    if (response == null || response.getAccessToken() == null) {
      throw new IllegalStateException("Failed to exchange authorization code for tokens.");
    }
    tokenService.updateFromAuthorization(response);
    return response;
  }

  private String basicAuthHeader() {
    String creds = clientId + ":" + clientSecret;
    return Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
  }
}
