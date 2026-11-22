package com.amuksith.expensetracker.ui

import java.io.File
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.amuksith.expensetracker.BuildConfig
import com.amuksith.expensetracker.R
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.rememberLibraries
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val GITHUB_REPO_URL = "https://github.com/amuksith/expense-tracker"

@Serializable
object AboutRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appVersion = "v${BuildConfig.VERSION_NAME}"

    var showUpdateLog by remember { mutableStateOf(false) }
    var showLibrariesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = !showLibrariesDialog && !showUpdateLog && !showPrivacyDialog) {
        onNavigateBack()
    }

    val appIconBitmap = remember(context) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
                ?: context.packageManager.getApplicationIcon(context.packageName)
            drawable.toBitmap(width = 192, height = 192).asImageBitmap()
        }.getOrNull()
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
    }

    fun shareApp() {
        runCatching {
            val shareText = "Check out Expense Tracker — a 100% offline-first, open-source personal finance app for Android: $GITHUB_REPO_URL"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, null))
        }
    }

    fun copyVersionToClipboard() {
        scope.launch {
            val clipEntry = ClipEntry(ClipData.newPlainText("Version", appVersion))
            clipboard.setClipEntry(clipEntry)
            snackbarHostState.showSnackbar(
                message = "Version copied to clipboard",
                duration = SnackbarDuration.Short
            )
        }
    }

    fun reportBug() {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        
        val crashFile = File(context.filesDir, "last_crash.txt")
        val crashLog = if (crashFile.exists()) {
            "\n\n**Last Recorded Crash Log**\n```\n${crashFile.readText().take(1500)}\n```"
        } else ""

        val body = """
            **App Version:** $appVersion
            **Device:** $manufacturer $model
            **Android Version:** $release (SDK $sdk)
            
            **Describe the bug**
            A clear and concise description of what the bug is.
            
            **To Reproduce**
            Steps to reproduce the behavior:
            1. Go to '...'
            2. Click on '....'
            3. See error$crashLog
        """.trimIndent()
        val url = "$GITHUB_REPO_URL/issues/new?body=${Uri.encode(body)}"
        openUrl(url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Card 1 — App Header Card
            item {
                Card(
                    onClick = { copyVersionToClipboard() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            onClick(label = "Copy app version", action = null)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "App Icon",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Expense Tracker",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$appVersion • Offline FOSS",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Card 2 — Developer Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "am",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "amuksith",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "DEVELOPER",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        Text(
                            text = "Creator & Lead Maintainer",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openUrl("https://github.com/amuksith") },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("@amuksith", maxLines = 1)
                            }
                            Button(
                                onClick = { openUrl(GITHUB_REPO_URL) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Star Project", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Section 1 — "Updates & Releases"
            item {
                Text(
                    text = "Updates & Releases",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Update Log") },
                    supportingContent = { Text("View recent changes and features") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                    modifier = Modifier.clickable { showUpdateLog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Check Releases on GitHub") },
                    supportingContent = { Text("Installed: $appVersion") },
                    leadingContent = { Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null) },
                    modifier = Modifier.clickable { openUrl("$GITHUB_REPO_URL/releases") }
                )
            }

            // Section 2 — "Project & Support"
            item {
                Text(
                    text = "Project & Support",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Report Bug & Feedback") },
                    supportingContent = { Text("Help improve the app") },
                    leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                    modifier = Modifier.clickable { reportBug() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Source Code on GitHub") },
                    supportingContent = { Text("View the complete source code") },
                    leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                    modifier = Modifier.clickable { openUrl(GITHUB_REPO_URL) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Share App") },
                    supportingContent = { Text("Recommend Expense Tracker to others") },
                    leadingContent = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    modifier = Modifier.clickable { shareApp() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Open-Source Libraries") },
                    supportingContent = { Text("View third-party software") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null) },
                    modifier = Modifier.clickable { showLibrariesDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Offline Privacy Policy") },
                    supportingContent = { Text("Learn how your data stays on your device") },
                    leadingContent = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                    modifier = Modifier.clickable { showPrivacyDialog = true }
                )
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showUpdateLog) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85).dp
        Dialog(onDismissRequest = { showUpdateLog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "What's New in $appVersion 🎉",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Squashed some pesky bugs, put recurring transactions on the calendar, and gave your money more ways to show up. — Yours, @amuksith",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val highlights = listOf(
                            "New Scheduled screen to manage recurring transactions",
                            "Schedule expenses, income, and wallet transfers",
                            "Daily, weekly, monthly, and custom schedule options",
                            "Expanded currency selection",
                            "Improved the overall UI experience"
                        )
                        highlights.forEach { highlight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showUpdateLog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (showLibrariesDialog) {
        val resources = context.applicationContext.resources
        val libraries by rememberLibraries {
            resources.openRawResource(R.raw.aboutlibraries).readBytes().decodeToString()
        }
        ModalBottomSheet(
            onDismissRequest = { showLibrariesDialog = false },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Open-Source Licenses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showLibrariesDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider()
                LibrariesContainer(
                    libraries = libraries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Offline Privacy Policy") },
            text = {
                Text("Expense Tracker respects your privacy.\n\n" +
                        "This app requests zero internet permissions and stores all financial data strictly inside the local on-device Room SQLite database.\n\n" +
                        "Your data never leaves your device unless you manually export a backup file.")
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun AboutScreenPreview() {
    MaterialTheme {
        AboutScreen(onNavigateBack = {})
    }
}

