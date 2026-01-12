package com.example.spotifyhost.controller;

import com.example.spotifyhost.service.SpotifyAuthService;
import com.example.spotifyhost.service.SpotifyTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthController {
  private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

  private final SpotifyAuthService authService;

  public OAuthController(SpotifyAuthService authService) {
    this.authService = authService;
  }

  @GetMapping("/oauth/login")
  public ResponseEntity<Void> login() {
    String url = authService.buildLoginUrl();
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(java.net.URI.create(url));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  @GetMapping("/oauth/callback")
  public ResponseEntity<String> callback(
    @RequestParam("code") String code,
    @RequestParam("state") String state
  ) {
    if (!authService.isStateValid(state)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid state parameter.");
    }
    SpotifyTokenResponse tokenResponse = authService.exchangeCodeForToken(code);
    if (tokenResponse.getRefreshToken() != null && !tokenResponse.getRefreshToken().isBlank()) {
      log.info("Spotify refresh token obtained. Save this value to SPOTIFY_REFRESH_TOKEN: {}", tokenResponse.getRefreshToken());
    }
    return ResponseEntity.ok("Spotify account linked successfully. You may close this window.");
  }
}
