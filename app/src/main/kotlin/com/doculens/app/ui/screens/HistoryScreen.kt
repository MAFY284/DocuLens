package com.doculens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doculens.app.data.model.ScannedDocument
import com.doculens.app.ui.components.DocumentCard
import com.doculens.app.ui.components.ScreenHeader
import com.doculens.app.ui.components.SearchField
import com.doculens.app.ui.viewmodel.AppViewModel

@Composable
fun HistoryScreen(vm: AppViewModel) {
    var deleteTarget by remember { mutableStateOf<ScannedDocument?>(null) }
    val filtered = remember(vm.documents, vm.historyQuery) {
        if (vm.historyQuery.isBlank()) vm.documents
        else vm.documents.filter { it.name.contains(vm.historyQuery, ignoreCase = true) }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = "Historial",
            subtitle = "${vm.documents.size} documento${if (vm.documents.size == 1) "" else "s"}",
            onBack = { vm.pop() }
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SearchField(
                value = vm.historyQuery,
                onValueChange = { vm.updateHistoryQuery(it) },
                placeholder = "Buscar por nombre"
            )
        }
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (vm.documents.isEmpty()) "Todavía no hay documentos" else "Sin resultados",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { doc ->
                    DocumentCard(
                        document = doc,
                        onClick = { vm.openDocument(doc.id) },
                        onShare = { vm.shareDocument(doc) },
                        onDelete = { deleteTarget = doc }
                    )
                }
            }
        }
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar documento") },
            text = { Text("Se eliminarán ${doc.pageCount} página(s) de este documento.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDocument(doc)
                    deleteTarget = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}
