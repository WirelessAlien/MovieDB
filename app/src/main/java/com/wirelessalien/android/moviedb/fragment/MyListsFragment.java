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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wirelessalien.android.moviedb.R;
import com.wirelessalien.android.moviedb.activity.MyListDetailsActivity;
import com.wirelessalien.android.moviedb.adapter.ListAdapter;
import com.wirelessalien.android.moviedb.databinding.FragmentMyListsBinding;
import com.wirelessalien.android.moviedb.tmdb.account.FetchListThreadTMDb;

import java.util.ArrayList;

public class MyListsFragment extends BaseFragment {

    private ListAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FragmentMyListsBinding binding = FragmentMyListsBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        FloatingActionButton fab = requireActivity().findViewById( R.id.fab);
        fab.setImageResource(R.drawable.ic_add);

        listAdapter = new ListAdapter(new ArrayList<>(), listData -> {
            Intent intent = new Intent(getActivity(), MyListDetailsActivity.class);
            intent.putExtra("listId", listData.getId());
            startActivity(intent);
        }, true);

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerView.setAdapter(listAdapter);

        binding.progressBar.setVisibility(View.VISIBLE);

        FetchListThreadTMDb fetcher = new FetchListThreadTMDb(getContext(), null);
        fetcher.fetchLists().thenAccept(listData -> requireActivity().runOnUiThread(() -> {
            listAdapter.updateData(listData);
            binding.progressBar.setVisibility(View.GONE);
        }));

        fab.setOnClickListener(v -> {
            ListBottomSheetDialogFragment listBottomSheetDialogFragment = new ListBottomSheetDialogFragment(0, null, getContext(), false);
            listBottomSheetDialogFragment.show(getChildFragmentManager(), listBottomSheetDialogFragment.getTag());
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        FloatingActionButton fab = requireActivity().findViewById(R.id.fab);
        fab.setVisibility(View.VISIBLE);
        fab.setOnClickListener(v -> {
            ListBottomSheetDialogFragment listBottomSheetDialogFragment = new ListBottomSheetDialogFragment(0, null, getContext(), false);
            listBottomSheetDialogFragment.show(getChildFragmentManager(), listBottomSheetDialogFragment.getTag());
        });
    }

}