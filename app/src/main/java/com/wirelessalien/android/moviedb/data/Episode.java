package com.wirelessalien.android.moviedb.data;

public class Episode {

    private String airDate;
    private int episodeNumber;
    private String name;
    private String overview;
    private int runtime;
    private double voteAverage;
    private String posterPath;

    public Episode(String airDate, int episodeNumber, String name, String overview, int runtime, String posterPath, double voteAverage) {
        this.airDate = airDate;
        this.episodeNumber = episodeNumber;
        this.name = name;
        this.overview = overview;
        this.runtime = runtime;
        this.posterPath = posterPath;
        this.voteAverage = voteAverage;
    }

    public String getAirDate() {
        return airDate;
    }

    public void setAirDate(String airDate) {
        this.airDate = airDate;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(int episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public int getRuntime() {
        return runtime;
    }

    public void setRuntime(int runtime) {
        this.runtime = runtime;
    }

    public double getVoteAverage() {
        return voteAverage;
    }

    public void setVoteAverage(double voteAverage) {
        this.voteAverage = voteAverage;
    }

    public String getPosterPath() {
        return "https://image.tmdb.org/t/p/w500" + posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

}