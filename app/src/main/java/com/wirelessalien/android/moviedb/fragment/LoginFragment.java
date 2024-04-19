package com.wirelessalien.android.moviedb.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountIdThread;
import com.wirelessalien.android.moviedb.tmdb.account.TMDbAuthThread;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LoginFragment extends BottomSheetDialogFragment {

    private EditText usernameEditText;
    private EditText passwordEditText;
    private SharedPreferences preferences;
    private Context context;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
    }

    @NonNull
    private SharedPreferences getEncryptedSharedPreferences(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                "encrypted_preferences",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

        usernameEditText = view.findViewById(R.id.username);
        passwordEditText = view.findViewById(R.id.password);
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


        if (preferences.getInt("accountId", -1) != -1 && preferences.getString("session_id", null) != null){
            loginButton.setVisibility(View.GONE);
            logoutButton.setVisibility(View.VISIBLE);
            usernameEditText.setVisibility(View.GONE);
            passwordEditText.setVisibility(View.GONE);
            loginStatus.setText("You are logged in");

        }

        logoutButton.setOnClickListener(v -> {
            preferences.edit().remove("session_id").apply();
            preferences.edit().remove("accountId").apply();
            loginButton.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.GONE);
            usernameEditText.setVisibility(View.VISIBLE);
            passwordEditText.setVisibility(View.VISIBLE);
            loginStatus.setText("You are not logged in");
        });

        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString();
            String password = passwordEditText.getText().toString();

            try {
                SharedPreferences sharedPreferences = getEncryptedSharedPreferences(getContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("username", username);
                editor.putString("password", password);
                editor.apply();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
            }

            TMDbAuthThread authTask = new TMDbAuthThread(username, password, getContext()) {
                @Override
                public void run() {
                    String sessionId = authenticate();
                    if (sessionId != null) {
                        preferences.edit().putString("session_id", sessionId).apply();
                        GetAccountIdThread getAccountIdThread = new GetAccountIdThread(sessionId, context);
                        getAccountIdThread.start();

                        requireActivity().runOnUiThread(() -> {
                            loginButton.setVisibility(View.GONE);
                            logoutButton.setVisibility(View.VISIBLE);
                            loginStatus.setText("You are logged in");
                            usernameEditText.setVisibility(View.GONE);
                            passwordEditText.setVisibility(View.GONE);
                            Toast.makeText(getContext(), "Login successful, Please reopen the app.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            };
            authTask.start();
        });

        return view;
    }
}