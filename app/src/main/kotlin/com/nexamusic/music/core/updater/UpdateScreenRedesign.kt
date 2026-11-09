package com.nexamusic.music.core.updater

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Observer
import androidx.navigation.NavHostController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.nexamusic.music.BuildConfig
import com.nexamusic.music.R
import com.nexamusic.music.core.updater.downloadmanager.UpdateDownloadWorker
import com.nexamusic.music.core.updater.downloadmanager.DownloadNotificationManager
import com.nexamusic.music.ui.ios.compat.AppTheme
import com.nexamusic.music.ui.component.GlassComponent
import com.nexamusic.music.ui.component.LocalGlassEffectConfig
import com.nexamusic.music.ui.component.isGlassAllowed
import com.nexamusic.music.ui.component.liquidGlass
import com.nexamusic.music.ui.component.withFixedStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Redesigned update page. All update-check, WorkManager download and APK
 * installation actions remain connected to the existing updater functions.
 */
@Composable
fun UpdateScreen(navController: NavHostController) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pageColor = AppTheme.colorScheme.background
    val darkTheme = pageColor.luminance() < 0.5f
    val palette = if (darkTheme) UpdateScreenTokens.Dark else UpdateScreenTokens.Light

    var status by remember {
        mutableStateOf<NexaMusicUpdateStatus>(NexaMusicUpdateStatus.NoUpdate(BuildConfig.VERSION_NAME))
    }
    var isDownloading by remember { mutableStateOf(false) }
    val downloadProgressState = remember { mutableFloatStateOf(0f) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workLiveData = remember(workManager) {
        workManager.getWorkInfosForUniqueWorkLiveData("update_download")
    }

    // Observe the existing worker without leaking an observeForever observer.
    DisposableEffect(workLiveData) {
        val observer = Observer<List<WorkInfo>> { infos ->
            val info = infos?.firstOrNull() ?: return@Observer
            when (info.state) {
                WorkInfo.State.RUNNING -> {
                    isDownloading = true
                    isDownloadComplete = false
                    downloadError = null
                    downloadProgressState.floatValue = info.progress.getFloat("progress", 0f).coerceIn(0f, 1f)
                }
                WorkInfo.State.SUCCEEDED -> {
                    isDownloading = false
                    isDownloadComplete = true
                    downloadProgressState.floatValue = 1f
                    val path = info.outputData.getString("file_path")
                    downloadedFile = path?.let(::File)
                }
                WorkInfo.State.FAILED -> {
                    isDownloading = false
                    isDownloadComplete = false
                    downloadError = info.outputData.getString("error")
                        ?: context.getString(R.string.download_failed)
                }
                WorkInfo.State.CANCELLED -> {
                    isDownloading = false
                    downloadProgressState.floatValue = 0f
                }
                else -> Unit
            }
        }
        workLiveData.observeForever(observer)
        onDispose { workLiveData.removeObserver(observer) }
    }

    LaunchedEffect(isDownloadComplete, downloadedFile) {
        if (isDownloadComplete && downloadedFile?.exists() == false) {
            isDownloadComplete = false
            downloadedFile = null
            downloadProgressState.floatValue = 0f
        }
    }

    val autoUpdateCheckEnabled = remember(context) { getAutoUpdateCheckSetting(context) }

    fun triggerUpdateCheck() {
        status = NexaMusicUpdateStatus.Checking
        downloadError = null
        scope.launch {
            delay(350L)
            checkForUpdate(
                context = context,
                onSuccess = { tag, available, changelog, size, date, description, imageUrl, apkUrl ->
                    saveLastCheckedTime(
                        context,
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a"))
                    )
                    saveUpdateAvailableState(context, available)
                    status = if (available) {
                        NexaMusicUpdateStatus.Available(
                            version = tag,
                            changelog = changelog,
                            size = size,
                            releaseDate = date,
                            description = description,
                            imageUrl = imageUrl,
                            apkUrl = apkUrl
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
        DownloadNotificationManager.initialize(context)
        if (autoUpdateCheckEnabled) triggerUpdateCheck()
    }

    // Use the app's resolved theme (including its Appearance override) for
    // system-bar icon contrast. The status-bar background stays plain page color.
    val activity = remember(context) { context.findActivity() }
    androidx.compose.runtime.SideEffect {
        activity?.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val reduceMotion = remember {
        if (Build.VERSION.SDK_INT >= 26) !ValueAnimator.areAnimatorsEnabled() else false
    }
    val entrance = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(1f, tween(UpdateScreenTokens.OpenDurationMs, easing = LinearEasing))
        }
    }

    val available = status as? NexaMusicUpdateStatus.Available
    val notes = available?.changelog.orEmpty().flatMap { it.items }
    val totalSizeLabel = available?.size?.takeIf { it.isNotBlank() } ?: "119.4 MB"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = UpdateScreenTokens.SideMargin,
                    end = UpdateScreenTokens.SideMargin,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 132.dp
                )
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = if (reduceMotion) 0f else (1f - entrance.value) * 16.dp.toPx()
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val current = status) {
                is NexaMusicUpdateStatus.Available -> {
                    UpdateHeroCard(
                        version = current.version,
                        isBeta = current.version.contains("beta", true) ||
                            current.version.contains("nightly", true),
                        releaseDate = current.releaseDate,
                        size = totalSizeLabel,
                        downloading = isDownloading,
                        progressState = downloadProgressState,
                        ready = isDownloadComplete,
                        error = downloadError,
                        palette = palette,
                        reduceMotion = reduceMotion
                    )

                    Text(
                        text = "NexaMusic ${current.version.removePrefix("v")} Beta — a major lyrics, interface, playback, and performance update.",
                        fontFamily = UpdateScreenTokens.BodyFont,
                        fontSize = UpdateScreenTokens.SummarySize,
                        color = palette.secondaryText,
                        lineHeight = 21.sp,
                        maxLines = 2
                    )

                    UpdateSectionLabel("WHAT'S NEW", palette.sectionLabel)
                    ReleaseNotesCard(
                        notes = notes,
                        palette = palette
                    )
                }

                is NexaMusicUpdateStatus.Checking -> {
                    EmptyUpdateState(
                        title = "Checking for updates",
                        detail = "Looking for the latest NexaMusic release.",
                        palette = palette,
                        loading = true
                    )
                }

                is NexaMusicUpdateStatus.NoUpdate -> {
                    EmptyUpdateState(
                        title = context.getString(R.string.on_latest_version),
                        detail = context.getString(R.string.current_version_v, current.version),
                        palette = palette,
                        loading = false
                    )
                }

                is NexaMusicUpdateStatus.Error -> {
                    UpdateHeroCard(
                        version = BuildConfig.VERSION_NAME,
                        isBeta = false,
                        releaseDate = "",
                        size = "",
                        downloading = false,
                        progressState = downloadProgressState,
                        ready = false,
                        error = current.message,
                        palette = palette,
                        reduceMotion = reduceMotion
                    )
                }

                NexaMusicUpdateStatus.Idle -> {
                    EmptyUpdateState(
                        title = stringResource(R.string.settings_check_updates_title),
                        detail = "",
                        palette = palette,
                        loading = false
                    )
                }
            }
        }

        // Bottom fade is intentionally below the status bar and above the fixed
        // action row; it never overlays the hero title or version.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, pageColor.copy(alpha = 0.94f), pageColor)
                    )
                )
                .padding(
                    start = UpdateScreenTokens.SideMargin,
                    end = UpdateScreenTokens.SideMargin,
                    top = 28.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 30.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(UpdateScreenTokens.ButtonGap)
            ) {
                LaterGlassButton(
                    modifier = Modifier.weight(1f),
                    palette = palette,
                    onClick = { navController.navigateUp() }
                )

                UpdateMainButton(
                    modifier = Modifier.weight(1.4f),
                    label = when {
                        status is NexaMusicUpdateStatus.Error || downloadError != null -> "Try again"
                        isDownloading -> ""
                        isDownloadComplete -> "Install now"
                        status is NexaMusicUpdateStatus.Available -> "Update"
                        status is NexaMusicUpdateStatus.Checking -> "Checking…"
                        else -> stringResource(R.string.check_for_update)
                    },
                    icon = when {
                        isDownloadComplete -> UpdateScreenIcons.install
                        isDownloading -> null
                        status is NexaMusicUpdateStatus.Available -> UpdateScreenIcons.update
                        else -> null
                    },
                    progressState = if (isDownloading) downloadProgressState else null,
                    enabled = status !is NexaMusicUpdateStatus.Checking && (!isDownloading || isDownloadComplete),
                    onClick = {
                        val currentStatus = status
                        when {
                            downloadError != null || currentStatus is NexaMusicUpdateStatus.Error -> {
                                if (currentStatus is NexaMusicUpdateStatus.Error) {
                                    triggerUpdateCheck()
                                } else {
                                    downloadError = null
                                    isDownloading = false
                                    val release = available
                                    if (release != null) enqueueUpdateDownload(context, release)
                                }
                            }
                            isDownloadComplete -> {
                                val file = downloadedFile
                                if (file == null || !file.exists()) {
                                    isDownloadComplete = false
                                    downloadedFile = null
                                    downloadProgressState.floatValue = 0f
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                                        !context.packageManager.canRequestPackageInstalls()
                                    ) {
                                        val permissionIntent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                                        ).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(permissionIntent)
                                    } else {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.FileProvider",
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
                            }
                            currentStatus is NexaMusicUpdateStatus.Available -> enqueueUpdateDownload(context, currentStatus)
                            else -> triggerUpdateCheck()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UpdateHeroCard(
    version: String,
    isBeta: Boolean,
    releaseDate: String,
    size: String,
    downloading: Boolean,
    progressState: State<Float>,
    ready: Boolean,
    error: String?,
    palette: UpdateScreenTokens.Palette,
    reduceMotion: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UpdateScreenTokens.HeroCorner),
        color = UpdateScreenTokens.Hero,
        border = BorderStroke(1.dp, UpdateScreenTokens.HeroBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = UpdateScreenTokens.HeroMinHeight)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "NEW UPDATE",
                        color = UpdateScreenTokens.HeroInk,
                        fontFamily = UpdateScreenTokens.BodyFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    if (isBeta) {
                        Surface(
                            color = UpdateScreenTokens.HeroInk,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "BETA",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                color = UpdateScreenTokens.Hero,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                NexaLogo()
            }

            val fontScale = LocalDensity.current.fontScale
            Text(
                text = if (version.startsWith("v")) version else "v$version",
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { heading() },
                color = UpdateScreenTokens.HeroInk,
                fontFamily = UpdateScreenTokens.DisplayFont,
                fontSize = (UpdateScreenTokens.VersionSize.value / fontScale.coerceAtLeast(1f)).sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2.6).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )

            Spacer(Modifier.weight(1f))

            when {
                error != null -> Text(
                    text = error,
                    color = UpdateScreenTokens.HeroInk,
                    fontFamily = UpdateScreenTokens.BodyFont,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 19.sp
                )

                ready -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ready to install",
                            color = UpdateScreenTokens.HeroInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        val checkScale = remember(reduceMotion) {
                            Animatable(if (reduceMotion) 1f else 0.6f)
                        }
                        LaunchedEffect(checkScale) {
                            if (!reduceMotion) {
                                checkScale.animateTo(1.1f, tween(120))
                                checkScale.animateTo(1f, tween(100))
                            }
                        }
                        Icon(
                            imageVector = UpdateScreenIcons.ready,
                            contentDescription = null,
                            tint = UpdateScreenTokens.HeroInk,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = checkScale.value
                                    scaleY = checkScale.value
                                }
                        )
                    }
                    HeroProgressBar(progress = 1f)
                    Text(
                        "Download complete · $size",
                        color = UpdateScreenTokens.HeroInk.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }

                downloading -> DownloadingHeroState(progressState = progressState, size = size)

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HeroMetaPill(compactReleaseDate(releaseDate), Modifier.weight(1.15f))
                        HeroMetaPill(size, Modifier.weight(0.85f))
                    }
                }
            }
        }
    }
}

/** Existing single-colour NexaMusic N mark, tinted without changing its path. */

@Composable
private fun DownloadingHeroState(
    progressState: State<Float>,
    size: String
) {
    val progress = progressState.value.coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt().coerceIn(0, 100)
    val downloadedMb = totalSizeInMb(size) * progress
    val downloadedSize = String.format(Locale.getDefault(), "%.1f MB of %s", downloadedMb, size)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloading…",
                color = UpdateScreenTokens.HeroInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$percent%",
                color = UpdateScreenTokens.HeroInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
            )
        }
        HeroProgressBar(progress = progress)
        Text(
            downloadedSize,
            color = UpdateScreenTokens.HeroInk.copy(alpha = 0.78f),
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 7.dp)
        )
    }
}

@Composable
private fun NexaLogo() {
    Icon(
        painter = painterResource(R.drawable.nexamusic_logo_mark),
        contentDescription = "NexaMusic",
        tint = UpdateScreenTokens.HeroInk,
        modifier = Modifier.size(width = 92.dp, height = 92.dp)
    )
}

@Composable
private fun HeroMetaPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Transparent)
            .then(Modifier)
            .padding(0.dp)
    ) {
        Surface(
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, UpdateScreenTokens.HeroInk.copy(alpha = 0.38f)),
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                color = UpdateScreenTokens.HeroInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeroProgressBar(progress: Float) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(UpdateScreenTokens.ProgressDurationMs, easing = LinearEasing),
        label = "heroDownloadProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(UpdateScreenTokens.HeroInk.copy(alpha = 0.18f))
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    animated,
                    0f..1f,
                    (animated * 100).roundToInt()
                )
                contentDescription = if (animated >= 1f) "Download complete" else
                    "Downloading, ${(animated * 100).roundToInt()} percent"
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .graphicsLayer {
                    scaleX = animated
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(RoundedCornerShape(50))
                .background(UpdateScreenTokens.HeroInk)
        )
    }
}

@Composable
private fun UpdateSectionLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontFamily = UpdateScreenTokens.BodyFont,
        fontSize = UpdateScreenTokens.SectionSize,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
    )
}

@Composable
private fun ReleaseNotesCard(
    notes: List<String>,
    palette: UpdateScreenTokens.Palette
) {
    val tileColors = listOf(
        UpdateScreenTokens.TileLyrics,
        UpdateScreenTokens.TileDuets,
        UpdateScreenTokens.TileInterludes,
        UpdateScreenTokens.TileLook,
        UpdateScreenTokens.TileSync,
        UpdateScreenTokens.TileFix
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(UpdateScreenTokens.NoteCardCorner),
        color = palette.card,
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            notes.forEachIndexed { index, note ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.divider)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(UpdateScreenTokens.NoteTileSize)
                            .clip(RoundedCornerShape(UpdateScreenTokens.NoteTileCorner))
                            .background(tileColors.getOrElse(index) { UpdateScreenTokens.TileFix }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = UpdateScreenIcons.noteIcons.getOrElse(index) { UpdateScreenIcons.fix },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(UpdateScreenTokens.NoteIconSize)
                        )
                    }
                    Text(
                        text = note,
                        modifier = Modifier.weight(1f),
                        color = palette.primaryText,
                        fontFamily = UpdateScreenTokens.BodyFont,
                        fontSize = UpdateScreenTokens.BodySize,
                        lineHeight = 19.3.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LaterGlassButton(
    modifier: Modifier,
    palette: UpdateScreenTokens.Palette,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        tween(UpdateScreenTokens.PressDurationMs),
        label = "laterPressedScale"
    )
    val glassConfig = LocalGlassEffectConfig.current.withFixedStyle().forAccessory()
    val useLiquidGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(UpdateScreenTokens.ButtonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (useLiquidGlass) {
                    Modifier.liquidGlass(config = glassConfig, shape = RoundedCornerShape(50))
                } else {
                    Modifier.background(palette.glass, RoundedCornerShape(50))
                }
            ),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        contentColor = palette.primaryText,
        border = BorderStroke(1.dp, palette.glassBorder),
        interactionSource = interaction
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "Later",
                color = palette.primaryText,
                fontFamily = UpdateScreenTokens.BodyFont,
                fontSize = UpdateScreenTokens.ButtonSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UpdateMainButton(
    modifier: Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    progressState: State<Float>?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        tween(UpdateScreenTokens.PressDurationMs),
        label = "updatePressedScale"
    )
    val progress = progressState?.value
    val buttonLabel = if (progress != null) "${(progress * 100f).roundToInt()}%" else label
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(UpdateScreenTokens.ProgressDurationMs, easing = LinearEasing),
        label = "buttonDownloadProgress"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(UpdateScreenTokens.ButtonHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(50),
        color = if (progress != null) UpdateScreenTokens.Action.copy(alpha = 0.28f)
            else UpdateScreenTokens.Action,
        contentColor = Color.White,
        interactionSource = interaction
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = animatedProgress
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(UpdateScreenTokens.Action)
                        .align(Alignment.CenterStart)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(UpdateScreenTokens.ButtonIconSize)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = buttonLabel,
                    color = Color.White,
                    fontFamily = UpdateScreenTokens.BodyFont,
                    fontSize = UpdateScreenTokens.ButtonSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
            }
        }
    }
}

@Composable
private fun EmptyUpdateState(
    title: String,
    detail: String,
    palette: UpdateScreenTokens.Palette,
    loading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                color = UpdateScreenTokens.Action,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = UpdateScreenTokens.TileFix,
                modifier = Modifier.size(64.dp)
            )
        }
        Text(
            title,
            color = palette.primaryText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = UpdateScreenTokens.BodyFont
        )
        if (detail.isNotBlank()) {
            Text(
                detail,
                color = palette.secondaryText,
                fontSize = 14.sp,
                fontFamily = UpdateScreenTokens.BodyFont
            )
        }
    }
}

private fun enqueueUpdateDownload(
    context: Context,
    release: NexaMusicUpdateStatus.Available
) {
    val url = release.apkUrl
        ?: "https://github.com/nkosanamahungela-dev/NexaMusic/releases/download/${release.version}/nexamusic-${release.version}.apk"
    val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
        .setInputData(
            workDataOf(
                "apk_url" to url,
                "version" to release.version,
                "file_size" to release.size
            )
        )
        .addTag("update_download")
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "update_download",
        ExistingWorkPolicy.REPLACE,
        request
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}


/** Converts the updater's existing long date string to the compact hero-pill format. */
private fun compactReleaseDate(value: String): String = try {
    LocalDateTime.parse(
        value,
        DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", Locale.getDefault())
    ).format(DateTimeFormatter.ofPattern("d MMM yyyy · h:mm a", Locale.getDefault()))
} catch (_: Exception) {
    value
}


private fun totalSizeInMb(label: String): Float =
    Regex("""([\d]+(?:\.[\d]+)?)""").find(label)
        ?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 119.4f
