package com.nutomic.syncthingandroid.onboarding

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.isTelevision

private const val COMPACT_SCREEN_MAX_DP = 360
private val focusIndicatorStrokeWidth = 3.dp
private val focusIndicatorGap = 2.dp
private val focusIndicatorPadding = focusIndicatorStrokeWidth + focusIndicatorGap

/**
 * Icon type for the onboarding scaffold — either a Material vector icon or app logo.
 */
sealed interface OnboardingIcon {
    data class Vector(val imageVector: ImageVector) : OnboardingIcon
    object Logo : OnboardingIcon
}

/**
 * A reusable scaffold for each onboarding page.
 * Provides a consistent layout with icon, title, description, and optional action slot.
 */
@Composable
fun OnboardingScaffold(
    icon: OnboardingIcon,
    title: String,
    description: String,
    pageIndex: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    action: @Composable (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null,
    requestTvFocus: Boolean = false,
    canGoBack: Boolean = true,
    backVisible: Boolean = true,
    nextLabel: String,
    nextEnabled: Boolean = true,
    nextVisible: Boolean = true,
) {
    val config = LocalConfiguration.current
    val isTv = config.isTelevision
    val nextFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isTv, requestTvFocus, actionFocusRequester, nextFocusRequester, nextVisible, nextEnabled) {
        if (!isTv || !requestTvFocus) return@LaunchedEffect
        val focusRequester = actionFocusRequester ?: nextFocusRequester.takeIf { nextVisible && nextEnabled }
        withFrameNanos { }
        focusRequester?.requestFocus()
    }

    Scaffold { paddingValues ->
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            PortraitScaffoldContent(
                paddingValues = paddingValues,
                icon = icon,
                title = title,
                description = description,
                action = action,
                pageIndex = pageIndex,
                pageCount = pageCount,
                canGoBack = canGoBack,
                backVisible = backVisible,
                nextLabel = nextLabel,
                nextEnabled = nextEnabled,
                nextVisible = nextVisible,
                onBack = onBack,
                onNext = onNext,
                nextFocusRequester = nextFocusRequester,
            )
        } else {
            LandscapeScaffoldContent(
                paddingValues = paddingValues,
                icon = icon,
                title = title,
                description = description,
                action = action,
                pageIndex = pageIndex,
                pageCount = pageCount,
                canGoBack = canGoBack,
                backVisible = backVisible,
                nextLabel = nextLabel,
                nextEnabled = nextEnabled,
                nextVisible = nextVisible,
                onBack = onBack,
                onNext = onNext,
                nextFocusRequester = nextFocusRequester,
            )
        }
    }
}

@Composable
private fun PortraitScaffoldContent(
    icon: OnboardingIcon,
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
    pageIndex: Int,
    pageCount: Int,
    canGoBack: Boolean,
    backVisible: Boolean,
    nextLabel: String,
    nextEnabled: Boolean,
    nextVisible: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    paddingValues: PaddingValues,
    nextFocusRequester: FocusRequester,
) {
    val compactScreen = isCompactOnboardingScreen()
    val iconSize = if (compactScreen) 72.dp else 80.dp
    val titleStyle = if (compactScreen) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.headlineMedium
    }
    val descriptionStyle = if (compactScreen) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (icon) {
                is OnboardingIcon.Vector -> {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OnboardingIcon.Logo -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_monochrome_ui),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = description,
                style = descriptionStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (action != null) {
                Spacer(Modifier.height(32.dp))
                action()
            }
        }

        Spacer(Modifier.height(16.dp))

        OnboardingControls(
            pageIndex = pageIndex,
            pageCount = pageCount,
            canGoBack = canGoBack,
            backVisible = backVisible,
            nextLabel = nextLabel,
            nextEnabled = nextEnabled,
            nextVisible = nextVisible,
            onBack = onBack,
            onNext = onNext,
            nextFocusRequester = nextFocusRequester,
        )
    }
}

@Composable
private fun LandscapeScaffoldContent(
    icon: OnboardingIcon,
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
    pageIndex: Int,
    pageCount: Int,
    canGoBack: Boolean,
    backVisible: Boolean,
    nextLabel: String,
    nextEnabled: Boolean,
    nextVisible: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    paddingValues: PaddingValues,
    nextFocusRequester: FocusRequester,
) {
    val compactScreen = isCompactOnboardingScreen()
    val iconSize = if (compactScreen) 64.dp else 80.dp
    val titleStyle = if (compactScreen) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.headlineMedium
    }
    val descriptionStyle = if (compactScreen) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    val contentControlsSpacing = if (compactScreen) 12.dp else 16.dp

    Row(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(2f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (icon) {
                is OnboardingIcon.Vector -> {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OnboardingIcon.Logo -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_monochrome_ui),
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (action != null) {
                Spacer(Modifier.height(32.dp))
                action()
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(3f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = description,
                    style = descriptionStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(contentControlsSpacing))

            OnboardingControls(
                pageIndex = pageIndex,
                pageCount = pageCount,
                canGoBack = canGoBack,
                backVisible = backVisible,
                nextLabel = nextLabel,
                nextEnabled = nextEnabled,
                nextVisible = nextVisible,
                onBack = onBack,
                onNext = onNext,
                nextFocusRequester = nextFocusRequester,
            )
        }
    }
}

@Composable
private fun OnboardingControls(
    pageIndex: Int,
    pageCount: Int,
    canGoBack: Boolean,
    backVisible: Boolean,
    nextLabel: String,
    nextEnabled: Boolean,
    nextVisible: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextFocusRequester: FocusRequester,
) {
    val compactScreen = isCompactOnboardingScreen()
    val controlHeight = if (compactScreen) 48.dp else 56.dp
    val rowHorizontalPadding = if (compactScreen) 8.dp else 16.dp
    val rowSpacing = if (compactScreen) 8.dp else 12.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageIndicator(
            pageIndex = pageIndex,
            pageCount = pageCount,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = rowHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            if (backVisible) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(controlHeight),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (nextVisible) {
                    NextButton(
                        label = nextLabel,
                        enabled = nextEnabled,
                        onClick = onNext,
                        focusRequester = nextFocusRequester,
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .height(controlHeight)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageIndex: Int,
    pageCount: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == pageIndex
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 8.dp)
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun NextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val compactScreen = isCompactOnboardingScreen()
    val buttonHeight = if (compactScreen) 48.dp else 56.dp
    val minWidth = if (compactScreen) 200.dp else 240.dp
    val labelFontSize = if (compactScreen) 18.sp else 20.sp
    val buttonShape = RoundedCornerShape(buttonHeight / 2)
    val focusIndicatorShape = RoundedCornerShape(buttonHeight / 2 + focusIndicatorPadding)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val containerColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 350),
        label = "next_button_container_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 350),
        label = "next_button_content_color",
    )

    FocusIndicatorBox(
        focused = focused && enabled,
        shape = focusIndicatorShape,
        modifier = Modifier
            .widthIn(min = minWidth, max = 560.dp)
            .fillMaxWidth(),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
            interactionSource = interactionSource,
            modifier = Modifier
                .height(buttonHeight)
                .fillMaxWidth()
                .focusRequester(focusRequester),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = labelFontSize),
            )
        }
    }
}

@Composable
fun PermissionButton(
    granted: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester = FocusRequester(),
) {
    val compactScreen = isCompactOnboardingScreen()
    val buttonHeight = if (compactScreen) 48.dp else 56.dp
    val iconSize = if (compactScreen) 22.dp else 24.dp
    val iconTextSpacing = if (compactScreen) 10.dp else 12.dp
    val labelFontSize = if (compactScreen) 18.sp else 20.sp
    val horizontalPadding = if (compactScreen) 18.dp else 24.dp
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    val containerColor by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 350),
        label = "permission_button_container_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = tween(durationMillis = 350),
        label = "permission_button_content_color",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (granted) {
            if (compactScreen) 12.dp else 14.dp
        } else {
            if (compactScreen) 24.dp else 28.dp
        },
        animationSpec = tween(durationMillis = 350),
        label = "permission_button_corner_radius",
    )
    val buttonShape = RoundedCornerShape(cornerRadius)
    val focusIndicatorShape = RoundedCornerShape(cornerRadius + focusIndicatorPadding)

    FocusIndicatorBox(
        focused = focused && !granted,
        shape = focusIndicatorShape,
    ) {
        Button(
            onClick = onClick,
            enabled = !granted,
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor,
                disabledContentColor = contentColor,
            ),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            interactionSource = interactionSource,
            modifier = Modifier
                .height(buttonHeight)
                .focusRequester(focusRequester),
        ) {
            AnimatedContent(
                targetState = granted,
                label = "permission_button_state",
            ) { isGranted ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(Modifier.width(iconTextSpacing))
                    Text(
                        text = if (isGranted) {
                            stringResource(R.string.permission_granted)
                        } else {
                            stringResource(R.string.grant_permission)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = labelFontSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusIndicatorBox(
    focused: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(
                width = focusIndicatorStrokeWidth,
                color = if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = shape,
            )
            .padding(focusIndicatorPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// TODO: use window size class when material3.adaptive package is added
@Composable
private fun isCompactOnboardingScreen(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    val compactScreenMaxSize = COMPACT_SCREEN_MAX_DP.dp
    return with(LocalDensity.current) {
        containerSize.width.toDp() <= compactScreenMaxSize ||
            containerSize.height.toDp() <= compactScreenMaxSize
    }
}
