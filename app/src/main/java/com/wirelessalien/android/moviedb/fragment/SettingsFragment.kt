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
package com.wirelessalien.android.moviedb.fragment

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.MutableLiveData
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wirelessalien.android.moviedb.BuildConfig
import com.wirelessalien.android.moviedb.NotificationReceiver
import com.wirelessalien.android.moviedb.R
import com.wirelessalien.android.moviedb.activity.MainActivity
import com.wirelessalien.android.moviedb.activity.SettingsActivity
import com.wirelessalien.android.moviedb.adapter.SectionsPagerAdapter
import com.wirelessalien.android.moviedb.databinding.DialogSyncProviderBinding
import com.wirelessalien.android.moviedb.helper.NotificationDatabaseHelper
import com.wirelessalien.android.moviedb.helper.ScheduledNotificationDatabaseHelper
import com.wirelessalien.android.moviedb.work.DailyWorkerTkt
import com.wirelessalien.android.moviedb.work.GetTmdbTvDetailsWorker
import com.wirelessalien.android.moviedb.work.UpdateWorker
import com.wirelessalien.android.moviedb.work.WeeklyWorkerTkt
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private val notificationDbHelper: NotificationDatabaseHelper by lazy {
        NotificationDatabaseHelper(requireContext())
    }

    private val scheduledNotificationDbHelper: ScheduledNotificationDatabaseHelper by lazy {
        ScheduledNotificationDatabaseHelper(requireContext())
    }

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updatePermissionPreferenceVisibility()
            if (isGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        requestExactAlarmPermission()
                    }
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.post_notification_permission_not_granted), Toast.LENGTH_SHORT).show()
            }
        }

    private val requestExactAlarmLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updatePermissionPreferenceVisibility()
        }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            requestExactAlarmLauncher.launch(intent)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        updatePermissionPreferenceVisibility()
        val aboutPreference = findPreference<Preference>("about_key")
        aboutPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val fragmentManager = parentFragmentManager
            val newFragment = AboutFragment()

            // Show the fragment fullscreen.
            val transaction = fragmentManager.beginTransaction()
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            transaction.add(android.R.id.content, newFragment)
                .addToBackStack(null)
                .commit()
            true
        }


        val privacyKey = findPreference<Preference>("privacy_key")
        privacyKey?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val url = "https://showcase-app.blogspot.com/2024/11/privacy-policy.html"
            try {
                val builder = CustomTabsIntent.Builder()
                val customTabsIntent = builder.build()
                customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            }
            true
        }

        val searchEngineKey = findPreference<EditTextPreference>("key_search_engine")
        searchEngineKey?.setOnBindEditTextListener {
            it.hint = "Example: https://www.google.com/search?q="
        }

        val apiLanguageKey = findPreference<EditTextPreference>("key_api_language")
        apiLanguageKey?.setOnBindEditTextListener {
            it.hint = "Example: en-US or en"
        }

        val apiRegionKey = findPreference<EditTextPreference>("key_api_region")
        apiRegionKey?.setOnBindEditTextListener {
            it.hint = "Example: US (the iso3166-1 tag)"
        }

        val apiTimezoneKey = findPreference<EditTextPreference>("key_api_timezone")
        apiTimezoneKey?.setOnBindEditTextListener {
            it.hint = "Example: America/New_York"
        }

        val omdbApiKeyPreference = findPreference<EditTextPreference>("omdb_api_key")
        if (BuildConfig.OMDB_API_KEY.isNotEmpty()) {
            omdbApiKeyPreference?.summary = getString(R.string.omdb_api_key_summary_provided)

        }

        val hideAccountTab = findPreference<CheckBoxPreference>("key_hide_account_tab")
        val hideAccountTktTab = findPreference<CheckBoxPreference>("key_hide_account_tkt_tab")

        val preferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
            val newState = newValue as Boolean

            when (preference.key) {
                "key_hide_account_tab" -> {
                    if (!newState && !hideAccountTktTab?.isChecked!!) {
                        return@OnPreferenceChangeListener false
                    }
                }
                "key_hide_account_tkt_tab" -> {
                    if (!newState && !hideAccountTab?.isChecked!!) {
                        return@OnPreferenceChangeListener false
                    }
                }
            }
            true
        }

        hideAccountTab?.onPreferenceChangeListener = preferenceChangeListener
        hideAccountTktTab?.onPreferenceChangeListener = preferenceChangeListener

        val syncProvider = findPreference<Preference>("sync_provider")

        syncProvider?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showSyncProviderDialog()
            true
        }

        findPreference<SwitchPreferenceCompat>("key_get_notified_for_saved")?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPostNotifications = ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasScheduleExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val alarmManager =
                                requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                            alarmManager.canScheduleExactAlarms()
                        } else {
                            true
                        }

                        if (!hasPostNotifications || !hasScheduleExactAlarm) {
                            Toast.makeText(requireContext(), getString(R.string.grant_notification_permissions), Toast.LENGTH_SHORT).show()
                            preference.isChecked = false
                            return@setOnPreferenceChangeListener false
                        }
                    }

                    // Check network connectivity
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null && networkCapabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        // Network is connected, enqueue the work request
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                        val dailyWorkRequest = PeriodicWorkRequest.Builder(DailyWorkerTkt::class.java, 1, TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .build()

                        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                            "daily_work_tkt",
                            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                            dailyWorkRequest
                        )
                    } else {
                        // Network is not connected, show error and uncheck the switch
                        Toast.makeText(requireContext(), getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                        preference.isChecked = false
                        return@setOnPreferenceChangeListener false
                    }
                } else {
                    // Cancel all notifications
                    cancelAllNotifications()
                }
                true
            }
        }

        val donateKey = findPreference<Preference>("donate_key")
        donateKey?.setOnPreferenceClickListener {
            val donateFragment = DonationFragment()
            donateFragment.show(requireActivity().supportFragmentManager, "donationFragment")
            true
        }

        findPreference<SwitchPreferenceCompat>("key_auto_sync_tkt_data")?.let { preference ->
            val traktToken = preferences.getString("trakt_access_token", null)
            preference.isEnabled = !traktToken.isNullOrEmpty()

            if (!preference.isEnabled) {
                preference.summary = getString(R.string.trakt_login_required)
                preference.isChecked = false
                // Cancel any existing work if token is not available
                WorkManager.getInstance(requireContext()).cancelUniqueWork("weekly_work_tkt")
            }

            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    // Check network connectivity
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null && networkCapabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        // Network is connected, enqueue the work request
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                        val dailyWorkRequest = PeriodicWorkRequest.Builder(WeeklyWorkerTkt::class.java, 7, TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .build()

                        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                            "weekly_work_tkt",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            dailyWorkRequest
                        )
                    } else {
                        // Network is not connected, show error and uncheck the switch
                        Toast.makeText(requireContext(), getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                        preference.isChecked = false
                        return@setOnPreferenceChangeListener false
                    }
                } else {
                    WorkManager.getInstance(requireContext()).cancelUniqueWork("weekly_work_tkt")
                }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("key_auto_update_episode_data")?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    // Schedule the worker if enabled
                    val connectivityManager =
                        requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null && networkCapabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                        )
                    ) {
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                        val monthlyWorkRequest =
                            PeriodicWorkRequestBuilder<GetTmdbTvDetailsWorker>(30, TimeUnit.DAYS)
                                .setConstraints(constraints)
                                .build()

                        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                            "monthlyTvShowUpdateWorker",
                            ExistingPeriodicWorkPolicy.KEEP,
                            monthlyWorkRequest
                        )
                    } else {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.no_internet_connection),
                            Toast.LENGTH_SHORT
                        ).show()
                        preference.isChecked = false
                        return@setOnPreferenceChangeListener false
                    }
                } else {
                    // Cancel the worker if disabled
                    WorkManager.getInstance(requireContext())
                        .cancelUniqueWork("monthlyTvShowUpdateWorker")
                }
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("key_get_in_app_update")?.let { preference ->
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue as Boolean) {
                    // Check network connectivity
                    val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities != null && networkCapabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET)) {

                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()

                        val updateWorker = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                            .setConstraints(constraints)
                            .build()

                        WorkManager.getInstance(requireContext())
                            .enqueueUniquePeriodicWork(
                                "updateWorker",
                                ExistingPeriodicWorkPolicy.KEEP,
                                updateWorker
                            )

                    } else {
                        // Network is not connected, show error and uncheck the switch
                        Toast.makeText(requireContext(), getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                        preference.isChecked = false
                        return@setOnPreferenceChangeListener false
                    }
                } else {
                    // Cancel the worker if disabled
                    WorkManager.getInstance(requireContext())
                        .cancelUniqueWork("updateWorker")
                }
                true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findPreference<SwitchPreferenceCompat>("key_auto_update_episode_data")?.let { preference ->
            val workManager = WorkManager.getInstance(requireContext())
            val switchState = MutableLiveData(false)

            // Observe worker state
            workManager.getWorkInfosForUniqueWorkLiveData("monthlyTvShowUpdateWorker")
                .observe(viewLifecycleOwner) { workInfoList ->
                    val isRunning = workInfoList?.any {
                        it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                    } == true
                    switchState.value = isRunning
                }

            // Observe LiveData and update switch state
            switchState.observe(viewLifecycleOwner) { isRunning ->
                preference.isChecked = isRunning
            }
        }
    }

    private fun cancelAllNotifications() {
        val scheduledNotifications = scheduledNotificationDbHelper.getAllScheduledNotifications()
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), NotificationReceiver::class.java)

        for (notification in scheduledNotifications) {
            val pendingIntent = PendingIntent.getBroadcast(
                requireContext(),
                notification.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        scheduledNotificationDbHelper.deleteAllScheduledNotifications()
        notificationDbHelper.deleteAllNotifications()
    }

    private fun showSyncProviderDialog() {
        val dialogBinding = DialogSyncProviderBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()

        // Retrieve stored values from SharedPreferences
        val syncProvider = preferences.getString("sync_provider", "local")
        val forceLocalSync = preferences.getBoolean("force_local_sync", false)

        // Set initial state of radio buttons based on stored value
        when (syncProvider) {
            "local" -> dialogBinding.radioLocal.isChecked = true
            "trakt" -> dialogBinding.radioTrakt.isChecked = true
            "tmdb" -> dialogBinding.radioTmdb.isChecked = true
        }

        // Set initial state of forceLocalSync checkbox
        dialogBinding.forceLocalSync.isChecked = forceLocalSync
        dialogBinding.forceLocalSync.isEnabled = syncProvider != "local"

        dialogBinding.radioLocal.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.forceLocalSync.isEnabled = !isChecked
        }

        dialogBinding.forceLocalSync.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("force_local_sync", isChecked).apply()
        }

        dialogBinding.buttonOk.setOnClickListener {
            val selectedProvider = when {
                dialogBinding.radioLocal.isChecked -> "local"
                dialogBinding.radioTrakt.isChecked -> "trakt"
                dialogBinding.radioTmdb.isChecked -> "tmdb"
                else -> "local"
            }

            preferences.edit().putString("sync_provider", selectedProvider).apply()
            preferences.edit().putBoolean("sync_provider_dialog_shown", true).apply()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updatePermissionPreferenceVisibility() {
        val requestPermissionPreference = findPreference<Preference>("key_request_notification_permission")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPostNotifications = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            val hasScheduleExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (hasPostNotifications && hasScheduleExactAlarm) {
                requestPermissionPreference?.isVisible = false
            } else {
                requestPermissionPreference?.isVisible = true
                requestPermissionPreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    if (!hasPostNotifications) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        requestExactAlarmPermission()
                    }
                    true
                }
            }
        } else {
            requestPermissionPreference?.isVisible = false
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionPreferenceVisibility()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == SectionsPagerAdapter.HIDE_MOVIES_PREFERENCE || key == SectionsPagerAdapter.HIDE_ACCOUNT_PREFERENCE || key == SectionsPagerAdapter.HIDE_SAVED_PREFERENCE || key == SectionsPagerAdapter.HIDE_SERIES_PREFERENCE) {
            (requireActivity() as SettingsActivity).mTabsPreferenceChanged = true
        }
        if (key == MainActivity.AMOLED_THEME_PREFERENCE) {
            requireActivity().recreate()
        }
    }
}
