package com.wirelessalien.android.moviedb.activity;


import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.TMDbAuthThread;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private EditText passwordEditText;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        usernameEditText = findViewById(R.id.username);
        passwordEditText = findViewById(R.id.password);
        Button loginButton = findViewById( R.id.login );

        loginButton.setOnClickListener( v -> {
            String username = usernameEditText.getText().toString();
            String password = passwordEditText.getText().toString();

            try {
                SharedPreferences sharedPreferences = getEncryptedSharedPreferences(LoginActivity.this);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("username", username);
                editor.putString("password", password);
                editor.apply();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
            }

            TMDbAuthThread authTask = new TMDbAuthThread(username, password, LoginActivity.this);
            authTask.start();
        } );
    }
}