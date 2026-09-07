package com.netraze.app.ui.auth

import com.netraze.app.data.remote.api.AuthApi
import com.netraze.app.data.remote.dto.LoginRequestDto
import com.netraze.app.data.remote.dto.LoginResponseDto
import com.netraze.app.data.remote.dto.RegisterRequestDto
import com.netraze.app.data.remote.dto.UserDto
import com.netraze.app.data.repository.AuthRepository
import com.netraze.app.data.security.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var fakeAuthApi: FakeAuthApi
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepository = FakeAuthRepository()
        fakeAuthApi = FakeAuthApi()
        viewModel = LoginViewModel(fakeAuthRepository, fakeAuthApi)
    }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun testFormStateValidation() {
        assertFalse(viewModel.uiState.value.isLoginEnabled)
        viewModel.onIdentityChanged("user@netraze.app")
        assertFalse(viewModel.uiState.value.isLoginEnabled)
        viewModel.onPasswordChanged("Password123")
        assertTrue(viewModel.uiState.value.isLoginEnabled)
    }

    @Test
    fun testSuccessfulLoginFlow() = runTest {
        viewModel.onIdentityChanged("user@netraze.app")
        viewModel.onPasswordChanged("CorrectPassword")
        var called = false
        viewModel.submitLogin { called = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(called)
        assertTrue(viewModel.authState.value.isAuthenticated)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testFailedLoginFlow() = runTest {
        fakeAuthRepository.shouldReturnError = true
        viewModel.onIdentityChanged("user@netraze.app")
        viewModel.onPasswordChanged("WrongPassword")
        viewModel.submitLogin()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Invalid email address or password.", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.authState.value.isAuthenticated)
    }

    @Test
    fun testPublicRegistrationFlow() = runTest {
        viewModel.updateCreateUserForm("newuser@netraze.app", "Password123!", "Password123!")
        viewModel.submitCreateUser()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.createUserState.value.isSuccess)
        assertEquals("newuser@netraze.app", fakeAuthApi.lastRegistration?.email)
    }

    @Test
    fun testRegistrationRejectsShortPasswordBeforeApiCall() = runTest {
        viewModel.updateCreateUserForm("newuser@netraze.app", "short", "short")
        viewModel.submitCreateUser()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Password must be between 8 and 128 characters", viewModel.createUserState.value.errorMessage)
        assertNull(fakeAuthApi.lastRegistration)
    }

    private class FakeAuthRepository : AuthRepository {
        var shouldReturnError = false
        private var currentSession: AuthSession? = null
        override suspend fun login(email: String, password: String): Result<UserDto> =
            if (shouldReturnError) Result.failure(Exception("Invalid email address or password."))
            else {
                val id = UUID.randomUUID()
                currentSession = AuthSession("token_123", id, email, "user")
                Result.success(UserDto(id, email, "user"))
            }
        override suspend fun logout() { currentSession = null }
        override suspend fun hasActiveSession() = currentSession != null
        override suspend fun getCurrentSession() = currentSession
    }

    private class FakeAuthApi : AuthApi {
        var lastRegistration: RegisterRequestDto? = null
        override suspend fun login(request: LoginRequestDto): LoginResponseDto {
            val id = UUID.randomUUID()
            return LoginResponseDto("token_123", "bearer", UserDto(id, request.email, "user"))
        }
        override suspend fun getMe() = UserDto(UUID.randomUUID(), "user@netraze.app", "user")
        override suspend fun registerUser(request: RegisterRequestDto): UserDto {
            lastRegistration = request
            return UserDto(UUID.randomUUID(), request.email, "user")
        }
    }
}
