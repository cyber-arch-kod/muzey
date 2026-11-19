package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.Museum
import com.example.domain.model.MuseumPhoto
import com.example.domain.model.RoomStyle
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.domain.model.UserSubscription
import com.example.domain.model.WeddingWish
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "museums",
    indices = [Index(value = ["slug"], unique = true)]
)
data class MuseumEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val title: String,
    val dedication: String,
    val roomStyle: String,
    val brideName: String = "",
    val groomName: String = "",
    val weddingDate: String = "",
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String,
    val editToken: String,
    val createdAt: Long,
    val isSyncedToCloud: Boolean
)

@Entity(
    tableName = "museum_photos",
    foreignKeys = [
        ForeignKey(
            entity = MuseumEntity::class,
            parentColumns = ["id"],
            childColumns = ["museumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["museumId"])]
)
data class MuseumPhotoEntity(
    @PrimaryKey val id: String,
    val museumId: String,
    val sortOrder: Int,
    val imageUrl: String,
    val localPath: String?,
    val width: Int,
    val height: Int,
    val title: String,
    val caption: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationName: String
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val plan: String,
    val startsAt: Long,
    val expiresAt: Long,
    val status: String
) {
    fun toDomain(): UserSubscription = UserSubscription(
        id = id,
        userId = userId,
        plan = SubscriptionPlan.fromKey(plan),
        startsAt = startsAt,
        expiresAt = expiresAt,
        status = SubscriptionStatus.fromKey(status)
    )
}

@Entity(
    tableName = "wedding_wishes",
    indices = [Index(value = ["museumId"])]
)
data class WeddingWishEntity(
    @PrimaryKey val id: String,
    val museumId: String,
    val guestName: String,
    val message: String,
    val createdAt: Long
) {
    fun toDomain(): WeddingWish = WeddingWish(
        id = id,
        museumId = museumId,
        guestName = guestName,
        message = message,
        createdAt = createdAt
    )
}

data class MuseumWithPhotos(
    @Embedded val museum: MuseumEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "museumId"
    )
    val photos: List<MuseumPhotoEntity>
) {
    fun toDomain(): Museum = Museum(
        id = museum.id,
        slug = museum.slug,
        title = museum.title,
        dedication = museum.dedication,
        roomStyle = RoomStyle.fromKey(museum.roomStyle),
        brideName = museum.brideName,
        groomName = museum.groomName,
        weddingDate = museum.weddingDate,
        latitude = museum.latitude,
        longitude = museum.longitude,
        locationName = museum.locationName,
        editToken = museum.editToken,
        createdAt = museum.createdAt,
        isSyncedToCloud = museum.isSyncedToCloud,
        photos = photos.sortedBy { it.sortOrder }.map { p ->
            MuseumPhoto(
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
}

@Dao
interface MuseumDao {
    @Transaction
    @Query("SELECT * FROM museums WHERE editToken != '' ORDER BY createdAt DESC")
    fun observeOwnedMuseums(): Flow<List<MuseumWithPhotos>>

    @Transaction
    @Query("SELECT * FROM museums WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): MuseumWithPhotos?

    @Query("SELECT COUNT(*) FROM museums WHERE editToken != ''")
    suspend fun countOwnedMuseums(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuseum(museum: MuseumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<MuseumPhotoEntity>)

    @Query("DELETE FROM museum_photos WHERE museumId = :museumId")
    suspend fun deletePhotosForMuseum(museumId: String)

    @Query("DELETE FROM museums WHERE id = :museumId")
    suspend fun deleteMuseumById(museumId: String)

    @Transaction
    suspend fun upsertMuseumWithPhotos(museum: MuseumEntity, photos: List<MuseumPhotoEntity>) {
        insertMuseum(museum)
        deletePhotosForMuseum(museum.id)
        insertPhotos(photos)
    }
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    fun observeForUser(userId: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE userId = :userId LIMIT 1")
    suspend fun getForUser(userId: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)
}

@Dao
interface WeddingWishDao {
    @Query("SELECT * FROM wedding_wishes WHERE museumId = :museumId ORDER BY createdAt DESC")
    fun observeForMuseum(museumId: String): Flow<List<WeddingWishEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wish: WeddingWishEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(wishes: List<WeddingWishEntity>)
}

@Database(
    entities = [
        MuseumEntity::class,
        MuseumPhotoEntity::class,
        SubscriptionEntity::class,
        WeddingWishEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MuzeychaDatabase : RoomDatabase() {
    abstract fun museumDao(): MuseumDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun weddingWishDao(): WeddingWishDao

    companion object {
        @Volatile
        private var INSTANCE: MuzeychaDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE museums ADD COLUMN brideName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE museums ADD COLUMN groomName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE museums ADD COLUMN weddingDate TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscriptions (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        plan TEXT NOT NULL,
                        startsAt INTEGER NOT NULL,
                        expiresAt INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wedding_wishes (
                        id TEXT NOT NULL PRIMARY KEY,
                        museumId TEXT NOT NULL,
                        guestName TEXT NOT NULL,
                        message TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wedding_wishes_museumId ON wedding_wishes(museumId)")
            }
        }

        fun getInstance(context: Context): MuzeychaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MuzeychaDatabase::class.java,
                    "muzeycha_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
