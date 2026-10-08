package co.veyra.bank.connection

import co.veyra.common.connection.VeyraConnection
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mode is the app's decision: no default, and an unset one stops the app naming what to set. */
class AppConnectionTest {

    private fun from(mode: String, base: String = "https://bank.example") =
        AppConnection.from(mode, "id", "secret", base, { "s" }, OkHttpClient())

    @Test
    fun eachModeBuildsItsConnection() {
        @Suppress("DEPRECATION")
        assertTrue(from("directWithClientSecret") is VeyraConnection.DirectWithClientSecret)
        assertTrue(from("directWithAssertion") is VeyraConnection.DirectWithAssertion)
        assertTrue(from("viaAppBackend") is VeyraConnection.ViaAppBackend)
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
