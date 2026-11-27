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
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AppDataZipHelper {

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val SQLITE_AUX_SUFFIXES = listOf("-wal", "-shm", "-journal")

    private fun isSqliteDatabaseFile(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { fis ->
            var totalRead = 0
            while (totalRead < header.size) {
                val read = fis.read(header, totalRead, header.size - totalRead)
                if (read == -1) return false
                totalRead += read
            }
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    private fun isAuxiliarySqliteFile(file: File): Boolean {
        val name = file.name
        val baseName = SQLITE_AUX_SUFFIXES.firstOrNull { name.endsWith(it) }?.let { suffix ->
            name.removeSuffix(suffix)
        } ?: return false
        val baseFile = File(file.parentFile, baseName)
        return baseFile.exists() && isSqliteDatabaseFile(baseFile)
    }

    private fun writeSqliteSnapshotToZip(
        context: Context,
        dbFile: File,
        relativePath: String,
        zipOut: ZipOutputStream
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val snapshotFile = File.createTempFile("sqlite_backup_", ".db", context.cacheDir)
            snapshotFile.delete()
            try {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                ).use { db ->
                    db.execSQL("VACUUM INTO ?", arrayOf(snapshotFile.absolutePath))
                }
                zipOut.putNextEntry(ZipEntry(relativePath))
                FileInputStream(snapshotFile).use { fis ->
                    fis.copyTo(zipOut)
                }
                zipOut.closeEntry()
                return
            } finally {
                snapshotFile.delete()
            }
        }

        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        ).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                cursor.moveToFirst()
            }
        }
        zipOut.putNextEntry(ZipEntry(relativePath))
        FileInputStream(dbFile).use { fis ->
            fis.copyTo(zipOut)
        }
        zipOut.closeEntry()
    }

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
                        if (dirName == "databases" && isAuxiliarySqliteFile(file)) {
                            return@forEach
                        }
                        val relativePath = file.relativeTo(dataDir).path.replace('\\', '/')
                        try {
                            if (dirName == "databases" && isSqliteDatabaseFile(file)) {
                                writeSqliteSnapshotToZip(context, file, relativePath, zipOut)
                            } else {
                                zipOut.putNextEntry(ZipEntry(relativePath))
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        } catch (e: IOException) {
                            throw e
                        } catch (e: Exception) {
                            throw IOException("Failed to export $relativePath", e)
                        }
                    }
                }
            }
            zipOut.finish()
        }
    }

    fun importAppDataFromZip(context: Context, inputStream: InputStream) {
        val dataDir = context.dataDir
        val canonicalDataDir = dataDir.canonicalPath
        val canonicalDataDirPrefix = if (canonicalDataDir.endsWith(File.separator)) {
            canonicalDataDir
        } else {
            canonicalDataDir + File.separator
        }
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
                    if (targetFile.canonicalPath.startsWith(canonicalDataDirPrefix)) {
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
