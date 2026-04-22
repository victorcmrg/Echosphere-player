package com.example.echosphereplayer

import retrofit2.http.*

// ── Models de busca via Python API ────────────────────────────────────────────
data class PythonSearchResponse(val results: List<PythonSearchResult>)
data class PythonSearchResult(
    val title: String,
    val channel: String,
    val duration: String?,
    val url: String,
    val thumbnail: String? = null
)
data class PythonDownloadRequest(
    val url: String,
    val title: String,
    val thumbnail: String? = null
)
data class PythonDownloadResponse(val message: String, val file: String?)

interface PythonApi {
    @GET("search")
    suspend fun searchMusic(@Query("q") query: String): PythonSearchResponse

    @POST("download")
    suspend fun downloadMusic(@Body request: PythonDownloadRequest): retrofit2.Response<PythonDownloadResponse>
}

// ── Models Subsonic / Navidrome ───────────────────────────────────────────────
data class StarredWrapper(val song: List<SubsonicSong>? = null)

data class SubsonicResponse(val `subsonic-response`: SubsonicRoot)

data class SubsonicRoot(
    val status: String,
    val version: String,
    val indexes: SubsonicIndexes? = null,
    val searchResult3: SearchResult3? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: PlaylistDetail? = null,
    val starred: StarredWrapper? = null,
    val nowPlaying: NowPlayingData? = null, // PATCH: Adicionado para ler o status de amigos!
    val error: SubsonicError? = null
)
data class SubsonicError(val code: Int, val message: String)
data class SubsonicIndexes(val index: List<SubsonicIndex>)
data class SubsonicIndex(val name: String, val artist: List<SubsonicArtist>)
data class SubsonicArtist(val id: String, val name: String, val albumCount: Int = 0)
data class SearchResult3(
    val song: List<SubsonicSong>? = null,
    val album: List<SubsonicAlbum>? = null,
    val artist: List<SubsonicArtist>? = null
)
data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val duration: Int? = null,
    val coverArt: String? = null,
    val starred: String? = null
)
data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String? = null,
    val songCount: Int = 0,
    val coverArt: String? = null
)
data class PlaylistsWrapper(val playlist: List<SubsonicPlaylist>)
data class SubsonicPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val coverArt: String? = null,
    val public: Boolean = false
)
data class PlaylistDetail(
    val id: String,
    val name: String,
    val created: String? = null,
    val comment: String? = null,
    val coverArt: String? = null,
    val entry: List<SubsonicSong>? = null
)

// PATCH: Classes para ler o Status das outras pessoas
data class NowPlayingData(val entry: List<NowPlayingEntry>? = null)
data class NowPlayingEntry(
    val id: String,
    val title: String,
    val artist: String? = null,
    val username: String? = null
)

// ── Interface Subsonic API ────────────────────────────────────────────────────
interface SubsonicApi {

    @GET("rest/ping")
    suspend fun ping(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    // PATCH: Chamada para saber quem está a escutar o quê agora
    @GET("rest/getNowPlaying")
    suspend fun getNowPlaying(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/getIndexes")
    suspend fun getIndexes(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/search3")
    suspend fun search(
        @Query("query") query: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("songCount") songCount: Int = 20,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/getPlaylists")
    suspend fun getPlaylists(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/getPlaylist")
    suspend fun getPlaylist(
        @Query("id") playlistId: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/createPlaylist")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/startScan")
    suspend fun startScan(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/updatePlaylist")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("name") name: String? = null,
        @Query("comment") comment: String? = null,
        @Query("public") public: Boolean? = null,
        @Query("songIdToAdd") songIdToAdd: String? = null,
        @Query("songIndexToRemove") songIndexToRemove: Int? = null,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") v: String = "1.16.1",
        @Query("c") c: String = "EchoSphere"
    ): SubsonicResponse

    @GET("rest/deletePlaylist")
    suspend fun deletePlaylist(
        @Query("id") playlistId: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/updatePlaylist")
    suspend fun removeFromPlaylist(
        @Query("playlistId") playlistId: String,
        @Query("songIndexToRemove") songIndex: Int,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/star.view")
    suspend fun starSong(
        @Query("id") songId: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/unstar.view")
    suspend fun unstarSong(
        @Query("id") songId: String,
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse

    @GET("rest/getStarred")
    suspend fun getStarred(
        @Query("u") user: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = "1.16.1",
        @Query("c") client: String = "EchoSphere",
        @Query("f") format: String = "json"
    ): SubsonicResponse
}