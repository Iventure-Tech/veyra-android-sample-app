package co.veyra.bank.connection

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BankBackendAssertionProviderTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun provider(session: String? = "bank-session") =
        BankBackendAssertionProvider(server.url("/").toString().trimEnd('/'), { session }, OkHttpClient())

    @Test
    fun postsTheThumbprintAndReturnsTheAssertion() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"assertion":"eyJ.a.b"}"""))
        assertEquals("eyJ.a.b", provider().assertion("JKT-1"))
        val recorded = server.takeRequest()
        assertEquals("/sdk-assertion", recorded.path)
        assertEquals("JKT-1", JSONObject(recorded.body.readUtf8()).getString("jkt"))
        assertEquals("Bearer bank-session", recorded.getHeader("Authorization"))
    }

    @Test
    fun noSessionMeansNoAssertionAndNoCall() = runBlocking {
        assertNull(provider(session = null).assertion("JKT"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun parseRules() {
        assertNull(BankBackendAssertionProvider.parse(401, ""))
        assertEquals("x", BankBackendAssertionProvider.parse(200, """{"assertion":"x"}"""))
        assertThrows { BankBackendAssertionProvider.parse(500, "") }
        assertThrows { BankBackendAssertionProvider.parse(200, """{"other":1}""") }
        assertThrows { BankBackendAssertionProvider.parse(200, """{"assertion":""}""") }
    }

    private fun assertThrows(block: () -> Unit) {
        try { block() } catch (e: IllegalStateException) { return }
        throw AssertionError("expected IllegalStateException")
    }
}
