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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.squareup.picasso.Picasso;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.tmdb.account.GetAccountDetailsThread;

import de.hdodenhof.circleimageview.CircleImageView;

public class AccountDataFragment extends BaseFragment {

    private SharedPreferences preferences;
    private TextView nameTextView;
    private CircleImageView avatar;
    private TabLayout tabLayout;
    private String sessionId;
    private String accountId;
    private ImageView loginBtn;
    private FloatingActionButton fab;

    public AccountDataFragment() {
        // Required empty public constructor
    }

    public static AccountDataFragment newInstance() {
        return new AccountDataFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account_data, container, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        nameTextView = view.findViewById(R.id.userName);
        avatar = view.findViewById(R.id.profileImage);
        tabLayout = view.findViewById(R.id.tabs);
        loginBtn = view.findViewById( R.id.loginLogoutBtn );
        fab = requireActivity().findViewById(R.id.fab);

        sessionId = preferences.getString( "access_token", null );
        accountId = preferences.getString( "account_id", null );

        if (sessionId == null || accountId == null) {
            loginBtn.setEnabled( false );
            fab.setEnabled( false );
        } else {
            loginBtn.setOnClickListener( v -> {
                String url = "https://www.themoviedb.org/settings/profile";
                openLinkInBrowser(url);
            });
            loginBtn.setEnabled( true );
            fab.setEnabled( true );
        }

        setupTabs();
        loadAccountDetails();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        MenuItem item = menu.findItem(R.id.action_search);
        if (item != null) {
            item.setVisible(false);
            item.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (sessionId == null || accountId == null) {
            loginBtn.setEnabled( false );
            fab.setEnabled( false );
        } else {
            loginBtn.setOnClickListener( v -> {
                String url = "https://www.themoviedb.org/settings/profile";
                openLinkInBrowser(url);
            });
            loginBtn.setEnabled( true );
            fab.setEnabled( true );
        }
    }

    private void openLinkInBrowser(String url) {
        requireActivity().runOnUiThread(() -> {
            android.content.Intent browserIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(browserIntent);
        });
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.watchlist)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.favourite)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.rated)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.lists)));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment selectedFragment = switch (tab.getPosition()) {
                    case 0 -> new WatchlistFragment();
                    case 1 -> new FavoriteFragment();
                    case 2 -> new RatedListFragment();
                    case 3 -> new MyListsFragment();
                    default -> null;
                };
                if (selectedFragment != null) {
                    getChildFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // Not used
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // Not used
            }
        });

        if (sessionId == null || accountId == null) {
            getChildFragmentManager().beginTransaction()
                    .replace( R.id.fragment_container, new LoginFragment() )
                    .commit();
        } else {
            getChildFragmentManager().beginTransaction()
                    .replace( R.id.fragment_container, new WatchlistFragment() )
                    .commit();
        }
    }

    private void loadAccountDetails() {
        if (preferences.getString("access_token", null) != null) {
            GetAccountDetailsThread getAccountIdThread = new GetAccountDetailsThread(getContext(), (accountId, name, username, avatarPath, gravatar) -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        if (name != null && !name.isEmpty()) {
                            nameTextView.setText(name);
                        } else if (username != null && !username.isEmpty()) {
                            nameTextView.setText(username);
                        }

                        if (avatarPath != null && !avatarPath.isEmpty()) {
                            Picasso.get().load("https://image.tmdb.org/t/p/w200" + avatarPath).into(avatar);
                        } else if (gravatar != null && !gravatar.isEmpty()) {
                            Picasso.get().load("https://secure.gravatar.com/avatar/" + gravatar + ".jpg?s=200").into(avatar);
                        }
                    });
                }
            });
            getAccountIdThread.start();
        }
    }
}