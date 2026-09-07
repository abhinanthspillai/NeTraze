package com.netraze.app.data.remote.api

import com.netraze.app.data.remote.dto.LoginRequestDto
import com.netraze.app.data.remote.dto.LoginResponseDto
import com.netraze.app.data.remote.dto.RegisterRequestDto
import com.netraze.app.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Normal end-user authentication API.
 * Administrator user-management endpoints are intentionally kept out of this
 * interface. Forgot-password/self-service reset remains a later implementation.
 */
interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): LoginResponseDto

    @GET("api/v1/auth/me")
    suspend fun getMe(): UserDto

    @POST("api/v1/auth/register")
    suspend fun registerUser(
        @Body request: RegisterRequestDto
    ): UserDto
}
