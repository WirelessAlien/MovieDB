package com.wirelessalien.android.moviedb.data;

import android.os.Parcel;
import android.os.Parcelable;

public class ListDetails implements Parcelable {

    private final String mediaType;
    private final String title;
    private final String posterPath;
    private final String overview;
    private final String releaseDate;
    private final double voteAverage;
    private final int voteCount;
    private final int id;
    private final String backdropPath;

    public ListDetails(String mediaType, String title, String posterPath, String overview, String releaseDate, double voteAverage, int voteCount, int id, String backdropPath) {
        this.mediaType = mediaType;
        this.title = title;
        this.posterPath = posterPath;
        this.overview = overview;
        this.releaseDate = releaseDate;
        this.voteAverage = voteAverage;
        this.voteCount = voteCount;
        this.id = id;
        this.backdropPath = backdropPath;
    }

    protected ListDetails(Parcel in) {
        mediaType = in.readString();
        title = in.readString();
        posterPath = in.readString();
        overview = in.readString();
        releaseDate = in.readString();
        voteAverage = in.readDouble();
        voteCount = in.readInt();
        id = in.readInt();
        backdropPath = in.readString();
    }

    public static final Creator<ListDetails> CREATOR = new Creator<ListDetails>() {
        @Override
        public ListDetails createFromParcel(Parcel in) {
            return new ListDetails(in);
        }

        @Override
        public ListDetails[] newArray(int size) {
            return new ListDetails[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mediaType);
        dest.writeString(title);
        dest.writeString(posterPath);
        dest.writeString(overview);
        dest.writeString(releaseDate);
        dest.writeDouble(voteAverage);
        dest.writeInt(voteCount);
        dest.writeInt(id);
        dest.writeString(backdropPath);
    }
    public String getMediaType() {
        return mediaType;
    }

    public String getTitle() {
        return title;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public String getOverview() {
        return overview;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public double getVoteAverage() {
        return voteAverage;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public int getId() {
        return id;
    }

    public String getBackdropPath() {
        return backdropPath;
    }

}