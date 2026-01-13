package io.github.pingisfun.muzikant.model;

import java.util.List;

public class PlaylistResponse {
  private String name;
  private List<TrackDto> tracks;

  public PlaylistResponse() {}

  public PlaylistResponse(String name, List<TrackDto> tracks) {
    this.name = name;
    this.tracks = tracks;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<TrackDto> getTracks() {
    return tracks;
  }

  public void setTracks(List<TrackDto> tracks) {
    this.tracks = tracks;
  }
}
