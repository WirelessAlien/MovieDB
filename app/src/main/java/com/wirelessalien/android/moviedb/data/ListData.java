package com.wirelessalien.android.moviedb.data;

public class ListData {
    private final int id;
    private final String name;
    private final String description;
    private final int itemCount;
    private final double averageRating;


    public ListData(int id, String name, String description, int itemCount, double averageRating) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.itemCount = itemCount;
        this.averageRating = averageRating;

    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getItemCount() {
        return itemCount;
    }

    public double getAverageRating() {
        return averageRating;
    }
}
