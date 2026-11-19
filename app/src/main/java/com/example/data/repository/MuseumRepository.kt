package com.example.data.repository

import android.content.Context
import com.example.data.local.MuseumDao
import com.example.data.local.MuseumEntity
import com.example.data.local.MuseumPhotoEntity
import com.example.data.local.MuzeychaDatabase
import com.example.data.local.WeddingWishDao
import com.example.data.local.WeddingWishEntity
import com.example.data.remote.SecretsConfig
import com.example.data.remote.SupabaseDataSource
import com.example.domain.model.DraftPhotoItem
import com.example.domain.model.LocationPin
import com.example.domain.model.Museum
import com.example.domain.model.MuseumPhoto
import com.example.domain.model.RoomStyle
import com.example.domain.model.ShareLinkBuilder
import com.example.domain.model.WeddingWish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class MuseumRepository(
    context: Context,
    private val museumDao: MuseumDao = MuzeychaDatabase.getInstance(context).museumDao(),
    private val wishDao: WeddingWishDao = MuzeychaDatabase.getInstance(context).weddingWishDao(),
    private val remote: SupabaseDataSource = SupabaseDataSource()
) {
    private val appContext = context.applicationContext
    val ownedMuseums: Flow<List<Museum>> = museumDao.observeOwnedMuseums().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun countOwnedMuseums(): Int = museumDao.countOwnedMuseums()

    suspend fun getMuseumBySlug(slug: String): Result<Museum?> {
        val local = museumDao.getBySlug(slug)?.toDomain()
        if (local != null) {
            return Result.success(local)
        }
        if (!SecretsConfig.isSupabaseConfigured) {
            return Result.success(null)
        }
        return remote.fetchMuseumBySlug(slug)
    }

    suspend fun saveMuseum(
        existingMuseumId: String? = null,
        existingSlug: String? = null,
        existingEditToken: String? = null,
        ownerUserId: String = "",
        title: String,
        dedication: String,
        roomStyle: RoomStyle,
        brideName: String = "",
        groomName: String = "",
        weddingDate: String = "",
        museumLocation: LocationPin?,
        draftPhotos: List<DraftPhotoItem>
    ): Result<Museum> {
        return runCatching {
            val museumId = existingMuseumId ?: UUID.randomUUID().toString()
            val slug = existingSlug ?: ShareLinkBuilder.generateSlug(title)
            val editToken = existingEditToken?.ifBlank { null } ?: UUID.randomUUID().toString()
            val resolvedOwnerId = ownerUserId.ifBlank {
                SubscriptionRepository(appContext).deviceUserId
            }
            val now = System.currentTimeMillis()

            val cloudEnabled = SecretsConfig.isSupabaseConfigured
            var synced = false

            val finalPhotos = draftPhotos.mapIndexed { index, draft ->
                var finalRemoteUrl = draft.remoteUrl.orEmpty()
                if (cloudEnabled && finalRemoteUrl.isBlank() && !draft.compressedFilePath.isNullOrBlank()) {
                    val file = File(draft.compressedFilePath)
                    if (file.exists()) {
                        val objectPath = "$museumId/${draft.id}.webp"
                        finalRemoteUrl = remote.uploadWebpPhoto(objectPath, file).getOrThrow()
                    }
                }
                MuseumPhoto(
                    id = draft.id,
                    museumId = museumId,
                    sortOrder = index,
                    imageUrl = finalRemoteUrl,
                    localPath = draft.compressedFilePath,
                    width = draft.width,
                    height = draft.height,
                    title = draft.title.trim(),
                    caption = draft.caption.trim(),
                    latitude = draft.locationPin?.latitude,
                    longitude = draft.locationPin?.longitude,
                    locationName = draft.locationPin?.name?.trim().orEmpty()
                )
            }

            val museum = Museum(
                id = museumId,
                slug = slug,
                ownerUserId = resolvedOwnerId,
                title = title.trim(),
                dedication = dedication.trim(),
                roomStyle = roomStyle,
                brideName = if (roomStyle == RoomStyle.WEDDING) brideName.trim() else "",
                groomName = if (roomStyle == RoomStyle.WEDDING) groomName.trim() else "",
                weddingDate = if (roomStyle == RoomStyle.WEDDING) weddingDate.trim() else "",
                latitude = museumLocation?.latitude,
                longitude = museumLocation?.longitude,
                locationName = museumLocation?.name?.trim().orEmpty(),
                editToken = editToken,
                createdAt = now,
                isSyncedToCloud = false,
                photos = finalPhotos
            )

            if (cloudEnabled) {
                remote.upsertMuseumRemote(museum, finalPhotos).getOrThrow()
                synced = true
            }

            val savedMuseum = museum.copy(isSyncedToCloud = synced)
            museumDao.upsertMuseumWithPhotos(
                museum = MuseumEntity(
                    id = savedMuseum.id,
                    slug = savedMuseum.slug,
                    title = savedMuseum.title,
                    dedication = savedMuseum.dedication,
                    roomStyle = savedMuseum.roomStyle.key,
                    brideName = savedMuseum.brideName,
                    groomName = savedMuseum.groomName,
                    weddingDate = savedMuseum.weddingDate,
                    latitude = savedMuseum.latitude,
                    longitude = savedMuseum.longitude,
                    locationName = savedMuseum.locationName,
                    editToken = savedMuseum.editToken,
                    createdAt = savedMuseum.createdAt,
                    isSyncedToCloud = savedMuseum.isSyncedToCloud
                ),
                photos = savedMuseum.photos.map { p ->
                    MuseumPhotoEntity(
                        id = p.id,
                        museumId = p.museumId,
                        sortOrder = p.sortOrder,
                        imageUrl = p.imageUrl,
                        localPath = p.localPath,
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
            savedMuseum
        }
    }

    suspend fun deleteMuseum(museum: Museum): Result<Unit> {
        return runCatching {
            if (SecretsConfig.isSupabaseConfigured && museum.isSyncedToCloud && museum.editToken.isNotBlank()) {
                remote.deleteMuseumRemote(museum.id, museum.editToken).getOrThrow()
            }
            museumDao.deleteMuseumById(museum.id)
            museum.photos.forEach { photo ->
                photo.localPath?.let { path ->
                    runCatching { File(path).delete() }
                }
            }
        }
    }

    fun observeWeddingWishes(museumId: String): Flow<List<WeddingWish>> {
        return wishDao.observeForMuseum(museumId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun refreshWeddingWishes(museumId: String) {
        if (!SecretsConfig.isSupabaseConfigured) return
        remote.fetchWeddingWishesRemote(museumId).onSuccess { wishes ->
            wishDao.insertAll(
                wishes.map {
                    WeddingWishEntity(
                        id = it.id,
                        museumId = it.museumId,
                        guestName = it.guestName,
                        message = it.message,
                        createdAt = it.createdAt
                    )
                }
            )
        }
    }

    suspend fun submitWeddingWish(museumId: String, guestName: String, message: String): Result<WeddingWish> {
        return runCatching {
            val wish = WeddingWish(
                id = UUID.randomUUID().toString(),
                museumId = museumId,
                guestName = guestName.trim(),
                message = message.trim(),
                createdAt = System.currentTimeMillis()
            )
            if (SecretsConfig.isSupabaseConfigured) {
                remote.insertWeddingWishRemote(wish).getOrThrow()
            }
            wishDao.insert(
                WeddingWishEntity(
                    id = wish.id,
                    museumId = wish.museumId,
                    guestName = wish.guestName,
                    message = wish.message,
                    createdAt = wish.createdAt
                )
            )
            wish
        }
    }
}
