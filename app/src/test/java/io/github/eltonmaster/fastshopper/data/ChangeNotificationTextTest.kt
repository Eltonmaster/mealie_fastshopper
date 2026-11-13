package io.github.eltonmaster.fastshopper.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeNotificationTextTest {
    @Test fun oneNewItem() = assertEquals("1 new item (Milk)", ChangeNotificationText.text(listOf("Milk"), emptyList()))

    @Test fun threeNewItems() = assertEquals(
        "3 new items (Milk, Eggs, Bread)",
        ChangeNotificationText.text(listOf("Milk", "Eggs", "Bread"), emptyList()),
    )

    @Test fun onlyChanged() = assertEquals(
        "2 changed items (Milk, Eggs)",
        ChangeNotificationText.text(emptyList(), listOf("Milk", "Eggs")),
    )

    @Test fun oneChanged() = assertEquals("1 changed item", ChangeNotificationText.summary(0, 1))

    @Test fun newAndChanged() = assertEquals(
        "2 new, 1 changed (Milk, Eggs, Bread)",
        ChangeNotificationText.text(listOf("Milk", "Eggs"), listOf("Bread")),
    )

    @Test fun longListIsCut() = assertEquals(
        "5 new items (Milk, Eggs, Bread +2 more)",
        ChangeNotificationText.text(listOf("Milk", "Eggs", "Bread", "Butter", "Jam"), emptyList()),
    )

    @Test fun exactlyThreeNamesNoMore() = assertEquals("A, B, C", ChangeNotificationText.names(listOf("A", "B", "C")))
}
