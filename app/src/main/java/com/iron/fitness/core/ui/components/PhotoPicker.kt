package com.iron.fitness.core.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iron.fitness.R
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.theme.Iron

/**
 * Блок выбора фото: превью, «Камера», «Галерея», «Убрать».
 * [requestCameraUri] должен вернуть content:// Uri файла, куда камера сохранит снимок.
 */
@Composable
fun PhotoPickerBlock(
    photo: Any?,
    onPickGallery: (Uri) -> Unit,
    requestCameraUri: () -> Uri,
    onCameraResult: (Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    previewHeight: Dp = 180.dp,
) {
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickGallery(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        onCameraResult(ok)
    }
    val shape = RoundedCornerShape(6.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(shape)
                .background(Iron.colors.surfaceHigh)
                .border(1.dp, Iron.colors.border, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                )
            } else {
                IronIcon(IronIcons.Image, null, tint = Iron.colors.textSecondary, size = 36.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                stringResource(R.string.action_camera),
                onClick = { camera.launch(requestCameraUri()) },
                icon = IronIcons.Camera,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                stringResource(R.string.action_gallery),
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                icon = IronIcons.Image,
                modifier = Modifier.weight(1f),
            )
        }
        if (photo != null && onRemove != null) {
            GhostButton(stringResource(R.string.action_remove_photo), onRemove, color = Iron.colors.error)
        }
    }
}
