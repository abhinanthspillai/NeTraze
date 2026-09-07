package com.netraze.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netraze.app.ui.components.AppTextField
import com.netraze.app.ui.components.InfoCard
import com.netraze.app.ui.components.NetrazeLogo
import com.netraze.app.ui.components.PasswordField
import com.netraze.app.ui.components.PrimaryButton
import com.netraze.app.ui.theme.NetrazeTypography
import com.netraze.app.ui.theme.PrimaryDark
import com.netraze.app.ui.theme.SuccessChipBackground
import com.netraze.app.ui.theme.SurfaceLight
import com.netraze.app.ui.theme.TextPrimary
import com.netraze.app.ui.theme.TextSecondary

@Composable
fun CreateUserScreen(
    viewModel: LoginViewModel,
    onBackToLogin: () -> Unit
) {
    val state by viewModel.createUserState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            NetrazeLogo(
                size = 64.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (state.isSuccess) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Success",
                    tint = SuccessChipBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Account created successfully!",
                    style = NetrazeTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(32.dp))
                PrimaryButton(
                    text = "BACK TO LOGIN",
                    onClick = onBackToLogin,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Join Netraze",
                    style = NetrazeTypography.displaySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create an account to begin surveying.",
                    style = NetrazeTypography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                if (state.errorMessage != null) {
                    InfoCard(
                        isHighEmphasis = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage!!,
                            color = Color(0xFFC62828),
                            style = NetrazeTypography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                AppTextField(
                    value = state.newUserEmail,
                    onValueChange = { viewModel.updateCreateUserForm(newUserEmail = it) },
                    label = "Email Address",
                    placeholder = "Enter your email address",
                    leadingIcon = Icons.Rounded.Email,
                    isError = state.errorMessage != null,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                PasswordField(
                    value = state.newUserPassword,
                    onValueChange = { viewModel.updateCreateUserForm(newUserPassword = it) },
                    label = "Password",
                    isError = state.errorMessage != null,
                    enabled = !state.isLoading,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                PasswordField(
                    value = state.confirmPassword,
                    onValueChange = { viewModel.updateCreateUserForm(confirmPassword = it) },
                    label = "Confirm Password",
                    isError = state.errorMessage != null,
                    enabled = !state.isLoading,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!state.isLoading && state.newUserEmail.isNotBlank() && state.newUserPassword.isNotBlank()) {
                                viewModel.submitCreateUser()
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryButton(
                    text = "CREATE ACCOUNT",
                    onClick = { viewModel.submitCreateUser() },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = state.isLoading,
                    enabled = !state.isLoading && state.newUserEmail.isNotBlank() && state.newUserPassword.isNotBlank()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        style = NetrazeTypography.bodyMedium,
                        color = TextSecondary
                    )
                    TextButton(onClick = onBackToLogin) {
                        Text(
                            text = "Login",
                            style = NetrazeTypography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
