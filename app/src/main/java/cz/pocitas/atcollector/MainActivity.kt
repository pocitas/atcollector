package cz.pocitas.atcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import cz.pocitas.atcollector.ui.AddSourceScreen
import cz.pocitas.atcollector.model.SourceConfig
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import cz.pocitas.atcollector.ui.theme.StatusAmberDark
import cz.pocitas.atcollector.ui.theme.StatusAmberLight
import cz.pocitas.atcollector.ui.theme.StatusGreenDark
import cz.pocitas.atcollector.ui.theme.StatusGreenLight
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import cz.pocitas.atcollector.source.SourceHealth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import cz.pocitas.atcollector.ui.theme.AtcollectorTheme
import kotlinx.coroutines.launch

/** Sections shown in the left pane menu. */
enum class Section(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    STATUS(R.string.section_status, R.drawable.ic_eyeglasses_2),
    SOURCES(R.string.section_sources, R.drawable.ic_exit_to_app),
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
        val bluetoothState by viewModel.bluetoothState.collectAsState()
        val close = { editorOpen = false; editingId = null }
        AddSourceScreen(
            initial = sources.firstOrNull { it.id == editingId },
            devices = devices,
            isScanning = isScanning,
            bluetoothState = bluetoothState,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onBlePermissionsGranted = viewModel::onBlePermissionsGranted,
            onBluetoothEnableResult = viewModel::onBluetoothEnableResult,
            onSave = { viewModel.save(it); close() },
            onCancel = close,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    val onAdd = { editingId = null; editorOpen = true }
    val onEdit = { id: String -> editingId = id; editorOpen = true }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWidePane = maxWidth >= WidePaneThreshold

        if (isWidePane) {
            WideLayout(
                viewModel = viewModel, onAdd = onAdd, onEdit = onEdit,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
                snackbarHostState = snackbarHostState,
            )
        } else {
            NarrowLayout(
                viewModel = viewModel, onAdd = onAdd, onEdit = onEdit,
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
                drawerState = drawerState,
                snackbarHostState = snackbarHostState,
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
    snackbarHostState: SnackbarHostState,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                CategoryMenu(selectedSection, onSectionSelected)
            }
        },
    ) {
        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
            MainContent(
                snackbarHostState = snackbarHostState,
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
    snackbarHostState: SnackbarHostState,
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
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_menu))
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(selectedSection.labelRes)) },
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
                                contentDescription = stringResource(R.string.open_menu),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            MainContent(
                snackbarHostState = snackbarHostState,
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
                icon = { Icon(painterResource(section.iconRes), contentDescription = null) },
                label = { Text(stringResource(section.labelRes)) },
                selected = section == selectedSection,
                onClick = { onSectionSelected(section) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
fun MainContent(
    snackbarHostState: SnackbarHostState,
    viewModel: SourcesViewModel,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    section: Section,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (section) {
        Section.STATUS -> StatusScreen(viewModel, modifier, contentPadding)
        Section.SOURCES -> SourcesScreen(viewModel, snackbarHostState, onAdd, onEdit, modifier, contentPadding)
    }
}

@Composable
private fun EmptyHint() {
    Text(
        text = stringResource(R.string.no_sources),
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
    val allSources by viewModel.sources.collectAsState()
    val sources = allSources.filter { it.enabled }
    val statuses by viewModel.statuses.collectAsState()
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = contentPadding,
    ) {
        if (sources.isEmpty()) {
            item { EmptyHint() }
        }
        items(sources, key = { it.id }) { source ->
            val status = statuses[source.id]
            TopAlignedListItem(
                leadingContent = {
                    Icon(painterResource(source.type.iconRes), contentDescription = stringResource(source.type.labelRes))
                },
                headlineContent = { Text(source.name) },
                supportingContent = { Text(status?.label(context) ?: stringResource(R.string.status_stopped)) },
                trailingContent = { HealthIcon(status?.health ?: SourceHealth.WARNING) },
            )
            HorizontalDivider()
        }
    }
}

/** Lists sources with enable switch, context menu and long-press drag reordering, plus a button to add a new one. */
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val sources by viewModel.sources.collectAsState()
    val scope = rememberCoroutineScope()
    val direction = LocalLayoutDirection.current
    val endInset = if (direction == LayoutDirection.Ltr) {
        contentPadding.calculateRightPadding(direction)
    } else {
        contentPadding.calculateLeftPadding(direction)
    }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    var shown by remember { mutableStateOf(sources) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(sources) { if (!dragging) shown = sources }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = shown.indexOfFirst { it.id == from.key }
        val toIndex = shown.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            shown = shown.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            contentPadding = contentPadding,
        ) {
            if (shown.isEmpty()) {
                item { EmptyHint() }
            }
            items(shown, key = { it.id }) { source ->
                val deletedMessage = stringResource(R.string.source_deleted, source.name)
                val undoLabel = stringResource(R.string.undo)
                val enableText = stringResource(R.string.enable_source_named, source.name)
                var menuOpen by remember { mutableStateOf(false) }
                val moveUpLabel = stringResource(R.string.move_up)
                val moveDownLabel = stringResource(R.string.move_down)
                ReorderableItem(reorderState, key = source.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "dragElevation")
                val scale by animateFloatAsState(if (isDragging) 1.1f else 1f, label = "dragScale")
                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .shadow(elevation)
                        .background(MaterialTheme.colorScheme.surface)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUpLabel) {
                                    moveSource(sources, source.id, -1)?.let(viewModel::reorder) != null
                                },
                                CustomAccessibilityAction(moveDownLabel) {
                                    moveSource(sources, source.id, 1)?.let(viewModel::reorder) != null
                                },
                            )
                        }
                        .longPressDraggableHandle(
                            onDragStarted = {
                                dragging = true
                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                dragging = false
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                viewModel.reorder(shown.map { it.id })
                            },
                        ),
                ) {
                    TopAlignedListItem(
                        contentAlpha = if (source.enabled) 1f else 0.38f,
                        verticalAlignment = Alignment.CenterVertically,
                        startContent = {
                            Switch(
                                checked = source.enabled,
                                onCheckedChange = { viewModel.save(source.withEnabled(it)) },
                                modifier = Modifier.semantics { contentDescription = enableText },
                            )
                        },
                        leadingContent = {
                            Icon(painterResource(source.type.iconRes), contentDescription = null)
                        },
                        headlineContent = { Text(source.name) },
                        supportingContent = { Text(stringResource(source.type.labelRes)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = stringResource(R.string.more_options_named, source.name),
                                        )
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_edit)) },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                onEdit(source.id)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_delete)) },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                val index = sources.indexOf(source)
                                                viewModel.delete(source.id)
                                                scope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = deletedMessage,
                                                        actionLabel = undoLabel,
                                                        withDismissAction = true,
                                                        duration = androidx.compose.material3.SnackbarDuration.Long,
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) viewModel.restore(source, index)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = endInset + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_source))
        }
    }
}
/** Returns the ids with [id] moved by [delta] positions, or null if it would leave the list. */
private fun moveSource(sources: List<SourceConfig>, id: String, delta: Int): List<String>? {
    val from = sources.indexOfFirst { it.id == id }
    val to = from + delta
    if (from < 0 || to !in sources.indices) return null
    return sources.map { it.id }.toMutableList().apply { add(to, removeAt(from)) }
}

@Composable
private fun HealthIcon(health: SourceHealth) {
    val (icon, label) = when (health) {
        SourceHealth.HEALTHY -> R.drawable.ic_check_circle to R.string.health_healthy
        SourceHealth.WARNING -> R.drawable.ic_warning to R.string.health_warning
        SourceHealth.ERROR -> R.drawable.ic_dangerous to R.string.health_error
    }
    val dark = isSystemInDarkTheme()
    val tint = when (health) {
        SourceHealth.HEALTHY -> if (dark) StatusGreenDark else StatusGreenLight
        SourceHealth.WARNING -> if (dark) StatusAmberDark else StatusAmberLight
        SourceHealth.ERROR -> MaterialTheme.colorScheme.error
    }
    Icon(painterResource(icon), contentDescription = stringResource(label), tint = tint)
}

/** Like [androidx.compose.material3.ListItem], but leading and trailing content stay at the top. */
@Composable
private fun TopAlignedListItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    leadingContent: @Composable () -> Unit,
    trailingContent: @Composable () -> Unit,
    contentAlpha: Float = 1f,
    startContent: @Composable () -> Unit = {},
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = verticalAlignment,
    ) {
        startContent()
        Box(Modifier.alpha(contentAlpha)) { leadingContent() }
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) { supportingContent() }
        }
        trailingContent()
    }
}
