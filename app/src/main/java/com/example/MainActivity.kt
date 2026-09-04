package com.example

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: SensorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = uiState.appThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LocalAppColors.current.bg
                ) {
                    SensorsOffSleekApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }
}

enum class SleekTab {
    HOME, LOGS, ABOUT
}

@Composable
fun SensorsOffSleekApp(viewModel: SensorViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(SleekTab.HOME) }
    val colors = LocalAppColors.current

    LaunchedEffect(Unit) {
        if (uiState.isShizukuRunning && !uiState.isShizukuAuthorized) {
            viewModel.requestShizukuPermission()
        }
    }

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            SleekNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Enterprise Header
            SleekHeader(
                onRefresh = { viewModel.refreshState() }
            )

            // Body based on selected tab
            when (selectedTab) {
                SleekTab.HOME -> SleekHomeTabContent(viewModel = viewModel, uiState = uiState)
                SleekTab.LOGS -> SleekLogsTabContent(viewModel = viewModel, uiState = uiState)
                SleekTab.ABOUT -> SleekAboutTabContent(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SleekHeader(onRefresh: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accentCyan)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "QUANTUM SENSOR PRIVACY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentCyan,
                    letterSpacing = 1.6.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "SensorsOff",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                letterSpacing = (-0.5).sp
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cardBg)
                .border(1.dp, if (colors.isDark) colors.glowColor else colors.border, RoundedCornerShape(14.dp))
                .clickable { onRefresh() }
                .testTag("refresh_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = colors.accentCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SleekHomeTabContent(
    viewModel: SensorViewModel,
    uiState: SensorUiState
) {
    val colors = LocalAppColors.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Master Toggle Card
        item {
            SleekMasterToggleCard(
                isSensorsOff = uiState.isSensorsOff,
                onToggle = { viewModel.toggleSensorsOff() }
            )
        }

        // Status Grid (Shizuku & Root)
        item {
            SleekStatusGrid(
                uiState = uiState,
                onRequestShizuku = { viewModel.requestShizukuPermission() },
                onLog = { viewModel.addLog(it) }
            )
        }

        // Visual Theme Selection Card
        item {
            SleekThemeSelectionCard(
                currentTheme = uiState.appThemeMode,
                onSelectTheme = { viewModel.updateAppThemeMode(it) }
            )
        }

        // Quick Settings Tile Preferences Card
        item {
            SleekTileCustomizationCard(
                tileSettings = uiState.tileSettings,
                onSaveTileSettings = { style, label, activeSub, disabledSub, mode ->
                    viewModel.updateTileSettings(style, label, activeSub, disabledSub, mode)
                },
                onImportCustomIcon = { uri ->
                    viewModel.importCustomTileIconUri(uri)
                }
            )
        }

        // Quick Settings Tile Setup Guide
        item {
            SleekQuickTileTipCard(
                uiState = uiState,
                onInjectTile = { isNative, onResult ->
                    viewModel.injectTileToQuickSettings(isNative, onResult)
                }
            )
        }

        // Background Keep-Alive Daemon Card
        item {
            SleekBackgroundKeepAliveCard(
                isKeepAliveEnabled = uiState.isKeepAliveEnabled,
                onToggleKeepAlive = { enabled ->
                    viewModel.setKeepAliveEnabled(enabled)
                }
            )
        }

        if (uiState.showExperimentalToggles) {
            // Monitored Sensors List Header (Experimental Mode)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MONITORED HARDWARE SENSORS (EXPERIMENTAL)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.2.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accentAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Manual Mode",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentAmber
                        )
                    }
                }
            }

            items(uiState.sensorList, key = { it.id }) { sensor ->
                SleekSensorRow(
                    sensor = sensor,
                    showSwitch = true,
                    onToggleSensor = { sensorId ->
                        viewModel.toggleIndividualSensor(sensorId)
                    }
                )
            }
        } else {
            // Sleek Unified Hardware Telemetry Card (Toggles removed by default)
            item {
                SleekSensorsStatusCard(
                    sensorList = uiState.sensorList,
                    isSensorsOff = uiState.isSensorsOff
                )
            }
        }
    }
}

@Composable
fun SleekMasterToggleCard(
    isSensorsOff: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalAppColors.current
    val buttonBgColor by animateColorAsState(
        targetValue = if (isSensorsOff) colors.accentRose else colors.accentCyan,
        animationSpec = tween(350),
        label = "btnColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_toggle_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (colors.isDark) {
                if (isSensorsOff) colors.accentRose.copy(alpha = 0.45f) else colors.glowColor
            } else colors.border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(156.dp)
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                // Quantum Outer Halo
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .clip(CircleShape)
                        .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.08f) else colors.accentCyan.copy(alpha = 0.08f))
                )
                // Quantum Middle Glow
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .clip(CircleShape)
                        .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.18f) else colors.accentCyan.copy(alpha = 0.18f))
                        .border(
                            1.5.dp,
                            if (isSensorsOff) colors.accentRose.copy(alpha = 0.35f) else colors.accentCyan.copy(alpha = 0.35f),
                            CircleShape
                        )
                )
                // Quantum Core Trigger
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .clickable { onToggle() }
                        .testTag("toggle_sensors_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSensorsOff) Icons.Default.Shield else Icons.Default.Sensors,
                        contentDescription = "Toggle Sensors",
                        modifier = Modifier.size(40.dp),
                        tint = if (isSensorsOff) Color.White else CyberVoid
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Futuristic Status Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.15f) else colors.accentGreen.copy(alpha = 0.15f))
                    .border(
                        1.dp,
                        if (isSensorsOff) colors.accentRose.copy(alpha = 0.3f) else colors.accentGreen.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isSensorsOff) "[ SENSOR MATRIX BLOCKED ]" else "[ HARDWARE ONLINE ]",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = if (isSensorsOff) colors.accentRose else colors.accentGreen,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSensorsOff) "Sensors Disabled" else "Sensors Enabled",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isSensorsOff)
                    "All device camera, microphone, and motion sensors are privacy blocked."
                else
                    "All device sensors are currently active and available.",
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SleekStatusGrid(
    uiState: SensorUiState,
    onRequestShizuku: () -> Unit,
    onLog: (String) -> Unit
) {
    val colors = LocalAppColors.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Shizuku
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardBg)
                    .border(1.dp, if (colors.isDark) colors.glowColor else colors.border, RoundedCornerShape(18.dp))
                    .clickable { onRequestShizuku() }
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "SHIZUKU PRIVILEGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCyan,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isShizukuAuthorized) colors.accentGreen else if (uiState.isShizukuRunning) colors.accentAmber else colors.textMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isShizukuAuthorized) "Authorized" else if (uiState.isShizukuRunning) "Needs Auth" else "Inactive",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            // Card 2: Root / Permissions
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardBg)
                    .border(1.dp, if (colors.isDark) colors.glowColor else colors.border, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "SYSTEM ENGINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCyan,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isRootAvailable || uiState.hasSecureSettingsPermission) colors.accentGreen else colors.accentAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isRootAvailable) "Root SU Ready" else if (uiState.hasSecureSettingsPermission) "ADB Granted" else "Standard",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SleekThemeSelectionCard(
    currentTheme: String,
    onSelectTheme: (String) -> Unit
) {
    val colors = LocalAppColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_selection_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) colors.glowColor else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.softBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "Theme",
                        tint = colors.accentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Visual Aesthetic",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Quantum dark, cyber titanium, or dynamic monet",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "dark" to "Cyber Dark",
                    "light" to "Titanium",
                    "system" to "System",
                    "dynamic" to "Monet"
                ).forEach { (modeKey, modeLabel) ->
                    FilterChip(
                        selected = currentTheme == modeKey,
                        onClick = { onSelectTheme(modeKey) },
                        label = { Text(modeLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentCyan,
                            selectedLabelColor = CyberVoid,
                            containerColor = colors.softBg,
                            labelColor = colors.textSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SleekTileCustomizationCard(
    tileSettings: TileSettingsState,
    onSaveTileSettings: (String, String, String, String, String) -> Unit,
    onImportCustomIcon: (android.net.Uri) -> Unit
) {
    val colors = LocalAppColors.current
    var iconStyle by remember(tileSettings.iconStyle) { mutableStateOf(tileSettings.iconStyle) }
    var customLabel by remember(tileSettings.customLabel) { mutableStateOf(tileSettings.customLabel) }
    var activeSubtitle by remember(tileSettings.activeSubtitle) { mutableStateOf(tileSettings.activeSubtitle) }
    var disabledSubtitle by remember(tileSettings.disabledSubtitle) { mutableStateOf(tileSettings.disabledSubtitle) }
    var blockMode by remember(tileSettings.blockMode) { mutableStateOf(tileSettings.blockMode) }
    var isExpanded by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportCustomIcon(it) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tile_customization_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.softBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure Tile",
                            tint = colors.accentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Quick Settings Tile Customization",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Icons, subtitles, custom PNGs & action scope",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.textMuted
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "TILE ICON STYLE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "aosp" to "Official AOSP",
                                "stock" to "Telemetry Wave",
                                "shield" to "Shield"
                            ).forEach { (styleKey, styleName) ->
                                FilterChip(
                                    selected = iconStyle == styleKey,
                                    onClick = { iconStyle = styleKey },
                                    label = { Text(styleName, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "camera_off" to "Cam Off",
                                "mic_off" to "Mic Off",
                                "motion_off" to "Motion Off"
                            ).forEach { (styleKey, styleName) ->
                                FilterChip(
                                    selected = iconStyle == styleKey,
                                    onClick = { iconStyle = styleKey },
                                    label = { Text(styleName, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Custom PNG Icon Upload Button
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "Import PNG", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (tileSettings.customIconPath != null) "Change Custom PNG Icon" else "Import Custom PNG Tile Icon",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "TILE ACTION SCOPE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = blockMode == "global",
                            onClick = { blockMode = "global" },
                            label = { Text("Global (All Sensors)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentBlue,
                                selectedLabelColor = Color.White
                            )
                        )

                        FilterChip(
                            selected = blockMode == "cam_mic",
                            onClick = { blockMode = "cam_mic" },
                            label = { Text("Camera + Mic Only", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    Text(
                        text = "CUSTOM LABELS & SUBTITLES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Tile Primary Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = activeSubtitle,
                        onValueChange = { activeSubtitle = it },
                        label = { Text("Active Subtitle (Sensors Disabled)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = disabledSubtitle,
                        onValueChange = { disabledSubtitle = it },
                        label = { Text("Disabled Subtitle (Sensors Enabled)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            onSaveTileSettings(iconStyle, customLabel, activeSubtitle, disabledSubtitle, blockMode)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Tile Configuration", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SleekQuickTileTipCard(
    uiState: SensorUiState,
    onInjectTile: (Boolean, (Boolean, String) -> Unit) -> Unit
) {
    val colors = LocalAppColors.current
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isInjecting by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qs_tile_guide_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Tip",
                        tint = colors.accentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Settings Tile & Quick Inject",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "1-Click tile injection via Shizuku or manual drag setup",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = colors.textMuted
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1-Click Tile Injections
                    Text(
                        text = "1-CLICK TILE INJECTION (SHIZUKU / SYSTEM):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCyan,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isInjecting = true
                                onInjectTile(false) { success, message ->
                                    isInjecting = false
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isInjecting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "App Tile",
                                modifier = Modifier.size(16.dp),
                                tint = CyberVoid
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add App Tile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberVoid
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                isInjecting = true
                                onInjectTile(true) { success, message ->
                                    isInjecting = false
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            enabled = !isInjecting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Native AOSP",
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add AOSP Tile",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGreen
                            )
                        }
                    }

                    Text(
                        text = "• 'Add App Tile' configures our customizable tile.\n• 'Add AOSP Tile' injects the built-in system tile directly without leaving Developer Options ON (banking app safe).",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = colors.border)

                    Text(
                        text = "Manual Drag Setup:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    SleekStepRow(step = "1", text = "Swipe down twice from top status bar to open Quick Settings.")
                    SleekStepRow(step = "2", text = "Tap the Edit (Pencil) button at the bottom.")
                    SleekStepRow(step = "3", text = "Find 'Sensors Off' in Available Tiles and drag to your active tiles.")
                    SleekStepRow(step = "4", text = "Tap the tile anytime to toggle hardware sensor privacy.")

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = colors.border)

                    Text(
                        text = "ADB Permission Setup (Optional for Non-Root / Non-Shizuku):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.softBg)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.adbGrantCommand,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = colors.accentCyan
                        )
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.adbGrantCommand))
                            Toast.makeText(context, "ADB command copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Copy ADB Grant Command", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SleekStepRow(step: String, text: String) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(colors.accentBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentBlue
            )
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SleekSensorsStatusCard(
    sensorList: List<SensorItem>,
    isSensorsOff: Boolean
) {
    val colors = LocalAppColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sensors_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) colors.glowColor else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.15f) else colors.accentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSensorsOff) Icons.Default.Shield else Icons.Default.Sensors,
                            contentDescription = "Sensors Status",
                            tint = if (isSensorsOff) colors.accentRose else colors.accentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "DEVICE HARDWARE SENSORS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (isSensorsOff) "Unified Hardware Privacy Active" else "All Hardware Sensors Online",
                            fontSize = 11.sp,
                            color = if (isSensorsOff) colors.accentRose else colors.accentGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.15f) else colors.accentGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSensorsOff) "BLOCKED" else "ONLINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = if (isSensorsOff) colors.accentRose else colors.accentGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2-column grid of sensor items showing telemetry status without toggle switches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sensorList.chunked(2).forEach { rowPair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPair.forEach { sensor ->
                            val icon = when (sensor.id) {
                                "camera" -> Icons.Default.PhotoCamera
                                "mic" -> Icons.Default.Mic
                                "motion" -> Icons.Default.DirectionsRun
                                "gyro" -> Icons.Default.ScreenRotation
                                "proximity" -> Icons.Default.Sensors
                                else -> Icons.Default.WbSunny
                            }
                            val isBlocked = sensor.isBlocked || isSensorsOff

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.softBg)
                                    .border(0.5.dp, colors.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = sensor.name,
                                            tint = if (isBlocked) colors.accentRose else colors.accentCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = sensor.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary,
                                            maxLines = 1
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isBlocked) colors.accentRose else colors.accentGreen)
                                    )
                                }
                            }
                        }
                        if (rowPair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Android protects all motion sensors as a unified hardware kill-switch. Experimental individual switches can be enabled in System tab.",
                    fontSize = 11.sp,
                    color = colors.textMuted,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun SleekSensorRow(
    sensor: SensorItem,
    showSwitch: Boolean = false,
    onToggleSensor: (String) -> Unit = {}
) {
    val colors = LocalAppColors.current
    val icon = when (sensor.id) {
        "camera" -> Icons.Default.PhotoCamera
        "mic" -> Icons.Default.Mic
        "motion" -> Icons.Default.DirectionsRun
        "gyro" -> Icons.Default.ScreenRotation
        "proximity" -> Icons.Default.Sensors
        else -> Icons.Default.WbSunny
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sensor.isBlocked) colors.accentRose.copy(alpha = 0.1f) else colors.accentGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = sensor.name,
                        tint = if (sensor.isBlocked) colors.accentRose else colors.accentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = sensor.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = sensor.type,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sensor.isBlocked) colors.accentRose.copy(alpha = 0.15f) else colors.accentGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (sensor.isBlocked) "Blocked" else "Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sensor.isBlocked) colors.accentRose else colors.accentGreen
                    )
                }

                if (showSwitch) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = sensor.isBlocked,
                        onCheckedChange = { onToggleSensor(sensor.id) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accentRose,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = colors.accentGreen
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SleekLogsTabContent(
    viewModel: SensorViewModel,
    uiState: SensorUiState
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Real-time 1-second ticker for live relative seconds and session uptime
    val currentWallTimeMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()
    val tileDiagnostics by viewModel.tileDiagnostics.collectAsStateWithLifecycle()

    val filteredLogs = remember(telemetryLogs, uiState.selectedLogCategory) {
        if (uiState.selectedLogCategory == LogCategory.ALL) {
            telemetryLogs
        } else {
            telemetryLogs.filter { it.category == uiState.selectedLogCategory }
        }
    }

    val exportText = remember(telemetryLogs, tileDiagnostics) {
        buildString {
            appendLine("==================================================")
            appendLine("           SensorsOff Advanced Telemetry          ")
            appendLine("==================================================")
            appendLine("App Version       : 2.3 (SensorsOff)")
            appendLine("Device            : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
            appendLine("Session Uptime    : ${tileDiagnostics.getUptimeString(System.currentTimeMillis())}")
            appendLine("Quick Tile State  : ${tileDiagnostics.lastState}")
            appendLine("Quick Tile Mode   : ${tileDiagnostics.blockMode}")
            appendLine("Tile Architecture : SensorsOffTileService (Passive SystemUI Mode)")
            appendLine("Background Daemon : ${if (uiState.isKeepAliveEnabled) "ACTIVE (Immune to Task Killer)" else "STANDBY (On-Demand)"}")
            appendLine("Last Action       : ${tileDiagnostics.lastAction} at ${tileDiagnostics.lastActionTime}")
            tileDiagnostics.lastLatencyMs?.let { appendLine("Last Latency      : ${it}ms") }
            appendLine("==================================================")
            appendLine("            TIMED EVENT LOGS (PRECISE)            ")
            appendLine("==================================================")
            val chronological = telemetryLogs.reversed()
            for (i in chronological.indices) {
                val entry = chronological[i]
                val prevTs = if (i > 0) chronological[i - 1].timestamp else null
                appendLine(entry.toFormattedString(prevTs))
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(exportText.toByteArray())
                }
                Toast.makeText(context, "Telemetry report exported successfully!", Toast.LENGTH_SHORT).show()
                viewModel.addLog("Exported telemetry report to file.", category = LogCategory.SYSTEM)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export logs: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // 1. LIVE QUICK TILE MONITOR CARD WITH ADVANCED TIMING
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBg),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (tileDiagnostics.lastState.contains("ACTIVE")) colors.accentRose.copy(alpha = 0.5f)
                else if (colors.isDark) colors.glowColor else colors.border
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = "Quick Tile",
                                tint = colors.accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "QUICK TILE REAL-TIME MONITOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textMuted,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "SensorsOffTileService (ACTIVE_TILE)",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (tileDiagnostics.isListening) colors.accentGreen.copy(alpha = 0.15f)
                                else colors.accentBlue.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (tileDiagnostics.isListening) "SHADE OPEN" else "STANDBY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tileDiagnostics.isListening) colors.accentGreen else colors.accentBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tile Diagnostics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // State Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (colors.isDark) Color(0xFF141923) else Color(0xFFF1F5F9))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("Current State", fontSize = 9.sp, color = colors.textMuted)
                            Text(
                                text = if (tileDiagnostics.lastState.contains("ACTIVE")) "ACTIVE (Blocked)" else "INACTIVE (On)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tileDiagnostics.lastState.contains("ACTIVE")) colors.accentRose else colors.accentGreen
                            )
                        }
                    }

                    // Mode Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (colors.isDark) Color(0xFF141923) else Color(0xFFF1F5F9))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("Block Mode", fontSize = 9.sp, color = colors.textMuted)
                            Text(
                                text = if (tileDiagnostics.blockMode == "cam_mic") "Camera + Mic" else "All Sensors",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }

                    // Latency Card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (colors.isDark) Color(0xFF141923) else Color(0xFFF1F5F9))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text("IPC Latency", fontSize = 9.sp, color = colors.textMuted)
                            Text(
                                text = tileDiagnostics.lastLatencyMs?.let { "${it}ms" } ?: "--",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Advanced Timing & Event Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(
                            text = "Last Event: ${tileDiagnostics.lastAction}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${tileDiagnostics.lastActionTime} ",
                                fontSize = 9.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = colors.textMuted
                            )
                            if (tileDiagnostics.lastActionTimestamp > 0L) {
                                Text(
                                    text = "(${tileDiagnostics.getActionRelativeTime(currentWallTimeMs)})",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accentCyan
                                )
                            }
                        }
                    }

                    // Session Uptime with Seconds
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (colors.isDark) Color(0xFF1A2230) else Color(0xFFE2E8F0))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Uptime",
                                tint = colors.accentGreen,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tileDiagnostics.getUptimeString(currentWallTimeMs),
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentGreen
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. CATEGORY FILTER CHIPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LogCategory.values().forEach { category ->
                val isSelected = uiState.selectedLogCategory == category
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) colors.accentBlue else if (colors.isDark) Color(0xFF1A2230) else Color(0xFFE2E8F0))
                        .clickable { viewModel.setLogCategoryFilter(category) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.badgeText,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2.5 ADVANCED TIME MODE SELECTOR BAR (Exact ss.ms / Relative / Delta / Full)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TIME:",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMuted,
                letterSpacing = 0.5.sp
            )
            TimeDisplayMode.values().forEach { mode ->
                val isSelected = uiState.selectedTimeMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) colors.accentCyan.copy(alpha = 0.2f)
                            else if (colors.isDark) Color(0xFF111722) else Color(0xFFEDF2F7)
                        )
                        .border(
                            1.dp,
                            if (isSelected) colors.accentCyan else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { viewModel.setTimeDisplayMode(mode) }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.badge,
                        fontSize = 9.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colors.accentCyan else colors.textMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. ACTION CONTROLS (COPY, EXPORT, CLEAR)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (exportText.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(exportText))
                        Toast.makeText(context, "Full telemetry report copied!", Toast.LENGTH_SHORT).show()
                        viewModel.addLog("Copied telemetry report to clipboard.", category = LogCategory.SYSTEM)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Report", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    createDocumentLauncher.launch("sensorsoff_telemetry_$timeStamp.txt")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(15.dp), tint = Color(0xFF0F172A))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export TXT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }

            OutlinedButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(15.dp), tint = colors.accentRose)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentRose)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. DEEP TIMED LOGS CONSOLE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.cardBg)
                .border(1.dp, if (colors.isDark) colors.glowColor else colors.border, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "[ NO ${uiState.selectedLogCategory.displayName.uppercase()} LOGS ]",
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = colors.textMuted
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs.size, key = { index -> filteredLogs[index].id }) { index ->
                        val entry = filteredLogs[index]
                        val prevItemTs = if (index + 1 < filteredLogs.size) filteredLogs[index + 1].timestamp else null

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (colors.isDark) Color(0xFF131822) else Color(0xFFF8FAFC))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Category Tag Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when (entry.category) {
                                                        LogCategory.TILE -> colors.accentCyan.copy(alpha = 0.2f)
                                                        LogCategory.PRIVILEGE -> colors.accentBlue.copy(alpha = 0.2f)
                                                        LogCategory.SENSOR -> colors.accentGreen.copy(alpha = 0.2f)
                                                        else -> colors.textMuted.copy(alpha = 0.2f)
                                                    }
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = entry.category.badgeText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (entry.category) {
                                                    LogCategory.TILE -> colors.accentCyan
                                                    LogCategory.PRIVILEGE -> colors.accentBlue
                                                    LogCategory.SENSOR -> colors.accentGreen
                                                    else -> colors.textMuted
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Advanced Time Rendering according to selected mode
                                        val displayTimeText = when (uiState.selectedTimeMode) {
                                            TimeDisplayMode.EXACT -> entry.formattedTime
                                            TimeDisplayMode.RELATIVE -> entry.getRelativeTime(currentWallTimeMs)
                                            TimeDisplayMode.DELTA -> entry.getDeltaTime(prevItemTs)
                                            TimeDisplayMode.FULL -> if (entry.fullDateTime.isNotBlank()) entry.fullDateTime else entry.formattedTime
                                        }

                                        Text(
                                            text = displayTimeText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = if (uiState.selectedTimeMode == TimeDisplayMode.RELATIVE) colors.accentCyan else colors.textMuted
                                        )

                                        // Supplementary Relative Time pill if not in relative mode
                                        if (uiState.selectedTimeMode != TimeDisplayMode.RELATIVE) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "(${entry.getRelativeTime(currentWallTimeMs)})",
                                                fontSize = 9.sp,
                                                color = colors.textMuted.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    // Right Side Timing Badges (Interval Delta + Latency)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (prevItemTs != null && uiState.selectedTimeMode != TimeDisplayMode.DELTA) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(if (colors.isDark) Color(0xFF1E2638) else Color(0xFFE2E8F0))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = entry.getDeltaTime(prevItemTs),
                                                    fontSize = 8.5.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }

                                        entry.executionMs?.let { latency ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(colors.accentCyan.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${latency}ms",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    color = colors.accentCyan
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Title
                                Text(
                                    text = entry.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (entry.level) {
                                        LogLevel.ERROR -> colors.accentRose
                                        LogLevel.WARN -> Color(0xFFF59E0B)
                                        LogLevel.SUCCESS -> colors.accentGreen
                                        else -> colors.textPrimary
                                    }
                                )

                                // Deep Technical Detail
                                if (entry.detail.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = entry.detail,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = colors.textSecondary,
                                        lineHeight = 14.sp
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
fun SensorsOffBrandLogo(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(colors.cardBg)
            .border(1.dp, if (colors.isDark) colors.glowColor else colors.border, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_app_logo_hero),
            contentDescription = "SensorsOff Logo",
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun SleekAboutTabContent(
    uiState: SensorUiState,
    viewModel: SensorViewModel
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SensorsOffBrandLogo(
                        modifier = Modifier.size(76.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SensorsOff",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Text(
                        text = "Version 2.3",
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "SensorsOff restores the native Android Quick Settings sensor block tile for custom ROMs, HyperOS/MIUI, and modern devices via Shizuku & Root SU integration.",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DEVICE & ENVIRONMENT METRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCyan,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SleekInfoRow(label = "Manufacturer", value = uiState.deviceManufacturer.ifBlank { "Standard" })
                    SleekInfoRow(label = "Device Model", value = uiState.deviceModel.ifBlank { "Android Device" })
                    SleekInfoRow(label = "Android Release", value = "Android ${uiState.androidVersion}")
                    SleekInfoRow(label = "Shizuku Integration", value = if (uiState.isShizukuAuthorized) "Authorized (Active)" else "Inactive")
                    SleekInfoRow(label = "Root Privileges", value = if (uiState.isRootAvailable) "Granted" else "None")
                    SleekInfoRow(label = "Hardware Privacy State", value = if (uiState.isSensorsOff) "Privacy Mode Active" else "Sensors Enabled")
                    SleekInfoRow(label = "Background Keep-Alive", value = if (uiState.isKeepAliveEnabled) "Running (Foreground Daemon)" else "Disabled (On-Demand)")
                }
            }
        }

        item {
            SleekBackgroundKeepAliveCard(
                isKeepAliveEnabled = uiState.isKeepAliveEnabled,
                onToggleKeepAlive = { enabled ->
                    viewModel.setKeepAliveEnabled(enabled)
                }
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) colors.glowColor else colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.accentAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = "Experimental",
                                tint = colors.accentAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "EXPERIMENTAL & SYSTEM SETTINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentAmber,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Individual sensor controls & system shortcuts",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Experimental per-sensor toggle option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Individual Sensor Toggles",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Show per-sensor manual switches for Camera, Microphone, and motion sensors on the Matrix dashboard.",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        Switch(
                            checked = uiState.showExperimentalToggles,
                            onCheckedChange = { viewModel.setShowExperimentalToggles(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentAmber,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = colors.softBg
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = colors.border)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "NATIVE ANDROID SETTINGS SHORTCUTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Privacy settings not directly accessible", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Privacy",
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Privacy Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentCyan)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Developer options not directly accessible", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = "Developer Options",
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentAmber
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Developer Options", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentAmber)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e2: Exception) {
                                        Toast.makeText(context, "Battery settings not directly accessible", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Battery Unrestricted", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentGreen)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "App info not accessible", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Info",
                                modifier = Modifier.size(16.dp),
                                tint = colors.accentBlue
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("App Info / Autostart", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentBlue)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) colors.glowColor else colors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SECURITY & ISOLATION GUARANTEE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentGreen,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• 100% On-Device execution with zero network telemetry or tracking.\n• Direct system IPC hook into Android SensorPrivacyService.\n• Zero telemetry, zero analytics, open privacy architecture.",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SleekInfoRow(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = colors.textSecondary)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    }
}

@Composable
fun SleekNavigationBar(
    selectedTab: SleekTab,
    onTabSelected: (SleekTab) -> Unit
) {
    val colors = LocalAppColors.current
    Surface(
        color = colors.cardBg,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (colors.isDark) colors.glowColor else colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SleekNavItem(
                icon = Icons.Default.Home,
                label = "Matrix",
                isSelected = selectedTab == SleekTab.HOME,
                onClick = { onTabSelected(SleekTab.HOME) }
            )
            SleekNavItem(
                icon = Icons.Default.ListAlt,
                label = "Telemetry",
                isSelected = selectedTab == SleekTab.LOGS,
                onClick = { onTabSelected(SleekTab.LOGS) }
            )
            SleekNavItem(
                icon = Icons.Default.Info,
                label = "System",
                isSelected = selectedTab == SleekTab.ABOUT,
                onClick = { onTabSelected(SleekTab.ABOUT) }
            )
        }
    }
}

@Composable
fun SleekNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) colors.accentCyan else colors.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.accentCyan else colors.textMuted
        )
    }
}

@Composable
fun SleekBackgroundKeepAliveCard(
    isKeepAliveEnabled: Boolean,
    onToggleKeepAlive: (Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onToggleKeepAlive(true)
        if (!isGranted) {
            Toast.makeText(context, "Notification permission recommended to maintain keep-alive status", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isKeepAliveEnabled) colors.accentCyan.copy(alpha = 0.5f) else (if (colors.isDark) colors.glowColor else colors.border)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isKeepAliveEnabled) colors.accentCyan.copy(alpha = 0.15f) else colors.softBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Background Keep-Alive",
                        tint = if (isKeepAliveEnabled) colors.accentCyan else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Background Keep-Alive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (isKeepAliveEnabled) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.accentCyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.accentCyan
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isKeepAliveEnabled) "Protected from OEM task killers" else "Run in background to stay active",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                Switch(
                    checked = isKeepAliveEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onToggleKeepAlive(true)
                            }
                        } else {
                            onToggleKeepAlive(false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accentCyan,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = colors.softBg
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Runs a lightweight persistent foreground service so Android and OEM task killers (HyperOS, MIUI, Samsung) never kill the app when swiped from Recents. Maintains Shizuku connection 24/7 for instant Quick Settings tile response, plus adds a status notification with a 1-tap toggle.",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 17.sp
            )

            if (isKeepAliveEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exclude from Battery Optimization",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
