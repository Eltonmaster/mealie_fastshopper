package io.github.eltonmaster.fastshopper.data

import io.github.eltonmaster.fastshopper.data.api.MealieApi
import io.github.eltonmaster.fastshopper.data.api.MealieClient
import io.github.eltonmaster.fastshopper.data.api.ShoppingList
import io.github.eltonmaster.fastshopper.data.api.ShoppingListSummary
import io.github.eltonmaster.fastshopper.data.api.UserSelf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class NotConfiguredException : IllegalStateException("App is not connected to a Mealie server")

class MealieRepository(private val settingsStore: SettingsStore) {
    private val lock = Mutex()
    private var cachedKey: Pair<String, String>? = null
    private var cachedApi: MealieApi? = null

    private suspend fun api(): MealieApi = lock.withLock {
        val settings = settingsStore.settings.first()
        val url = settings.serverUrl
        val token = settings.token
        if (url.isNullOrBlank() || token.isNullOrBlank()) throw NotConfiguredException()
        val key = url to token
        cachedApi?.takeIf { cachedKey == key } ?: MealieClient.create(url, token).also {
            cachedApi = it
            cachedKey = key
        }
    }

    /** Checks a server URL + token pair without saving it. */
    suspend fun verify(serverUrl: String, token: String): Result<UserSelf> =
        runCatching { MealieClient.create(serverUrl, token).getSelf() }

    suspend fun shoppingLists(): Result<List<ShoppingListSummary>> =
        runCatching { api().getShoppingLists().items }

    suspend fun shoppingList(id: String): Result<ShoppingList> =
        runCatching { api().getShoppingList(id) }
}

/** Human-readable message for errors coming from the Mealie API. */
fun Throwable.toUserMessage(): String = when (this) {
    is NotConfiguredException -> "Not connected to a Mealie server."
    is HttpException -> when (code()) {
        401, 403 -> "The server rejected the API token."
        404 -> "Not found. Check the server URL (including any sub-path)."
        in 500..599 -> "The Mealie server had an error (HTTP ${code()})."
        else -> "Request failed (HTTP ${code()})."
    }
    is UnknownHostException -> "Server not found. Check the URL and your connection."
    is SSLException -> "Secure connection failed. Check the server's HTTPS certificate."
    is SerializationException -> "Unexpected answer from the server. Is this a Mealie server?"
    is IOException -> "Couldn't reach the server: ${message ?: "network error"}"
    else -> message ?: "Something went wrong."
}
