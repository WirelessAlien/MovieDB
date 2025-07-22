/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */

package com.wirelessalien.android.moviedb.helper

import android.content.Context
import android.content.pm.PackageManager

object UpdateUtils {

    fun getInstalledVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }
    }

    fun isNewVersionAvailable(installedVersion: String, latestVersion: String): Boolean {
        val installed = installedVersion.split(".").map { it.toInt() }
        val latest = latestVersion.split(".").map { it.toInt() }
        val length = maxOf(installed.size, latest.size)
        for (i in 0 until length) {
            val installedPart = if (i < installed.size) installed[i] else 0
            val latestPart = if (i < latest.size) latest[i] else 0
            if (latestPart > installedPart) {
                return true
            }
            if (latestPart < installedPart) {
                return false
            }
        }
        return false
    }
}
