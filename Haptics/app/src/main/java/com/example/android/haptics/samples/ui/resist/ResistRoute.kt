/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.haptics.samples.ui.resist

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.android.haptics.samples.R
import com.example.android.haptics.samples.ui.components.Screen
import com.example.android.haptics.samples.ui.theme.HapticSamplerTheme
import kotlinx.coroutines.delay

// Vibration related constants for the resist effect.
private const val TICK_INTERVAL_MIN_MS = 5L
private const val TICK_INTERVAL_MAX_MS = 25L
private const val TICK_INTENSITY_MIN = 0f
private const val TICK_INTENSITY_MAX = 0.8f

//
private const val MAX_DRAG_DISTANCE = 1000f
private val MAX_Y_OFFSET = 300.dp
private const val MAX_ROTATION = 360f

// Start and target values for the resistance indicator on the screen.
private val START_SIZE = 64.dp
private val START_STROKE_WIDTH = 8.dp
private const val START_INITIAL_ROTATION = 90f
private val START_Y_OFFSET = 0.dp
private val TARGET_SIZE = 128.dp
private val TARGET_STROKE_SIZE = 16.dp
private const val TIME_TO_ANIMATE_BACK = 1000
private const val DRAG_DISTANCE_BUFFER = 5f // Without a small buffer, indicators will flicker.

@Composable
fun ResistRoute(viewModel: ResistViewModel) {
    ResistScreen(
        isLowTickSupported = viewModel.isLowTickSupported,
        messageForUser = viewModel.messageForUser,
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ResistScreen(isLowTickSupported: Boolean, messageForUser: String = "") {
    var dragDistance by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val vibrator = LocalContext.current.getSystemService(Vibrator::class.java)

    isDragging = dragDistance > 0

    // State of the animated refresh indicator.
    var size by remember { mutableStateOf(START_SIZE) }
    var strokeWidth by remember { mutableStateOf(START_SIZE) }
    var rotation by remember { mutableStateOf(START_INITIAL_ROTATION) }
    var offsetY by remember { mutableStateOf(START_Y_OFFSET) }

    // Actual values used (since we want to animate).
    // Note we only need an animation back, otherwise animation  moves as the user drags.
    val animationSpecDp: AnimationSpec<Dp> = if (isDragging) tween(0) else tween(TIME_TO_ANIMATE_BACK)
    val animationSpecFloat: AnimationSpec<Float> = if (isDragging) tween(0) else tween(TIME_TO_ANIMATE_BACK)
    val sizeAnimated by animateDpAsState(
        targetValue = if (isDragging) size else START_SIZE,
        animationSpec = animationSpecDp
    )
    val strokeWidthAnimated by animateDpAsState(
        targetValue = if (isDragging) strokeWidth else START_STROKE_WIDTH,
        animationSpec = animationSpecDp
    )
    val rotationAnimated by animateFloatAsState(
        targetValue = if (isDragging) rotation else START_INITIAL_ROTATION,
        animationSpec = animationSpecFloat
    )
    val offsetYAnimated by animateDpAsState(
        targetValue = if (isDragging) offsetY else START_Y_OFFSET,
        animationSpec = animationSpecDp
    )

    // The interval (time between) and the intensity of the vibrations.
    var vibrationInterval by remember { mutableStateOf(TICK_INTERVAL_MAX_MS) }
    var vibrationIntensity by remember { mutableStateOf(TICK_INTENSITY_MIN) }

    // Use an interpolator to simulate resistance as the user approaches end of their drag.
    val interpolator = DecelerateInterpolator()

    // Use the drag distance to calculate the indicator state and the vibration intensity.
    if (dragDistance <= 0) {
        // Initial state of the indicator.
        size = START_SIZE
        strokeWidth = START_STROKE_WIDTH
        rotation = START_INITIAL_ROTATION
        vibrationInterval = TICK_INTERVAL_MAX_MS
        vibrationIntensity = TICK_INTENSITY_MIN
        offsetY = START_Y_OFFSET
    } else if (dragDistance < MAX_DRAG_DISTANCE) {
        // User is dragging between start and max drag distance.
        val percentDragInterpolated = interpolator.getInterpolation(dragDistance / MAX_DRAG_DISTANCE)
        val relativeInterval =
            ((1.0 - percentDragInterpolated) * (TICK_INTERVAL_MAX_MS - TICK_INTERVAL_MIN_MS)).toLong()
        val additionalSizeBasedOnDrag = Dp(percentDragInterpolated * (TARGET_SIZE.value - START_SIZE.value))
        val additionalStrokeWidthBasedOnDrag =
            Dp((percentDragInterpolated * (TARGET_STROKE_SIZE.value - START_STROKE_WIDTH.value)))
        val additionalRotation = percentDragInterpolated * MAX_ROTATION

        size = START_SIZE + additionalSizeBasedOnDrag
        strokeWidth = START_STROKE_WIDTH + additionalStrokeWidthBasedOnDrag
        rotation = additionalRotation + START_INITIAL_ROTATION
        offsetY = Dp(percentDragInterpolated * MAX_Y_OFFSET.value)

        vibrationInterval = TICK_INTERVAL_MIN_MS + relativeInterval
        vibrationIntensity = percentDragInterpolated * TICK_INTENSITY_MAX
    } else {
        // Max size, offset, and vibration intensity/frequency.
        size = TARGET_SIZE
        strokeWidth = TARGET_STROKE_SIZE
        rotation = START_INITIAL_ROTATION + MAX_ROTATION
        vibrationInterval = TICK_INTERVAL_MIN_MS
        vibrationIntensity = TICK_INTENSITY_MAX
        offsetY = MAX_Y_OFFSET
    }

    LaunchedEffect(Unit) {
        // We must continuously run this effect because we want vibration to occur even when the
        // view is not being drawn, which is the case if user stops dragging midway through
        // animation.
        while (true) {
            delay(vibrationInterval)
            if (isDragging) {
                vibrateTick(
                    vibrator = vibrator, isLowTickSupported = isLowTickSupported,
                    intensity = vibrationIntensity
                )
            }
        }
    }

    Screen(pageTitle = stringResource(R.string.resist_screen_title), messageToUser = messageForUser) {
        Column(
            Modifier
                .draggable(
                    onDragStopped = {
                        dragDistance = 0f
                    },
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        dragDistance += delta
                    }
                )
                .fillMaxWidth()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ResistIndicator(
                size = sizeAnimated, offsetY = offsetYAnimated, strokeWidth = strokeWidthAnimated,
                rotation = rotationAnimated, dragDistance = dragDistance
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ResistIndicator(size: Dp, offsetY: Dp, strokeWidth: Dp, rotation: Float, dragDistance: Float) {
    Box(
        // Want this box to be a few times larger than the size of indicator as it determines the
        // portion of the screen that user can start their drag from.
        modifier = Modifier
            .height(START_SIZE * 3)
    ) {

        Column(modifier = Modifier.align(Alignment.Center)) {
            CircularProgressIndicator(
                progress = 0.75f,
                modifier = Modifier
                    .padding(8.dp)
                    .size(size)
                    .offset(y = offsetY)
                    .rotate(rotation),
                color = MaterialTheme.colors.primaryVariant,
                strokeWidth = strokeWidth
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = dragDistance == 0f || dragDistance < DRAG_DISTANCE_BUFFER,
                enter = fadeIn(animationSpec = tween(delayMillis = TIME_TO_ANIMATE_BACK)),
                exit = fadeOut(),

            ) {
                Text(stringResource(R.string.resist_screen_drag_down), Modifier.offset(y = START_SIZE / 2))
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = offsetY)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = dragDistance >= MAX_DRAG_DISTANCE - DRAG_DISTANCE_BUFFER,
                enter = fadeIn(),
                exit = fadeOut()
            ) {

                // Indicator that max resistance has been reached.
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.secondary)
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.Center)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = dragDistance == 0f || dragDistance < DRAG_DISTANCE_BUFFER,
                enter = fadeIn(animationSpec = tween(delayMillis = TIME_TO_ANIMATE_BACK)),
                exit = fadeOut()
            ) {
                Icon(
                    Icons.Rounded.ArrowDownward,
                    null,
                    tint = MaterialTheme.colors.onPrimary,
                    modifier = Modifier
                        .offset(y = -(4.dp))
                        .align(Alignment.Center)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(top = 16.dp)
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = dragDistance >= MAX_DRAG_DISTANCE - DRAG_DISTANCE_BUFFER,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(stringResource(R.string.resist_screen_and_release))
            }
        }
    }
}

private fun vibrateTick(vibrator: Vibrator, isLowTickSupported: Boolean, intensity: Float) {
    // Composition primitives require Android R.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }

    // By default the preferred primitive for this experience is low tick instead of tick.
    var vibrationEffectToUse = VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    var vibrationIntensityToUse = intensity
    if (!isLowTickSupported) {
        // Use tick and cut intensity if low tick is not supported.
        vibrationEffectToUse = VibrationEffect.Composition.PRIMITIVE_TICK
        vibrationIntensityToUse *= .75f
    }

    vibrator.vibrate(
        VibrationEffect.startComposition()
            .addPrimitive(
                vibrationEffectToUse,
                vibrationIntensityToUse,
            )
            .compose()
    )
}

@Preview(showBackground = true)
@Composable
fun ResistScreenPreview() {
    HapticSamplerTheme {
        ResistScreen(
            isLowTickSupported = false,
            messageForUser = "A message to display to user."
        )
    }
}
