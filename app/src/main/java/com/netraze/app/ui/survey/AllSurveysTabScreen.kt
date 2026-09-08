package com.netraze.app.ui.survey

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Science
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
import com.netraze.app.data.local.entity.SurveyEntity
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AllSurveysTabScreen(
    viewModel: SurveyViewModel,
    onSurveyClick: (SurveyEntity) -> Unit,
    onStartSurveyClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadAllSurveys()
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
                text = "Surveys",
                style = NetrazeTypography.displaySmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "All Recorded Indoor Wi-Fi Surveys",
                style = NetrazeTypography.bodyLarge,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "START NEW SURVEY",
                onClick = onStartSurveyClick,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
                containerColor = PrimaryDark,
                contentColor = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryDark)
                }
            } else if (uiState.surveys.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No surveys recorded yet.",
                        style = NetrazeTypography.bodyLarge,
                        color = TextSecondary
                    )
                }
            } else {
                InfoCard(isHighEmphasis = false, modifier = Modifier.fillMaxSize()) {
                    LazyColumn {
                        itemsIndexed(uiState.surveys) { index, survey ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSurveyClick(survey) }
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
                            
                            if (index < uiState.surveys.lastIndex) {
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
