package com.paras.novelreaderkt.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paras.novelreaderkt.BrowserViewModel
import com.paras.novelreaderkt.WtrAudioControlBridge
import com.paras.novelreaderkt.sites.WebsiteSupportRegistry
import com.paras.novelreaderkt.ui.theme.ThemeSwatches

@Composable
fun SettingsPanel(
    onDismissRequest: () -> Unit,
    viewModel: BrowserViewModel,
    onThemeChanged: (String) -> Unit,
    webViewsMap: Map<Long, android.webkit.WebView>
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // SAF Create Document launcher for Exporting Backup JSON
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "Backup downloaded successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { e ->
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // SAF Open Document launcher for Importing Backup JSON
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackup(
                uri = uri,
                onSuccess = {
                    Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                    onDismissRequest() // Auto-close settings dialog so values are refreshed
                },
                onError = { e ->
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val sharedPrefs = remember(context) { context.getSharedPreferences("wtr_browser_settings", Context.MODE_PRIVATE) }
    
    var enableWebTrackplayer by remember { mutableStateOf(sharedPrefs.getBoolean("enable_web_trackplayer", false)) }
    var forceDarkContent by remember { mutableStateOf(sharedPrefs.getBoolean("force_dark_content", false)) }
    var autoFocusParagraphs by remember { mutableStateOf(sharedPrefs.getBoolean("auto_focus_paragraphs", true)) }
    var rememberParagraphs by remember { mutableStateOf(sharedPrefs.getBoolean("remember_paragraphs", true)) }
    var autoTranslateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("auto_translate_enabled", true)) }
    val defaultTranslateDomains = remember { WebsiteSupportRegistry.getAutoTranslateSites().joinToString(", ") }
    var autoTranslateDomains by remember { mutableStateOf(sharedPrefs.getString("auto_translate_domains", defaultTranslateDomains) ?: defaultTranslateDomains) }
    var geminiTranslateEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("gemini_translate_enabled", false)) }
    var geminiApiKey by remember { mutableStateOf(com.paras.novelreaderkt.SecurePreferences.getGeminiApiKey(context)) }
    var antiCaptchaDelay by remember { mutableStateOf(sharedPrefs.getBoolean("anti_captcha_delay", false)) }
    var adBlockerEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("ad_blocker_enabled", true)) }
    var customTextZoom by remember { mutableStateOf(sharedPrefs.getInt("custom_text_zoom", 115)) }
    var currentThemeName by remember { mutableStateOf(sharedPrefs.getString("app_theme", "Dark") ?: "Dark") }
    
    val activeTtsSpeed by WtrAudioControlBridge.ttsSpeed.collectAsStateWithLifecycle()
    val activeTtsAccent by WtrAudioControlBridge.ttsAccent.collectAsStateWithLifecycle()
    val activeTtsPitch by WtrAudioControlBridge.ttsPitch.collectAsStateWithLifecycle()
    val activeTtsVoiceName by WtrAudioControlBridge.ttsVoiceName.collectAsStateWithLifecycle()
    val availableVoices by WtrAudioControlBridge.availableVoices.collectAsStateWithLifecycle()
    val searchEngineUrl by viewModel.searchEngine.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Settings & Profiles",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Personalize Novel Reader",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            IconButton(onClick = onDismissRequest) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Settings")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
                // SECTION 1: THEME & VISUAL PAIRINGS
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Appearance & Styling",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Text(
                            text = "Choose an eye-friendly color palette crafted for readers:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        
                        val themesRow1 = listOf(
                            "Dark" to "Night Dark",
                            "Grey" to "Slate Grey",
                            "White" to "Pristine Light"
                        )
                        val themesRow2 = listOf(
                            "Sepia" to "Warm Sepia",
                            "Forest" to "Forest Green",
                            "Ocean" to "Ocean Blue"
                        )
                        val themesRow3 = listOf(
                            "CosmicSlate" to "Cosmic Slate",
                            "Bauhaus" to "Bauhaus"
                        )

                        // Helper: text color for a given theme chip
                        fun chipTextColor(key: String): Color = when (key) {
                            "White", "Sepia", "Bauhaus" -> Color(0xFF1A1C1E)
                            else -> Color.White
                        }

                        // Helper: swatch for a given theme key
                        fun chipSwatch(key: String): Pair<Color, Color> = when (key) {
                            "Dark" -> ThemeSwatches.Dark
                            "Grey" -> ThemeSwatches.Grey
                            "White" -> ThemeSwatches.White
                            "Sepia" -> ThemeSwatches.Sepia
                            "Forest" -> ThemeSwatches.Forest
                            "Ocean" -> ThemeSwatches.Ocean
                            "CosmicSlate" -> ThemeSwatches.CosmicSlate
                            "Bauhaus" -> ThemeSwatches.Bauhaus
                            else -> ThemeSwatches.Dark
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                themesRow1.forEach { (themeKey, label) ->
                                    val isSelected = currentThemeName == themeKey
                                    val (bg, prim) = chipSwatch(themeKey)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) prim else Color.Gray.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentThemeName = themeKey
                                                sharedPrefs.edit().putString("app_theme", themeKey).apply()
                                                onThemeChanged(themeKey)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = chipTextColor(themeKey)
                                        )
                                    }
                                }
                            }
                            
                            // Row 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                themesRow2.forEach { (themeKey, label) ->
                                    val isSelected = currentThemeName == themeKey
                                    val (bg, prim) = chipSwatch(themeKey)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) prim else Color.Gray.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentThemeName = themeKey
                                                sharedPrefs.edit().putString("app_theme", themeKey).apply()
                                                onThemeChanged(themeKey)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = chipTextColor(themeKey)
                                        )
                                    }
                                }
                            }

                            // Row 3 — new themes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                themesRow3.forEach { (themeKey, label) ->
                                    val isSelected = currentThemeName == themeKey
                                    val (bg, prim) = chipSwatch(themeKey)
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) prim else Color.Gray.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentThemeName = themeKey
                                                sharedPrefs.edit().putString("app_theme", themeKey).apply()
                                                onThemeChanged(themeKey)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = chipTextColor(themeKey)
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Default Article Text Size",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adjust the default text sizing scaling on text novel sites:",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            val zoomOptions = listOf(
                                "Compact" to 95,
                                "Default" to 115,
                                "Medium" to 130,
                                "Large" to 145,
                                "Huge" to 160
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                zoomOptions.forEach { (label, zoomVal) ->
                                    val isSelected = customTextZoom == zoomVal
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                customTextZoom = zoomVal
                                                sharedPrefs.edit().putInt("custom_text_zoom", zoomVal).apply()
                                                webViewsMap.values.forEach { it.settings.textZoom = zoomVal }
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DarkMode,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Inject Night Styling (Force CSS)",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Re-render external web pages with high-contrast layout filters",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = forceDarkContent,
                                onCheckedChange = { 
                                    forceDarkContent = it 
                                    sharedPrefs.edit().putBoolean("force_dark_content", it).apply()
                                },
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    }
                }

                // SECTION 2: AUDIO ENGINE (TTS)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Novel Speech & Audio Engine",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Media Trackplayer",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Show professional system media player notification/controls for browser paragraphs",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = enableWebTrackplayer,
                                onCheckedChange = { 
                                    enableWebTrackplayer = it 
                                    sharedPrefs.edit().putBoolean("enable_web_trackplayer", it).apply()
                                },
                                modifier = Modifier.testTag("toggle_trackplayer_switch").scale(0.85f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Focus / Active Scrolling",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Offset scroll and highlight read text position concurrently",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = autoFocusParagraphs,
                                onCheckedChange = { 
                                    autoFocusParagraphs = it 
                                    sharedPrefs.edit().putBoolean("auto_focus_paragraphs", it).apply()
                                },
                                modifier = Modifier.testTag("auto_focus_paragraphs_switch").scale(0.85f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smart Saving of Paragraphs",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Save and restore the exact reading position when launching tabs or returning",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = rememberParagraphs,
                                onCheckedChange = { 
                                    rememberParagraphs = it 
                                    sharedPrefs.edit().putBoolean("remember_paragraphs", it).apply()
                                },
                                modifier = Modifier.testTag("remember_paragraphs_switch").scale(0.85f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Enable Session Logging",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Record background diagnostics & web redirects to resolve whitescreens",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            var enableLogs by remember { mutableStateOf(com.paras.novelreaderkt.WtrLogManager.isLoggingEnabled()) }
                            Switch(
                                checked = enableLogs,
                                onCheckedChange = { 
                                    enableLogs = it 
                                    com.paras.novelreaderkt.WtrLogManager.setLoggingEnabled(context, it)
                                },
                                modifier = Modifier.testTag("enable_logs_switch").scale(0.85f)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Playback Speed Multiplier",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val speedOptions = listOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
                                speedOptions.forEach { speed ->
                                    val isSelected = activeTtsSpeed == speed
                                    val label = when (speed) {
                                        4.0f -> "4x (Def)"
                                        else -> "${speed.toInt()}x"
                                    }
                                    
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                WtrAudioControlBridge.setTtsSpeed(speed)
                                                sharedPrefs.edit().putFloat("tts_speed", speed).apply()
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Speech Pitch (Tone)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val pitchOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f)
                                pitchOptions.forEach { pitchVal ->
                                    val isSelected = activeTtsPitch == pitchVal
                                    val label = when (pitchVal) {
                                        0.75f -> "Deep"
                                        1.0f -> "Normal"
                                        1.25f -> "Crisp"
                                        1.5f -> "High"
                                        else -> "${pitchVal}x"
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                WtrAudioControlBridge.setTtsPitch(pitchVal)
                                                sharedPrefs.edit().putFloat("tts_pitch", pitchVal).apply()
                                            }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Voice Accent / Dialect",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val accentOptions = listOf("US", "UK", "AU", "IN")
                                accentOptions.forEach { accentKey ->
                                    val isSelected = activeTtsAccent == accentKey
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                WtrAudioControlBridge.setTtsAccent(accentKey)
                                                sharedPrefs.edit().putString("tts_accent", accentKey).apply()
                                            }
                                    ) {
                                        Text(
                                            text = accentKey,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (availableVoices.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "System Installed Voice Profile",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                var expanded by remember { mutableStateOf(false) }
                                val cleanVoiceLabel = activeTtsVoiceName.ifEmpty { "Default Engine Voice" }
                                
                                Box {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = cleanVoiceLabel,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 240.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("System Default Voice", fontSize = 11.sp) },
                                            onClick = {
                                                WtrAudioControlBridge.setTtsVoiceName("")
                                                sharedPrefs.edit().putString("tts_voice_name", "").apply()
                                                expanded = false
                                            }
                                        )
                                        availableVoices.forEach { voice ->
                                            val shortName = voice.substringAfterLast(".").replace("_", " ").uppercase()
                                            DropdownMenuItem(
                                                text = { Text(shortName, fontSize = 11.sp) },
                                                onClick = {
                                                    WtrAudioControlBridge.setTtsVoiceName(voice)
                                                    sharedPrefs.edit().putString("tts_voice_name", voice).apply()
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: AUTOMATIC TRANSLATION
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Language Translation",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Translate Foreign Sites",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Auto translate untamed non-English web novels in-place via high-speed proxy",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = autoTranslateEnabled,
                                onCheckedChange = { 
                                    autoTranslateEnabled = it 
                                    sharedPrefs.edit().putBoolean("auto_translate_enabled", it).apply()
                                },
                                modifier = Modifier.testTag("auto_translate_switch").scale(0.85f)
                            )
                        }
                        
                        if (autoTranslateEnabled) {
                            Spacer(modifier = Modifier.height(2.dp))
                            OutlinedTextField(
                                value = autoTranslateDomains,
                                onValueChange = { 
                                    autoTranslateDomains = it
                                    sharedPrefs.edit().putString("auto_translate_domains", it).apply()
                                },
                                label = { Text("Auto-translate domain keywords (comma-separated)", fontSize = 10.sp) },
                                placeholder = { Text("e.g. timotxt.com, novelhall", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth().testTag("auto_translate_domains_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Use Gemini AI Translation",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "AI-translates Chinese novel chapters with name highlighting, glossary context, and preserved novel tropes (Chinese sites, chapters only)",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = geminiTranslateEnabled,
                                    onCheckedChange = { 
                                        geminiTranslateEnabled = it 
                                        sharedPrefs.edit().putBoolean("gemini_translate_enabled", it).apply()
                                    },
                                    modifier = Modifier.testTag("gemini_translate_switch").scale(0.8f)
                                )
                            }

                            if (geminiTranslateEnabled) {
                                Spacer(modifier = Modifier.height(2.dp))
                                var showApiKey by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = geminiApiKey,
                                    onValueChange = { 
                                        geminiApiKey = it
                                        com.paras.novelreaderkt.SecurePreferences.setGeminiApiKey(context, it)
                                    },
                                    label = { Text("Gemini API Key", fontSize = 10.sp) },
                                    placeholder = { Text("AIzaSy...", fontSize = 10.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showApiKey = !showApiKey }) {
                                            Icon(
                                                imageVector = if (showApiKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (showApiKey) "Hide API Key" else "Show API Key",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Anti-CAPTCHA Delay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Pauses 4.5s on translated chapters to avoid Google Translate bot blocks",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = antiCaptchaDelay,
                                    onCheckedChange = { 
                                        antiCaptchaDelay = it 
                                        sharedPrefs.edit().putBoolean("anti_captcha_delay", it).apply()
                                    },
                                    modifier = Modifier.testTag("anti_captcha_delay_switch").scale(0.8f)
                                )
                            }
                        }
                    }
                }

                // SECTION 4: WEB UTILITIES & PRIVACY
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Search & Utilities",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Preferred Search Engine",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val engineOptions = listOf(
                                "Google" to "https://www.google.com/search?q=",
                                "DuckDuckGo" to "https://duckduckgo.com/?q=",
                                "Bing" to "https://www.bing.com/search?q="
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                engineOptions.forEach { option ->
                                    val isSelected = searchEngineUrl == option.second
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                viewModel.setSearchEngine(option.second)
                                            }
                                    ) {
                                        Text(
                                            text = option.first,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Popup Shield & Ad-Blocker",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Block sketches, tracking, intrusive popups and advertisements on novel-reading sites",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Switch(
                                    checked = adBlockerEnabled,
                                    onCheckedChange = { 
                                        adBlockerEnabled = it 
                                        sharedPrefs.edit().putBoolean("ad_blocker_enabled", it).apply()
                                    },
                                    modifier = Modifier.testTag("popups_ad_blocker_switch").scale(0.85f)
                                )
                            }
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                        Button(
                            onClick = {
                                viewModel.clearHistory()
                                Toast.makeText(context, "Cookies & history database optimized successfully!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text("Clear Navigation History & Cache", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // SECTION 5: BACKUP & RESTORE DATA
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("backup_restore_section_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Backup & Restore Data",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Download a copy of history, bookmarks, tabs, and preferences, or restore the browser state from a previously saved JSON file.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    try {
                                        exportLauncher.launch("wtr_browser_backup.json")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error starting exporter: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.5f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Download Backup", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        importLauncher.launch(arrayOf("*/*"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error starting importer: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.5f).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("Upload Backup", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
