package com.example.spotifyhost.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  public OpenAPI spotifyHostOpenApi() {
    return new OpenAPI()
      .info(new Info()
        .title("Spotify Host Backend API")
        .description("Host-only backend for fetching Spotify playlist tracks.")
        .version("1.0.0"));
  }
}
