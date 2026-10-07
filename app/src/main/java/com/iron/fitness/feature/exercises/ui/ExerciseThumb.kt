package com.iron.fitness.feature.exercises.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.iron.fitness.core.ui.IronIcons
import com.iron.fitness.core.ui.components.IronIcon
import com.iron.fitness.core.ui.theme.Iron
import com.iron.fitness.feature.exercises.data.ExerciseCategory

@Composable
fun ExerciseThumb(
    model: Any?,
    category: ExerciseCategory,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(Iron.colors.surfaceHigh)
            .border(1.dp, Iron.colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            CategoryIcon(category)
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { CategoryIcon(category) },
                error = { CategoryIcon(category) },
            )
        }
    }
}

@Composable
private fun CategoryIcon(category: ExerciseCategory) {
    val icon = when (category) {
        ExerciseCategory.STRENGTH -> IronIcons.Workouts
        ExerciseCategory.CARDIO -> IronIcons.Heart
        ExerciseCategory.STRETCHING -> IronIcons.Stretch
    }
    IronIcon(icon, null, tint = Iron.colors.textSecondary, size = 22.dp)
}
