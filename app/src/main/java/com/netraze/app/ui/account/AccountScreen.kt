package com.netraze.app.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.SurfaceTranslucent
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AccountScreen(
    email: String,
    role: String,
    onSignOut: () -> Unit
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
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 88.dp), // Bottom padding for compact floating nav
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "Account",
                    style = NetrazeTypography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "Manage your Netraze profile",
                    style = NetrazeTypography.bodyLarge,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(SurfaceTranslucent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = "Profile",
                    tint = TextPrimary,
                    modifier = Modifier.size(64.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = name,
                style = NetrazeTypography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            InfoCard(isHighEmphasis = false, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Email, contentDescription = "Email", tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Email Address", style = NetrazeTypography.labelSmall, color = TextSecondary)
                        Text(text = email, style = NetrazeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TextSecondary.copy(alpha = 0.2f)))
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Badge, contentDescription = "Role", tint = TextPrimary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("System Role", style = NetrazeTypography.labelSmall, color = TextSecondary)
                        Text(text = displayRole, style = NetrazeTypography.bodyLarge, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = "SIGN OUT",
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Logout,
                containerColor = Color(0xFFFEE2E2),
                contentColor = Color(0xFFDC2626)
            )
        }
    }
}
