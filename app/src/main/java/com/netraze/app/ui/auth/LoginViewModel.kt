package com.netraze.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netraze.app.data.remote.api.AuthApi
import com.netraze.app.data.remote.dto.RegisterRequestDto
import com.netraze.app.data.remote.dto.UserDto
import com.netraze.app.data.repository.AuthRepository
import com.netraze.app.data.security.AuthSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AuthenticatedState(
    val isAuthenticated: Boolean = false,
    val session: AuthSession? = null,
    val userProfile: UserDto? = null,
    val isFetchingProfile: Boolean = false,
    val profileError: String? = null
)

data class CreateUserUiState(
    val newUserEmail: String = "",
    val newUserPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel @Inject constructor(
    private var authRepository: AuthRepository?,
    private var authApi: AuthApi?
) : ViewModel() {

    constructor() : this(null, null)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _authState = MutableStateFlow(AuthenticatedState())
    val authState: StateFlow<AuthenticatedState> = _authState.asStateFlow()

    private val _createUserState = MutableStateFlow(CreateUserUiState())
    val createUserState: StateFlow<CreateUserUiState> = _createUserState.asStateFlow()

    init {
        checkSessionRestoration()
    }

    fun setDependencies(repository: AuthRepository, api: AuthApi) {
        this.authRepository = repository
        this.authApi = api
        checkSessionRestoration()
    }

    /**
     * A locally stored token is not treated as authenticated until the backend
     * validates it through /auth/me. Expired/invalid sessions are cleared.
     */
    fun checkSessionRestoration() {
        val repository = authRepository ?: return
        val api = authApi ?: return

        viewModelScope.launch {
            val session = repository.getCurrentSession()
            if (session == null || session.accessToken.isBlank()) {
                _authState.value = AuthenticatedState(isAuthenticated = false)
                return@launch
            }

            _authState.value = AuthenticatedState(
                isAuthenticated = false,
                session = session,
                isFetchingProfile = true
            )

            try {
                val profile = api.getMe()
                _authState.value = AuthenticatedState(
                    isAuthenticated = true,
                    session = session,
                    userProfile = profile,
                    isFetchingProfile = false
                )
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    repository.logout()
                    _authState.value = AuthenticatedState(isAuthenticated = false)
                } else {
                    _authState.value = AuthenticatedState(
                        isAuthenticated = false,
                        session = session,
                        isFetchingProfile = false,
                        profileError = "Unable to verify your session. Please try again."
                    )
                }
            } catch (e: Exception) {
                // Do not destroy a potentially valid session just because the device
                // is temporarily offline. It will be validated again on next launch/retry.
                _authState.value = AuthenticatedState(
                    isAuthenticated = false,
                    session = session,
                    isFetchingProfile = false,
                    profileError = "Unable to verify your session. Check your connection."
                )
            }
        }
    }

    fun onIdentityChanged(identity: String) {
        _uiState.update { it.copy(identity = identity, errorMessage = null) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun submitLogin(onSuccess: () -> Unit = {}) {
        val repository = authRepository
        if (repository == null) {
            _uiState.update { it.copy(errorMessage = "Auth repository not initialized") }
            return
        }

        val currentState = _uiState.value
        if (!currentState.isLoginEnabled) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = repository.login(currentState.identity, currentState.password)
            if (result.isSuccess) {
                val user = result.getOrNull()
                val session = repository.getCurrentSession()
                if (user != null && session != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                    _authState.value = AuthenticatedState(
                        isAuthenticated = true,
                        session = session,
                        userProfile = user
                    )
                    onSuccess()
                } else {
                    repository.logout()
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Login failed: missing session data") }
                }
            } else {
                val exception = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception?.message ?: "Login failed due to an unknown error."
                    )
                }
            }
        }
    }

    fun logout() {
        val repository = authRepository ?: return
        viewModelScope.launch {
            repository.logout()
            _authState.value = AuthenticatedState(isAuthenticated = false)
            _uiState.value = LoginUiState()
        }
    }

    fun updateCreateUserForm(
        newUserEmail: String = _createUserState.value.newUserEmail,
        newUserPassword: String = _createUserState.value.newUserPassword,
        confirmPassword: String = _createUserState.value.confirmPassword
    ) {
        _createUserState.update {
            it.copy(
                newUserEmail = newUserEmail,
                newUserPassword = newUserPassword,
                confirmPassword = confirmPassword,
                errorMessage = null,
                isSuccess = false
            )
        }
    }

    fun submitCreateUser() {
        val api = authApi ?: return
        val state = _createUserState.value
        val email = state.newUserEmail.trim()

        when {
            email.isBlank() || state.newUserPassword.isBlank() -> {
                _createUserState.update { it.copy(errorMessage = "Email and password are required") }
                return
            }
            state.newUserPassword.length !in 8..128 -> {
                _createUserState.update { it.copy(errorMessage = "Password must be between 8 and 128 characters") }
                return
            }
            state.newUserPassword.isBlank() -> {
                _createUserState.update { it.copy(errorMessage = "Password cannot be blank") }
                return
            }
            state.newUserPassword != state.confirmPassword -> {
                _createUserState.update { it.copy(errorMessage = "Passwords do not match") }
                return
            }
        }

        _createUserState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                api.registerUser(
                    RegisterRequestDto(
                        email = email,
                        password = state.newUserPassword,
                        confirm_password = state.confirmPassword
                    )
                )
                _createUserState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: HttpException) {
                val message = when (e.code()) {
                    409 -> "An account with this email address already exists."
                    422 -> "Please check the email address and password requirements."
                    else -> "Failed to create account (${e.code()})."
                }
                _createUserState.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: Exception) {
                _createUserState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to create account. Check your connection and try again."
                    )
                }
            }
        }
    }

    fun resetCreateUserForm() {
        _createUserState.value = CreateUserUiState()
    }
}
