package io.github.eltonmaster.fastshopper.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.AddRequest
import io.github.eltonmaster.fastshopper.data.FoodSearch
import io.github.eltonmaster.fastshopper.data.ItemFormat
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.db.LabelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodSuggestion(val id: String, val name: String, val usualUnit: String?)
data class UnitSuggestion(val id: String, val name: String)

private data class AddForm(
    val query: String = "",
    val foodId: String? = null,
    val quantityText: String = "1",
    val unitText: String = "",
    val unitId: String? = null,
    val labelId: String? = null,
    val labelTouched: Boolean = false,
)

data class AddItemUiState(
    val query: String = "",
    val suggestions: List<FoodSuggestion> = emptyList(),
    val showCreate: Boolean = false,
    val quantityText: String = "1",
    val unitText: String = "",
    val unitPlaceholder: String? = null,
    val unitSuggestions: List<UnitSuggestion> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
    val labelId: String? = null,
    val labelName: String? = null,
    val busy: Boolean = false,
    /** Ids resolved for the add call. */
    internal val effectiveFoodId: String? = null,
    internal val placeholderUnitId: String? = null,
    internal val exactUnitId: String? = null,
) {
    val canAdd: Boolean get() = query.isNotBlank() && !busy
}

class AddItemViewModel(
    private val listId: String,
    private val repository: MealieRepository,
) : ViewModel() {
    private val form = MutableStateFlow(AddForm())
    private val busy = MutableStateFlow(false)

    private val catalog = combine(
        repository.observeFoods(),
        repository.observeUnits(),
        repository.observeLabels(),
        repository.observeHistory(),
    ) { foods, units, labels, history -> Catalog(foods, units, labels, history) }

    val state: StateFlow<AddItemUiState> = combine(catalog, form, busy) { c, f, b ->
        val unitsById = c.units.associateBy { it.id }
        val historyByFood = c.history.associateBy { it.foodId }
        fun usualUnitId(foodId: String?) = foodId?.let { historyByFood[it]?.lastUnitId }

        val exactFood = if (f.foodId == null) c.foods.firstOrNull { FoodSearch.isExactMatch(f.query, it.name) } else null
        val effectiveFoodId = f.foodId ?: exactFood?.id
        val effectiveFood = effectiveFoodId?.let { id -> c.foods.firstOrNull { it.id == id } }

        val suggestions = if (f.foodId != null) emptyList() else {
            val candidates = c.foods.map {
                val h = historyByFood[it.id]
                FoodSearch.Candidate(it.id, it.name, h?.timesAdded ?: 0, h?.lastAddedAt ?: 0L)
            }
            FoodSearch.rank(f.query, candidates).map {
                FoodSuggestion(it.id, it.name, usualUnitId(it.id)?.let { u -> unitsById[u]?.name })
            }
        }

        val placeholderUnitId = usualUnitId(effectiveFoodId)
        val exactUnit = if (f.unitId == null && f.unitText.isNotBlank()) {
            c.units.firstOrNull {
                FoodSearch.isExactMatch(f.unitText, it.name) ||
                    (it.abbreviation != null && FoodSearch.isExactMatch(f.unitText, it.abbreviation))
            }
        } else null
        val unitSuggestions = if (f.unitId != null || f.unitText.isBlank()) emptyList() else {
            FoodSearch.rank(f.unitText, c.units.map { FoodSearch.Candidate(it.id, it.name, 0, 0L) }, limit = 4)
                .map { UnitSuggestion(it.id, it.name) }
        }

        val labelId = if (f.labelTouched) f.labelId else effectiveFood?.labelId
        AddItemUiState(
            query = f.query,
            suggestions = suggestions,
            showCreate = f.query.isNotBlank() && effectiveFoodId == null,
            quantityText = f.quantityText,
            unitText = f.unitText,
            unitPlaceholder = placeholderUnitId?.let { unitsById[it]?.name },
            unitSuggestions = unitSuggestions,
            labels = c.labels,
            labelId = labelId,
            labelName = labelId?.let { id -> c.labels.firstOrNull { it.id == id }?.name },
            busy = b,
            effectiveFoodId = effectiveFoodId,
            placeholderUnitId = placeholderUnitId,
            exactUnitId = exactUnit?.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddItemUiState())

    init {
        viewModelScope.launch { repository.refreshCatalog() }
    }

    fun onQueryChange(text: String) = form.update { it.copy(query = text, foodId = null) }

    fun pickFood(suggestion: FoodSuggestion) =
        form.update { it.copy(query = suggestion.name, foodId = suggestion.id, labelTouched = false) }

    fun onQuantityChange(text: String) =
        form.update { it.copy(quantityText = text.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }) }

    fun stepQuantity(delta: Int) = form.update {
        val current = parseQuantity(it.quantityText)
        val next = (current + delta).coerceAtLeast(1.0)
        it.copy(quantityText = ItemFormat.number(next))
    }

    fun onUnitTextChange(text: String) = form.update { it.copy(unitText = text, unitId = null) }

    fun pickUnit(unit: UnitSuggestion) = form.update { it.copy(unitText = unit.name, unitId = unit.id) }

    fun pickLabel(labelId: String?) = form.update { it.copy(labelId = labelId, labelTouched = true) }

    /** Adds the item; the form resets for the next one. [onMessage] reports fallbacks. */
    fun add(onMessage: (String) -> Unit) {
        val s = state.value
        val f = form.value
        if (!s.canAdd) return
        val newUnitName: String?
        val unitId: String?
        when {
            f.unitId != null -> { unitId = f.unitId; newUnitName = null }
            f.unitText.isBlank() -> { unitId = s.placeholderUnitId; newUnitName = null } // use the placeholder
            s.exactUnitId != null -> { unitId = s.exactUnitId; newUnitName = null }
            else -> { unitId = null; newUnitName = f.unitText.trim() }
        }
        val request = AddRequest(
            listId = listId,
            foodId = s.effectiveFoodId,
            newFoodName = if (s.effectiveFoodId == null) f.query.trim() else null,
            quantity = parseQuantity(f.quantityText),
            unitId = unitId,
            newUnitName = newUnitName,
            labelId = s.labelId,
        )
        busy.value = true
        viewModelScope.launch {
            val result = runCatching { repository.addItem(request) }
            busy.value = false
            result.onSuccess {
                form.value = AddForm()
                if (it.fellBackToNote) onMessage("Couldn't create it in Mealie, saved as a note instead.")
            }.onFailure { onMessage("Couldn't add the item.") }
        }
    }

    private fun parseQuantity(text: String): Double =
        text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
}

private data class Catalog(
    val foods: List<io.github.eltonmaster.fastshopper.data.db.FoodEntity>,
    val units: List<io.github.eltonmaster.fastshopper.data.db.UnitEntity>,
    val labels: List<LabelEntity>,
    val history: List<io.github.eltonmaster.fastshopper.data.db.ContainerHistory>,
)
