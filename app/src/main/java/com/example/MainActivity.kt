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
                SleekTab.ABOUT -> SleekAboutTabContent(uiState = uiState)
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
            Text(
                text = "ENTERPRISE SENSOR PRIVACY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentBlue,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "SensorsOff",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.cardBg)
                .border(1.dp, colors.border, CircleShape)
                .clickable { onRefresh() }
                .testTag("refresh_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = colors.textSecondary,
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
            SleekQuickTileTipCard(uiState = uiState)
        }

        // Monitored Sensors List Header
        item {
            Text(
                text = "MONITORED HARDWARE SENSORS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textMuted,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(uiState.sensorList) { sensor ->
            SleekSensorRow(
                sensor = sensor,
                onToggleSensor = { sensorId ->
                    viewModel.toggleIndividualSensor(sensorId)
                }
            )
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
        targetValue = if (isSensorsOff) colors.accentRose else colors.accentBlue,
        animationSpec = tween(350),
        label = "btnColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_toggle_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBg),
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
                    .size(150.dp)
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.1f) else colors.accentBlue.copy(alpha = 0.1f))
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(if (isSensorsOff) colors.accentRose.copy(alpha = 0.2f) else colors.accentBlue.copy(alpha = 0.2f))
                )
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(buttonBgColor)
                        .clickable { onToggle() }
                        .testTag("toggle_sensors_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSensorsOff) Icons.Default.Shield else Icons.Default.Sensors,
                        contentDescription = "Toggle Sensors",
                        modifier = Modifier.size(38.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isSensorsOff) "Sensors Disabled" else "Sensors Enabled",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
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
                    .background(colors.softBg)
                    .clickable { onRequestShizuku() }
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "SHIZUKU PRIVILEGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isShizukuAuthorized) colors.accentGreen else if (uiState.isShizukuRunning) Amber600 else colors.textMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isShizukuAuthorized) "Authorized" else if (uiState.isShizukuRunning) "Needs Auth" else "Inactive",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
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
                    .background(colors.softBg)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "SYSTEM ENGINE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentBlue,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isRootAvailable || uiState.hasSecureSettingsPermission) colors.accentGreen else Amber600)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.isRootAvailable) "Root SU Ready" else if (uiState.hasSecureSettingsPermission) "ADB Granted" else "Standard",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
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
                        tint = colors.accentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "App Visual Theme",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Dynamic light, dark, or system scheme",
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
                    "system" to "System",
                    "light" to "Light",
                    "dark" to "Dark",
                    "dynamic" to "Monet"
                ).forEach { (modeKey, modeLabel) ->
                    FilterChip(
                        selected = currentTheme == modeKey,
                        onClick = { onSelectTheme(modeKey) },
                        label = { Text(modeLabel, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentBlue,
                            selectedLabelColor = Color.White
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
                                "stock" to "Stock Wave",
                                "shield" to "Shield",
                                "camera_off" to "Cam Off"
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
                                "mic_off" to "Mic Off",
                                "motion_off" to "Motion Off",
                                "aosp" to "AOSP"
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
fun SleekQuickTileTipCard(uiState: SensorUiState) {
    val colors = LocalAppColors.current
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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
                        .background(Amber50),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "Tip",
                        tint = Amber600,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Settings Tile & ADB Guide",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Adding QS tile and granting secure settings permission",
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
                            .background(Slate800)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.adbGrantCommand,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Blue200
                        )
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.adbGrantCommand))
                            Toast.makeText(context, "ADB command copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
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
fun SleekSensorRow(
    sensor: SensorItem,
    onToggleSensor: (String) -> Unit
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

@Composable
fun SleekLogsTabContent(
    viewModel: SensorViewModel,
    uiState: SensorUiState
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val logsText = remember(uiState.logs) {
        uiState.logs.reversed().joinToString(separator = "\n")
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(logsText.toByteArray())
                }
                Toast.makeText(context, "Logs exported successfully!", Toast.LENGTH_SHORT).show()
                viewModel.addLog("Exported logs to file.")
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export logs: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "ACTION LOGS & CONSOLE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Log Control Buttons Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Copy Logs Button
            Button(
                onClick = {
                    if (logsText.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(logsText))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                        viewModel.addLog("Copied logs console to clipboard.")
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Export Logs Button
            Button(
                onClick = {
                    val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    createDocumentLauncher.launch("sensorsoff_logs_$timeStamp.txt")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = "Export", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Clear Console Button
            OutlinedButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = colors.accentRose)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accentRose)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Slate800)
                .padding(16.dp)
        ) {
            if (uiState.logs.isEmpty()) {
                Text(
                    text = "No system logs recorded.",
                    fontSize = 12.sp,
                    color = Slate400
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.logs) { log ->
                        Text(
                            text = log,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (log.contains("Error")) Rose500 else Blue200
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorsOffBrandLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.sensors_off_logo),
            contentDescription = "SensorsOff Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
        )
    }
}

@Composable
fun SleekAboutTabContent(uiState: SensorUiState) {
    val colors = LocalAppColors.current

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
                        text = "Version 1.4.0 (Enterprise Privacy Engine)",
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
                        color = colors.accentBlue,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    SleekInfoRow(label = "Manufacturer", value = uiState.deviceManufacturer.ifBlank { "Standard" })
                    SleekInfoRow(label = "Device Model", value = uiState.deviceModel.ifBlank { "Android Device" })
                    SleekInfoRow(label = "Android Release", value = "Android ${uiState.androidVersion}")
                    SleekInfoRow(label = "Shizuku Integration", value = if (uiState.isShizukuAuthorized) "Authorized (Active)" else "Inactive")
                    SleekInfoRow(label = "Root Privileges", value = if (uiState.isRootAvailable) "Granted" else "None")
                    SleekInfoRow(label = "Hardware Privacy State", value = if (uiState.isSensorsOff) "Privacy Mode Active" else "Sensors Enabled")
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
                        text = "ENTERPRISE SECURITY GUARANTEE",
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
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
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
                label = "Home",
                isSelected = selectedTab == SleekTab.HOME,
                onClick = { onTabSelected(SleekTab.HOME) }
            )
            SleekNavItem(
                icon = Icons.Default.ListAlt,
                label = "Logs",
                isSelected = selectedTab == SleekTab.LOGS,
                onClick = { onTabSelected(SleekTab.LOGS) }
            )
            SleekNavItem(
                icon = Icons.Default.Info,
                label = "About",
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
            tint = if (isSelected) colors.accentBlue else colors.textMuted,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.accentBlue else colors.textMuted
        )
    }
}
