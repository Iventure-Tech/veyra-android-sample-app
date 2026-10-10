package co.veyra.bank.connection

import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class BankBackendAssertionProviderTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun provider(session: String? = "bank-session") =
        BankBackendAssertionProvider("client-id", "bank-client-id", "bank-client-secret", server.url("/").toString().trimEnd('/'), { session }, OkHttpClient())

    @Test
    fun exchangesTheBankSessionForAnAssertionAtTheTokenEndpoint() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"access_token":"eyJ.a.b","issued_token_type":"urn:ietf:params:oauth:token-type:access_token"}"""))
        assertEquals("eyJ.a.b", provider().assertion("https://api.uat.veyra.co", "JKT-1"))
        val recorded = server.takeRequest()
        assertEquals("/oauth2/token", recorded.path)
        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"))
        assertEquals(Credentials.basic("bank-client-id", "bank-client-secret"), recorded.getHeader("Authorization"))
        val form = recorded.body.readUtf8().split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }
        assertEquals(
            mapOf(
                "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token" to "bank-session",
                "subject_token_type" to "urn:ietf:params:oauth:token-type:access_token",
                "requested_token_type" to "urn:ietf:params:oauth:token-type:jwt",
                "audience" to "https://api.uat.veyra.co",
            ),
            form,
        )
    }

    @Test
    fun aRefusedSessionMeansNoAssertion() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(provider().assertion("https://api.uat.veyra.co", "JKT"))
    }

    @Test
    fun anErrorAnswerThrowsAndQuotesTheServer() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        val e = runCatching { provider().assertion("https://api.uat.veyra.co", "JKT") }.exceptionOrNull()
        assertTrue(e is IllegalStateException && e.message!!.contains("invalid_grant"))
    }

    @Test
    fun noSessionMeansNoAssertionAndNoCall() = runBlocking {
        assertNull(provider(session = null).assertion("https://api.uat.veyra.co", "JKT"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun parseRules() {
        assertNull(BankBackendAssertionProvider.parse(401, ""))
        assertEquals("x", BankBackendAssertionProvider.parse(200, """{"access_token":"x"}"""))
        assertThrows { BankBackendAssertionProvider.parse(500, "") }
        assertThrows { BankBackendAssertionProvider.parse(200, """{"other":1}""") }
        assertThrows { BankBackendAssertionProvider.parse(200, """{"access_token":""}""") }
    }

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (e: IllegalStateException) { return }
        throw AssertionError("expected IllegalStateException")
    }
}
