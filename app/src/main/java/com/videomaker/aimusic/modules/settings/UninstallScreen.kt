package com.videomaker.aimusic.modules.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.alcheclub.lib.acccore.ads.compose.NativeAdView
import coil.compose.AsyncImage
import coil.decode.BitmapFactoryDecoder
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.videomaker.aimusic.R
import com.videomaker.aimusic.core.analytics.Analytics
import com.videomaker.aimusic.core.analytics.AnalyticsEvent
import com.videomaker.aimusic.core.constants.AdPlacement
import com.videomaker.aimusic.domain.model.MusicSong
import com.videomaker.aimusic.domain.model.VideoTemplate
import com.videomaker.aimusic.media.audio.AudioPreviewCache
import com.videomaker.aimusic.modules.songs.MusicPlayerBottomSheet
import com.videomaker.aimusic.ui.components.AppAsyncImage
import com.videomaker.aimusic.ui.components.ModifierExtension.clickableSingle
import com.videomaker.aimusic.ui.components.ShimmerPlaceholder
import com.videomaker.aimusic.ui.theme.AppDimens
import com.videomaker.aimusic.ui.theme.FoundationBlack
import com.videomaker.aimusic.ui.theme.FoundationBlack_100
import com.videomaker.aimusic.ui.theme.Neutral_N100
import com.videomaker.aimusic.ui.theme.Neutral_N800
import com.videomaker.aimusic.ui.theme.Primary
import com.videomaker.aimusic.ui.theme.Primary_N500
import com.videomaker.aimusic.ui.theme.TextPrimary
import com.videomaker.aimusic.ui.theme.TextSecondary
import com.videomaker.aimusic.ui.utils.innerShadowCustom
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallScreen(
    viewModel: UninstallViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTemplatePreviewer: (templateId: String) -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToAllSongs: () -> Unit,
    onNavigateToTemplatePreviewerWithSong: (songId: Long) -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigationEvent by viewModel.navigationEvent.collectAsStateWithLifecycle()
    val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
    val audioPreviewCache: AudioPreviewCache = koinInject()

    LaunchedEffect(Unit) {
        Analytics.trackUninstallView()
    }

    LaunchedEffect(navigationEvent) {
        navigationEvent?.let { event ->
            when (event) {
                is UninstallNavigationEvent.NavigateBack -> onNavigateBack()
                is UninstallNavigationEvent.NavigateToTemplatePreviewer -> onNavigateToTemplatePreviewer(event.templateId)
                is UninstallNavigationEvent.NavigateToTemplates -> onNavigateToTemplates()
                is UninstallNavigationEvent.NavigateToAllSongs -> onNavigateToAllSongs()
                is UninstallNavigationEvent.NavigateToTemplatePreviewerWithSong -> onNavigateToTemplatePreviewerWithSong(event.songId)
            }
            viewModel.onNavigationHandled()
        }
    }

    selectedSong?.let { song ->
        MusicPlayerBottomSheet(
            song = song,
            cacheDataSourceFactory = audioPreviewCache.cacheDataSourceFactory,
            location = AnalyticsEvent.Value.Location.UNINSTALL,
            onDismiss = viewModel::onDismissPlayer,
            onUseToCreate = { viewModel.onUseToCreateVideo(song) }
        )
    }

    val uninstallData = when (val state = uiState) {
        is UninstallUiState.Success -> state.data
        else -> UninstallData()
    }
    // Pad to fixed counts so the row layout is stable during loading
    val displayTemplates: List<VideoTemplate?> = uninstallData.likedTemplates.take(3).let { list ->
        list + List(3 - list.size) { null }
    }
    val displaySongs: List<MusicSong?> = uninstallData.likedSongs.take(2).let { list ->
        list + List(2 - list.size) { null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.uninstall_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.W600,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        Analytics.trackUninstallCtaClick(type = "dont_uninstall")
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FoundationBlack
                )
            )
        },
        containerColor = FoundationBlack,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.uninstall_description),
                fontSize = 14.sp,
                color = FoundationBlack_100,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White.copy(0.08f), RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                // ---- Liked Templates header ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lead_search),
                        contentDescription = null,
                        tint = Primary_N500,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.like_template_section_header),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::onSeeMoreTemplatesClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "See all",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // ---- Liked Templates row ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displayTemplates.forEach { template ->
                        if (template != null) {
                            TemplateCard(
                                template = template,
                                onClick = viewModel::onTemplateClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            ShimmerPlaceholder(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                cornerRadius = 12.dp
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(0.08f))
                )

                // ---- Liked Songs header ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lead_search),
                        contentDescription = null,
                        tint = Primary_N500,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_tab_songs),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = viewModel::onSeeMoreSongsClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "See all",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // ---- Liked Songs row ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displaySongs.forEach { song ->
                        if (song != null) {
                            SongCard(
                                song = song,
                                onClick = { viewModel.onSongClick(song) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            ShimmerPlaceholder(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(3f),
                                cornerRadius = 8.dp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.uninstall_confirm),
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = Color.White,
                modifier = Modifier
                    .clickableSingle{
                        Analytics.trackUninstallCtaClick(type = "uninstall")
                        val packageUri = Uri.parse("package:${context.packageName}")

                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = packageUri
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        try {
                            if (uninstallIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(uninstallIntent)
                            } else {
                                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = packageUri
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(settingsIntent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    Analytics.trackUninstallCtaClick(type = "dont_uninstall")
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary
                )
            ) {
                Text(
                    text = stringResource(R.string.uninstall_cancel),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W500,
                    color = Neutral_N100,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Native Ad at bottom (auto height - TOP PRIORITY)
            NativeAdView(
                placement = AdPlacement.NATIVE_UNINSTALL_BOTTOM,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: VideoTemplate,
    onClick: (VideoTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dimens = AppDimens.current

    val imageRequest = remember(template.id) {
        ImageRequest.Builder(context)
            .data(template.thumbnailPath.ifEmpty { null })
            .size(Size(720, 405))
            .precision(Precision.INEXACT)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey("uninstall_${template.id}_static")
            .diskCacheKey("featured_${template.id}")
            .crossfade(200)
            .decoderFactory(BitmapFactoryDecoder.Factory())
            .build()
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            if (template.thumbnailPath.isNotEmpty()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = template.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clickableSingle(
                            onClick = { onClick(template) }
                        )
                )
            } else {
                ShimmerPlaceholder(
                    modifier = Modifier
                        .matchParentSize()
                        .clickableSingle(
                            onClick = { onClick(template) }
                        ),
                    cornerRadius = 18.dp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.widget_trending_song_hot),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.W700,
                        fontSize = 8.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = TextPrimary,
                    modifier = Modifier
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFFED4523), Color(0xFFF751C8))
                            ),
                            shape = RoundedCornerShape(topEnd = 7.dp, bottomStart = 7.dp, bottomEnd = 7.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.56f),
                            shape = RoundedCornerShape(topEnd = 7.dp, bottomStart = 7.dp, bottomEnd = 7.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )

                Icon(
                    painterResource(R.drawable.ic_more_menu),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(18.dp)
                        .background(Color.Black.copy(0.12f), CircleShape)
                        .border(0.75.dp, Color.White.copy(0.12f), CircleShape)
                        .innerShadowCustom(
                            color = Color.White.copy(0.06f),
                            borderRadius = 360.dp,
                            blurRadius = 12.dp,
                            offsetY = 3.dp,
                            offsetX = 1.5.dp,
                        )
                        .padding(3.dp)
                )
            }

            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(0.4f), CircleShape)
                    .padding(5.dp)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SongCard(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppDimens.current

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Neutral_N800, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                AppAsyncImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                        .padding(5.dp)
                        .align(Alignment.Center)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.W500,
                        fontSize = 12.sp
                    ),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dimens.spaceXxs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(dimens.spaceXxs))
                    Text(
                        text = song.formattedUsageCount,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
