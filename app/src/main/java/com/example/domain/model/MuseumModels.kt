package com.example.domain.model

import androidx.annotation.DrawableRes
import com.example.R
import com.example.config.PricingConfig
import com.example.i18n.UzStrings
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class RoomStyle(
    val key: String,
    val title: String,
    val description: String,
    @DrawableRes val previewResId: Int?,
    val isPremium: Boolean = false
) {
    CLASSIC_WHITE(
        key = "CLASSIC_WHITE",
        title = UzStrings.STYLE_CLASSIC_TITLE,
        description = UzStrings.STYLE_CLASSIC_DESC,
        previewResId = R.drawable.img_style_classic,
        isPremium = false
    ),
    WARM_WOOD(
        key = "WARM_WOOD",
        title = UzStrings.STYLE_WOOD_TITLE,
        description = UzStrings.STYLE_WOOD_DESC,
        previewResId = R.drawable.img_style_wood,
        isPremium = false
    ),
    MODERN_DARK(
        key = "MODERN_DARK",
        title = UzStrings.STYLE_DARK_TITLE,
        description = UzStrings.STYLE_DARK_DESC,
        previewResId = R.drawable.img_style_dark,
        isPremium = false
    ),
    ROMANTIC(
        key = "ROMANTIC",
        title = UzStrings.STYLE_ROMANTIC_TITLE,
        description = UzStrings.STYLE_ROMANTIC_DESC,
        previewResId = null,
        isPremium = true
    ),
    WEDDING(
        key = "WEDDING",
        title = UzStrings.STYLE_WEDDING_TITLE,
        description = UzStrings.STYLE_WEDDING_DESC,
        previewResId = null,
        isPremium = true
    );

    companion object {
        fun fromKey(key: String?): RoomStyle =
            entries.firstOrNull { it.key == key } ?: CLASSIC_WHITE
    }
}

enum class SubscriptionPlan(
    val key: String,
    val displayName: String,
    val priceUzs: Int,
    val durationDays: Long
) {
    FREE(
        key = "FREE",
        displayName = UzStrings.PLAN_FREE_NAME,
        priceUzs = 0,
        durationDays = 0L
    ),
    SIX_MONTHS(
        key = "SIX_MONTHS",
        displayName = UzStrings.PLAN_6M_NAME,
        priceUzs = PricingConfig.SIX_MONTHS_PRICE_UZS,
        durationDays = PricingConfig.SIX_MONTHS_DURATION_DAYS
    ),
    ONE_YEAR(
        key = "ONE_YEAR",
        displayName = UzStrings.PLAN_1Y_NAME,
        priceUzs = PricingConfig.ONE_YEAR_PRICE_UZS,
        durationDays = PricingConfig.ONE_YEAR_DURATION_DAYS
    );

    companion object {
        fun fromKey(key: String?): SubscriptionPlan =
            entries.firstOrNull { it.key == key } ?: FREE
    }
}

enum class SubscriptionStatus(val key: String) {
    ACTIVE("ACTIVE"),
    EXPIRED("EXPIRED"),
    CANCELLED("CANCELLED");

    companion object {
        fun fromKey(key: String?): SubscriptionStatus =
            entries.firstOrNull { it.key == key } ?: EXPIRED
    }
}

data class UserSubscription(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val plan: SubscriptionPlan = SubscriptionPlan.FREE,
    val startsAt: Long = 0L,
    val expiresAt: Long = 0L,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE
) {
    fun isPaidActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return plan != SubscriptionPlan.FREE &&
            status == SubscriptionStatus.ACTIVE &&
            expiresAt > nowMillis
    }

    val maxPhotos: Int
        get() = if (isPaidActive()) PricingConfig.PAID_MAX_PHOTOS else PricingConfig.FREE_MAX_PHOTOS

    val maxMuseums: Int
        get() = if (isPaidActive()) PricingConfig.PAID_MAX_MUSEUMS else PricingConfig.FREE_MAX_MUSEUMS

    val hasMusicFeature: Boolean
        get() = isPaidActive()
}

data class WeddingWish(
    val id: String = UUID.randomUUID().toString(),
    val museumId: String,
    val guestName: String,
    val message: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class LocationPin(
    val latitude: Double,
    val longitude: Double,
    val name: String = ""
)

data class MuseumPhoto(
    val id: String,
    val museumId: String,
    val sortOrder: Int,
    val imageUrl: String,
    val localPath: String? = null,
    val width: Int,
    val height: Int,
    val title: String = "",
    val caption: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = ""
) {
    val locationPin: LocationPin?
        get() = if (latitude != null && longitude != null) {
            LocationPin(latitude, longitude, locationName)
        } else null
}

data class Museum(
    val id: String,
    val slug: String,
    val ownerUserId: String = "",
    val title: String,
    val dedication: String = "",
    val roomStyle: RoomStyle = RoomStyle.CLASSIC_WHITE,
    val brideName: String = "",
    val groomName: String = "",
    val weddingDate: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String = "",
    val editToken: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSyncedToCloud: Boolean = false,
    val photos: List<MuseumPhoto> = emptyList()
) {
    val locationPin: LocationPin?
        get() = if (latitude != null && longitude != null) {
            LocationPin(latitude, longitude, locationName)
        } else null
}

data class DraftPhotoItem(
    val id: String = UUID.randomUUID().toString(),
    val compressedFilePath: String? = null,
    val remoteUrl: String? = null,
    val width: Int = 1200,
    val height: Int = 900,
    val title: String = "",
    val caption: String = "",
    val locationPin: LocationPin? = null
)

data class ShareLinks(
    val webUrl: String,
    val fallbackWebUrl: String,
    val deepLinkUrl: String,
    val telegramShareUrl: String,
    val ogPreviewImageUrl: String?
)

object ShareLinkBuilder {
    fun build(
        slug: String,
        museumTitle: String,
        shareBaseUrl: String,
        botUsername: String,
        coverImageUrl: String?
    ): ShareLinks {
        val cleanBase = shareBaseUrl.trimEnd('/')
        val cleanBot = botUsername.trim().trimStart('@').ifBlank { "muzeycha_uz_bot" }
        val primaryTelegramAppUrl = "https://t.me/$cleanBot?startapp=$slug"
        val fallbackWebUrl = "$cleanBase/m/$slug"
        val deepLinkUrl = "muzeycha://m/$slug"
        val encodedUrl = URLEncoder.encode(primaryTelegramAppUrl, StandardCharsets.UTF_8.toString())
        val shareText = URLEncoder.encode(
            "${UzStrings.SHARE_TELEGRAM_TEXT_PREFIX} $museumTitle",
            StandardCharsets.UTF_8.toString()
        )
        val telegramUrl = "https://t.me/share/url?url=$encodedUrl&text=$shareText"
        return ShareLinks(
            webUrl = primaryTelegramAppUrl,
            fallbackWebUrl = fallbackWebUrl,
            deepLinkUrl = deepLinkUrl,
            telegramShareUrl = telegramUrl,
            ogPreviewImageUrl = coverImageUrl
        )
    }

    fun extractSlug(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val startAppMatch = Regex("[?&](?:startapp|tgWebAppStartParam)=([^&#]+)").find(trimmed)
        if (startAppMatch != null) {
            return startAppMatch.groupValues[1].trim().takeIf { it.isNotBlank() }
        }

        val normalized = trimmed
            .substringAfter("/m/", missingDelimiterValue = trimmed)
            .substringAfter("muzeycha://m/", missingDelimiterValue = trimmed)
            .substringBefore("?")
            .substringBefore("#")
            .trim('/')
        return normalized.takeIf { it.isNotBlank() }
    }

    fun generateSlug(title: String): String {
        val clean = title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "muzey" }
        val suffix = UUID.randomUUID().toString().take(6)
        return "$clean-$suffix"
    }
}
