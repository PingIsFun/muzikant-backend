package io.github.pingisfun.muzikant.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.pingisfun.muzikant.model.PlaylistResponse;
import io.github.pingisfun.muzikant.model.TrackDto;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SpotifyApiService {
  private static final String TRACK_FIELDS = "items(track(id,name,artists(name),album(name,release_date),external_urls(spotify))),next";

  private static final Logger log = LoggerFactory.getLogger(SpotifyApiService.class);

  private final RestTemplate restTemplate;
  private final SpotifyTokenService tokenService;
  private final Semaphore spotifySemaphore;

  // Backend enforces global Spotify backoff and serializes API calls to respect Spotify Web API rate limits.
  private final AtomicLong retryAfterEpochMs = new AtomicLong(0);

  public SpotifyApiService(
    RestTemplate restTemplate,
    SpotifyTokenService tokenService,
    @Value("${spotify.max.concurrent.calls:1}") int maxConcurrentCalls
  ) {
    this.restTemplate = restTemplate;
    this.tokenService = tokenService;
    this.spotifySemaphore = new Semaphore(Math.max(1, maxConcurrentCalls));
  }

  public PlaylistResponse fetchPlaylist(String playlistId) {
    List<TrackDto> results = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    String playlistName = fetchPlaylistName(playlistId);

    String nextUrl = UriComponentsBuilder
      .fromHttpUrl("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks")
      .queryParam("limit", 100)
      .queryParam("offset", 0)
      .queryParam("fields", TRACK_FIELDS)
      .build()
      .encode()
      .toUriString();

    while (nextUrl != null && !nextUrl.isBlank()) {
      PlaylistTracksResponse page = get(nextUrl, PlaylistTracksResponse.class);
      if (page == null || page.items == null) {
        break;
      }
      addTracks(results, seen, page.items);
      nextUrl = page.next;
    }

    results.sort(Comparator.comparing(TrackDto::getYear, Comparator.nullsLast(Integer::compareTo)));
    return new PlaylistResponse(playlistName, results);
  }

  private String fetchPlaylistName(String playlistId) {
    String url = UriComponentsBuilder
      .fromHttpUrl("https://api.spotify.com/v1/playlists/" + playlistId)
      .queryParam("fields", "name")
      .build()
      .encode()
      .toUriString();
    PlaylistNameResponse response = get(url, PlaylistNameResponse.class);
    return response != null ? response.name : null;
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
      log.info("Waiting {} ms before Spotify request (rate limited until {}).", waitMs, Instant.ofEpochMilli(retryUntil));
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
    Instant start = Instant.now();
    log.info("Spotify request start: {}", url);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenService.getValidAccessToken());
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    log.info("Spotify request finished in {} ms: {}", Duration.between(start, Instant.now()).toMillis(), url);
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

  private void addTracks(List<TrackDto> results, Set<String> seen, List<PlaylistTrackItem> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    for (PlaylistTrackItem item : items) {
      if (item == null || item.track == null || item.isLocal) {
        continue;
      }
      if (item.track.id == null || item.track.id.isBlank()) {
        continue;
      }
      if (!seen.add(item.track.id)) {
        continue;
      }
      String artists = buildArtistNames(item.track.artists);
      String album = item.track.album != null ? item.track.album.name : null;
      Integer year = extractYear(item.track.album != null ? item.track.album.releaseDate : null);
      String spotifyUrl = item.track.externalUrls != null ? item.track.externalUrls.spotify : null;
      results.add(new TrackDto(item.track.id, item.track.name, artists, album, year, spotifyUrl));
    }
  }

  private String buildArtistNames(List<SpotifyArtist> artists) {
    if (artists == null || artists.isEmpty()) {
      return "";
    }
    return artists.stream()
      .map(artist -> artist != null ? artist.name : null)
      .filter(Objects::nonNull)
      .filter(name -> !name.isBlank())
      .collect(Collectors.joining(", "));
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
    public String name;
    @JsonProperty("release_date")
    public String releaseDate;
  }

  private static class SpotifyArtist {
    public String name;
  }

  private static class SpotifyExternalUrls {
    public String spotify;
  }

  private static class PlaylistNameResponse {
    public String name;
  }

}
