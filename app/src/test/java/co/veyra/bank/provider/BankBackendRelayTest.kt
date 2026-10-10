package co.veyra.bank.provider

import co.veyra.common.providers.VeyraRelayException
import co.veyra.common.net.NetworkFailureKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** The reference relay: forwards the envelope unchanged and classifies failures by `neverSent`. */
class BankBackendRelayTest {

    private lateinit var server: MockWebServer
    private val http = OkHttpClient.Builder().readTimeout(1, TimeUnit.SECONDS).build()

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() = server.shutdown()

    private fun relay(session: String? = "bank-session") =
        BankBackendRelay(server.url("/").toString().trimEnd('/'), { session }, http)

    private fun failure(block: suspend () -> Unit): VeyraRelayException = try {
        runBlocking { block() }
        fail("expected a relay failure"); throw AssertionError()
    } catch (e: VeyraRelayException) { e }

    @Test
    fun forwardsTheEnvelopeUnchangedAndReturnsTheBodyUnchanged() = runBlocking {
        val veyraBody = """{"response_code":"00","weird":"ü ✓"}"""
        server.enqueue(MockResponse().setBody(veyraBody))
        val envelope = """{"v":1,"path":"/paymentgateway/v1/payment","headers":{},"body":"{}"}"""
        assertEquals(veyraBody, relay().patch(envelope))
        val recorded = server.takeRequest()
        assertEquals("/veyra-relay/patch", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals(envelope, recorded.body.readUtf8())
        assertEquals("Bearer bank-session", recorded.getHeader("Authorization"))
    }

    @Test
    fun eachMethodHasItsOwnRoute() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setBody("ok")) }
        val r = relay()
        r.post("{}"); r.get("{}"); r.put("{}"); r.delete("{}"); r.patch("{}")
        assertEquals(
            listOf("post", "get", "put", "delete", "patch").map { "/veyra-relay/$it" },
            List(5) { server.takeRequest().path },
        )
    }

    /** A non-2xx came back: the request was delivered — may have been processed. */
    @Test
    fun aNon2xxIsMayHaveBeenSentWithItsStatus() {
        server.enqueue(MockResponse().setResponseCode(503))
        val e = failure { relay().post("{}") }
        assertFalse(e.neverSent)
        assertEquals(503, e.httpStatus)
    }

    /** Written, then no answer: may have been processed. */
    @Test
    fun aTimeoutIsMayHaveBeenSent() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val e = failure { relay().post("{}") }
        assertFalse(e.neverSent)
        assertEquals(NetworkFailureKind.TIMEOUT, e.kind)
    }

    /** Nothing listening: refused before anything was written — provably never sent. */
    @Test
    fun aRefusedConnectionIsNeverSent() {
        val url = server.url("/").toString().trimEnd('/')
        server.shutdown()
        val e = failure { BankBackendRelay(url, { null }, http).get("{}") }
        assertTrue(e.neverSent)
        assertEquals(NetworkFailureKind.CONNECTION_REFUSED, e.kind)
    }

    @Test
    fun classificationTable() {
        assertTrue(BankBackendRelay.classify(UnknownHostException()).neverSent)
        assertEquals(NetworkFailureKind.NO_NETWORK, BankBackendRelay.classify(UnknownHostException()).kind)
        assertTrue(BankBackendRelay.classify(NoRouteToHostException()).neverSent)
        assertTrue(BankBackendRelay.classify(ConnectException()).neverSent)
        assertFalse(BankBackendRelay.classify(SocketTimeoutException()).neverSent)
        assertFalse(BankBackendRelay.classify(IOException("connection reset")).neverSent)
    }
}
