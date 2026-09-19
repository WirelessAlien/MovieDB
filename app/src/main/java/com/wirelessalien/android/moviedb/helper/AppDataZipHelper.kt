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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AppDataZipHelper {

    fun exportAppDataToZip(context: Context, outputStream: OutputStream) {
        val dataDir = context.dataDir
        // Note: 'files' directory is omitted because the app does not store persistent user data there
        // that needs backing up, and to avoid system files like profileinstaller_*.dat.
        val directoriesToBackup = listOf("databases", "shared_prefs")

        ZipOutputStream(outputStream.buffered()).use { zipOut ->
            for (dirName in directoriesToBackup) {
                val dir = File(dataDir, dirName)
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        if (file.name == "webdav_prefs.xml") {
                            return@forEach
                        }
                        try {
                            val relativePath = file.relativeTo(dataDir).path.replace('\\', '/')
                            zipOut.putNextEntry(ZipEntry(relativePath))
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            zipOut.finish()
        }
    }

    fun importAppDataFromZip(context: Context, inputStream: InputStream) {
        val dataDir = context.dataDir
        // Note: 'files' directory is omitted because the app does not store persistent user data there
        // that needs backing up, and to avoid system files like profileinstaller_*.dat.
        val allowedDirectories = listOf("databases", "shared_prefs")

        ZipInputStream(inputStream.buffered()).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val relativePath = entry.name.replace('\\', '/')
                val topDir = relativePath.substringBefore('/')
                val fileName = File(relativePath).name

                if (!entry.isDirectory && topDir in allowedDirectories && fileName != "webdav_prefs.xml") {
                    val targetFile = File(dataDir, relativePath)

                    // Security check to prevent Zip Slip vulnerability
                    if (targetFile.canonicalPath.startsWith(dataDir.canonicalPath)) {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            zipIn.copyTo(fos)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }
}
