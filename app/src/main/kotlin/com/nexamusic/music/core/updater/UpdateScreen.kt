package com.nexamusic.music.core.updater

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import com.nexamusic.music.BuildConfig
import com.nexamusic.music.R
import com.nexamusic.music.core.updater.downloadmanager.UpdateDownloadWorker
import com.nexamusic.music.ui.component.ErrorSnackbar
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.ios.compat.Icon
import com.nexamusic.music.ui.ios.compat.Text
import com.nexamusic.music.ui.ios.compat.Button
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(navController: androidx.navigation.NavHostController) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<NexaMusicUpdateStatus>(NexaMusicUpdateStatus.NoUpdate(BuildConfig.VERSION_NAME)) }
    var isDownloading by remember { mutableStateOf(false) }
    val downloadProgress = remember { mutableFloatStateOf(0f) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val currentVersion = BuildConfig.VERSION_NAME
    val autoUpdateCheckEnabled = getAutoUpdateCheckSetting(context)
    val isDarkTheme = AppTheme.colorScheme.background.luminance() < 0.5f
    val palette = UpdateScreenTokens.palette(isDarkTheme)

    // The resolved app Appearance theme controls status/navigation icon contrast.
    SideEffect {
        context.findUpdateScreenActivity()?.window?.let { window ->
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDarkTheme
                isAppearanceLightNavigationBars = !isDarkTheme
            }
        }
    }

    LaunchedEffect(Unit) { DownloadNotificationManager.initialize(context) }

    // Observe the existing WorkManager job and remove the observer when this screen leaves.
    val downloadWork = remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData("update_download")
    }
    DisposableEffect(downloadWork) {
        val observer = Observer<List<WorkInfo>> { workInfos ->
            val info = workInfos.firstOrNull() ?: return@Observer
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                    isDownloading = true
                    downloadError = null
                    downloadProgress.floatValue = info.progress.getFloat("progress", 0f).coerceIn(0f, 1f)
                }
                WorkInfo.State.SUCCEEDED -> {
                    isDownloading = false
                    isDownloadComplete = true
                    downloadProgress.floatValue = 1f
                    val filePath = info.outputData.getString("file_path")
                    downloadedFile = filePath?.let(::File)
                    if (downloadedFile?.exists() != true) {
                        isDownloadComplete = false
                        downloadError = context.getString(R.string.download_failed)
                    }
                }
                WorkInfo.State.FAILED -> {
                    isDownloading = false
                    isDownloadComplete = false
                    downloadError = context.getString(R.string.download_failed)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.download_failed))
                    }
                }
                WorkInfo.State.CANCELLED -> {
                    isDownloading = false
                    downloadProgress.floatValue = 0f
                }
                else -> Unit
            }
        }
        downloadWork.observeForever(observer)
        onDispose { downloadWork.removeObserver(observer) }
    }

    LaunchedEffect(isDownloadComplete, downloadedFile) {
        if (isDownloadComplete && downloadedFile?.exists() == false) {
            isDownloadComplete = false
            downloadedFile = null
            downloadProgress.floatValue = 0f
            downloadError = context.getString(R.string.download_failed)
        }
    }

    fun triggerUpdateCheck() {
        status = NexaMusicUpdateStatus.Checking
        downloadError = null
        scope.launch {
            delay(1000L)
            checkForUpdate(
                context = context,
                onSuccess = { tag, isAvailable, isBetaRelease, changelog, size, date, description, imageUrl, apkUrl ->
                    saveLastCheckedTime(
                        context,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a"))
                    )
                    saveUpdateAvailableState(context, isAvailable)
                    status = if (isAvailable) {
                        NexaMusicUpdateStatus.Available(
                            version = tag,
                            changelog = changelog,
                            size = size,
                            releaseDate = date,
                            description = description,
                            imageUrl = imageUrl,
                            apkUrl = apkUrl,
                            isBeta = isBetaRelease
                        )
                    } else {
                        NexaMusicUpdateStatus.NoUpdate(tag)
                    }
                },
                onError = {
                    status = NexaMusicUpdateStatus.Error(context.getString(R.string.cant_check_updates))
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (autoUpdateCheckEnabled) triggerUpdateCheck()
    }

    val available = status as? NexaMusicUpdateStatus.Available
    val version = when (status) {
        is NexaMusicUpdateStatus.Available -> (status as NexaMusicUpdateStatus.Available).version
        is NexaMusicUpdateStatus.NoUpdate -> (status as NexaMusicUpdateStatus.NoUpdate).version
        else -> currentVersion
    }
    val beta = available?.isBeta == true
    val cleanVersion = version.removePrefix("v").substringBefore("-")
    val summary = when {
        available != null -> "NexaMusic " + cleanVersion + (if (beta) " Beta" else "") +
            " — a major lyrics, interface, playback, and performance update."
        status is NexaMusicUpdateStatus.NoUpdate -> "You're already on the latest version of NexaMusic."
        status is NexaMusicUpdateStatus.Checking -> "Checking the latest NexaMusic release."
        else -> "We couldn't check for an update. Please try again."
    }

    val reduceMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    var entryStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entryStarted = true }
    val entryAlpha by animateFloatAsState(
        targetValue = if (entryStarted) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else UpdateScreenTokens.entryDurationMs),
        label = "updateFadeIn"
    )
    val entryRise by animateDpAsState(
        targetValue = if (entryStarted || reduceMotion) 0.dp else 16.dp,
        animationSpec = tween(if (reduceMotion) 0 else UpdateScreenTokens.entryDurationMs),
        label = "updateRiseIn"
    )

    var readyPopStarted by remember { mutableStateOf(false) }
    LaunchedEffect(isDownloadComplete) {
        if (isDownloadComplete) readyPopStarted = true else readyPopStarted = false
    }

    val mainAction: () -> Unit = {
        when {
            status is NexaMusicUpdateStatus.Error -> triggerUpdateCheck()
            available == null -> triggerUpdateCheck()
            isDownloadComplete -> {
                val file = downloadedFile
                if (file == null || !file.exists()) {
                    isDownloadComplete = false
                    downloadedFile = null
                    downloadProgress.floatValue = 0f
                    downloadError = context.getString(R.string.download_failed)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:" + context.packageName)
                    }
                    context.startActivity(intent)
                } else {
                    val uri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".FileProvider",
                        file
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ContextCompat.startActivity(context, installIntent, null)
                }
            }
            !isDownloading -> {
                downloadError = null
                val url = available.apkUrl ?: (
                    "https://github.com/nkosanamahungela-dev/NexaMusic/releases/download/" +
                        available.version + "/nexamusic-" + available.version + ".apk"
                    )
                val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            "apk_url" to url,
                            "version" to available.version,
                            "file_size" to available.size
                        )
                    )
                    .addTag("update_download")
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "update_download",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                isDownloading = true
            }
        }
    }

    val mainLabel = when {
        isDownloading -> ""
        isDownloadComplete -> "Install now"
        downloadError != null || status is NexaMusicUpdateStatus.Error -> "Try again"
        available != null -> "Update"
        status is NexaMusicUpdateStatus.Checking -> "Checking…"
        else -> stringResource(R.string.check_for_update)
    }
    val mainIcon: Int? = when {
        isDownloading -> null
        isDownloadComplete -> UpdateScreenIcons.install
        else -> UpdateScreenIcons.update
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.page)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .graphicsLayer {
                    alpha = entryAlpha
                    translationY = entryRise.toPx()
                },
            contentPadding = PaddingValues(
                start = UpdateScreenTokens.pageSideMargin,
                end = UpdateScreenTokens.pageSideMargin,
                top = 8.dp,
                bottom = 188.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "update-hero") {
                UpdateHeroCard(
                    version = version,
                    isBeta = beta,
                    status = status,
                    isDownloading = isDownloading,
                    progressState = downloadProgress,
                    isDownloadComplete = isDownloadComplete,
                    downloadError = downloadError,
                    releaseDate = available?.releaseDate.orEmpty(),
                    releaseSize = available?.size.orEmpty(),
                    reduceMotion = reduceMotion,
                    readyPopStarted = readyPopStarted
                )
            }
            item(key = "update-summary") {
                Text(
                    text = summary,
                    modifier = Modifier.fillMaxWidth(),
                    color = palette.secondaryText,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = UpdateScreenTokens.summaryFontSize,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (available != null && available.changelog.isNotEmpty()) {
                item(key = "update-label") {
                    Text(
                        text = "WHAT'S NEW",
                        color = palette.sectionLabel,
                        fontFamily = UpdateScreenTokens.bodyFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                }
                item(key = "update-notes") {
                    UpdateReleaseNotesCard(available.changelog, palette)
                }
            }
        }

        // The page fades only into the fixed bottom action area. No blur or overlay
        // is drawn over the status bar, version label or mint hero card.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(UpdateScreenTokens.bottomFadeHeight)
                    .background(Brush.verticalGradient(listOf(UpdateScreenTokens.transparent, palette.page)))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UpdateScreenTokens.pageSideMargin)
                    .padding(bottom = UpdateScreenTokens.bottomEdgeSpacing),
                horizontalArrangement = Arrangement.spacedBy(UpdateScreenTokens.actionGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UpdateLaterButton(
                    modifier = Modifier.weight(1f),
                    palette = palette,
                    enabled = !isDownloading,
                    onClick = { navController.navigateUp() }
                )
                UpdateMainButton(
                    modifier = Modifier.weight(1.4f),
                    label = mainLabel,
                    icon = mainIcon,
                    progressState = if (isDownloading) downloadProgress else null,
                    enabled = !isDownloading && status !is NexaMusicUpdateStatus.Checking,
                    reduceMotion = reduceMotion,
                    palette = palette,
                    onClick = mainAction
                )
            }
        }

        ErrorSnackbar(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 126.dp)
        )
    }
}

/** Existing N vector asset, unchanged in shape and tinted with the hero foreground. */
@Composable
private fun NexaLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.nexamusic_logo_mark),
        contentDescription = null,
        tint = UpdateScreenTokens.heroInk,
        modifier = modifier.size(UpdateScreenTokens.logoSize)
    )
}

@Composable
private fun UpdateHeroCard(
    version: String,
    isBeta: Boolean,
    status: NexaMusicUpdateStatus,
    isDownloading: Boolean,
    progressState: State<Float>,
    isDownloadComplete: Boolean,
    downloadError: String?,
    releaseDate: String,
    releaseSize: String,
    reduceMotion: Boolean,
    readyPopStarted: Boolean
) {
    val progress = progressState.value
    val displayVersion = "v" + version.removePrefix("v").substringBefore("-")
    val fontScale = LocalDensity.current.fontScale
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UpdateScreenTokens.heroMinHeight)
            .clip(RoundedCornerShape(UpdateScreenTokens.heroCorner))
            .background(UpdateScreenTokens.hero)
            .padding(horizontal = UpdateScreenTokens.heroHorizontalPadding, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (status is NexaMusicUpdateStatus.Available) "NEW UPDATE" else "NEXAMUSIC UPDATE",
                        color = UpdateScreenTokens.heroInk,
                        fontFamily = UpdateScreenTokens.bodyFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    if (isBeta) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(UpdateScreenTokens.heroInk)
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "BETA",
                                color = UpdateScreenTokens.hero,
                                fontFamily = UpdateScreenTokens.bodyFont,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }
            NexaLogo()
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fittedSize = minOf(
                UpdateScreenTokens.versionMaxFontSize.value,
                maxWidth.value / (displayVersion.length.coerceAtLeast(1) * 0.60f * fontScale)
            ).coerceAtLeast(34f)
            Text(
                text = displayVersion,
                modifier = Modifier.semantics { heading() },
                color = UpdateScreenTokens.heroInk,
                fontFamily = UpdateScreenTokens.displayFont,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fittedSize.sp,
                letterSpacing = (fittedSize * -0.04f).sp,
                maxLines = 1,
                softWrap = false
            )
        }

        Spacer(Modifier.weight(1f))

        when {
            downloadError != null -> {
                Text(
                    text = downloadError,
                    color = UpdateScreenTokens.heroInk,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = UpdateScreenTokens.bodyFontSize,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )
            }
            isDownloadComplete -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ready to install",
                        color = UpdateScreenTokens.heroInk,
                        fontFamily = UpdateScreenTokens.bodyFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    val readyScale by animateFloatAsState(
                        targetValue = if (reduceMotion) 1f else if (readyPopStarted) 1f else 0.6f,
                        animationSpec = if (reduceMotion) tween(0) else keyframes {
                            durationMillis = UpdateScreenTokens.readyPopDurationMs
                            0.6f at 0
                            1.1f at 90
                            1f at 220
                        },
                        label = "readyCheckPop"
                    )
                    Icon(
                        painter = painterResource(UpdateScreenIcons.install),
                        contentDescription = null,
                        tint = UpdateScreenTokens.heroInk,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = readyScale
                                scaleY = readyScale
                            }
                    )
                }
                Spacer(Modifier.height(8.dp))
                UpdateHeroProgressBar(1f, reduceMotion)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Download complete · " + formatUpdateSize(releaseSize),
                    color = UpdateScreenTokens.heroInk.copy(alpha = 0.78f),
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = 13.sp
                )
            }
            isDownloading -> {
                val percent = (progress * 100f).toInt().coerceIn(0, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloading…",
                        color = UpdateScreenTokens.heroInk,
                        fontFamily = UpdateScreenTokens.bodyFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = percent.toString() + "%",
                        color = UpdateScreenTokens.heroInk,
                        fontFamily = UpdateScreenTokens.bodyFont,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                    )
                }
                Spacer(Modifier.height(8.dp))
                UpdateHeroProgressBar(progress, reduceMotion)
                Spacer(Modifier.height(6.dp))
                val totalMb = releaseSize.removeSuffix(" MB").toDoubleOrNull()
                val downloadedMb = totalMb?.times(progress.coerceIn(0f, 1f))
                val line = if (totalMb != null && downloadedMb != null) {
                    String.format("%.1f MB of %.1f MB", downloadedMb, totalMb)
                } else {
                    percent.toString() + "% downloaded"
                }
                Text(
                    text = line,
                    color = UpdateScreenTokens.heroInk.copy(alpha = 0.78f),
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = 13.sp
                )
            }
            status is NexaMusicUpdateStatus.Available -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UpdateHeroPill(formatUpdateDate(releaseDate))
                    if (releaseSize.isNotBlank()) UpdateHeroPill(formatUpdateSize(releaseSize))
                }
            }
            status is NexaMusicUpdateStatus.Checking -> {
                Text(
                    text = "Checking for updates…",
                    color = UpdateScreenTokens.heroInk,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            status is NexaMusicUpdateStatus.Error -> {
                Text(
                    text = (status as NexaMusicUpdateStatus.Error).message,
                    color = UpdateScreenTokens.heroInk,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = UpdateScreenTokens.bodyFontSize,
                    fontWeight = FontWeight.SemiBold
                )
            }
            else -> {
                Text(
                    text = "You're on the latest version",
                    color = UpdateScreenTokens.heroInk,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UpdateHeroPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, UpdateScreenTokens.heroInk.copy(alpha = 0.38f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = UpdateScreenTokens.heroInk,
            fontFamily = UpdateScreenTokens.bodyFont,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun UpdateHeroProgressBar(progress: Float, reduceMotion: Boolean) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else UpdateScreenTokens.progressDurationMs,
            easing = LinearEasing
        ),
        label = "updateHeroProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UpdateScreenTokens.heroProgressHeight)
            .clip(RoundedCornerShape(50))
            .background(UpdateScreenTokens.heroInk.copy(alpha = 0.18f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                contentDescription = "Downloading, " + (progress.coerceIn(0f, 1f) * 100).toInt() + " percent"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(UpdateScreenTokens.heroInk)
        )
    }
}

@Composable
private fun UpdateReleaseNotesCard(
    sections: List<ChangelogSection>,
    palette: UpdateScreenTokens.Palette
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UpdateScreenTokens.noteCardCorner))
            .background(palette.noteCard)
            .border(1.dp, palette.noteBorder, RoundedCornerShape(UpdateScreenTokens.noteCardCorner))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        var noteIndex = 0
        sections.forEachIndexed { sectionIndex, section ->
            if (section.title.isNotBlank()) {
                Text(
                    text = section.title,
                    color = palette.sectionLabel,
                    fontFamily = UpdateScreenTokens.bodyFont,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(
                        top = if (sectionIndex == 0) 10.dp else 16.dp,
                        bottom = 4.dp
                    )
                )
            }
            section.items.forEach { note ->
                if (noteIndex > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.divider)
                    )
                }
                UpdateReleaseNoteRow(
                    text = note,
                    icon = UpdateScreenIcons.noteIcon(noteIndex),
                    tileColor = UpdateScreenTokens.noteTileColors[noteIndex.coerceAtMost(5)],
                    textColor = palette.text,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                noteIndex += 1
            }
        }
    }
}

@Composable
private fun UpdateReleaseNoteRow(
    text: String,
    icon: Int,
    tileColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UpdateScreenTokens.actionGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(UpdateScreenTokens.noteTileSize)
                .clip(RoundedCornerShape(UpdateScreenTokens.noteTileCorner))
                .background(tileColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = UpdateScreenTokens.white,
                modifier = Modifier.size(UpdateScreenTokens.noteIconSize)
            )
        }
        Text(
            text = text,
            color = textColor,
            fontFamily = UpdateScreenTokens.bodyFont,
            fontSize = 14.sp,
            lineHeight = 19.3.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UpdateLaterButton(
    modifier: Modifier,
    palette: UpdateScreenTokens.Palette,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "laterPressScale"
    )
    Box(
        modifier = modifier
            .height(UpdateScreenTokens.actionHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(palette.glass)
            .border(1.dp, palette.glassBorder, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.later),
            color = palette.text,
            fontFamily = UpdateScreenTokens.bodyFont,
            fontSize = UpdateScreenTokens.buttonFontSize,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UpdateMainButton(
    modifier: Modifier,
    label: String,
    icon: Int?,
    progressState: State<Float>?,
    enabled: Boolean,
    reduceMotion: Boolean,
    palette: UpdateScreenTokens.Palette,
    onClick: () -> Unit
) {
    val progress = progressState?.value
    val visibleLabel = if (progress != null) (progress * 100f).toInt().coerceIn(0, 100).toString() + "%" else label
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "updateButtonPressScale"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else UpdateScreenTokens.progressDurationMs,
            easing = LinearEasing
        ),
        label = "updateButtonProgress"
    )
    Box(
        modifier = modifier
            .height(UpdateScreenTokens.actionHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (progress == null) UpdateScreenTokens.actionRed
                else UpdateScreenTokens.actionRed.copy(alpha = 0.28f)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .semantics {
                if (progress != null) {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                    contentDescription = "Downloading, " + (progress.coerceIn(0f, 1f) * 100).toInt() + " percent"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(UpdateScreenTokens.actionRed)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = UpdateScreenTokens.white,
                    modifier = Modifier.size(UpdateScreenTokens.actionIconSize)
                )
            }
            Text(
                text = visibleLabel,
                color = UpdateScreenTokens.white,
                fontFamily = UpdateScreenTokens.bodyFont,
                fontSize = UpdateScreenTokens.buttonFontSize,
                fontWeight = FontWeight.Bold,
                style = if (progress != null) androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum") else androidx.compose.ui.text.TextStyle.Default,
                maxLines = 1
            )
        }
    }
}

private fun formatUpdateSize(value: String): String {
    if (value.isBlank()) return ""
    return if (value.contains("MB", ignoreCase = true)) value else value + " MB"
}

private fun formatUpdateDate(value: String): String {
    if (value.isBlank()) return ""
    return runCatching {
        val input = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMMM yyyy, h:mm a")
            .toFormatter()
        LocalDateTime.parse(value, input)
            .format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a"))
            .lowercase()
    }.getOrDefault(value)
}

/** Resolve the Activity through Compose's ContextThemeWrapper for system-bar control. */
private tailrec fun Context.findUpdateScreenActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findUpdateScreenActivity()
    else -> null
}
