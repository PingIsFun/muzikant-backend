package com.example.spotifyhost.service;

import com.example.spotifyhost.model.TrackDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SpotifyApiService {
  private final RestTemplate restTemplate;
  private final SpotifyTokenService tokenService;

  public SpotifyApiService(RestTemplate restTemplate, SpotifyTokenService tokenService) {
    this.restTemplate = restTemplate;
    this.tokenService = tokenService;
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
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(tokenService.getValidAccessToken());
    HttpEntity<Void> entity = new HttpEntity<>(headers);
    ResponseEntity<T> response = restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    return response.getBody();
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


  private static class SpotifyImage {
    public String url;
    public Integer width;
    public Integer height;
  }
}
