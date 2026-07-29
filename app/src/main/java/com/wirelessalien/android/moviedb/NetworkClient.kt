package com.wirelessalien.android.moviedb

import android.content.Context
import androidx.preference.PreferenceManager
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkClient {

    private var _client: OkHttpClient? = null

    val client: OkHttpClient
        get() = _client ?: throw IllegalStateException("NetworkClient must be initialized first")

    fun init(context: Context) {
        if (_client == null) {
            _client = buildClient(context)
        }
    }

    fun rebuild(context: Context, newCustomDnsEnabled: Boolean? = null, newCustomDnsUrl: String? = null) {
        _client = buildClient(context, newCustomDnsEnabled, newCustomDnsUrl)
    }

    private fun buildClient(context: Context, customEnabled: Boolean? = null, customUrl: String? = null): OkHttpClient {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val customDnsEnabled = customEnabled ?: prefs.getBoolean("key_custom_dns_enabled", false)
        val customDnsUrl = customUrl ?: prefs.getString("key_custom_dns_url", "https://1.1.1.1/dns-query") ?: "https://1.1.1.1/dns-query"

        // Close previous cache if it exists to prevent leaks
        _client?.cache?.close()

        if (customDnsEnabled) {
            val appCache = okhttp3.Cache(java.io.File(context.cacheDir, "okhttpcache"), 10 * 1024 * 1024)
            val bootstrapClient = OkHttpClient.Builder()
                .cache(appCache)
                .build()

            val dnsBuilder = DnsOverHttps.Builder().client(bootstrapClient)
                .url(customDnsUrl.toHttpUrl())

            // Try to resolve the host asynchronously to avoid NetworkOnMainThreadException.
            // If the host is not an IP address, we'll fall back to letting OkHttp resolve it during the bootstrap request.
            val host = customDnsUrl.toHttpUrl().host

            // Check if it's already an IP address
            val isIpAddress = host.matches(Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$")) || host.contains(":")

            val hostsList = if (isIpAddress) {
                 try {
                     InetAddress.getAllByName(host).toList()
                 } catch (e: Exception) {
                     listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                 }
            } else {
                 listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"), InetAddress.getByName("8.8.8.8"))
            }

            dnsBuilder.bootstrapDnsHosts(hostsList)

            return OkHttpClient.Builder()
                .cache(appCache)
                .dns(dnsBuilder.build())
                .build()
        }

        return OkHttpClient()
    }
}
