package com.meshwalkie.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshwalkie.core.AppTheme

/**
 * Theme-aware replacements for stock M3 Button/OutlinedButton/TextButton.
 * Stock M3 controls hardcode CornerFull (pill) shape and ignore
 * MaterialTheme.shapes, so the three light themes looked identical in
 * structure even with different color schemes. These read [LocalAppTheme]
 * directly instead, since shape/border/padding differ per theme rather than
 * just color.
 */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.FIELD }

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    when (LocalAppTheme.current) {
        // Solid ink chip, paper text - the jpg's selected SPATIAL segment. Keeps
        // primary actions and selected chips distinct from the flat gray blocks
        // of AppOutlinedButton/AppTextButton.
        AppTheme.FIELD -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RoundedCornerShape(3.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
            content = content
        )
        // Inverse black tile (the webp's DYN OFF), hard corners - distinct from
        // the white-framed boxes of AppOutlinedButton.
        AppTheme.CORRUPTION -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RectangleShape,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
            content = content
        )
        AppTheme.RADIO -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RoundedCornerShape(50), content = content
        )
        AppTheme.DARK, AppTheme.NIGHT -> Button(
            onClick = onClick, modifier = modifier, enabled = enabled, content = content
        )
    }
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    when (LocalAppTheme.current) {
        // Same flat gray fill as AppButton - no outline in the jpg's control row.
        AppTheme.FIELD -> OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RoundedCornerShape(3.dp),
            border = null,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            content = content
        )
        // Same white-fill, thick-black-frame box as AppButton.
        AppTheme.CORRUPTION -> OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RectangleShape,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            content = content
        )
        AppTheme.RADIO -> OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            content = content
        )
        AppTheme.DARK, AppTheme.NIGHT -> OutlinedButton(
            onClick = onClick, modifier = modifier, enabled = enabled, content = content
        )
    }
}

/** The workhorse chip: menus, quick actions, dialog inline buttons. */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    when (LocalAppTheme.current) {
        // Same flat gray block as AppButton, compact chip padding.
        AppTheme.FIELD -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 32.dp),
            shape = RoundedCornerShape(3.dp),
            border = null,
            contentPadding = compactPadding,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            content = content
        )
        // White fill, thinner 1.5dp black border, compact chip padding.
        AppTheme.CORRUPTION -> OutlinedButton(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 32.dp),
            shape = RectangleShape,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            contentPadding = compactPadding,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            content = content
        )
        AppTheme.RADIO -> Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 32.dp),
            shape = RoundedCornerShape(50),
            contentPadding = compactPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            content = content
        )
        AppTheme.DARK, AppTheme.NIGHT -> TextButton(onClick = onClick, modifier = modifier, content = content)
    }
}

/** Section title for SettingsScreen groups; rule style differs per theme. */
@Composable
fun SectionHeader(title: String) {
    when (LocalAppTheme.current) {
        AppTheme.FIELD -> Column {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        }
        AppTheme.CORRUPTION -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.sp)
            )
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        AppTheme.RADIO -> Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        AppTheme.DARK, AppTheme.NIGHT -> Text(title, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppDivider() {
    when (LocalAppTheme.current) {
        AppTheme.FIELD -> HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        AppTheme.CORRUPTION -> HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
        AppTheme.RADIO -> HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        AppTheme.DARK, AppTheme.NIGHT -> HorizontalDivider()
    }
}

/**
 * Theme-aware Switch. Stock M3 Switch is a rounded pill that clashes with the
 * hard-edged paper themes, so FIELD/CORRUPTION draw their own block track and
 * square-ish thumb (the reference UIs' LIM slider / M-S selector); the other
 * themes keep the stock control.
 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    when (LocalAppTheme.current) {
        AppTheme.FIELD -> PaperSwitch(
            checked, onCheckedChange,
            shape = RoundedCornerShape(3.dp), borderWidth = 0.dp
        )
        AppTheme.CORRUPTION -> PaperSwitch(
            checked, onCheckedChange,
            shape = RectangleShape, borderWidth = 2.dp
        )
        AppTheme.RADIO, AppTheme.DARK, AppTheme.NIGHT ->
            Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Block-style switch: on = ink track/paper thumb, off = paper track/ink thumb. */
@Composable
private fun PaperSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    borderWidth: androidx.compose.ui.unit.Dp
) {
    val ink = MaterialTheme.colorScheme.primary
    val paper = if (borderWidth > 0.dp) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.surfaceVariant
    val track = if (checked) ink else paper
    val thumb = if (checked) MaterialTheme.colorScheme.onPrimary else ink
    val thumbOffset by animateDpAsState(if (checked) 26.dp else 4.dp, label = "thumbOffset")
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .background(track, shape)
            .then(
                if (borderWidth > 0.dp)
                    Modifier.border(borderWidth, MaterialTheme.colorScheme.outline, shape)
                else Modifier
            )
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(22.dp)
                .background(thumb, shape)
        )
    }
}
