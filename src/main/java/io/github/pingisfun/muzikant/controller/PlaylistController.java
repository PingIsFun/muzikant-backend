package io.github.pingisfun.muzikant.controller;

import io.github.pingisfun.muzikant.model.PlaylistResponse;
import io.github.pingisfun.muzikant.service.SpotifyApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlaylistController {
  private final SpotifyApiService apiService;

  public PlaylistController(SpotifyApiService apiService) {
    this.apiService = apiService;
  }

  @GetMapping(value = "/playlist/{playlistId}")
  public ResponseEntity<PlaylistResponse> playlist(@PathVariable String playlistId) {
    if (playlistId == null || playlistId.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    PlaylistResponse playlist = apiService.fetchPlaylist(playlistId);
    return ResponseEntity.ok(playlist);
  }
}
