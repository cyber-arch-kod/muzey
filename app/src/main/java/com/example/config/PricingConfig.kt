package com.example.config

/**
 * Tarif narxlari, muddatlari va limitlari bitta faylda saqlanadi.
 * Narx yoki limitlarni o'zgartirish uchun shu qiymatlarni tahrirlang.
 */
object PricingConfig {
    // Bepul tarif limitlari
    const val FREE_MAX_MUSEUMS = 1
    const val FREE_MAX_PHOTOS = 5

    // Pullik tarif limitlari
    const val PAID_MAX_MUSEUMS = 100
    const val PAID_MAX_PHOTOS = 20

    // Narxlar (so'mda)
    const val SIX_MONTHS_PRICE_UZS = 30_000
    const val ONE_YEAR_PRICE_UZS = 100_000

    // Obuna muddatlari (kunlarda)
    const val SIX_MONTHS_DURATION_DAYS = 180L
    const val ONE_YEAR_DURATION_DAYS = 365L

    // Fon musiqasi uchun asset fayl yo'llari (keyinroq app/src/main/assets/audio/ ichiga qo'shing)
    const val MUSIC_ASSET_PATH = "audio/museum_bg.mp3"

    fun musicAssetCandidates(roomStyle: com.example.domain.model.RoomStyle): List<String> {
        return when (roomStyle) {
            com.example.domain.model.RoomStyle.ROMANTIC -> listOf("audio/romantic.mp3", MUSIC_ASSET_PATH)
            com.example.domain.model.RoomStyle.WEDDING -> listOf("audio/wedding.mp3", MUSIC_ASSET_PATH)
            else -> listOf(MUSIC_ASSET_PATH)
        }
    }

    fun formatUzs(amount: Int): String {
        val raw = amount.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in raw.length - 1 downTo 0) {
            sb.append(raw[i])
            count++
            if (count % 3 == 0 && i > 0) sb.append(' ')
        }
        return "${sb.reverse()} so'm"
    }
}
