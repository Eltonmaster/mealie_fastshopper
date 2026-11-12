package io.github.eltonmaster.fastshopper.data.api

import kotlinx.serialization.Serializable

@Serializable
data class UserSelf(
    val id: String,
    val username: String? = null,
    val fullName: String? = null,
    val email: String? = null,
) {
    val displayName: String get() = fullName?.takeIf { it.isNotBlank() } ?: username ?: email ?: "Mealie user"
}

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val total: Int? = null,
)

@Serializable
data class ShoppingListSummary(
    val id: String,
    val name: String? = null,
)

@Serializable
data class ShoppingList(
    val id: String,
    val name: String? = null,
    val listItems: List<ShoppingListItem> = emptyList(),
)

@Serializable
data class ShoppingListItem(
    val id: String,
    val display: String? = null,
    val note: String? = null,
    val quantity: Double = 0.0,
    val checked: Boolean = false,
    val position: Int = 0,
)
