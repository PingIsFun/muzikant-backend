package io.github.pingisfun.muzikant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SpotifyStartupValidator implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(SpotifyStartupValidator.class);

  private final SpotifyTokenService tokenService;
  private final boolean oauthEnabled;

  public SpotifyStartupValidator(
    SpotifyTokenService tokenService,
    @Value("${spotify.oauth.enabled:false}") boolean oauthEnabled
  ) {
    this.tokenService = tokenService;
    this.oauthEnabled = oauthEnabled;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (oauthEnabled) {
      log.info("Spotify OAuth endpoints enabled.");
      return;
    }
    log.info("Spotify OAuth endpoints disabled. Using refresh token authentication only.");
    String refreshToken = tokenService.getRefreshToken();
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalStateException("spotify.refresh.token is required when OAuth is disabled.");
    }
    tokenService.getValidAccessToken();
  }
}
