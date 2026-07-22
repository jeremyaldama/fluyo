package com.qolve.fluyo.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qolve.fluyo.domain.model.Category
import com.qolve.fluyo.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Curated icon tokens offered in the category editor (resolved by `iconForToken`). */
val CATEGORY_ICON_TOKENS: List<String> = listOf(
    "utensils", "fastfood", "bus", "train", "gamepad", "music", "movie", "coffee",
    "drink", "heart", "hospital", "book", "groceries", "shopping", "pet", "home",
    "bolt", "money", "tag",
)

/** Palette offered in the category editor. */
val CATEGORY_COLORS: List<String> = listOf(
    "#FF7043", "#42A5F5", "#AB47BC", "#FFA726", "#EF5350", "#26A69A",
    "#66BB6A", "#7E57C2", "#78909C", "#EC407A", "#5C6BC0", "#FF8F00",
)

data class CategoryEditorState(
    val editingId: String? = null, // null while adding
    val name: String = "",
    val icon: String = CATEGORY_ICON_TOKENS.first(),
    val color: String = CATEGORY_COLORS.first(),
    val isSaving: Boolean = false,
) {
    val canSave: Boolean get() = !isSaving && name.trim().isNotEmpty()
}

data class ManageCategoriesUiState(
    val categories: List<Category> = emptyList(),
    val editor: CategoryEditorState? = null,
    val errorMessage: String? = null,
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
)

@HiltViewModel
class ManageCategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<CategoryEditorState?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val loadState = MutableStateFlow(LoadState())
    private var refreshJob: Job? = null
    private val pendingDeletes = mutableSetOf<String>()

    private data class LoadState(
        val hasLoaded: Boolean = false,
        val isLoading: Boolean = true,
        val errorMessage: String? = null,
    )

    val uiState: StateFlow<ManageCategoriesUiState> = combine(
        categoryRepository.observeCategories(),
        editor,
        error,
        loadState,
    ) { categories, editorState, err, load ->
        ManageCategoriesUiState(
            categories = categories,
            editor = editorState,
            errorMessage = err,
            hasLoaded = load.hasLoaded,
            isLoading = load.isLoading && !load.hasLoaded,
            loadErrorMessage = load.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ManageCategoriesUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val hadContent = loadState.value.hasLoaded
            loadState.update { it.copy(isLoading = true, errorMessage = null) }
            categoryRepository.refresh().fold(
                onSuccess = {
                    loadState.value = LoadState(hasLoaded = true, isLoading = false)
                },
                onFailure = {
                    loadState.value = LoadState(
                        hasLoaded = hadContent,
                        isLoading = false,
                        errorMessage = "No se pudieron cargar las categorías",
                    )
                },
            )
        }
    }

    fun openAdd() {
        editor.value = CategoryEditorState()
    }

    fun openEdit(category: Category) {
        editor.value = CategoryEditorState(
            editingId = category.id,
            name = category.name,
            icon = category.icon,
            color = category.color,
        )
    }

    fun closeEditor() {
        editor.value = null
    }

    fun onNameChange(value: String) {
        editor.update { it?.copy(name = value.take(30)) }
    }

    fun onIconChange(token: String) {
        editor.update { it?.copy(icon = token) }
    }

    fun onColorChange(hex: String) {
        editor.update { it?.copy(color = hex) }
    }

    fun consumeError() {
        error.value = null
    }

    fun save() {
        val current = editor.value ?: return
        if (!current.canSave) return
        editor.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (current.editingId == null) {
                categoryRepository.createCategory(current.name, current.icon, current.color)
            } else {
                categoryRepository.updateCategory(
                    current.editingId, current.name, current.icon, current.color,
                )
            }
            result.fold(
                onSuccess = { editor.value = null },
                onFailure = { e ->
                    editor.update { it?.copy(isSaving = false) }
                    error.value = "No se pudo guardar la categoría"
                },
            )
        }
    }

    fun delete(category: Category) {
        if (!pendingDeletes.add(category.id)) return
        viewModelScope.launch {
            try {
                categoryRepository.deleteCategory(category.id).onFailure {
                    error.value = "No se pudo eliminar la categoría"
                }
            } finally {
                pendingDeletes.remove(category.id)
            }
        }
    }
}
