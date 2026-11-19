package com.example.data.remote

import com.example.domain.model.Museum
import com.example.domain.model.MuseumPhoto
import com.example.domain.model.RoomStyle
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.domain.model.UserSubscription
import com.example.domain.model.WeddingWish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class SupabaseMuseumDto(
    val id: String,
    val slug: String,
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val title: String,
    val dedication: String = "",
    @SerialName("room_style") val roomStyle: String = "CLASSIC_WHITE",
    @SerialName("bride_name") val brideName: String = "",
    @SerialName("groom_name") val groomName: String = "",
    @SerialName("wedding_date") val weddingDate: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_name") val locationName: String = "",
    @SerialName("edit_token") val editToken: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("museum_photos") val photos: List<SupabasePhotoDto>? = null
)

@Serializable
data class SupabasePhotoDto(
    val id: String,
    @SerialName("museum_id") val museumId: String,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("image_url") val imageUrl: String,
    val width: Int,
    val height: Int,
    val title: String = "",
    val caption: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("location_name") val locationName: String = ""
)

@Serializable
data class SupabaseSubscriptionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val plan: String,
    @SerialName("starts_at") val startsAt: Long,
    @SerialName("expires_at") val expiresAt: Long,
    val status: String,
    @SerialName("reminder_3d_sent") val reminder3dSent: Boolean = false
)

@Serializable
data class SupabaseWeddingWishDto(
    val id: String,
    @SerialName("museum_id") val museumId: String,
    @SerialName("guest_name") val guestName: String,
    val message: String,
    @SerialName("created_at") val createdAt: Long
)

class SupabaseDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val webpMediaType = "image/webp".toMediaType()

    private fun baseRequest(url: String): Request.Builder {
        val key = SecretsConfig.supabaseAnonKey
        return Request.Builder()
            .url(url)
            .header("apikey", key)
            .header("Authorization", "Bearer $key")
    }

    suspend fun uploadWebpPhoto(objectPath: String, file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val uploadUrl = "$baseUrl/storage/v1/object/museum-photos/$objectPath"
            val request = baseRequest(uploadUrl)
                .header("x-upsert", "true")
                .post(file.asRequestBody(webpMediaType))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Storage upload failed (${resp.code}): $err")
                }
            }
            "$baseUrl/storage/v1/object/public/museum-photos/$objectPath"
        }
    }

    suspend fun upsertMuseumRemote(museum: Museum, photos: List<MuseumPhoto>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val museumDto = SupabaseMuseumDto(
                id = museum.id,
                slug = museum.slug,
                ownerUserId = museum.ownerUserId,
                title = museum.title,
                dedication = museum.dedication,
                roomStyle = museum.roomStyle.key,
                brideName = museum.brideName,
                groomName = museum.groomName,
                weddingDate = museum.weddingDate,
                latitude = museum.latitude,
                longitude = museum.longitude,
                locationName = museum.locationName,
                editToken = museum.editToken,
                createdAt = museum.createdAt,
                photos = null
            )

            val museumPayload = json.encodeToString(museumDto).toRequestBody(jsonMediaType)
            val museumReq = baseRequest("$baseUrl/rest/v1/museums?on_conflict=id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(museumPayload)
                .build()

            client.newCall(museumReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Supabase museum save failed (${resp.code}): $err")
                }
            }

            val delReq = baseRequest("$baseUrl/rest/v1/museum_photos?museum_id=eq.${museum.id}")
                .delete()
                .build()
            client.newCall(delReq).execute().use { }

            val photoDtos = photos.mapIndexed { idx, p ->
                SupabasePhotoDto(
                    id = p.id,
                    museumId = museum.id,
                    sortOrder = idx,
                    imageUrl = p.imageUrl,
                    width = p.width,
                    height = p.height,
                    title = p.title,
                    caption = p.caption,
                    latitude = p.latitude,
                    longitude = p.longitude,
                    locationName = p.locationName
                )
            }
            val photosPayload = json.encodeToString(photoDtos).toRequestBody(jsonMediaType)
            val photosReq = baseRequest("$baseUrl/rest/v1/museum_photos")
                .header("Prefer", "return=minimal")
                .post(photosPayload)
                .build()

            client.newCall(photosReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Supabase photos save failed (${resp.code}): $err")
                }
            }
        }
    }

    suspend fun fetchMuseumBySlug(slug: String): Result<Museum?> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val url = "$baseUrl/rest/v1/museums?slug=eq.$slug&select=*,museum_photos(*)&limit=1"
            val req = baseRequest(url).get().build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Supabase fetch failed (${resp.code}): $err")
                }
                val body = resp.body?.string().orEmpty()
                val list = json.decodeFromString<List<SupabaseMuseumDto>>(body)
                val dto = list.firstOrNull() ?: return@runCatching null
                Museum(
                    id = dto.id,
                    slug = dto.slug,
                    ownerUserId = dto.ownerUserId,
                    title = dto.title,
                    dedication = dto.dedication,
                    roomStyle = RoomStyle.fromKey(dto.roomStyle),
                    brideName = dto.brideName,
                    groomName = dto.groomName,
                    weddingDate = dto.weddingDate,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    locationName = dto.locationName,
                    editToken = "",
                    createdAt = dto.createdAt,
                    isSyncedToCloud = true,
                    photos = (dto.photos ?: emptyList())
                        .sortedBy { it.sortOrder }
                        .map { p ->
                            MuseumPhoto(
                                id = p.id,
                                museumId = p.museumId,
                                sortOrder = p.sortOrder,
                                imageUrl = p.imageUrl,
                                localPath = null,
                                width = p.width,
                                height = p.height,
                                title = p.title,
                                caption = p.caption,
                                latitude = p.latitude,
                                longitude = p.longitude,
                                locationName = p.locationName
                            )
                        }
                )
            }
        }
    }

    suspend fun deleteMuseumRemote(museumId: String, editToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val url = "$baseUrl/rest/v1/museums?id=eq.$museumId&edit_token=eq.$editToken"
            val req = baseRequest(url).delete().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Supabase delete failed (${resp.code}): $err")
                }
            }
        }
    }

    suspend fun upsertSubscriptionRemote(sub: UserSubscription): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val dto = SupabaseSubscriptionDto(
                id = sub.id,
                userId = sub.userId,
                plan = sub.plan.key,
                startsAt = sub.startsAt,
                expiresAt = sub.expiresAt,
                status = sub.status.key,
                reminder3dSent = false
            )
            val payload = json.encodeToString(dto).toRequestBody(jsonMediaType)
            val req = baseRequest("$baseUrl/rest/v1/subscriptions?on_conflict=user_id")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Subscription save failed (${resp.code}): $err")
                }
            }
        }
    }

    suspend fun fetchSubscriptionRemote(userId: String): Result<UserSubscription?> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val url = "$baseUrl/rest/v1/subscriptions?user_id=eq.$userId&limit=1"
            val req = baseRequest(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Subscription fetch failed (${resp.code}): $err")
                }
                val body = resp.body?.string().orEmpty()
                val dto = json.decodeFromString<List<SupabaseSubscriptionDto>>(body).firstOrNull()
                    ?: return@runCatching null
                UserSubscription(
                    id = dto.id,
                    userId = dto.userId,
                    plan = SubscriptionPlan.fromKey(dto.plan),
                    startsAt = dto.startsAt,
                    expiresAt = dto.expiresAt,
                    status = SubscriptionStatus.fromKey(dto.status)
                )
            }
        }
    }

    suspend fun insertWeddingWishRemote(wish: WeddingWish): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val dto = SupabaseWeddingWishDto(
                id = wish.id,
                museumId = wish.museumId,
                guestName = wish.guestName,
                message = wish.message,
                createdAt = wish.createdAt
            )
            val payload = json.encodeToString(dto).toRequestBody(jsonMediaType)
            val req = baseRequest("$baseUrl/rest/v1/wedding_wishes")
                .header("Prefer", "return=minimal")
                .post(payload)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Wish save failed (${resp.code}): $err")
                }
            }
        }
    }

    suspend fun fetchWeddingWishesRemote(museumId: String): Result<List<WeddingWish>> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = SecretsConfig.supabaseUrl
            val url = "$baseUrl/rest/v1/wedding_wishes?museum_id=eq.$museumId&order=created_at.desc"
            val req = baseRequest(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw IllegalStateException("Wishes fetch failed (${resp.code}): $err")
                }
                val body = resp.body?.string().orEmpty()
                json.decodeFromString<List<SupabaseWeddingWishDto>>(body).map {
                    WeddingWish(
                        id = it.id,
                        museumId = it.museumId,
                        guestName = it.guestName,
                        message = it.message,
                        createdAt = it.createdAt
                    )
                }
            }
        }
    }
}
