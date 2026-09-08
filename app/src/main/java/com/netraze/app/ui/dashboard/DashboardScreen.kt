package com.netraze.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.SurfaceTranslucent
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardHomeScreen(
    email: String,
    role: String,
    recentSurvey: SurveyEntity?,
    recentSurveys: List<SurveyEntity>,
    allSynced: Boolean,
    onContinueSurveyClick: (SurveyEntity) -> Unit,
    onBrowseLocations: () -> Unit,
    onViewAllSurveys: () -> Unit
) {
    val displayRole = when (role.lowercase()) {
        "administrator" -> "Administrator"
        "user" -> "User"
        else -> "User"
    }
    
    val name = email.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                // Bottom padding to avoid the compact floating nav bar.
                .padding(bottom = 88.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceTranslucent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Avatar",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = name,
                            style = NetrazeTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = displayRole,
                            style = NetrazeTypography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceTranslucent),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { /* TODO: Notifications */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Large Title
            Text(
                text = "Surveys that\nwork for you",
                style = NetrazeTypography.displaySmall,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoCard(
                    modifier = Modifier.weight(1f),
                    isHighEmphasis = false,
                    onClick = onViewAllSurveys
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Assignment,
                            contentDescription = "Total",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Total Surveys",
                            style = NetrazeTypography.labelLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${recentSurveys.size}",
                        style = NetrazeTypography.headlineMedium,
                        color = TextPrimary
                    )
                }

                InfoCard(
                    modifier = Modifier.weight(1f),
                    isHighEmphasis = false
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (allSynced) Icons.Rounded.Check else Icons.Rounded.WifiTethering,
                            contentDescription = "Sync",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sync Status",
                            style = NetrazeTypography.labelLarge,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (allSynced) "Synced" else "Pending",
                        style = NetrazeTypography.titleLarge,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent",
                    style = NetrazeTypography.titleLarge,
                    color = TextPrimary
                )
                TextButton(onClick = onViewAllSurveys) {
                    Text(text = "View all", color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (recentSurveys.isNotEmpty()) {
                InfoCard(isHighEmphasis = false) {
                    recentSurveys.forEachIndexed { index, survey ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onContinueSurveyClick(survey) }
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
                                    imageVector = if (survey.mode == "location_survey") Icons.Rounded.Business else Icons.Rounded.Science,
                                    contentDescription = "Icon",
                                    tint = TextPrimary
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = survey.title.ifBlank { "Untitled Survey" },
                                    style = NetrazeTypography.titleMedium,
                                    color = TextPrimary
                                )
                                val formattedMode = survey.mode.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                Text(
                                    text = formattedMode,
                                    style = NetrazeTypography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(survey.updatedAt))
                                Text(
                                    text = dateStr,
                                    style = NetrazeTypography.bodyMedium,
                                    color = TextSecondary
                                )
                                val displayStatus = if (survey.status == "completed" && survey.syncState == "synced") "Synced" else survey.status.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                                Text(
                                    text = displayStatus,
                                    style = NetrazeTypography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                        
                        if (index < recentSurveys.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(TextSecondary.copy(alpha = 0.2f))
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No recent surveys available.",
                    style = NetrazeTypography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
