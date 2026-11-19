package com.example.data.repository

import android.content.Context
import com.example.data.local.MuzeychaDatabase
import com.example.data.local.SubscriptionDao
import com.example.data.local.SubscriptionEntity
import com.example.data.remote.SecretsConfig
import com.example.data.remote.SupabaseDataSource
import com.example.domain.model.SubscriptionPlan
import com.example.domain.model.SubscriptionStatus
import com.example.domain.model.UserSubscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubscriptionRepository(
    context: Context,
    private val subscriptionDao: SubscriptionDao = MuzeychaDatabase.getInstance(context).subscriptionDao(),
    private val remote: SupabaseDataSource = SupabaseDataSource()
) {
    private val prefs = context.getSharedPreferences("muzeycha_prefs", Context.MODE_PRIVATE)

    val deviceUserId: String
        get() {
            val existing = prefs.getString("device_user_id", null)
            if (!existing.isNullOrBlank()) return existing
            val created = "usr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            prefs.edit().putString("device_user_id", created).apply()
            return created
        }

    fun observeSubscription(): Flow<UserSubscription> {
        val uid = deviceUserId
        return subscriptionDao.observeForUser(uid).map { entity ->
            val sub = entity?.toDomain() ?: UserSubscription(userId = uid)
            normalizeExpiry(sub)
        }
    }

    suspend fun getCurrentSubscription(): UserSubscription {
        val uid = deviceUserId
        val local = subscriptionDao.getForUser(uid)?.toDomain()
        if (local != null) {
            val normalized = normalizeExpiry(local)
            if (normalized.status != local.status) {
                persist(normalized)
            }
            return normalized
        }

        if (SecretsConfig.isSupabaseConfigured) {
            remote.fetchSubscriptionRemote(uid).getOrNull()?.let { fetched ->
                val normalized = normalizeExpiry(fetched)
                persist(normalized)
                return normalized
            }
        }

        val freeSub = UserSubscription(
            userId = uid,
            plan = SubscriptionPlan.FREE,
            startsAt = 0L,
            expiresAt = 0L,
            status = SubscriptionStatus.ACTIVE
        )
        persist(freeSub)
        return freeSub
    }

    suspend fun activateTestPlan(plan: SubscriptionPlan): Result<UserSubscription> {
        return runCatching {
            val uid = deviceUserId
            val now = System.currentTimeMillis()
            val existing = subscriptionDao.getForUser(uid)
            val id = existing?.id ?: UUID.randomUUID().toString()

            val sub = if (plan == SubscriptionPlan.FREE) {
                UserSubscription(
                    id = id,
                    userId = uid,
                    plan = SubscriptionPlan.FREE,
                    startsAt = now,
                    expiresAt = 0L,
                    status = SubscriptionStatus.ACTIVE
                )
            } else {
                val expires = now + TimeUnit.DAYS.toMillis(plan.durationDays)
                UserSubscription(
                    id = id,
                    userId = uid,
                    plan = plan,
                    startsAt = now,
                    expiresAt = expires,
                    status = SubscriptionStatus.ACTIVE
                )
            }
            persist(sub)
            sub
        }
    }

    suspend fun simulateExpiryForTest(): Result<UserSubscription> {
        return runCatching {
            val current = getCurrentSubscription()
            val pastTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            val expired = current.copy(
                plan = if (current.plan == SubscriptionPlan.FREE) SubscriptionPlan.SIX_MONTHS else current.plan,
                startsAt = pastTime - TimeUnit.DAYS.toMillis(180),
                expiresAt = pastTime,
                status = SubscriptionStatus.EXPIRED
            )
            persist(expired)
            expired
        }
    }

    private fun normalizeExpiry(sub: UserSubscription): UserSubscription {
        val now = System.currentTimeMillis()
        return if (sub.plan != SubscriptionPlan.FREE &&
            sub.status == SubscriptionStatus.ACTIVE &&
            sub.expiresAt <= now
        ) {
            sub.copy(status = SubscriptionStatus.EXPIRED)
        } else {
            sub
        }
    }

    private suspend fun persist(sub: UserSubscription) {
        subscriptionDao.upsert(
            SubscriptionEntity(
                id = sub.id,
                userId = sub.userId,
                plan = sub.plan.key,
                startsAt = sub.startsAt,
                expiresAt = sub.expiresAt,
                status = sub.status.key
            )
        )
        if (SecretsConfig.isSupabaseConfigured) {
            // Best-effort remote sync to subscriptions table
            remote.upsertSubscriptionRemote(sub)
        }
    }
}
