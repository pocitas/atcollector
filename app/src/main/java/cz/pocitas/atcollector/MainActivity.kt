package cz.pocitas.atcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import cz.pocitas.atcollector.ui.AddSourceScreen
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
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import cz.pocitas.atcollector.ui.theme.AtcollectorTheme
import kotlinx.coroutines.launch

/** Sections shown in the left pane menu. */
enum class Section(val label: String) {
    STATUS("Status"),
    SOURCES("Sources"),
}

/** Screen is wide enough to show the left pane permanently above this width. */
private val WidePaneThreshold = 600.dp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: SourcesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AtcollectorTheme {
                AdaptiveApp(viewModel)
            }
        }
    }
}

@Composable
fun AdaptiveApp(viewModel: SourcesViewModel) {
    var selectedSection by rememberSaveable { mutableStateOf(Section.STATUS) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val sources by viewModel.sources.collectAsState()

    if (editorOpen) {
        val devices by viewModel.devices.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()
        val close = { editorOpen = false; editingId = null }
        AddSourceScreen(
            initial = sources.firstOrNull { it.id == editingId },
            devices = devices,
            isScanning = isScanning,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onBlePermissionsGranted = viewModel::onBlePermissionsGranted,
            onSave = { viewModel.save(it); close() },
            onCancel = close,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val onAdd = { editingId = null; editorOpen = true }
    val onEdit = { id: String -> editingId = id; editorOpen = true }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWidePane = maxWidth >= WidePaneThreshold

        if (isWidePane) {
            WideLayout(
                viewModel = viewModel, onAdd = onAdd, onEdit = onEdit,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
            )
        } else {
            NarrowLayout(
                viewModel = viewModel, onAdd = onAdd, onEdit = onEdit,
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
private fun WideLayout(
    viewModel: SourcesViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    selectedSection: Section,
    onSectionSelected: (Section) -> Unit,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                CategoryMenu(selectedSection, onSectionSelected)
            }
        },
    ) {
        Scaffold { innerPadding ->
            MainContent(
                viewModel = viewModel,
                onAdd = onAdd,
                onEdit = onEdit,
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
    viewModel: SourcesViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
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
                viewModel = viewModel,
                onAdd = onAdd,
                onEdit = onEdit,
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
    viewModel: SourcesViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    section: Section,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (section) {
        Section.STATUS -> StatusScreen(viewModel, modifier, contentPadding)
        Section.SOURCES -> SourcesScreen(viewModel, onAdd, onEdit, modifier, contentPadding)
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = "No sources yet. Add one in the Sources section.",
        modifier = Modifier.padding(16.dp),
    )
}

/** Shows the status of each configured source. */
@Composable
fun StatusScreen(
    viewModel: SourcesViewModel,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val sources by viewModel.sources.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        if (sources.isEmpty()) {
            item { EmptyHint() }
        }
        items(sources, key = { it.id }) { source ->
            ListItem(
                headlineContent = { Text(source.name) },
                supportingContent = { Text(statuses[source.id]?.label ?: "Stopped") },
            )
            HorizontalDivider()
        }
    }
}

/** Lists sources with edit/delete actions, plus a button to add a new one. */
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val sources by viewModel.sources.collectAsState()
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = contentPadding,
        ) {
            if (sources.isEmpty()) {
                item { EmptyHint() }
            }
            items(sources, key = { it.id }) { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = { Text(source.type.label) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEdit(source.id) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit ${source.name}")
                            }
                            IconButton(onClick = { viewModel.delete(source.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${source.name}")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add source")
        }
    }
}
