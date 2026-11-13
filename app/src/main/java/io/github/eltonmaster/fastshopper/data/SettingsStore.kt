package io.github.eltonmaster.fastshopper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val serverUrl: String?,
    val token: String?,
    val userName: String?,
    val defaultListId: String?,
) {
    val isConfigured: Boolean get() = !serverUrl.isNullOrBlank() && !token.isNullOrBlank()
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token_encrypted")
        val USER_NAME = stringPreferencesKey("user_name")
        val DEFAULT_LIST = stringPreferencesKey("default_list_id")
        val THEME = stringPreferencesKey("theme_mode")
        val AUTO_CLEAR_DELAY = stringPreferencesKey("auto_clear_delay")
        val NOTIFY_CHANGES = booleanPreferencesKey("notify_changes")
        val NOTIFY_HINT_DONE = booleanPreferencesKey("notify_hint_done")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[Keys.SERVER_URL],
            token = prefs[Keys.TOKEN]?.let { TokenCipher.decrypt(it) },
            userName = prefs[Keys.USER_NAME],
            defaultListId = prefs[Keys.DEFAULT_LIST],
        )
    }.distinctUntilChanged() // changing the theme shouldn't look like a login change

    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { prefs -> ThemeMode.fromKey(prefs[Keys.THEME]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[Keys.THEME] = mode.key }
    }

    val autoClearDelay: Flow<AutoClearDelay> = context.dataStore.data
        .map { prefs -> AutoClearDelay.fromKey(prefs[Keys.AUTO_CLEAR_DELAY]) }
        .distinctUntilChanged()

    suspend fun setAutoClearDelay(delay: AutoClearDelay) {
        context.dataStore.edit { prefs -> prefs[Keys.AUTO_CLEAR_DELAY] = delay.key }
    }

    suspend fun saveLogin(serverUrl: String, token: String, userName: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_URL] = serverUrl
            prefs[Keys.TOKEN] = TokenCipher.encrypt(token)
            if (userName != null) prefs[Keys.USER_NAME] = userName else prefs.remove(Keys.USER_NAME)
        }
    }

    suspend fun setDefaultList(listId: String?) {
        context.dataStore.edit { prefs ->
            if (listId != null) prefs[Keys.DEFAULT_LIST] = listId else prefs.remove(Keys.DEFAULT_LIST)
        }
    }

    /** Forgets the login and list choices; the theme and auto-clear delay are phone preferences and stay. */
    suspend fun clear() {
        context.dataStore.edit { prefs ->
            val theme = prefs[Keys.THEME]
            val autoClearDelay = prefs[Keys.AUTO_CLEAR_DELAY]
            prefs.clear()
            if (theme != null) prefs[Keys.THEME] = theme
            if (autoClearDelay != null) prefs[Keys.AUTO_CLEAR_DELAY] = autoClearDelay
        }
    }

    // ---- Change notifications ----------------------------------------------------------------

    /** Background notifications when someone changes a list. Off until you turn them on. */
    val notifyChanges: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.NOTIFY_CHANGES] ?: false }
        .distinctUntilChanged()

    suspend fun setNotifyChanges(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.NOTIFY_CHANGES] = enabled }
    }

    /** Whether the "turn on notifications?" hint was answered (turned on or dismissed). */
    val notifyHintDone: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.NOTIFY_HINT_DONE] ?: false }
        .distinctUntilChanged()

    suspend fun setNotifyHintDone() {
        context.dataStore.edit { prefs -> prefs[Keys.NOTIFY_HINT_DONE] = true }
    }
}
