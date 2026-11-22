package com.doculens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doculens.app.BuildConfig
import com.doculens.app.domain.PdfQuality
import com.doculens.app.ui.components.ScreenHeader
import com.doculens.app.ui.theme.ThemeMode
import com.doculens.app.ui.viewmodel.AppViewModel

@Composable
fun SettingsScreen(vm: AppViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            ScreenHeader(
                title = "Ajustes",
                subtitle = "DocuLens ${BuildConfig.VERSION_NAME}",
                onBack = { vm.pop() }
            )
        }
        item {
            SettingsSection(title = "Apariencia") {
                ThemeRow(
                    icon = Icons.Rounded.SettingsBrightness,
                    title = "Seguir sistema",
                    selected = vm.themeMode == ThemeMode.SYSTEM,
                    onClick = { vm.setTheme(ThemeMode.SYSTEM) }
                )
                ThemeRow(
                    icon = Icons.Rounded.LightMode,
                    title = "Tema claro",
                    selected = vm.themeMode == ThemeMode.LIGHT,
                    onClick = { vm.setTheme(ThemeMode.LIGHT) }
                )
                ThemeRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Tema oscuro",
                    selected = vm.themeMode == ThemeMode.DARK,
                    onClick = { vm.setTheme(ThemeMode.DARK) }
                )
            }
        }
        item {
            SettingsSection(title = "Calidad de PDF predeterminada") {
                PdfQuality.entries.forEach { quality ->
                    SettingsRow(
                        icon = Icons.Rounded.PictureAsPdf,
                        title = quality.label,
                        subtitle = quality.description,
                        selected = vm.defaultQuality == quality,
                        onClick = { vm.updateDefaultQuality(quality) }
                    )
                }
            }
        }
        item {
            SettingsSection(title = "Privacidad") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("100% offline", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tus documentos se guardan en el almacenamiento interno del dispositivo. Sin anuncios, sin rastreo y sin subidas a la nube.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            SettingsSection(title = "Acerca de") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("DocuLens · Escáner de documentos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Cámara CameraX · ML Kit Document Scanner · OCR en el dispositivo · PDF nativo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun ThemeRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SettingsRow(icon = icon, title = title, selected = selected, onClick = onClick)
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
