package co.veyra.bank.connection

import co.veyra.common.providers.VeyraAssertionProvider
import co.veyra.common.providers.VeyraProvider
import co.veyra.common.providers.VeyraProviderType
import co.veyra.common.providers.VeyraProxyProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mode is the app's decision: no default, and an unset one stops the app naming what to set.
 * Each mode builds from its own settings only — the other modes' builders are never run.
 */
class AppConnectionTest {

    private val http = OkHttpClient()

    private fun forMode(mode: String, built: MutableList<String> = mutableListOf()): VeyraProvider =
        AppConnection.forMode(
            mode,
            assertion = { built += "assertion"; AppConnection.assertionProvider("id", "https://bank.example", { "s" }, http) },
            proxy = { built += "proxy"; AppConnection.proxyProvider("https://bank.example", { "s" }, http) },
            clientSecret = { built += "clientSecret"; AppConnection.clientSecretProvider("id", "secret") },
        )

    @Test
    fun eachModeBuildsOnlyItsOwnProvider() {
        for ((mode, expected) in listOf(
            "directWithAssertion" to "assertion",
            "viaAppBackend" to "proxy",
            "directWithClientSecret" to "clientSecret",
        )) {
            val built = mutableListOf<String>()
            forMode(mode, built)
            assertEquals(listOf(expected), built)
        }
    }

    @Test
    fun theAssertionModeBuildsAnAssertionProvider() {
        val provider = forMode("directWithAssertion")
        assertTrue(provider is VeyraAssertionProvider && provider.clientId == "id")
        assertEquals(VeyraProviderType.AUTHENTICATION, provider.providerType)
    }

    @Test
    fun theBackendModeBuildsAProxyProvider() {
        val provider = forMode("viaAppBackend")
        assertTrue(provider is VeyraProxyProvider)
        assertEquals(VeyraProviderType.REQUEST_PROCESSOR, provider.providerType)
    }

    @Test
    fun theClientSecretModeNeedsNoBankBackend() {
        @Suppress("DEPRECATION")
        val provider = AppConnection.clientSecretProvider("id", "secret") as co.veyra.common.providers.VeyraClientSecretProvider
        assertEquals("id", provider.clientId)
        assertEquals("secret", provider.clientSecret)
    }

    @Test
    fun anUnsetModeFailsLoudly() {
        val built = mutableListOf<String>()
        val e = runCatching { forMode("", built) }.exceptionOrNull()
        assertTrue(e is IllegalStateException && e.message!!.contains("connection.mode"))
        assertTrue(built.isEmpty())
    }

    @Test
    fun theBackendModesNeedTheBackendUrl() {
        for (build in listOf(
            { AppConnection.assertionProvider("id", " ", { "s" }, http) },
            { AppConnection.proxyProvider("", { "s" }, http) },
        )) {
            val e = runCatching { build() }.exceptionOrNull()
            assertTrue(e is IllegalStateException && e.message!!.contains("bankBackendBaseUrl"))
        }
    }
}
