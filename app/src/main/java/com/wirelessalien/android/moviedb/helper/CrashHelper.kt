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
import android.os.Build
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class CrashHelper {
    companion object {
        fun setDefaultUncaughtExceptionHandler(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler { _: Thread?, throwable: Throwable ->
                val crashLog = StringWriter()
                val printWriter = PrintWriter(crashLog)
                throwable.printStackTrace(printWriter)
                val osVersion = Build.VERSION.RELEASE
                var appVersion = ""
                try {
                    appVersion = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    e.printStackTrace()
                }
                printWriter.write("\nDevice OS Version: $osVersion")
                printWriter.write("\nApp Version: $appVersion")
                printWriter.close()
                try {
                    val fileName = "Crash_Log.txt"
                    val targetFile = File(context.filesDir, fileName)
                    val fileOutputStream = FileOutputStream(targetFile, true)
                    fileOutputStream.write((crashLog.toString() + "\n").toByteArray())
                    fileOutputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                Process.killProcess(Process.myPid())
            }
        }
    }
}