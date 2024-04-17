package com.wirelessalien.android.moviedb.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.wirelessalien.android.moviedb.GetAccountDetailsThread;
import com.wirelessalien.android.moviedb.R;

import java.util.Map;

public class AccountFragment extends AppCompatActivity {

    private String sessionId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_account);

        SharedPreferences sharedPreferences = getSharedPreferences("SessionId", Context.MODE_PRIVATE);
        sessionId = sharedPreferences.getString("session_id", null);

        GetAccountDetailsThread getAccountDetailsThread = new GetAccountDetailsThread(sessionId, this);
        getAccountDetailsThread.start();

        try {
            getAccountDetailsThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Map<String, String> details = getAccountDetailsThread.getDetails();

        TextView usernameTextView = findViewById(R.id.username);
        TextView nameTextView = findViewById(R.id.name);
        TextView avatarPathTextView = findViewById(R.id.avatar_path);

        usernameTextView.setText(details.get("username"));
        nameTextView.setText(details.get("name"));
        avatarPathTextView.setText(details.get("avatar_path"));
    }
}