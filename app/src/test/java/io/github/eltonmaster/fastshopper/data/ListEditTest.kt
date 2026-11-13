package io.github.eltonmaster.fastshopper.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListEditTest {
    private val fetched = Json.parseToJsonElement(
        """
        {
          "name": "Groceries",
          "extras": {"color": "green"},
          "createdAt": "2026-11-01T10:00:00",
          "updatedAt": "2026-11-12T18:30:00",
          "groupId": "g1",
          "userId": "u1",
          "id": "l1",
          "listItems": [
            {"id": "i1", "shoppingListId": "l1", "note": "Milk", "quantity": 2.0, "checked": false},
            {"id": "i2", "shoppingListId": "l1", "note": "Eggs", "quantity": 0.0, "checked": true}
          ],
          "householdId": "h1",
          "recipeReferences": [],
          "labelSettings": [{"id": "s1", "labelId": "lb1", "position": 0}]
        }
        """.trimIndent()
    ).jsonObject

    @Test fun renameChangesOnlyTheName() {
        val body = ListEdit.renamed(fetched, "Weekly shop")!!
        assertEquals(JsonPrimitive("Weekly shop"), body["name"])
        assertEquals(fetched.keys, body.keys)
        fetched.keys.filter { it != "name" }.forEach { key -> assertEquals(key, fetched[key], body[key]) }
    }

    @Test fun renameKeepsEveryItem() {
        val body = ListEdit.renamed(fetched, "Weekly shop")!!
        assertEquals(fetched["listItems"], body["listItems"])
    }

    @Test fun renameRefusesListWithoutItems() =
        assertNull(ListEdit.renamed(JsonObject(fetched - "listItems"), "Weekly shop"))

    @Test fun renameRefusesItemsThatAreNotAnArray() =
        assertNull(ListEdit.renamed(JsonObject(fetched + ("listItems" to JsonPrimitive("none"))), "Weekly shop"))

    @Test fun cleanNameTrims() = assertEquals("Drogerie", ListEdit.cleanName("  Drogerie "))
    @Test fun cleanNameRejectsBlank() = assertNull(ListEdit.cleanName("   "))

    @Test fun promptWithItems() = assertEquals(
        "Delete Groceries and its 12 items? This removes it for everyone in your household.",
        ListEdit.deletePrompt("Groceries", 12),
    )

    @Test fun promptWithOneItem() = assertEquals(
        "Delete Groceries and its 1 item? This removes it for everyone in your household.",
        ListEdit.deletePrompt("Groceries", 1),
    )

    @Test fun promptWhenEmpty() = assertEquals(
        "Delete Groceries? This removes it for everyone in your household.",
        ListEdit.deletePrompt("Groceries", 0),
    )

    @Test fun promptWhenCountUnknown() = assertEquals(
        "Delete Groceries and all its items? This removes it for everyone in your household.",
        ListEdit.deletePrompt("Groceries", null),
    )
}
