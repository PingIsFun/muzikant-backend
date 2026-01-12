package io.github.pingisfun.muzikant.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.pingisfun.muzikant.model.TrackDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class SpotifyApiService {
  private static final Logger log = LoggerFactory.getLogger(SpotifyApiService.class);

  private final RestTemplate restTemplate;
  private final SpotifyTokenService tokenService;
  private final Semaphore spotifySemaphore;

  // Backend enforces global Spotify backoff and serializes API calls to respect Spotify Web API rate limits.
  private final AtomicLong retryAfterEpochMs = new AtomicLong(0);

  public SpotifyApiService(
    RestTemplate restTemplate,
    SpotifyTokenService tokenService,
    @org.springframework.beans.factory.annotation.Value("${spotify.max.concurrent.calls:1}") int maxConcurrentCalls
  ) {
    this.restTemplate = restTemplate;
    this.tokenService = tokenService;
    this.spotifySemaphore = new Semaphore(Math.max(1, maxConcurrentCalls));
  }

  public List<TrackDto> fetchPlaylistTracks(String playlistId) {
    List<TrackDto> results = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    int offset = 0;
    int limit = 100;

    while (true) {
      String url = "https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=" + limit + "&offset=" + offset;
      PlaylistTracksResponse response = get(url, PlaylistTracksResponse.class);
      if (response == null || response.items == null) {
        break;
      }
      for (PlaylistTrackItem item : response.items) {
        if (item == null || item.track == null || item.isLocal) {
          continue;
        }
        if (item.track.id == null || item.track.id.isBlank()) {
          continue;
        }
        if (seen.add(item.track.id)) {
          String artist = "";
          if (item.track.artists != null && !item.track.artists.isEmpty()) {
            artist = item.track.artists.get(0).name;
          }
          Integer year = extractYear(item.track.album != null ? item.track.album.releaseDate : null);
          String spotifyUrl = item.track.externalUrls != null ? item.track.externalUrls.spotify : null;
          results.add(new TrackDto(item.track.id, item.track.name, artist, year, spotifyUrl));
        }
      }
      if (response.next == null || response.next.isBlank()) {
        break;
      }
      offset += limit;
    }

    results.sort(Comparator.comparing(TrackDto::getYear, Comparator.nullsLast(Integer::compareTo)));
    return results;
  }

  private <T> T get(String url, Class<T> responseType) {
    waitIfNeeded();
    try {
      spotifySemaphore.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SpotifyRateLimitException("Spotify API temporarily unavailable. Please try again shortly.");
    }
    try {
      return executeWithBackoff(url, responseType);
    } finally {
      spotifySemaphore.release();
    }
  }

  private void waitIfNeeded() {
    long now = System.currentTimeMillis();
    long retryUntil = retryAfterEpochMs.get();
    if (now < retryUntil) {
      long waitMs = retryUntil - now;
      log.info("Waiting {} ms before Spotify request.", waitMs);
      sleepMillis(waitMs);
    }
  }

  private <T> T executeWithBackoff(String url, Class<T> responseType) {
    try {
      return doGet(url, responseType);
    } catch (HttpClientErrorException.TooManyRequests e) {
      int retrySeconds = parseRetryAfterSeconds(e);
      long retryUntil = System.currentTimeMillis() + (retrySeconds * 1000L);
      retryAfterEpochMs.set(retryUntil);
      log.warn("Spotify 429 received. Global backoff until {}.", Instant.ofEpochMilli(retryUntil));
      sleepMillis(retrySeconds * 1000L);
      try {
        return doGet(url, responseType);
      } catch (HttpClientErrorException.TooManyRequests retryError) {
        throw new SpotifyRateLimitException("Spotify API temporarily unavailable. Please try again shortly.");
      }
    }
  }

  private <T> T doGet(String url, Class<T> responseType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenService.getValidAccessToken());
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    return response.getBody();
  }

  private int parseRetryAfterSeconds(HttpClientErrorException.TooManyRequests e) {
    return Optional.ofNullable(e.getResponseHeaders())
      .map(headers -> headers.getFirst("Retry-After"))
      .flatMap(value -> {
        try {
          return Optional.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
          return Optional.empty();
        }
      })
      .orElse(5);
  }

  private void sleepMillis(long waitMs) {
    try {
      Thread.sleep(waitMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Integer extractYear(String releaseDate) {
    if (releaseDate == null || releaseDate.isBlank()) {
      return null;
    }
    if (releaseDate.length() >= 4) {
      try {
        return Integer.parseInt(releaseDate.substring(0, 4));
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }


  private static class PlaylistTracksResponse {
    public List<PlaylistTrackItem> items;
    public String next;
  }

  private static class PlaylistTrackItem {
    @JsonProperty("is_local")
    public boolean isLocal;
    public SpotifyTrack track;
  }

  private static class SpotifyTrack {
    public String id;
    public String name;
    public SpotifyAlbum album;
    public List<SpotifyArtist> artists;
    @JsonProperty("external_urls")
    public SpotifyExternalUrls externalUrls;
  }

  private static class SpotifyAlbum {
    @JsonProperty("release_date")
    public String releaseDate;
  }

  private static class SpotifyArtist {
    public String name;
  }

  private static class SpotifyExternalUrls {
    public String spotify;
  }


}
