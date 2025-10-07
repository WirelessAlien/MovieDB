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
import android.net.Uri
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVReaderHeaderAwareBuilder
import java.io.InputStreamReader

object CsvParserUtil {

    fun readCsvHeaders(context: Context, fileUri: Uri, delimiter: Char = ','): List<String>? {
        return try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val csvParser = CSVParserBuilder().withSeparator(delimiter).build()
                    val csvReader = CSVReaderBuilder(reader).withCSVParser(csvParser).build()
                    val headers = csvReader.readNext()
                    csvReader.close()
                    headers?.toList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    suspend fun processCsvWithMapping(
        context: Context,
        fileUri: Uri,
        headerMapping: Map<String, String>,
        defaultValues: Map<String, Any?>,
        delimiter: Char,
        onRowProcessed: suspend (Map<String, String?>) -> Unit
    ): Boolean {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val csvParser = CSVParserBuilder().withSeparator(delimiter).withQuoteChar('"').build()
                    val csvReader = CSVReaderHeaderAwareBuilder(reader)
                        .withCSVParser(csvParser)
                        .build()

                    while (true) {
                        val rowValues = csvReader.readMap() ?: break
                        val mappedRow = mutableMapOf<String, String?>()

                        defaultValues.forEach { (dbCol, defaultValue) ->
                            mappedRow[dbCol] = defaultValue?.toString()
                        }

                        headerMapping.forEach { (csvHeader, dbColumnName) ->
                            val csvValue = rowValues[csvHeader]
                            if (!csvValue.isNullOrBlank()) {
                                mappedRow[dbColumnName] = csvValue
                            }
                        }
                        onRowProcessed(mappedRow)
                    }
                    csvReader.close()
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return false
    }
}
