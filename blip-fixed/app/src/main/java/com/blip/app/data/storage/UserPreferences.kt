package com.blip.app.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "blip_prefs")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val KEY_USER_ID       = stringPreferencesKey("user_id")
        val KEY_USER_NAME     = stringPreferencesKey("user_name")
        val KEY_AVATAR_COLOR  = intPreferencesKey("avatar_color")
        val KEY_STEALTH_MODE  = booleanPreferencesKey("stealth_mode")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications")
        val KEY_ONBOARDED     = booleanPreferencesKey("onboarded")
    }

    val userId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_ID] ?: UUID.randomUUID().toString().also { saveUserId(it) }
    }

    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val avatarColor: Flow<Int> = context.dataStore.data.map { it[KEY_AVATAR_COLOR] ?: 0xFF2979FF.toInt() }
    val stealthMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_STEALTH_MODE] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    val isOnboarded: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDED] ?: false }

    suspend fun saveUserId(id: String) = context.dataStore.edit { it[KEY_USER_ID] = id }
    suspend fun saveUserName(name: String) = context.dataStore.edit { it[KEY_USER_NAME] = name }
    suspend fun saveAvatarColor(color: Int) = context.dataStore.edit { it[KEY_AVATAR_COLOR] = color }
    suspend fun setStealthMode(enabled: Boolean) = context.dataStore.edit { it[KEY_STEALTH_MODE] = enabled }
    suspend fun setNotifications(enabled: Boolean) = context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    suspend fun setOnboarded() = context.dataStore.edit { it[KEY_ONBOARDED] = true }
}
