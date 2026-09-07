package com.netraze.app.data.repository

import com.netraze.app.data.remote.api.AuthApi
import com.netraze.app.data.remote.dto.LoginRequestDto
import com.netraze.app.data.remote.dto.UserDto
import com.netraze.app.data.security.AuthSession
import com.netraze.app.data.security.SecureSessionStore
import retrofit2.HttpException
import java.io.IOException

/**
 * Normal end-user authentication only.
 *
 * Administrator user-management operations are intentionally not part of this
 * repository. Forgot-password/self-service reset remains a later implementation.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): Result<UserDto>
    suspend fun logout()
    suspend fun hasActiveSession(): Boolean
    suspend fun getCurrentSession(): AuthSession?
}

class AuthRepositoryImpl(
    private val authApi: AuthApi,
    private val sessionStore: SecureSessionStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<UserDto> {
        return try {
            android.util.Log.d("NETRAZE_API", "Starting login request")
            val response = authApi.login(LoginRequestDto(email = email.trim(), password = password))
            val session = AuthSession(
                accessToken = response.accessToken,
                userId = response.user.id,
                email = response.user.email,
                role = response.user.role
            )
            sessionStore.saveSession(session)
            Result.success(response.user)
        } catch (e: HttpException) {
            android.util.Log.e("NETRAZE_API", "Login failed with HTTP ${e.code()}")
            val errorMsg = when (e.code()) {
                401 -> "Invalid email address or password."
                403 -> "Not authorized to perform this operation."
                404 -> "Authentication endpoint not found (404)."
                422 -> "Invalid data format sent to server (422)."
                in 500..599 -> "Server encountered an error (${e.code()})."
                else -> "Authentication server error (${e.code()})."
            }
            Result.failure(Exception(errorMsg, e))
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e("NETRAZE_API", "DNS/network failure")
            Result.failure(Exception("Unable to resolve server address. Check your connection.", e))
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e("NETRAZE_API", "Authentication request timed out")
            Result.failure(Exception("Connection timed out. Please try again.", e))
        } catch (e: java.net.ConnectException) {
            android.util.Log.e("NETRAZE_API", "Authentication connection refused")
            Result.failure(Exception("Connection refused by server.", e))
        } catch (e: javax.net.ssl.SSLException) {
            android.util.Log.e("NETRAZE_API", "Authentication SSL failure")
            Result.failure(Exception("SSL/TLS security error occurred.", e))
        } catch (e: IOException) {
            android.util.Log.e("NETRAZE_API", "Authentication network failure: ${e.javaClass.simpleName}")
            Result.failure(Exception("Unable to connect to Netraze. Check your connection and try again.", e))
        } catch (e: Exception) {
            android.util.Log.e("NETRAZE_API", "Unexpected authentication failure: ${e.javaClass.simpleName}")
            Result.failure(Exception("An unexpected authentication error occurred.", e))
        }
    }

    override suspend fun logout() {
        sessionStore.clearSession()
    }

    override suspend fun hasActiveSession(): Boolean {
        return sessionStore.hasActiveSession()
    }

    override suspend fun getCurrentSession(): AuthSession? {
        return sessionStore.getSession()
    }
}
