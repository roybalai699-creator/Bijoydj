package com.example.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

enum class SourceProviderId(val displayName: String) {
    LOCAL_DEVICE("Local Device Music"),
    IMPORTED("User-Imported Music"),
    SPOTIFY("Spotify"),
    SOUNDCLOUD("SoundCloud")
}

data class ExternalTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val albumOrPlaylist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val provider: SourceProviderId,
    val externalUrl: String? = null
)

data class SourceConnectionState(
    val provider: SourceProviderId,
    val isConfigured: Boolean,
    val isConnected: Boolean,
    val accountName: String? = null,
    val statusMessage: String,
    val limitationNotice: String,
    val tracks: List<ExternalTrackItem> = emptyList(),
    val isLoading: Boolean = false
)

data class ApiKeyConfigState(
    val spotifyClientIdSet: Boolean = false,
    val spotifyApiKeySet: Boolean = false,
    val soundCloudClientIdSet: Boolean = false,
    val customApiUrl: String = "",
    val customApiHeaderName: String = "X-API-Key",
    val customApiKeySet: Boolean = false,
    val lastCustomApiResponse: String = ""
)

/**
 * Modular interface allowing additional officially supported music providers to be registered cleanly.
 */
interface MusicSourceAdapter {
    val providerId: SourceProviderId
    fun isClientConfigured(): Boolean
    fun buildAuthIntent(): Intent?
}

class MusicSourceManager(private val context: Context) {

    private val tokenStore = SecureTokenStore(context)
    private val httpClient = OkHttpClient()

    private val redirectUriSpotify = "bijoydj://callback/spotify"
    private val redirectUriSoundCloud = "bijoydj://callback/soundcloud"

    private var pendingSpotifyVerifier: String? = null
    private var pendingSoundCloudVerifier: String? = null

    private val _spotifyState = MutableStateFlow(buildInitialSpotifyState())
    val spotifyState: StateFlow<SourceConnectionState> = _spotifyState.asStateFlow()

    private val _soundCloudState = MutableStateFlow(buildInitialSoundCloudState())
    val soundCloudState: StateFlow<SourceConnectionState> = _soundCloudState.asStateFlow()

    private val _apiKeyConfig = MutableStateFlow(buildInitialApiKeyConfig())
    val apiKeyConfig: StateFlow<ApiKeyConfigState> = _apiKeyConfig.asStateFlow()

    private fun effectiveSpotifyClientId(): String {
        val override = tokenStore.readToken("override_spotify_client_id").orEmpty().trim()
        if (override.isNotEmpty()) return override
        return BuildConfig.SPOTIFY_CLIENT_ID.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }.orEmpty()
    }

    private fun effectiveSpotifyApiKey(): String {
        val override = tokenStore.readToken("override_spotify_api_key").orEmpty().trim()
        if (override.isNotEmpty()) return override
        return BuildConfig.SPOTIFY_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }.orEmpty()
    }

    private fun effectiveSoundCloudClientId(): String {
        val override = tokenStore.readToken("override_soundcloud_client_id").orEmpty().trim()
        if (override.isNotEmpty()) return override
        return BuildConfig.SOUNDCLOUD_CLIENT_ID.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }.orEmpty()
    }

    private fun effectiveCustomApiKey(): String {
        val override = tokenStore.readToken("override_custom_api_key").orEmpty().trim()
        if (override.isNotEmpty()) return override
        return BuildConfig.CUSTOM_API_KEY.takeIf { it.isNotBlank() && !it.startsWith("YOUR_") }.orEmpty()
    }

    private fun isSpotifyConfigured(): Boolean = effectiveSpotifyClientId().isNotBlank()
    private fun isSoundCloudConfigured(): Boolean = effectiveSoundCloudClientId().isNotBlank()

    private fun buildInitialApiKeyConfig(): ApiKeyConfigState {
        return ApiKeyConfigState(
            spotifyClientIdSet = effectiveSpotifyClientId().isNotBlank(),
            spotifyApiKeySet = effectiveSpotifyApiKey().isNotBlank(),
            soundCloudClientIdSet = effectiveSoundCloudClientId().isNotBlank(),
            customApiUrl = tokenStore.readToken("custom_api_base_url").orEmpty(),
            customApiHeaderName = tokenStore.readToken("custom_api_header_name")?.takeIf { it.isNotBlank() } ?: "X-API-Key",
            customApiKeySet = effectiveCustomApiKey().isNotBlank()
        )
    }

    private fun buildInitialSpotifyState(): SourceConnectionState {
        val configured = isSpotifyConfigured()
        val hasToken = tokenStore.readToken("spotify_access_token") != null
        return SourceConnectionState(
            provider = SourceProviderId.SPOTIFY,
            isConfigured = configured,
            isConnected = hasToken,
            statusMessage = when {
                hasToken -> "Connected via Spotify OAuth PKCE"
                configured -> "Spotify Client ID ready — tap Connect Spotify"
                else -> "Enter Spotify Client ID & API Key below or in AI Studio Secrets"
            },
            limitationNotice = "Spotify App Remote / Web API provides remote playback control and playlist metadata only. Raw audio cannot be routed into BIJOY DJ's local mixing engine due to Spotify DRM & API terms."
        )
    }

    private fun buildInitialSoundCloudState(): SourceConnectionState {
        val configured = isSoundCloudConfigured()
        val hasToken = tokenStore.readToken("soundcloud_access_token") != null
        return SourceConnectionState(
            provider = SourceProviderId.SOUNDCLOUD,
            isConfigured = configured,
            isConnected = hasToken,
            statusMessage = when {
                hasToken -> "Connected via SoundCloud OAuth PKCE"
                configured -> "Ready to connect SoundCloud account"
                else -> "Enter SoundCloud Client ID below or in AI Studio Secrets"
            },
            limitationNotice = "SoundCloud integration uses official OAuth 2.0 PKCE and respects copyright, regional availability, attribution, and API streaming policies."
        )
    }

    fun saveSpotifyCredentials(clientId: String, apiKey: String) {
        if (clientId.isNotBlank()) tokenStore.saveToken("override_spotify_client_id", clientId.trim())
        if (apiKey.isNotBlank()) tokenStore.saveToken("override_spotify_api_key", apiKey.trim())
        _apiKeyConfig.value = buildInitialApiKeyConfig()
        _spotifyState.value = buildInitialSpotifyState()
    }

    fun clearSpotifyCredentials() {
        tokenStore.clearToken("override_spotify_client_id")
        tokenStore.clearToken("override_spotify_api_key")
        disconnectSpotify()
        _apiKeyConfig.value = buildInitialApiKeyConfig()
    }

    fun saveSoundCloudCredentials(clientId: String) {
        if (clientId.isNotBlank()) tokenStore.saveToken("override_soundcloud_client_id", clientId.trim())
        _apiKeyConfig.value = buildInitialApiKeyConfig()
        _soundCloudState.value = buildInitialSoundCloudState()
    }

    fun saveCustomApiConfig(baseUrl: String, headerName: String, apiKey: String) {
        tokenStore.saveToken("custom_api_base_url", baseUrl.trim())
        tokenStore.saveToken("custom_api_header_name", headerName.trim().ifEmpty { "X-API-Key" })
        if (apiKey.isNotBlank()) tokenStore.saveToken("override_custom_api_key", apiKey.trim())
        _apiKeyConfig.value = buildInitialApiKeyConfig()
    }

    suspend fun callCustomApiEndpoint(endpointPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = tokenStore.readToken("custom_api_base_url").orEmpty().trimEnd('/')
            require(base.startsWith("https://") || base.startsWith("http://")) {
                "Enter a valid Base URL starting with https://"
            }
            val cleanPath = if (endpointPath.isBlank()) "" else if (endpointPath.startsWith("/")) endpointPath else "/$endpointPath"
            val fullUrl = "$base$cleanPath"
            val header = tokenStore.readToken("custom_api_header_name")?.takeIf { it.isNotBlank() } ?: "X-API-Key"
            val key = effectiveCustomApiKey()

            val reqBuilder = Request.Builder().url(fullUrl).get()
            if (key.isNotBlank()) {
                reqBuilder.addHeader(header, key)
                if (header.equals("Authorization", ignoreCase = true) && !key.startsWith("Bearer ", ignoreCase = true)) {
                    reqBuilder.header("Authorization", "Bearer $key")
                }
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty().take(500)
                val summary = "HTTP ${resp.code}: ${body.ifBlank { resp.message }}"
                _apiKeyConfig.value = _apiKeyConfig.value.copy(lastCustomApiResponse = summary)
                summary
            }
        }.onFailure { err ->
            _apiKeyConfig.value = _apiKeyConfig.value.copy(
                lastCustomApiResponse = "Request error: ${err.localizedMessage ?: "failed"}"
            )
        }
    }

    fun createSpotifyAuthIntent(): Intent? {
        val clientId = effectiveSpotifyClientId()
        if (clientId.isBlank()) {
            _spotifyState.value = _spotifyState.value.copy(
                statusMessage = "Please enter your Spotify Client ID in the API Setup card below."
            )
            return null
        }
        val verifier = generatePkceVerifier()
        pendingSpotifyVerifier = verifier
        val challenge = generatePkceChallenge(verifier)
        val uri = Uri.Builder()
            .scheme("https")
            .authority("accounts.spotify.com")
            .appendPath("authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUriSpotify)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", "user-read-private playlist-read-private user-read-playback-state user-modify-playback-state")
            .build()
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createSoundCloudAuthIntent(): Intent? {
        val clientId = effectiveSoundCloudClientId()
        if (clientId.isBlank()) {
            _soundCloudState.value = _soundCloudState.value.copy(
                statusMessage = "Please enter your SoundCloud Client ID in the API Setup card below."
            )
            return null
        }
        val verifier = generatePkceVerifier()
        pendingSoundCloudVerifier = verifier
        val challenge = generatePkceChallenge(verifier)
        val uri = Uri.Builder()
            .scheme("https")
            .authority("secure.soundcloud.com")
            .appendPath("authorize")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUriSoundCloud)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun handleOAuthRedirect(uri: Uri) {
        val code = uri.getQueryParameter("code") ?: return
        when (uri.path) {
            "/spotify" -> exchangeSpotifyCode(code)
            "/soundcloud" -> exchangeSoundCloudCode(code)
        }
    }

    private suspend fun exchangeSpotifyCode(code: String) = withContext(Dispatchers.IO) {
        val verifier = pendingSpotifyVerifier ?: return@withContext
        val clientId = effectiveSpotifyClientId()
        _spotifyState.value = _spotifyState.value.copy(isLoading = true, statusMessage = "Exchanging Spotify token...")
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUriSpotify)
                .add("code_verifier", verifier)
                .build()
            val reqBuilder = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
            val apiKey = effectiveSpotifyApiKey()
            if (apiKey.isNotBlank()) {
                reqBuilder.addHeader("X-API-Key", apiKey)
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val json = JSONObject(text)
                    val token = json.optString("access_token")
                    if (token.isNotBlank()) {
                        tokenStore.saveToken("spotify_access_token", token)
                        _spotifyState.value = _spotifyState.value.copy(
                            isConnected = true,
                            isLoading = false,
                            accountName = "Spotify User",
                            statusMessage = "Connected to Spotify"
                        )
                        fetchSpotifyPlaylists()
                    }
                } else {
                    _spotifyState.value = _spotifyState.value.copy(
                        isLoading = false,
                        statusMessage = "Spotify authorization failed (${resp.code})"
                    )
                }
            }
        } catch (e: Exception) {
            _spotifyState.value = _spotifyState.value.copy(
                isLoading = false,
                statusMessage = "Network error: ${e.localizedMessage ?: "offline"}"
            )
        }
    }

    suspend fun fetchSpotifyPlaylists() = withContext(Dispatchers.IO) {
        val token = tokenStore.readToken("spotify_access_token") ?: return@withContext
        _spotifyState.value = _spotifyState.value.copy(isLoading = true)
        try {
            val req = Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists?limit=20")
                .addHeader("Authorization", "Bearer $token")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val items = root.optJSONArray("items")
                    val list = mutableListOf<ExternalTrackItem>()
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val o = items.getJSONObject(i)
                            val images = o.optJSONArray("images")
                            val imgUrl = if (images != null && images.length() > 0) {
                                images.getJSONObject(0).optString("url")
                            } else null
                            list.add(
                                ExternalTrackItem(
                                    id = o.optString("id"),
                                    title = o.optString("name", "Playlist"),
                                    artist = o.optJSONObject("owner")?.optString("display_name") ?: "Spotify",
                                    albumOrPlaylist = "${o.optJSONObject("tracks")?.optInt("total") ?: 0} tracks",
                                    artworkUrl = imgUrl,
                                    durationMs = 0L,
                                    provider = SourceProviderId.SPOTIFY,
                                    externalUrl = o.optJSONObject("external_urls")?.optString("spotify")
                                )
                            )
                        }
                    }
                    _spotifyState.value = _spotifyState.value.copy(isLoading = false, tracks = list)
                } else {
                    _spotifyState.value = _spotifyState.value.copy(isLoading = false)
                }
            }
        } catch (_: Exception) {
            _spotifyState.value = _spotifyState.value.copy(isLoading = false)
        }
    }

    private suspend fun exchangeSoundCloudCode(code: String) = withContext(Dispatchers.IO) {
        val verifier = pendingSoundCloudVerifier ?: return@withContext
        val clientId = effectiveSoundCloudClientId()
        _soundCloudState.value = _soundCloudState.value.copy(isLoading = true, statusMessage = "Exchanging SoundCloud token...")
        try {
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUriSoundCloud)
                .add("code_verifier", verifier)
                .build()
            val req = Request.Builder()
                .url("https://secure.soundcloud.com/oauth/token")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val token = json.optString("access_token")
                    if (token.isNotBlank()) {
                        tokenStore.saveToken("soundcloud_access_token", token)
                        _soundCloudState.value = _soundCloudState.value.copy(
                            isConnected = true,
                            isLoading = false,
                            accountName = "SoundCloud User",
                            statusMessage = "Connected to SoundCloud"
                        )
                    }
                } else {
                    _soundCloudState.value = _soundCloudState.value.copy(
                        isLoading = false,
                        statusMessage = "SoundCloud authorization failed (${resp.code})"
                    )
                }
            }
        } catch (e: Exception) {
            _soundCloudState.value = _soundCloudState.value.copy(
                isLoading = false,
                statusMessage = "Network error: ${e.localizedMessage ?: "offline"}"
            )
        }
    }

    suspend fun searchSoundCloudTracks(query: String) = withContext(Dispatchers.IO) {
        val token = tokenStore.readToken("soundcloud_access_token") ?: return@withContext
        if (query.isBlank()) return@withContext
        _soundCloudState.value = _soundCloudState.value.copy(isLoading = true)
        try {
            val url = "https://api.soundcloud.com/tracks?q=${Uri.encode(query)}&limit=15"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "OAuth $token")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val arr = org.json.JSONArray(resp.body?.string().orEmpty())
                    val list = mutableListOf<ExternalTrackItem>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            ExternalTrackItem(
                                id = o.optString("id"),
                                title = o.optString("title", "Track"),
                                artist = o.optJSONObject("user")?.optString("username") ?: "SoundCloud Artist",
                                albumOrPlaylist = o.optString("genre", "SoundCloud"),
                                artworkUrl = o.optString("artwork_url").takeIf { it.isNotBlank() },
                                durationMs = o.optLong("duration", 0L),
                                provider = SourceProviderId.SOUNDCLOUD,
                                externalUrl = o.optString("permalink_url").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    _soundCloudState.value = _soundCloudState.value.copy(isLoading = false, tracks = list)
                } else {
                    _soundCloudState.value = _soundCloudState.value.copy(isLoading = false)
                }
            }
        } catch (_: Exception) {
            _soundCloudState.value = _soundCloudState.value.copy(isLoading = false)
        }
    }

    fun disconnectSpotify() {
        tokenStore.clearToken("spotify_access_token")
        _spotifyState.value = buildInitialSpotifyState()
    }

    fun disconnectSoundCloud() {
        tokenStore.clearToken("soundcloud_access_token")
        _soundCloudState.value = buildInitialSoundCloudState()
    }

    private fun generatePkceVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generatePkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
