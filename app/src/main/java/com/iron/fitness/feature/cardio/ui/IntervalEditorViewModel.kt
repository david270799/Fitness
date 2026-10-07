package com.iron.fitness.feature.cardio.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iron.fitness.core.domain.BlockType
import com.iron.fitness.core.domain.IntervalBlock
import com.iron.fitness.core.domain.Intervals
import com.iron.fitness.feature.cardio.data.CardioRepository
import com.iron.fitness.feature.cardio.data.IntervalProgram
import com.iron.fitness.feature.exercises.data.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Блок в редакторе. [key] — локальный ключ. */
data class EditorBlock(
    val key: Long,
    val type: BlockType,
    val seconds: Int = 30,
    val label: String = "",
    val exerciseId: String? = null,
    val rounds: Int = 4,
    val children: List<EditorBlock> = emptyList(),
    val skipLastRest: Boolean = true,
)

data class IntervalEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val note: String = "",
    val workMet: Double = IntervalProgram.WORK_MET_HARD,
    val blocks: List<EditorBlock> = emptyList(),
    val nameError: Boolean = false,
    val emptyError: Boolean = false,
) {
    fun toBlocks(): List<IntervalBlock> = blocks.map { it.toBlock() }
}

fun EditorBlock.toBlock(): IntervalBlock = IntervalBlock(
    type = type,
    seconds = if (type == BlockType.REPEAT) 0 else seconds,
    label = label.trim().ifBlank { null },
    exerciseId = exerciseId,
    rounds = rounds,
    children = children.map { it.toBlock() },
    skipLastRest = skipLastRest,
)

@HiltViewModel
class IntervalEditorViewModel @Inject constructor(
    private val repo: CardioRepository,
    private val exercises: ExerciseRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val programId: Long? = savedStateHandle.get<Long>("id")?.takeIf { it > 0 }
    private var nextKey = 1L
    /** Блок, для которого выбирается упражнение в библиотеке. */
    var pickTargetKey: Long? = null

    private val _state = MutableStateFlow(IntervalEditorState(isNew = programId == null))
    val state: StateFlow<IntervalEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = programId?.let { repo.getProgram(it) }
            if (p == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        blocks = listOf(
                            EditorBlock(nextKey++, BlockType.WARMUP, seconds = 120),
                            EditorBlock(
                                nextKey++,
                                BlockType.REPEAT,
                                rounds = 8,
                                children = listOf(
                                    EditorBlock(nextKey++, BlockType.WORK, seconds = 20),
                                    EditorBlock(nextKey++, BlockType.REST, seconds = 10),
                                ),
                            ),
                        ),
                    )
                }
            } else {
                _state.value = IntervalEditorState(
                    loading = false,
                    isNew = false,
                    name = p.name,
                    note = p.note.orEmpty(),
                    workMet = p.workMet,
                    blocks = p.blocks.map { it.toEditor() },
                )
            }
        }
    }

    private fun IntervalBlock.toEditor(): EditorBlock = EditorBlock(
        key = nextKey++,
        type = type,
        seconds = seconds,
        label = label.orEmpty(),
        exerciseId = exerciseId,
        rounds = rounds,
        children = children.map { it.toEditor() },
        skipLastRest = skipLastRest,
    )

    fun setName(v: String) = _state.update { it.copy(name = v, nameError = false) }
    fun setNote(v: String) = _state.update { it.copy(note = v) }
    fun setWorkMet(v: Double) = _state.update { it.copy(workMet = v) }

    fun addTop(type: BlockType) = _state.update { s ->
        val block = if (type == BlockType.REPEAT) {
            EditorBlock(
                nextKey++,
                BlockType.REPEAT,
                rounds = 4,
                children = listOf(EditorBlock(nextKey++, BlockType.WORK, 30), EditorBlock(nextKey++, BlockType.REST, 15)),
            )
        } else {
            EditorBlock(nextKey++, type, seconds = defaultSeconds(type))
        }
        s.copy(blocks = s.blocks + block, emptyError = false)
    }

    fun addChild(parentKey: Long, type: BlockType) = _state.update { s ->
        s.copy(blocks = s.blocks.map { b ->
            if (b.key == parentKey) b.copy(children = b.children + EditorBlock(nextKey++, type, seconds = defaultSeconds(type))) else b
        })
    }

    private fun defaultSeconds(type: BlockType) = when (type) {
        BlockType.WARMUP, BlockType.COOLDOWN -> 120
        BlockType.REST -> 15
        else -> 30
    }

    fun update(key: Long, transform: (EditorBlock) -> EditorBlock) = _state.update { s ->
        s.copy(blocks = s.blocks.map { b ->
            when {
                b.key == key -> transform(b)
                b.children.any { it.key == key } -> b.copy(children = b.children.map { c -> if (c.key == key) transform(c) else c })
                else -> b
            }
        })
    }

    fun remove(key: Long) = _state.update { s ->
        s.copy(blocks = s.blocks.filterNot { it.key == key }.map { b -> b.copy(children = b.children.filterNot { it.key == key }) })
    }

    fun move(key: Long, delta: Int) = _state.update { s ->
        fun List<EditorBlock>.moved(): List<EditorBlock> {
            val i = indexOfFirst { it.key == key }
            if (i < 0) return this
            val t = i + delta
            if (t !in indices) return this
            return toMutableList().apply { add(t, removeAt(i)) }
        }
        if (s.blocks.any { it.key == key }) {
            s.copy(blocks = s.blocks.moved())
        } else {
            s.copy(blocks = s.blocks.map { b -> if (b.children.any { it.key == key }) b.copy(children = b.children.moved()) else b })
        }
    }

    fun setExercise(ids: List<String>) {
        val key = pickTargetKey ?: return
        val id = ids.firstOrNull() ?: return
        pickTargetKey = null
        viewModelScope.launch {
            val e = exercises.get(id) ?: return@launch
            update(key) { it.copy(exerciseId = e.id, label = e.name) }
        }
    }

    fun clearExercise(key: Long) = update(key) { it.copy(exerciseId = null) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val blocks = s.toBlocks()
        when {
            s.name.isBlank() -> { _state.update { it.copy(nameError = true) }; return }
            Intervals.expand(blocks).isEmpty() -> { _state.update { it.copy(emptyError = true) }; return }
        }
        viewModelScope.launch {
            repo.saveProgram(
                IntervalProgram(
                    id = programId ?: 0,
                    name = s.name.trim(),
                    note = s.note.trim().ifBlank { null },
                    blocks = blocks,
                    workMet = s.workMet,
                ),
            )
            onSaved()
        }
    }
}
