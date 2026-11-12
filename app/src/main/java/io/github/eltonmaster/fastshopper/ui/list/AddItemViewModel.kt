package io.github.eltonmaster.fastshopper.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eltonmaster.fastshopper.data.AddRequest
import io.github.eltonmaster.fastshopper.data.EditRequest
import io.github.eltonmaster.fastshopper.data.FoodSearch
import io.github.eltonmaster.fastshopper.data.ItemEdit
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

/** What the edited item looked like when the sheet opened. */
private data class EditOrigin(val itemId: String, val foodId: String?, val name: String)

private data class AddForm(
    val query: String = "",
    val foodId: String? = null,
    val quantityText: String = "1",
    val unitText: String = "",
    val unitId: String? = null,
    val labelId: String? = null,
    val labelTouched: Boolean = false,
    val note: String = "",
    val edit: EditOrigin? = null,
)

private data class Status(
    val busy: Boolean = false,
    /** False while the item to edit is still being loaded. */
    val loaded: Boolean = true,
    /** The item to edit doesn't exist anymore. */
    val missing: Boolean = false,
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
    val isEdit: Boolean = false,
    val note: String = "",
    val loaded: Boolean = true,
    val missing: Boolean = false,
    /** Ids resolved for the add call. */
    internal val effectiveFoodId: String? = null,
    internal val placeholderUnitId: String? = null,
    internal val exactUnitId: String? = null,
    internal val staysNote: Boolean = false,
) {
    val canAdd: Boolean get() = query.isNotBlank() && !busy && loaded
}

/** Backs the add sheet, and the same sheet in edit mode when [editItemId] is set. */
class AddItemViewModel(
    private val listId: String,
    private val repository: MealieRepository,
    private val editItemId: String? = null,
) : ViewModel() {
    private val form = MutableStateFlow(AddForm())
    private val status = MutableStateFlow(Status(loaded = editItemId == null))

    val isEdit: Boolean get() = editItemId != null

    private val catalog = combine(
        repository.observeFoods(),
        repository.observeUnits(),
        repository.observeLabels(),
        repository.observeHistory(),
    ) { foods, units, labels, history -> Catalog(foods, units, labels, history) }

    val state: StateFlow<AddItemUiState> = combine(catalog, form, status) { c, f, st ->
        val unitsById = c.units.associateBy { it.id }
        val historyByFood = c.history.associateBy { it.foodId }
        fun usualUnitId(foodId: String?) = foodId?.let { historyByFood[it]?.lastUnitId }

        // An untouched note item keeps its text as a note, even if a food has the same name.
        val staysNote = f.edit != null && f.foodId == null && ItemEdit.staysNote(f.edit.foodId, f.edit.name, f.query)
        val exactFood = if (f.foodId == null && !staysNote) c.foods.firstOrNull { FoodSearch.isExactMatch(f.query, it.name) } else null
        val effectiveFoodId = f.foodId ?: exactFood?.id
        val effectiveFood = effectiveFoodId?.let { id -> c.foods.firstOrNull { it.id == id } }

        val suggestions = if (f.foodId != null || staysNote) emptyList() else {
            val candidates = c.foods.map {
                val h = historyByFood[it.id]
                FoodSearch.Candidate(it.id, it.name, h?.timesAdded ?: 0, h?.lastAddedAt ?: 0L)
            }
            FoodSearch.rank(f.query, candidates).map {
                FoodSuggestion(it.id, it.name, usualUnitId(it.id)?.let { u -> unitsById[u]?.name })
            }
        }

        // When editing, an empty container means none rather than the usual one.
        val placeholderUnitId = if (f.edit != null) null else usualUnitId(effectiveFoodId)
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
            showCreate = f.query.isNotBlank() && effectiveFoodId == null && !staysNote,
            quantityText = f.quantityText,
            unitText = f.unitText,
            unitPlaceholder = placeholderUnitId?.let { unitsById[it]?.name },
            unitSuggestions = unitSuggestions,
            labels = c.labels,
            labelId = labelId,
            labelName = labelId?.let { id -> c.labels.firstOrNull { it.id == id }?.name },
            busy = st.busy,
            isEdit = isEdit,
            note = f.note,
            loaded = st.loaded,
            missing = st.missing,
            effectiveFoodId = effectiveFoodId,
            placeholderUnitId = placeholderUnitId,
            exactUnitId = exactUnit?.id,
            staysNote = staysNote,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddItemUiState())

    init {
        viewModelScope.launch { repository.refreshCatalog() }
        if (editItemId != null) viewModelScope.launch { load(editItemId) }
    }

    private suspend fun load(itemId: String) {
        val item = repository.item(itemId)
        if (item == null) {
            status.update { it.copy(missing = true) }
            return
        }
        val name = ItemEdit.nameText(item)
        form.value = AddForm(
            query = name,
            foodId = item.foodId,
            quantityText = ItemEdit.quantityText(item.quantity),
            unitText = item.unitName.orEmpty(),
            unitId = item.unitId,
            labelId = item.labelId,
            labelTouched = true,
            note = ItemEdit.noteText(item),
            edit = EditOrigin(item.id, item.foodId, name),
        )
        status.update { it.copy(loaded = true) }
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

    fun onNoteChange(text: String) = form.update { it.copy(note = text) }

    /** The container to send: a picked or matching one, a new one to create, or the usual one. */
    private fun unitChoice(s: AddItemUiState, f: AddForm): Pair<String?, String?> = when {
        f.unitId != null -> f.unitId to null
        f.unitText.isBlank() -> s.placeholderUnitId to null // the usual container (none when editing)
        s.exactUnitId != null -> s.exactUnitId to null
        else -> null to f.unitText.trim()
    }

    /** Adds the item; the form resets for the next one. [onMessage] reports fallbacks. */
    fun add(onMessage: (String) -> Unit) {
        val s = state.value
        val f = form.value
        if (!s.canAdd || s.isEdit) return
        val (unitId, newUnitName) = unitChoice(s, f)
        val request = AddRequest(
            listId = listId,
            foodId = s.effectiveFoodId,
            newFoodName = if (s.effectiveFoodId == null) f.query.trim() else null,
            quantity = parseQuantity(f.quantityText),
            unitId = unitId,
            newUnitName = newUnitName,
            labelId = s.labelId,
        )
        status.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { repository.addItem(request) }
            status.update { it.copy(busy = false) }
            result.onSuccess {
                form.value = AddForm()
                if (it.fellBackToNote) onMessage("Couldn't create it in Mealie, saved as a note instead.")
            }.onFailure { onMessage("Couldn't add the item.") }
        }
    }

    /** Saves the edited item through the offline queue, then calls [onSaved]. */
    fun save(onMessage: (String) -> Unit, onSaved: () -> Unit) {
        val s = state.value
        val f = form.value
        val origin = f.edit ?: return
        if (!s.canAdd) return
        val (unitId, newUnitName) = unitChoice(s, f)
        val request = EditRequest(
            itemId = origin.itemId,
            foodId = s.effectiveFoodId,
            newFoodName = if (s.effectiveFoodId == null && !s.staysNote) f.query.trim() else null,
            quantity = parseQuantity(f.quantityText),
            unitId = unitId,
            newUnitName = newUnitName,
            labelId = s.labelId,
            note = ItemEdit.composeNote(s.staysNote, f.query, f.note),
        )
        status.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = runCatching { repository.editItem(request) }
            status.update { it.copy(busy = false) }
            result.onSuccess {
                when {
                    it == null -> onMessage("This item was deleted meanwhile.")
                    it.fellBackToNote -> onMessage("Couldn't create it in Mealie, saved as a note instead.")
                }
                onSaved()
            }.onFailure { onMessage("Couldn't save the item.") }
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
