package com.wirelessalien.android.moviedb.fragment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.account.LogoutThread;
import com.wirelessalien.android.moviedb.tmdb.account.TMDbAuthThreadV4;

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

        Button loginButton = view.findViewById(R.id.login);
        Button logoutButton = view.findViewById(R.id.logout);
        TextView loginStatus = view.findViewById(R.id.login_status);
        Button signUpButton = view.findViewById(R.id.signup);

        signUpButton.setOnClickListener(v -> {
            String url = "https://www.themoviedb.org/signup";
            Intent i = new Intent( Intent.ACTION_VIEW);
            i.setData( Uri.parse(url));
            startActivity(i);
        });


        if (preferences.getInt("accountId", -1) != -1 && preferences.getString("access_token", null) != null){
            loginButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
            loginStatus.setText("You are logged in");

        }

        logoutButton.setOnClickListener(v -> {

            LogoutThread logoutThread = new LogoutThread(requireContext()) {
                @Override
                public void run() {
                    logout();
                }
            };



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

        return view;
    }
}