package com.example

import android.Manifest
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.ViewQuilt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.KawalSalesRepository
import com.example.data.model.DailyShift
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.KawalSalesViewModel
import com.example.ui.components.StatusPill
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoBg
import com.example.ui.screens.AdminCenterScreen
import com.example.ui.screens.GeotagMapScreen
import com.example.ui.screens.MonitoringScreen
import com.example.ui.screens.PlanogramScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.KawalSalesTheme
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.util.LocationTracker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KawalSalesTheme {
                AppNavigation()
            }
        }
    }
}

private enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    TASKS("Tugas", Icons.Filled.Checklist, Icons.Outlined.Checklist, "nav_tasks"),
    MAP_CSU("Peta CSU", Icons.Filled.Map, Icons.Outlined.Map, "nav_map"),
    PLANOGRAM("Planogram", Icons.Filled.ViewQuilt, Icons.Outlined.ViewQuilt, "nav_planogram"),
    MONITOR("Monitor", Icons.Filled.MonitorHeart, Icons.Outlined.MonitorHeart, "nav_monitor"),
    ADMIN("Kelola", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_admin");

    fun labelFor(role: UserRole): String = when (this) {
        MONITOR -> if (role == UserRole.SALES_LEAD) "Verifikasi" else if (role == UserRole.ASSISTANT) "Status" else "Monitor"
        ADMIN -> if (role == UserRole.ADMIN) "Kelola" else "Akun & Tim"
        else -> label
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            KawalSalesMainShell(currentUserId = user.uid)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KawalSalesMainShell(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: KawalSalesViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                } as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KawalSalesViewModel(
                    repository = KawalSalesRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val teamsState by viewModel.teamsState.collectAsStateWithLifecycle()
    val planogramsState by viewModel.planogramsState.collectAsStateWithLifecycle()
    val csuState by viewModel.csuOutletsState.collectAsStateWithLifecycle()
    val assistantsState by viewModel.salesAssistantsState.collectAsStateWithLifecycle()
    val shiftsState by viewModel.shiftsState.collectAsStateWithLifecycle()
    val visitsState by viewModel.visitReportsState.collectAsStateWithLifecycle()
    val isGpsStreaming by viewModel.isGpsStreaming.collectAsStateWithLifecycle()
    val bootstrapError by viewModel.bootstrapError.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.TASKS) }

    LaunchedEffect(Unit) {
        viewModel.feedback.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val userProfile = (profileState as? UiState.Success)?.data
    val teams = (teamsState as? UiState.Success)?.data.orEmpty()
    val activeTeam = remember(teams, userProfile) {
        teams.firstOrNull { it.id == userProfile?.activeTeamId } ?: teams.firstOrNull()
    }
    val isTeamAdmin = remember(activeTeam, currentUserId) {
        activeTeam != null && (currentUserId in activeTeam.adminUids || activeTeam.ownerUid == currentUserId)
    }
    val rawRole = UserRole.fromRaw(userProfile?.role)
    val effectiveRole = remember(isTeamAdmin, rawRole) {
        if (!isTeamAdmin && rawRole == UserRole.ADMIN) UserRole.ASSISTANT else rawRole
    }
    val canAdminEdit = isTeamAdmin && effectiveRole == UserRole.ADMIN
    val canReportVisit = effectiveRole == UserRole.ASSISTANT || canAdminEdit
    val canSignOff = effectiveRole == UserRole.SALES_LEAD || canAdminEdit

    val visibleTabs = remember(effectiveRole) {
        when (effectiveRole) {
            UserRole.ADMIN -> listOf(AppTab.ADMIN, AppTab.PLANOGRAM, AppTab.MAP_CSU, AppTab.MONITOR)
            UserRole.SALES_LEAD -> listOf(AppTab.MONITOR, AppTab.MAP_CSU, AppTab.PLANOGRAM, AppTab.ADMIN)
            UserRole.ASSISTANT -> listOf(AppTab.TASKS, AppTab.MAP_CSU, AppTab.PLANOGRAM, AppTab.MONITOR, AppTab.ADMIN)
        }
    }

    val homeTab = visibleTabs.first()
    BackHandler(enabled = currentTab != homeTab) {
        currentTab = homeTab
    }

    LaunchedEffect(visibleTabs) {
        if (currentTab !in visibleTabs) {
            currentTab = homeTab
        }
    }

    val planograms = (planogramsState as? UiState.Success)?.data.orEmpty()
    val latestPlanogram = remember(planograms) {
        planograms.firstOrNull { it.active } ?: planograms.firstOrNull()
    }
    val csuOutlets = (csuState as? UiState.Success)?.data.orEmpty()
    val salesAssistants = (assistantsState as? UiState.Success)?.data.orEmpty()
    val shifts = (shiftsState as? UiState.Success)?.data.orEmpty()
    val myActiveShift: DailyShift? = remember(shifts, currentUserId, effectiveRole) {
        if (effectiveRole == UserRole.ASSISTANT) {
            shifts.firstOrNull { it.assistantUid == currentUserId } ?: shifts.firstOrNull()
        } else {
            shifts.firstOrNull()
        }
    }
    val visitReports = (visitsState as? UiState.Success)?.data.orEmpty()

    var pendingStartGpsAfterPermission by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (granted) {
            if (pendingStartGpsAfterPermission) {
                viewModel.startLocationTracking(context, myActiveShift)
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Izin lokasi ditolak. Aktifkan di Pengaturan untuk pelacakan GPS.")
            }
        }
        pendingStartGpsAfterPermission = false
    }

    fun handleToggleGps() {
        if (isGpsStreaming) {
            viewModel.stopLocationTracking(myActiveShift)
        } else if (LocationTracker.hasLocationPermission(context)) {
            viewModel.startLocationTracking(context, myActiveShift)
        } else {
            pendingStartGpsAfterPermission = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val errorState = listOf(
        shiftsState, planogramsState, csuState, assistantsState, visitsState
    ).filterIsInstance<UiState.Error>().firstOrNull()

    val isLoadingInitial = shiftsState is UiState.Loading && myActiveShift == null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("KawalSales", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = activeTeam?.teamName ?: "Operasional Lapangan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        StatusPill(
                            text = when (effectiveRole) {
                                UserRole.ADMIN -> "Admin"
                                UserRole.SALES_LEAD -> "Salesman"
                                UserRole.ASSISTANT -> "Task Asistant"
                            },
                            containerColor = StatusInfoBg,
                            contentColor = StatusInfo,
                            modifier = Modifier.padding(end = if (isGpsStreaming) 6.dp else 12.dp)
                        )
                        if (isGpsStreaming) {
                            StatusPill(
                                text = "GPS ON",
                                containerColor = StatusSuccessBg,
                                contentColor = StatusSuccess,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        visibleTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val labelText = tab.labelFor(effectiveRole)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = labelText
                                    )
                                },
                                label = { Text(labelText) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(8.dp))
                        visibleTabs.forEach { tab ->
                            val selected = currentTab == tab
                            val labelText = tab.labelFor(effectiveRole)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = labelText
                                    )
                                },
                                label = { Text(labelText) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize()
                    ) {
                        when {
                            bootstrapError != null -> {
                                ErrorBannerBox(
                                    message = bootstrapError ?: "Gagal memuat ruang kerja",
                                    onRetry = { viewModel.bootstrapWorkspace() }
                                )
                            }
                            errorState != null && myActiveShift == null -> {
                                ErrorBannerBox(
                                    message = errorState.message,
                                    onRetry = { viewModel.bootstrapWorkspace() }
                                )
                            }
                            isLoadingInitial -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(10.dp))
                                        Text("Menghubungkan data operasional...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            else -> {
                                when (currentTab) {
                                    AppTab.TASKS -> TasksScreen(
                                        shift = myActiveShift,
                                        latestPlanogram = latestPlanogram,
                                        visitReports = visitReports,
                                        salesAssistants = salesAssistants,
                                        isGpsStreaming = isGpsStreaming,
                                        onToggleGps = ::handleToggleGps,
                                        onOpenPlanogramTab = { currentTab = AppTab.PLANOGRAM },
                                        onOpenMapTab = { currentTab = AppTab.MAP_CSU },
                                        onUpdateShiftFields = viewModel::updateShiftTask,
                                        onRequestSignOff = viewModel::requestSignOff,
                                        onSelectAssistant = viewModel::selectAssistantForShift,
                                        onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                    AppTab.MAP_CSU -> GeotagMapScreen(
                                        shift = myActiveShift,
                                        canReportVisit = canReportVisit,
                                        csuOutlets = csuOutlets,
                                        visitReports = visitReports,
                                        latestPlanogram = latestPlanogram,
                                        isGpsStreaming = isGpsStreaming,
                                        onCaptureSingleFix = { cb ->
                                            if (LocationTracker.hasLocationPermission(context)) {
                                                viewModel.captureSingleFix(context, cb)
                                            } else {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                                cb(null)
                                            }
                                        },
                                        onSubmitVisitReport = viewModel::submitVisitReport
                                    )
                                    AppTab.PLANOGRAM -> PlanogramScreen(
                                        team = activeTeam,
                                        canEditPlanogram = canAdminEdit,
                                        planograms = planograms,
                                        onPublishPlanogram = { team, title, ver, cycle, cat, instr, promo, img, active ->
                                            viewModel.publishPlanogram(
                                                team, title, ver, cycle, cat, instr, promo, img, active, planograms
                                            )
                                        },
                                        onActivatePlanogram = { p -> viewModel.activatePlanogram(p, planograms) },
                                        onDeletePlanogram = viewModel::deletePlanogram
                                    )
                                    AppTab.MONITOR -> MonitoringScreen(
                                        shifts = shifts,
                                        selectedShift = myActiveShift,
                                        isAdminView = canAdminEdit,
                                        canSignOff = canSignOff,
                                        csuOutlets = csuOutlets,
                                        visitReports = visitReports,
                                        onSubmitSignOff = viewModel::submitSalesSignOff,
                                        onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                    )
                                    AppTab.ADMIN -> AdminCenterScreen(
                                        team = activeTeam,
                                        isTeamAdmin = isTeamAdmin,
                                        effectiveRole = effectiveRole,
                                        userProfile = userProfile,
                                        activeShift = myActiveShift,
                                        csuOutlets = csuOutlets,
                                        salesAssistants = salesAssistants,
                                        onCaptureSingleFix = { cb ->
                                            if (LocationTracker.hasLocationPermission(context)) {
                                                viewModel.captureSingleFix(context, cb)
                                            } else {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                                cb(null)
                                            }
                                        },
                                        onAddCsuOutlet = viewModel::addCsuOutlet,
                                        onBulkImportCsu = viewModel::bulkImportCsuOutlets,
                                        onToggleCsuActive = viewModel::toggleCsuActive,
                                        onDeleteCsu = viewModel::deleteCsuOutlet,
                                        onAddAssistant = viewModel::addSalesAssistant,
                                        onBulkImportAssistants = viewModel::bulkImportSalesAssistants,
                                        onToggleAssistantActive = viewModel::toggleAssistantActive,
                                        onDeleteAssistant = viewModel::deleteSalesAssistant,
                                        onUpdateProfileSettings = viewModel::updateProfileSettings,
                                        onJoinTeam = viewModel::joinTeamByCode,
                                        onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                        onSignOut = {
                                            viewModel.stopLocationTracking(myActiveShift)
                                            signOut(credentialManager, scope)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBannerBox(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Terjadi Kendala Koneksi Data",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(onClick = onRetry) {
                    Text("Coba Lagi")
                }
            }
        }
    }
}
