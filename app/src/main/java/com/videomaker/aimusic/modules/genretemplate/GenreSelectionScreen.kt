package com.videomaker.aimusic.modules.genretemplate

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.videomaker.aimusic.R
import com.videomaker.aimusic.ui.theme.Primary

@Composable
fun GenreSelectionScreen(
    genres: List<OnboardingGenre>,
    selectedGenre: OnboardingGenre?,
    onGenreSelect: (OnboardingGenre) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(60.dp))

        Text(
            text = stringResource(R.string.genre_step_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.genre_step_subtitle),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(genres, key = { it.id }) { genre ->
                GenreCard(
                    genre = genre,
                    isSelected = selectedGenre?.id == genre.id,
                    onClick = { onGenreSelect(genre) }
                )
            }

            item {
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }
    }
}

@Composable
private fun GenreCard(
    genre: OnboardingGenre,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    val borderColor = if (isSelected) Primary else Color.White.copy(alpha = 0.08f)

    Box(Modifier) {
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .border(2.dp, borderColor, shape)
                .clickable(onClick = onClick)
        ) {
            AsyncImage(
                model = genre.imageRes,
                contentDescription = genre.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .height(125.dp)
                    .width(90.dp)
                    .padding(top = 12.dp)
            )

            // Genre label at top-start
            Text(
                text = genre.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            )

        }
        // Checkmark at top-end when selected
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .align(Alignment.TopEnd)
            ) {
                Image(
                    painter = painterResource(R.drawable.img_checkbox),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
