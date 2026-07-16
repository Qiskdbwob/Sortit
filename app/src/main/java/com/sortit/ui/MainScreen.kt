package com.sortit.ui

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    templateVm: TemplateViewModel,
    monitorVm: MonitorViewModel,
    sortVm: SortRunViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Template", "Monitor", "Sortir")
    Scaffold { pad ->
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> TemplateScreen(templateVm)
                1 -> MonitorScreen(monitorVm)
                2 -> SortirScreen(sortVm, templateVm)
            }
        }
    }
}
