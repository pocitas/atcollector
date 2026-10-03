package cz.pocitas.atcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cz.pocitas.atcollector.ui.theme.AtcollectorTheme
import kotlinx.coroutines.launch

/** Sections shown in the left pane menu. */
enum class Section(val label: String) {
    STATUS("Status"),
    CONNECTIONS("Connections"),
}

/** Placeholder data model for a single connection. */
data class ConnectionInfo(
    val id: String,
    val name: String,
    val status: String,
)

private val samplePlaceholderConnections = listOf(
    ConnectionInfo("1", "XC Guide", "Unknown"),
    ConnectionInfo("2", "SoftRF", "Connected")
)

/** Screen is wide enough to show the left pane permanently above this width. */
private val WidePaneThreshold = 600.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtcollectorTheme {
                AdaptiveApp()
            }
        }
    }
}

@Composable
fun AdaptiveApp() {
    var selectedSection by rememberSaveable { mutableStateOf(Section.STATUS) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWidePane = maxWidth >= WidePaneThreshold

        if (isWidePane) {
            WideLayout(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
            )
        } else {
            NarrowLayout(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
                drawerState = drawerState,
            )
        }
    }
}

/**
 * Wide screens (tablets, landscape): the categories menu is always visible.
 */
@Composable
private fun WideLayout(selectedSection: Section, onSectionSelected: (Section) -> Unit) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                CategoryMenu(selectedSection, onSectionSelected)
            }
        },
    ) {
        Scaffold { innerPadding ->
            MainContent(
                section = selectedSection,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = innerPadding,
            )
        }
    }
}

/**
 * Narrow screens (phones): the menu is hidden behind a hamburger icon.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NarrowLayout(
    selectedSection: Section,
    onSectionSelected: (Section) -> Unit,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        modifier = Modifier.fillMaxSize(),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(240.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = { scope.launch { drawerState.close() } },
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close menu")
                    }
                }
                CategoryMenu(
                    selectedSection = selectedSection,
                    onSectionSelected = {
                        onSectionSelected(it)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedSection.label) },
                    // The drawer has its own close button.
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open menu",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            MainContent(
                section = selectedSection,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
            )
        }
    }
}

/** List of sections */
@Composable
fun CategoryMenu(
    selectedSection: Section,
    onSectionSelected: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
        items(Section.entries) { section ->
            NavigationDrawerItem(
                label = { Text(section.label) },
                selected = section == selectedSection,
                onClick = { onSectionSelected(section) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun MainContent(
    section: Section,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (section) {
        Section.STATUS -> StatusScreen(modifier, contentPadding)
        Section.CONNECTIONS -> ConnectionsScreen(modifier, contentPadding)
    }
}

/** Shows the status of each known connection. */
@Composable
fun StatusScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        items(samplePlaceholderConnections) { connection ->
            ListItem(
                headlineContent = { Text(connection.name) },
                supportingContent = { Text(connection.status) },
            )
            HorizontalDivider()
        }
    }
}

/** Lists connections with edit/delete actions, plus a button to add a new one. */
@Composable
fun ConnectionsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = contentPadding,
        ) {
            items(samplePlaceholderConnections) { connection ->
                ListItem(
                    headlineContent = { Text(connection.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { /* TODO: edit connection */ }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${connection.name}")
                            }
                            IconButton(onClick = { /* TODO: delete connection */ }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${connection.name}")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(
            onClick = { /* TODO: add new connection */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add connection")
        }
    }
}

@Preview(name = "Phone", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
fun AdaptiveAppPhonePreview() {
    AtcollectorTheme { AdaptiveApp() }
}

@Preview(name = "Tablet", showBackground = true, widthDp = 1024, heightDp = 768)
@Composable
fun AdaptiveAppTabletPreview() {
    AtcollectorTheme { AdaptiveApp() }
}
