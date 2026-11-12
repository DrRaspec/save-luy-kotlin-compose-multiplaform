package com.bunleng.saving_luy

import com.bunleng.saving_luy.domain.BudgetCategory
import com.bunleng.saving_luy.domain.BudgetMonth
import com.bunleng.saving_luy.domain.CategoryBudgetEntry
import com.bunleng.saving_luy.domain.SimpleDate
import com.bunleng.saving_luy.widget.WidgetCategory
import com.bunleng.saving_luy.widget.WidgetSnapshot
import com.bunleng.saving_luy.widget.WidgetSnapshotUpdater
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetSnapshotTest {

    private fun category(name: String, sortOrder: Int, available: Long) =
        BudgetCategory(name, "g", name, sortOrder) to
            CategoryBudgetEntry(name, assigned = available, activity = 0L, carryover = 0L)

    @Test
    fun from_takesTopThreeByAvailable() {
        val snapshot = WidgetSnapshot.from(
            readyToAssign = 50_00L,
            categories = listOf(
                category("Rent", 0, 100_00L),
                category("Food", 1, -5_00L),
                category("Fun", 2, 300_00L),
                category("Bus", 3, 20_00L),
            ),
            currencyCode = "USD",
        )
        assertEquals(50_00L, snapshot.readyToAssign)
        assertEquals("$50.00", snapshot.readyToAssignText)
        assertEquals(
            listOf(
                WidgetCategory("Fun", 300_00L, "$300.00"),
                WidgetCategory("Rent", 100_00L, "$100.00"),
                WidgetCategory("Bus", 20_00L, "$20.00"),
            ),
            snapshot.topCategories,
        )
    }

    @Test
    fun from_formatsNegativeAvailable() {
        val snapshot = WidgetSnapshot.from(-10_00L, listOf(category("Food", 0, -5_00L)), "USD")
        assertEquals("-$10.00", snapshot.readyToAssignText)
        assertEquals("-$5.00", snapshot.topCategories.single().availableText)
    }

    @Test
    fun from_tiesKeepInputOrder() {
        val snapshot = WidgetSnapshot.from(
            0L,
            listOf(category("B", 1, 0L), category("A", 0, 0L)),
            "USD",
        )
        assertEquals(listOf("B", "A"), snapshot.topCategories.map { it.name })
    }

    @Test
    fun container_writesSnapshotAtStartupAndAfterEveryChange() {
        val writer = RecordingSnapshotWriter()
        val container = testContainer(writer)
        assertEquals(1, writer.snapshots.size)
        assertEquals(0L, writer.snapshots.last().readyToAssign)

        container.addTransaction(500_00L, SimpleDate(2026, 11, 1))
        assertEquals(500_00L, writer.snapshots.last().readyToAssign)

        container.budgetRepository.updateAssigned(BudgetMonth(2026, 11), "cat_food", 200_00L)
        assertEquals(3, writer.snapshots.size)
        assertEquals(300_00L, writer.snapshots.last().readyToAssign)
    }

    @Test
    fun updater_usesCurrentMonthAvailable() {
        val writer = RecordingSnapshotWriter()
        val container = testContainer()
        val nov = BudgetMonth(2026, 11)
        container.budgetRepository.updateAssigned(nov, "cat_food", 200_00L)
        container.budgetRepository.updateAssigned(nov, "cat_rent", 900_00L)
        container.addTransaction(-50_00L, SimpleDate(2026, 11, 2), "cat_food")

        WidgetSnapshotUpdater(container.budgetRepository, writer, currentMonth = { nov }).update()

        assertEquals(
            listOf(
                WidgetCategory("Rent", 900_00L, "$900.00"),
                WidgetCategory("Food", 150_00L, "$150.00"),
                WidgetCategory("Transport", 0L, "$0.00"),
            ),
            writer.snapshots.single().topCategories,
        )
    }
}
