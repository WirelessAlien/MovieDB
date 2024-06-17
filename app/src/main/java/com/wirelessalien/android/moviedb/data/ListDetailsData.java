/*
 *     This file is part of Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     Movie DB is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Movie DB is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Movie DB.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.data;

public class ListDetailsData {
    private final int movieId;
    private final int listId;
    private final String listName;
    private final String mediaType;
    private final boolean isMovieInList;

    public ListDetailsData(int movieId, int listId, String listName, String mediaType, boolean isMovieInList) {
        this.movieId = movieId;
        this.listId = listId;
        this.listName = listName;
        this.mediaType = mediaType;
        this.isMovieInList = isMovieInList;
    }

    public int getMovieId() {
        return movieId;
    }

    public int getListId() {
        return listId;
    }

    public String getListName() {
        return listName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public boolean isMovieInList() {
        return isMovieInList;
    }
}
