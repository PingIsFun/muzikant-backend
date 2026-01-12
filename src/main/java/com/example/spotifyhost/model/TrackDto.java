package com.example.spotifyhost.model;

public class TrackDto {
  private String id;
  private String title;
  private String artist;
  private Integer year;
  private String spotifyUrl;

  public TrackDto() {}

  public TrackDto(String id, String title, String artist, Integer year, String spotifyUrl) {
    this.id = id;
    this.title = title;
    this.artist = artist;
    this.year = year;
    this.spotifyUrl = spotifyUrl;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getArtist() {
    return artist;
  }

  public void setArtist(String artist) {
    this.artist = artist;
  }

  public Integer getYear() {
    return year;
  }

  public void setYear(Integer year) {
    this.year = year;
  }

  public String getSpotifyUrl() {
    return spotifyUrl;
  }

  public void setSpotifyUrl(String spotifyUrl) {
    this.spotifyUrl = spotifyUrl;
  }
}
