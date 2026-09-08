package com.netraze.app.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SecureSessionStoreTest {

    private lateinit var context: Context
    private lateinit var sessionCrypto: SessionCrypto
    private lateinit var sessionStore: SecureSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionCrypto = FakeSessionCrypto()
        sessionStore = SecureSessionStore(context.sessionDataStore, sessionCrypto)
    }

    @After
    fun tearDown() = runBlocking {
        sessionStore.clearSession()
    }

    @Test
    fun testSaveAndGetSession() = runBlocking {
        val userId = UUID.randomUUID()
        val session = AuthSession(
            accessToken = "test_jwt_token_abcdef123456",
            userId = userId,
            email = "tech@netraze.app",
            role = "user"
        )

        sessionStore.saveSession(session)

        val retrieved = sessionStore.getSession()
        assertNotNull(retrieved)
        assertEquals("test_jwt_token_abcdef123456", retrieved?.accessToken)
        assertEquals(userId, retrieved?.userId)
        assertEquals("tech@netraze.app", retrieved?.email)
        assertEquals("user", retrieved?.role)
        assertTrue(sessionStore.hasActiveSession())
    }

    @Test
    fun testClearSession() = runBlocking {
        val session = AuthSession(
            accessToken = "test_jwt_token_abcdef123456",
            userId = UUID.randomUUID(),
            email = "admin@netraze.app",
            role = "administrator"
        )
        sessionStore.saveSession(session)

        sessionStore.clearSession()

        val retrieved = sessionStore.getSession()
        assertNull(retrieved)
        assertFalse(sessionStore.hasActiveSession())
    }

    @Test
    fun testUnreadableStoredSessionReturnsNullAndClearsSession() = runBlocking {
        val session = AuthSession(
            accessToken = "test_jwt_token_abcdef123456",
            userId = UUID.randomUUID(),
            email = "corrupt@netraze.app",
            role = "user"
        )
        sessionStore.saveSession(session)

        val unreadableStore = SecureSessionStore(context.sessionDataStore, FakeSessionCrypto())

        assertNull(unreadableStore.getSession())
        assertFalse(unreadableStore.hasActiveSession())
    }
}
