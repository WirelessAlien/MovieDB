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

package com.wirelessalien.android.moviedb.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountDetailsThread;
import com.wirelessalien.android.moviedb.tmdb.account.LogoutThread;
import com.wirelessalien.android.moviedb.tmdb.account.TMDbAuthThreadV4;

import de.hdodenhof.circleimageview.CircleImageView;

public class LoginFragment extends BottomSheetDialogFragment {

    private SharedPreferences preferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.intent.setPackage("com.android.chrome");
        CustomTabsClient.bindCustomTabsService(requireContext(), "com.android.chrome", new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(@NonNull ComponentName componentName, @NonNull CustomTabsClient customTabsClient) {
                customTabsClient.warmup(0L);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        });

        Button loginButton = view.findViewById(R.id.login);
        Button logoutButton = view.findViewById(R.id.logout);
        TextView loginStatus = view.findViewById(R.id.login_status);
        Button signUpButton = view.findViewById(R.id.signup);
        TextView nameTextView = view.findViewById(R.id.name);
        CircleImageView avatar = view.findViewById(R.id.avatar);

        signUpButton.setOnClickListener(v -> {
            String url = "https://www.themoviedb.org/signup";
            Intent i = new Intent( Intent.ACTION_VIEW);
            i.setData( Uri.parse(url));
            startActivity(i);
        });


        if (preferences.getString("account_id", null) != null && preferences.getString("access_token", null) != null){
            loginButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
            loginStatus.setText("You are logged in");

        }

        logoutButton.setOnClickListener(v -> {
            Handler handler = new Handler( Looper.getMainLooper());
            LogoutThread logoutThread = new LogoutThread(requireContext(), handler);
            logoutThread.start();

            loginButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
            loginStatus.setText("You are not logged in");
        });

        loginButton.setOnClickListener(v -> {

            TMDbAuthThreadV4 authTask = new TMDbAuthThreadV4(getContext()) {
                @Override
                public void run() {
                    String accessToken = authenticate();
                    if (accessToken != null) {
                        preferences.edit().putString("request_token", accessToken).apply();
                    }
                }
            };
            authTask.start();
        });

        //if access token is not null, then the user is logged in
        if (preferences.getString("access_token", null) != null){
            GetAccountDetailsThread getAccountIdThread = new GetAccountDetailsThread(getContext(), (accountId, name, username, avatarPath, gravatar) -> requireActivity().runOnUiThread( () -> {
                // If both username and name are available, display name. Otherwise, display whichever is available.
                if (name != null && !name.isEmpty()) {
                    nameTextView.setText(name);
                } else if (username != null && !username.isEmpty()) {
                    nameTextView.setText(username);
                }

                // If both avatarPath and gravatar are available, display avatarPath. Otherwise, display whichever is available.
                if (avatarPath != null && !avatarPath.isEmpty()) {
                    Picasso.get().load("https://image.tmdb.org/t/p/w200" + avatarPath).into(avatar);
                } else if (gravatar != null && !gravatar.isEmpty()) {
                    Picasso.get().load("https://secure.gravatar.com/avatar/" + gravatar + ".jpg?s=200").into(avatar);
                }
            } ) );
            getAccountIdThread.start();
        }

        return view;
    }
}