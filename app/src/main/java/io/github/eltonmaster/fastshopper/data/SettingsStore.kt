package io.github.eltonmaster.fastshopper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
    }

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[Keys.SERVER_URL],
            token = prefs[Keys.TOKEN]?.let { TokenCipher.decrypt(it) },
            userName = prefs[Keys.USER_NAME],
            defaultListId = prefs[Keys.DEFAULT_LIST],
        )
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

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
