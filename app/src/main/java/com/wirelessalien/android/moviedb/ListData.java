package com.wirelessalien.android.moviedb;

public class ListData {
    private int id;
    private String name;

    public ListData(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
