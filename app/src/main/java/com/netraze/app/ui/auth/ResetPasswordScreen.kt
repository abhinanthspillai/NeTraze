package com.netraze.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary

/**
 * Placeholder only. Self-service forgot-password/reset is intentionally
 * deferred for a later implementation and is not part of the current auth milestone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordScreen(
    viewModel: LoginViewModel,
    onBackToLogin: () -> Unit
) {
    // Keep the parameter for navigation compatibility until the later reset feature is designed.
    @Suppress("UNUSED_VARIABLE")
    val unusedViewModel = viewModel

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Forgot Password",
                        style = NetrazeTypography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToLogin) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Login",
                            tint = PrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceLight)
            )
        },
        containerColor = SurfaceLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            InfoCard(isHighEmphasis = true) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockClock,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Password recovery is coming later",
                        style = NetrazeTypography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Self-service forgot-password recovery is intentionally deferred while the core Netraze survey workflow is completed.",
                        style = NetrazeTypography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onBackToLogin) {
                Text(
                    text = "Back to Login",
                    color = PrimaryDark,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
