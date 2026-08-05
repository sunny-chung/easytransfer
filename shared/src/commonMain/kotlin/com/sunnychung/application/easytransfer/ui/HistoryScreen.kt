package com.sunnychung.application.easytransfer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunnychung.application.easytransfer.ui.model.HistoryItemUi
import com.sunnychung.application.easytransfer.ui.model.PreviewData
import com.sunnychung.application.easytransfer.ui.model.TransferStatus

@Preview(
    name = "History screen",
    widthDp = 800,
    heightDp = 1_000,
)
@Composable
internal fun HistoryScreen(
    historyItems: List<HistoryItemUi> = PreviewData.historyItems,
    showPageTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    val visibleItems = historyItems.filter { item ->
        val matchesSearch = searchQuery.isBlank() || listOf(item.title, item.detail, item.sourceLabel)
            .any { it.contains(searchQuery, ignoreCase = true) }
        val matchesFilter = when (selectedFilter) {
            "Received" -> item.status == TransferStatus.Received
            "Sent" -> item.status == TransferStatus.Sent
            else -> true
        }
        matchesSearch && matchesFilter
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (showPageTitle) 40.dp else 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (showPageTitle) {
            PageHeading(
                title = "History",
                subtitle = "Everything you send or receive stays easy to find.",
            )
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search transfers") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Received", "Sent").forEach { label ->
                FilterChip(
                    selected = selectedFilter == label,
                    onClick = { selectedFilter = label },
                    label = { Text(label) },
                )
            }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Recent",
                    modifier = Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(visibleItems) { item ->
                HistoryRow(item = item)
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = "Received items are added here immediately, even if you choose an action later.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
