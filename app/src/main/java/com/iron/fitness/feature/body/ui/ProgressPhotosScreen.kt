package com.iron.fitness.feature.body.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.media.PhotoKind
import com.iron.fitness.core.media.PhotoStorage
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.ConfirmDialog
import com.iron.fitness.core.ui.components.EmptyState
import com.iron.fitness.core.ui.components.IronBottomSheet
import com.iron.fitness.core.ui.components.IronIconButton
import com.iron.fitness.core.ui.components.IronScaffold
import com.iron.fitness.core.ui.components.PrimaryButton
import com.iron.fitness.core.ui.components.SecondaryButton
import com.iron.fitness.core.ui.components.Segmented
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.core.util.Fmt
import com.iron.fitness.feature.body.data.BodyRepository
import com.iron.fitness.feature.body.data.PhotoPose
import com.iron.fitness.feature.body.data.ProgressPhotoEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProgressPhotosViewModel @Inject constructor(
    private val repo: BodyRepository,
    private val photos: PhotoStorage,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val list: StateFlow<List<ProgressPhotoEntity>> = repo.observePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var pose: PhotoPose = PhotoPose.FRONT

    fun newCameraUri(): Uri {
        val (uri, file) = photos.newCameraTarget()
        savedStateHandle["pending_camera"] = file.absolutePath
        return uri
    }

    fun onCameraResult(ok: Boolean) {
        val path = savedStateHandle.get<String>("pending_camera") ?: return
        savedStateHandle.remove<String>("pending_camera")
        val file = File(path)
        if (!ok) {
            file.delete()
            return
        }
        viewModelScope.launch { photos.importFile(file, PhotoKind.PROGRESS, 1600)?.let { save(it) } }
    }

    fun onGallery(uri: Uri) {
        viewModelScope.launch { photos.import(uri, PhotoKind.PROGRESS, 1600)?.let { save(it) } }
    }

    private suspend fun save(path: String) {
        repo.addPhoto(ProgressPhotoEntity(day = LocalDate.now().toEpochDay(), path = path, pose = pose, weightKg = repo.latestWeight()))
    }

    fun delete(id: Long) = viewModelScope.launch { repo.deletePhoto(id) }
}

@Composable
fun poseLabel(p: PhotoPose): String = stringResource(
    when (p) {
        PhotoPose.FRONT -> R.string.pose_front
        PhotoPose.SIDE -> R.string.pose_side
        PhotoPose.BACK -> R.string.pose_back
    },
)

/** Фото прогресса: сетка, добавление с камеры или из галереи, сравнение «до/после». */
@Composable
fun ProgressPhotosScreen(onBack: () -> Unit, viewModel: ProgressPhotosViewModel = hiltViewModel()) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    var addSheet by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<List<Long>>(emptyList()) }
    var viewing by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) viewModel.onGallery(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> viewModel.onCameraResult(ok) }

    IronScaffold(
        title = stringResource(R.string.body_photos),
        onBack = onBack,
        actions = {
            IronIconButton(IronIcons.Layers, stringResource(R.string.photos_compare), { compare = !compare; selected = emptyList() }, tint = if (compare) Iron.colors.accentText else Iron.colors.text)
            IronIconButton(IronIcons.Add, stringResource(R.string.action_add), { addSheet = true })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(stringResource(R.string.photos_private), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            if (compare) {
                CompareView(list.filter { it.id in selected }.sortedBy { it.day }, hint = selected.size < 2)
            }
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.photos_empty), icon = IronIcons.Camera, action = {
                    PrimaryButton(stringResource(R.string.action_add), { addSheet = true }, icon = IronIcons.Camera)
                })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(list, key = { it.id }) { p ->
                    val isSel = p.id in selected
                    Column(
                        Modifier.clickable {
                            if (compare) {
                                selected = if (isSel) selected - p.id else (selected + p.id).takeLast(2)
                            } else {
                                viewing = p
                            }
                        },
                    ) {
                        AsyncImage(
                            model = File(p.path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(4.dp))
                                .border(if (isSel) 3.dp else 0.dp, Iron.colors.accent, RoundedCornerShape(4.dp)),
                        )
                        Text(Fmt.dateCompact(LocalDate.ofEpochDay(p.day)) + " · " + poseLabel(p.pose), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary, maxLines = 1)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }

    if (addSheet) {
        var pose by remember { mutableStateOf(viewModel.pose) }
        IronBottomSheet(title = stringResource(R.string.photos_add), onDismiss = { addSheet = false }) {
            Segmented(items = PhotoPose.entries, selected = pose, label = { poseLabel(it) }, onSelect = { pose = it; viewModel.pose = it })
            Text(stringResource(R.string.photos_tip), style = MaterialTheme.typography.bodySmall, color = Iron.colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(stringResource(R.string.action_camera), {
                    addSheet = false
                    camera.launch(viewModel.newCameraUri())
                }, icon = IronIcons.Camera, modifier = Modifier.weight(1f))
                SecondaryButton(stringResource(R.string.action_gallery), {
                    addSheet = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, icon = IronIcons.Image, modifier = Modifier.weight(1f))
            }
        }
    }
    viewing?.let { p ->
        IronBottomSheet(title = Fmt.dateFull(LocalDate.ofEpochDay(p.day)) + " · " + poseLabel(p.pose), onDismiss = { viewing = null }) {
            AsyncImage(model = File(p.path), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(0.75f))
            p.weightKg?.let { Text(Fmt.num(it, 1) + " " + stringResource(R.string.unit_kg), style = Iron.numbers.small) }
            SecondaryButton(stringResource(R.string.action_delete), { deleteId = p.id; viewing = null }, icon = IronIcons.Delete, color = Iron.colors.error, modifier = Modifier.fillMaxWidth())
        }
    }
    deleteId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.photos_delete_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { viewModel.delete(id); deleteId = null },
            onDismiss = { deleteId = null },
        )
    }
}

@Composable
private fun CompareView(pair: List<ProgressPhotoEntity>, hint: Boolean) {
    if (hint) {
        Text(stringResource(R.string.photos_compare_hint), style = MaterialTheme.typography.bodySmall, color = Iron.colors.accentText, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (pair.size == 2) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEachIndexed { i, p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (i == 0) R.string.photos_before else R.string.photos_after).uppercase(), style = MaterialTheme.typography.labelSmall, color = Iron.colors.textSecondary)
                    AsyncImage(
                        model = File(p.path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        Fmt.dateCompact(LocalDate.ofEpochDay(p.day)) + (p.weightKg?.let { " · " + Fmt.num(it, 1) + " " + stringResource(R.string.unit_kg) } ?: ""),
                        style = Iron.numbers.tiny,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
