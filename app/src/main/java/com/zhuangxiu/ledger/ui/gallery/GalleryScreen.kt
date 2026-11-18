package com.zhuangxiu.ledger.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zhuangxiu.ledger.ui.components.EmptyHint
import com.zhuangxiu.ledger.ui.components.GlassVariant
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.liquidGlass

/** 凭证库（Liquid Glass 版）：冰晶相框网格 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onViewImage: (index: Int) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SubPageScaffold(title = if (state.count > 0) "凭证库 (${state.count})" else "凭证库", onBack = onBack) { modifier ->
        if (state.months.isEmpty() && !state.loading) {
            EmptyHint(Icons.Outlined.PhotoLibrary, "还没有凭证，记账时可以拍照或从相册添加", modifier)
            return@SubPageScaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.months.forEach { group ->
                item(key = "month-${group.month}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${group.month.year}年${group.month.monthValue}月",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                items(group.images, key = { it.attachment.id }) { image ->
                    AsyncImage(
                        model = viewModel.imageFile(image.attachment.fileName),
                        contentDescription = "凭证",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .liquidGlass(RoundedCornerShape(16.dp), GlassVariant.Clear, cornerRadius = 16.dp, shadowElevation = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onViewImage(image.index) },
                    )
                }
            }
        }
    }
}
