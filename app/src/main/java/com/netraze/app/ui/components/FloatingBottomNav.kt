package com.netraze.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceWhite

data class NavItem(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val isPrimaryAction: Boolean = false
)

@Composable
fun FloatingBottomNav(
    items: List<NavItem>,
    selectedId: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 22.dp)
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryDark, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedId && !item.isPrimaryAction
                val backgroundColor = if (isSelected) SurfaceWhite else Color.Transparent
                val iconColor = if (isSelected) PrimaryDark else SurfaceWhite
                val targetSize = 54.dp
                val iconSize = if (item.isPrimaryAction) 34.dp else 30.dp

                Box(
                    modifier = Modifier
                        .size(targetSize)
                        .background(backgroundColor, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = androidx.compose.material3.ripple(bounded = false, radius = targetSize / 2),
                            onClick = { onItemSelected(item.id) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription,
                        tint = iconColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}
