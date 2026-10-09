package co.veyra.bank.connection

import co.veyra.common.providers.VeyraAuthProvider
import co.veyra.common.providers.VeyraProviderType
import co.veyra.common.providers.VeyraProxyProvider
import org.junit.Assert.assertEquals
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mode is the app's decision: no default, and an unset one stops the app naming what to set. */
class AppConnectionTest {

    private fun from(mode: String, base: String = "https://bank.example") =
        AppConnection.from(mode, "id", "secret", base, { "s" }, OkHttpClient())

    @Test
    fun eachModeBuildsItsProvider() {
        @Suppress("DEPRECATION")
        assertTrue(from("directWithClientSecret") is co.veyra.common.providers.VeyraClientSecretProvider)
        val auth = from("directWithAssertion")
        assertTrue(auth is VeyraAuthProvider && auth.clientId == "id")
        assertEquals(VeyraProviderType.AUTHENTICATION, auth.providerType)
        val request = from("viaAppBackend")
        assertTrue(request is VeyraProxyProvider)
        assertEquals(VeyraProviderType.REQUEST_PROCESSOR, request.providerType)
    }

    @Test
    fun anUnsetModeFailsLoudly() {
        val e = runCatching { from("") }.exceptionOrNull()
        assertTrue(e is IllegalStateException && e.message!!.contains("connection.mode"))
    }

    @Test
    fun theBackendModesNeedTheBackendUrl() {
        val e = runCatching { from("viaAppBackend", base = "") }.exceptionOrNull()
        assertTrue(e is IllegalStateException && e.message!!.contains("bankBackendBaseUrl"))
    }
}
