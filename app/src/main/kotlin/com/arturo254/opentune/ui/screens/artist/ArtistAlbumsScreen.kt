/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.arturo254.opentune.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.arturo254.opentune.LocalPlayerAwareWindowInsets
import com.arturo254.opentune.LocalPlayerConnection
import com.arturo254.opentune.R
import com.arturo254.opentune.constants.CONTENT_TYPE_ALBUM
import com.arturo254.opentune.constants.CONTENT_TYPE_HEADER
import com.arturo254.opentune.constants.GridThumbnailHeight
import com.arturo254.opentune.ui.component.IconButton
import com.arturo254.opentune.ui.component.LibraryAlbumGridItem
import com.arturo254.opentune.ui.component.LocalMenuState
import com.arturo254.opentune.ui.utils.backToMain
import com.arturo254.opentune.viewmodels.ArtistAlbumsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistAlbumsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artist by viewModel.artist.collectAsState()
    val albums by viewModel.albums.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyGridState = rememberLazyGridState()

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Verificar si hay que mostrar el título en la barra superior
    val showTopBarTitle by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex > 0
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            // Hero Header
            item(
                key = "hero",
                span = { GridItemSpan(maxLineSpan) },
                contentType = CONTENT_TYPE_HEADER
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 48.dp)
                        .padding(bottom = 16.dp)
                ) {
                    // Artwork
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 16.dp,
                        modifier = Modifier.size(200.dp)
                    ) {
                        AsyncImage(
                            model = artist?.artist?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Artist name
                    Text(
                        text = artist?.artist?.name.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata chips row
                    Row(
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // Album count chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_album,
                                    albums.size,
                                    albums.size
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle button
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = {
                                // Implementar shuffle de álbumes
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = stringResource(R.string.shuffle),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Play all button
                        Button(
                            onClick = {
                                // Implementar reproducción de todos los álbumes
                            },
                            shape = RoundedCornerShape(percent = 50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = " ",
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Add to queue button
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = {
                                // Implementar añadir a cola
                            },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = stringResource(R.string.add_to_queue),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Albums grid header
            item(
                key = "header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = CONTENT_TYPE_HEADER
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.weight(1f))

                    Text(
                        text = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            items(
                items = albums.distinctBy { it.id },
                key = { it.id },
                contentType = { CONTENT_TYPE_ALBUM }
            ) { album ->
                LibraryAlbumGridItem(
                    navController = navController,
                    menuState = menuState,
                    coroutineScope = coroutineScope,
                    album = album,
                    isActive = album.id == mediaMetadata?.album?.id,
                    isPlaying = isPlaying,
                    modifier = Modifier.animateItem()
                )
            }
        }

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (showTopBarTitle) MaterialTheme.colorScheme.surface else Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
            title = {
                if (showTopBarTitle) {
                    Text(artist?.artist?.name.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}