package io.github.pingisfun.muzikant.controller;

import io.github.pingisfun.muzikant.model.PlaylistRequest;
import io.github.pingisfun.muzikant.model.TrackDto;
import io.github.pingisfun.muzikant.service.SpotifyApiService;
import io.github.pingisfun.muzikant.util.SpotifyUrlParser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlaylistController {
  private final SpotifyApiService apiService;

  public PlaylistController(SpotifyApiService apiService) {
    this.apiService = apiService;
  }

  @GetMapping(value = "/playlist/{playlistId}")
  public ResponseEntity<List<TrackDto>> playlist(@PathVariable String playlistId) {
    if (playlistId == null || playlistId.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    List<TrackDto> tracks = apiService.fetchPlaylistTracks(playlistId);
    return ResponseEntity.ok(tracks);
  }
}
