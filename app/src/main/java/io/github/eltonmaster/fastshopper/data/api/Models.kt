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
    val labelSettings: List<LabelSetting> = emptyList(),
)

@Serializable
data class LabelSetting(
    val labelId: String,
    val position: Int = 0,
    val label: LabelRef? = null,
)

@Serializable
data class LabelRef(
    val id: String,
    val name: String = "",
    val color: String? = null,
)

@Serializable
data class FoodRef(
    val id: String,
    val name: String = "",
    val pluralName: String? = null,
    val labelId: String? = null,
)

@Serializable
data class UnitRef(
    val id: String,
    val name: String = "",
    val pluralName: String? = null,
    val abbreviation: String? = null,
    val useAbbreviation: Boolean = false,
)

@Serializable
data class ShoppingListItem(
    val id: String,
    val shoppingListId: String? = null,
    val display: String? = null,
    val note: String? = null,
    val quantity: Double = 0.0,
    val checked: Boolean = false,
    val position: Int = 0,
    val foodId: String? = null,
    val unitId: String? = null,
    val labelId: String? = null,
    val food: FoodRef? = null,
    val unit: UnitRef? = null,
    val label: LabelRef? = null,
    val updatedAt: String? = null,
)

/** Body for creating or updating a shopping list item. Mealie recomputes `display` itself. */
@Serializable
data class ItemWrite(
    val id: String,
    val shoppingListId: String,
    val checked: Boolean,
    val position: Int,
    val quantity: Double,
    val note: String,
    val foodId: String?,
    val unitId: String?,
    val labelId: String?,
)

@Serializable
data class FoodCreate(
    val name: String,
    val labelId: String? = null,
)

@Serializable
data class UnitCreate(
    val name: String,
)
