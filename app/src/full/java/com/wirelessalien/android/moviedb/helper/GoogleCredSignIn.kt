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
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class GoogleCredSignIn(private val context: Context, serverClientId: String) {

    //https://stackoverflow.com/a/77643555/17538894

    private val credentialManager = CredentialManager.create(context)

    // GetSignInWithGoogleOption used to retrieve a user's Google ID Token from an explicit 'Sign in with Google' button.
    private val request = GetSignInWithGoogleOption.Builder(serverClientId).build().let {
        GetCredentialRequest.Builder().addCredentialOption(it).build()
    }

    fun googleLogin(callback: GoogleIdTokenCredential.() -> Unit) {
        if (context !is AppCompatActivity) {
            throw Exception("Please use Activity Context")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = credentialManager.getCredential(request = request, context = context)
                handleSignIn(callback, result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleSignIn(callback: GoogleIdTokenCredential.() -> Unit, result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                callback(googleIdTokenCredential)
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("TAG", "Received an invalid google id token response", e)
            }
        }
    }
}