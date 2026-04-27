package com.nutomic.syncthingandroid.onboarding

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nutomic.syncthingandroid.R

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
    action: @Composable (() -> Unit)? = null,
    next: @Composable () -> Unit,
) {
    val config = LocalConfiguration.current

    Scaffold { paddingValues ->
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            PortraitScaffoldContent(
                paddingValues = paddingValues,
                icon = icon,
                title = title,
                description = description,
                action = action,
                next = next,
            )
        } else {
            LandscapeScaffoldContent(
                paddingValues = paddingValues,
                icon = icon,
                title = title,
                description = description,
                action = action,
                next = next,
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
    next: @Composable () -> Unit,
    paddingValues: PaddingValues,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (icon) {
                is OnboardingIcon.Vector -> {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OnboardingIcon.Logo -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_monochrome_ui),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (action != null) {
                Spacer(Modifier.height(32.dp))
                action()
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            next()
        }
    }
}

@Composable
private fun LandscapeScaffoldContent(
    icon: OnboardingIcon,
    title: String,
    description: String,
    action: @Composable (() -> Unit)? = null,
    next: @Composable () -> Unit,
    paddingValues: PaddingValues,
) {
    Row(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (icon) {
                is OnboardingIcon.Vector -> {
                    Icon(
                        imageVector = icon.imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                OnboardingIcon.Logo -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_monochrome_ui),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (action != null) {
                    Spacer(Modifier.height(32.dp))
                    action()
                }
            }
            Box(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                next()
            }
        }
    }
}

@Composable
fun NextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
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

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier = Modifier.height(56.dp)
            .widthIn(min = 240.dp, max = 560.dp)
            .fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp),
        )
    }
}

@Composable
fun PermissionButton(
    granted: Boolean,
    onClick: () -> Unit,
) {
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
        targetValue = if (granted) 14.dp else 28.dp,
        animationSpec = tween(durationMillis = 350),
        label = "permission_button_corner_radius",
    )

    Button(
        onClick = onClick,
        enabled = !granted,
        shape = RoundedCornerShape(cornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier = Modifier.height(56.dp),
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
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isGranted) {
                        stringResource(R.string.permission_granted)
                    } else {
                        stringResource(R.string.grant_permission)
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp),
                )
            }
        }
    }
}
