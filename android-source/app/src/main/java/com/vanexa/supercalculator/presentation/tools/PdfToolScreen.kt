package com.vanexa.supercalculator.presentation.tools

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.vanexa.supercalculator.data.billing.BillingManager
import com.vanexa.supercalculator.data.billing.ToolsQuotaManager
import com.vanexa.supercalculator.presentation.ai.PaywallScreen
import com.vanexa.supercalculator.util.PdfTools
import com.vanexa.supercalculator.util.ImageTools
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val billingManager: BillingManager = koinInject()
    
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var statusMessage by remember { mutableStateOf("Select a document to begin") }
    var progress by remember { mutableStateOf<Int?>(null) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var resultPath by remember { mutableStateOf<String?>(null) }
    var lastMimeType by remember { mutableStateOf("application/pdf") }

    // Monetization State
    var showPaywall by remember { mutableStateOf(false) }
    val isUnlimited = billingManager.hasUnlimitedToolsAccess()
    var remainingQuota by remember { mutableStateOf(ToolsQuotaManager.getRemainingQuota(context, billingManager)) }

    // Refresh quota every time the screen comes to the foreground (fixes stale state on new day)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                remainingQuota = ToolsQuotaManager.getRemainingQuota(context, billingManager)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Image Format Selector UI State
    var showFormatSelector by remember { mutableStateOf(false) }
    var compressFormat by remember { mutableStateOf(ImageTools.CompressionFormat.JPEG) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Quota gate helper
    fun tryUseTool(action: () -> Unit) {
        if (ToolsQuotaManager.canUseTools(context, billingManager)) {
            action()
        } else {
            android.widget.Toast.makeText(context, "Daily limit reached — Upgrade to unlock unlimited tools", android.widget.Toast.LENGTH_LONG).show()
            showPaywall = true
        }
    }

    // PaywallScreen overlay
    if (showPaywall) {
        PaywallScreen(
            currentTier = billingManager.currentTier.collectAsState().value,
            targetTierToScroll = BillingManager.TIER_FAST,
            onDismiss = { showPaywall = false },
            onPurchaseClick = { productId ->
                val activity = context as? android.app.Activity
                activity?.let { billingManager.launchPurchaseFlow(it, productId) }
                showPaywall = false
            }
        )
    }

    // File Pickers
    val wordPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedUri = it
            resultPath = null
            activeTool = "wordToPdf"
            lastMimeType = "application/pdf"
            scope.launch {
                PdfTools.wordToPdf(context, it).collect { update ->
                    when (update) {
                        is PdfTools.ProgressUpdate.Loading -> {
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Progress -> {
                            progress = update.percentage
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Success -> {
                            if (!isUnlimited) {
                                ToolsQuotaManager.incrementUsage(context)
                                remainingQuota = ToolsQuotaManager.getRemainingQuota(context, billingManager)
                            }
                            resultPath = update.filePath
                            statusMessage = update.summary.ifEmpty { "Conversion complete!" }
                            progress = 100
                            activeTool = null
                        }
                        is PdfTools.ProgressUpdate.Error -> {
                            statusMessage = "Error: ${update.message}"
                            progress = null
                            activeTool = null
                        }
                    }
                }
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedUri = it
            resultPath = null
            activeTool = "compressPdf"
            lastMimeType = "application/pdf"
            scope.launch {
                PdfTools.compressPdf(context, it, PdfTools.CompressionMode.HIGH).collect { update ->
                    when (update) {
                        is PdfTools.ProgressUpdate.Loading -> {
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Progress -> {
                            progress = update.percentage
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Success -> {
                            if (!isUnlimited) {
                                ToolsQuotaManager.incrementUsage(context)
                                remainingQuota = ToolsQuotaManager.getRemainingQuota(context, billingManager)
                            }
                            resultPath = update.filePath
                            statusMessage = update.summary.ifEmpty { "Compression complete!" }
                            progress = 100
                            activeTool = null
                        }
                        is PdfTools.ProgressUpdate.Error -> {
                            statusMessage = "Error: ${update.message}"
                            progress = null
                            activeTool = null
                        }
                    }
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedUri = it
            resultPath = null
            activeTool = "compressImage"
            lastMimeType = if (compressFormat == ImageTools.CompressionFormat.JPEG) "image/jpeg" else "image/webp"
            scope.launch {
                ImageTools.compressImage(context, it, compressFormat).collect { update ->
                    when (update) {
                        is PdfTools.ProgressUpdate.Loading -> {
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Progress -> {
                            progress = update.percentage
                            statusMessage = update.status
                        }
                        is PdfTools.ProgressUpdate.Success -> {
                            if (!isUnlimited) {
                                ToolsQuotaManager.incrementUsage(context)
                                remainingQuota = ToolsQuotaManager.getRemainingQuota(context, billingManager)
                            }
                            resultPath = update.filePath
                            statusMessage = update.summary.ifEmpty { "Image Compression complete!" }
                            progress = 100
                            activeTool = null
                        }
                        is PdfTools.ProgressUpdate.Error -> {
                            statusMessage = "Error: ${update.message}"
                            progress = null
                            activeTool = null
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "File Engine", 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 14.dp) // Shifted slightly more to the right as requested
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable Content wrapper to prevent clipping on small screens
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Touch Point 1 — Persistent Upgrade Banner (hidden for paid users)
            if (!isUnlimited) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showPaywall = true },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00E676).copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(0.5.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Unlock Unlimited Tools & Vanexa AI",
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF00E676).copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Tool Cards
            ToolCard(
                title = "Compress PDF",
                desc = "Reduce file size (image optimization)",
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.vanexa.supercalculator.R.drawable.ic_tool_compress_pdf),
                color = Color(0xFFE53935),
                isLoading = activeTool == "compressPdf",
                enabled = activeTool == null,
                onClick = { tryUseTool { pdfPicker.launch(arrayOf("application/pdf")) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Image Compression Button
            ToolCard(
                title = "Compress Image",
                desc = "Reduce photo/image file size",
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.vanexa.supercalculator.R.drawable.ic_tool_compress_image),
                color = Color(0xFF00E676),
                isLoading = activeTool == "compressImage",
                enabled = activeTool == null,
                onClick = { showFormatSelector = true }
            )

            // ═══════════════════════════════════════════════════════
            // TEMPORARILY DISABLED: Word to PDF crashes in AAB/release
            // due to Apache POI class-loading failure. Re-enable when fixed.
            // Set to `true` to restore this feature.
            // ═══════════════════════════════════════════════════════
            if (false) {
            Spacer(modifier = Modifier.height(16.dp))

            ToolCard(
                title = "Word to PDF",
                desc = "Convert .docx to high-quality PDF",
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.vanexa.supercalculator.R.drawable.ic_tool_word_pdf),
                color = Color(0xFF2B579A),
                isLoading = activeTool == "wordToPdf",
                enabled = activeTool == null,
                onClick = { tryUseTool { wordPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) } }
            )
            } // END DISABLED: Word to PDF

                Spacer(modifier = Modifier.height(24.dp))
            } // Close Scrollable Column

            // Progress/Status Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF06101A),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (activeTool != null) "Processing..." else "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    if (activeTool != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        progress?.let {
                            LinearProgressIndicator(
                                progress = it / 100f,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF00E676)
                            )
                        } ?: LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00E676)
                        )
                    }

                    resultPath?.let { path ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val file = File(path)
                                if (file.exists()) {
                                    val savedUri = PdfTools.saveFileToPublicFolder(context, file, lastMimeType)
                                    if (savedUri != null) {
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Saved to Documents/Vanexa",
                                                actionLabel = "OPEN",
                                                duration = SnackbarDuration.Long
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                openSavedFileAction(context, savedUri, lastMimeType)
                                            }
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to save file", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save to Device", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        // Touch Point 2 — Post-Compression Premium Upgrade Nudge
                        if (!isUnlimited) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val infiniteTransition = rememberInfiniteTransition()
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 0.6f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "borderPulse"
                            )

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { showPaywall = true },
                                color = Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF06101A),
                                                    Color(0xFF00E676).copy(alpha = 0.08f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF00E676).copy(alpha = pulseAlpha),
                                                    Color(0xFF00E676).copy(alpha = 0.1f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(Color(0xFF00E676).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = com.vanexa.supercalculator.R.drawable.ic_unlimited_infinity),
                                                contentDescription = null,
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Go Unlimited",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Unlock unlimited use & get full access to\nVanexa AI reasoning engine.",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { showPaywall = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)), // Slightly dark blue
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Text("UPGRADE NOW", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Remaining Quota Display
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isUnlimited) {
                        Text(
                            text = "∞ Unlimited",
                            color = Color(0xFF00E676).copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "$remainingQuota of ${ToolsQuotaManager.getDailyLimit()} daily uses remaining",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // Format Selector Modal Bottom Sheet (Enterprise Grade Pop-up)
    if (showFormatSelector) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFormatSelector = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0A1520),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Output Format",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose the optimal format for your image compression.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                ToolCard(
                    title = "Export to JPEG",
                    desc = "High compatibility, standard file size",
                    icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.vanexa.supercalculator.R.drawable.ic_export_jpeg),
                    color = Color.White.copy(alpha = 0.8f),
                    isLoading = false,
                    enabled = true,
                    onClick = { 
                        compressFormat = ImageTools.CompressionFormat.JPEG
                        showFormatSelector = false
                        tryUseTool { imagePicker.launch(arrayOf("image/*")) }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                ToolCard(
                    title = "Export to WebP",
                    desc = "Maximum size reduction (Recommended)",
                    icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = com.vanexa.supercalculator.R.drawable.ic_export_webp),
                    color = Color(0xFF00E676),
                    isLoading = false,
                    enabled = true,
                    onClick = { 
                        compressFormat = ImageTools.CompressionFormat.WEBP
                        showFormatSelector = false
                        tryUseTool { imagePicker.launch(arrayOf("image/*")) }
                    }
                )
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun ToolCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        color = Color(0xFF06101A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (enabled) Color(0xFF252B3F) else Color.Transparent, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                Text(desc, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            }
        }
    }
}

private fun openSavedFileAction(context: android.content.Context, uri: Uri, mimeType: String) {
    try {
        val finalUri = if (uri.scheme == "file") {
            try {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    java.io.File(uri.path!!)
                )
            } catch (e: Exception) {
                uri // Fallback if file isn't in a shared path
            }
        } else {
            uri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(finalUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}
