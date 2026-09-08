package com.netraze.app.ui.hierarchy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netraze.app.data.local.entity.ProjectEntity
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.OfflineBanner
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary

@Composable
fun LocationsTabScreen(
    viewModel: HierarchyViewModel,
    userRole: String,
    currentUserId: String,
    onProjectClick: (ProjectEntity) -> Unit,
    onManageLocationsClick: () -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val isAdministrator = (userRole.lowercase() == "administrator")

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 88.dp) // Bottom padding for compact floating nav
        ) {
            Text(
                text = "Locations",
                style = NetrazeTypography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Browse and manage survey sites",
                style = NetrazeTypography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (error != null) {
                OfflineBanner(isOffline = true, hasCachedData = projects.isNotEmpty())
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isAdministrator) {
                PrimaryButton(
                    text = "Manage Location Hierarchy",
                    onClick = onManageLocationsClick,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Settings,
                    containerColor = PrimaryDark,
                    contentColor = Color.White
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (isLoading && projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryDark)
                }
            } else if (projects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No projects found.",
                        style = NetrazeTypography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                InfoCard(isHighEmphasis = false, modifier = Modifier.fillMaxSize()) {
                    LazyColumn {
                        itemsIndexed(projects) { index, project ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onProjectClick(project) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = "Project",
                                        tint = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = project.name,
                                        style = NetrazeTypography.titleMedium,
                                        color = TextPrimary
                                    )
                                    val subtitle = if (project.ownerId.toString() == currentUserId) "Owner Access" else "Member Access"
                                    Text(
                                        text = subtitle,
                                        style = NetrazeTypography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            }

                            if (index < projects.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(TextSecondary.copy(alpha = 0.2f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
