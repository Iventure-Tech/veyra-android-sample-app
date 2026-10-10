package co.veyra.bank.provider

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

/** The demo's bank login: password grant at the bank's token endpoint, cached until expiry. */
class BankSessionTest {

    private lateinit var server: MockWebServer
    private var now = 1_000_000L

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun session(user: String? = "ada", pass: String? = "p@ss w&rd") = BankSession(
        server.url("/").toString().trimEnd('/'), "bank-client-id", "bank-client-secret",
        username = { user }, password = { pass }, http = OkHttpClient(), clock = { now },
    )

    private fun token(value: String, expiresIn: Long = 3600) =
        MockResponse().setBody("""{"access_token":"$value","token_type":"Bearer","expires_in":$expiresIn}""")

    @Test
    fun logsInWithThePasswordGrantAndTheBanksClient() {
        server.enqueue(token("session-1"))
        assertEquals("session-1", session()())
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/oauth2/token", recorded.path)
        assertEquals(Credentials.basic("bank-client-id", "bank-client-secret"), recorded.getHeader("Authorization"))
        val form = recorded.body.readUtf8().split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        assertEquals(mapOf("grant_type" to "password", "username" to "ada", "password" to "p@ss w&rd"), form)
    }

    @Test
    fun theSessionIsReusedUntilItIsAboutToExpire() {
        server.enqueue(token("session-1", expiresIn = 120))
        server.enqueue(token("session-2", expiresIn = 120))
        val s = session()
        assertEquals("session-1", s())
        now += 60_000 // 90s left of 120 minus the 30s margin: still good
        assertEquals("session-1", s())
        now += 31_000 // past the renewal point
        assertEquals("session-2", s())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun refusedCredentialsMeanNobodyIsSignedIn() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertNull(session()())
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(session()())
    }

    @Test
    fun missingCredentialsSendNothing() {
        assertNull(session(user = "")())
        assertNull(session(pass = null)())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aServerErrorThrowsAndCachesNothing() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(token("session-1"))
        val s = session()
        try {
            s()
            fail("expected a failure")
        } catch (e: IllegalStateException) {
            assertEquals("bank login answered HTTP 503", e.message)
        }
        assertEquals("session-1", s())
    }

    @Test
    fun clearForgetsTheSession() {
        server.enqueue(token("session-1"))
        server.enqueue(token("session-2"))
        val s = session()
        assertEquals("session-1", s())
        s.clear()
        assertEquals("session-2", s())
    }
}
