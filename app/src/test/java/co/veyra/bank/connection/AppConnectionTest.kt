package co.veyra.bank.connection

import co.veyra.common.providers.VeyraAssertionProvider
import co.veyra.common.providers.VeyraProviderType
import co.veyra.common.providers.VeyraProxyProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each provider is built from its own values only; the SDK infers the method from its type. */
class AppConnectionTest {

    private val http = OkHttpClient()

    @Test
    fun theSampleShipsTheTestingOnlyClientSecretProvider() {
        @Suppress("DEPRECATION")
        assertTrue(AppConnection.provider() is co.veyra.common.providers.VeyraClientSecretProvider)
    }

    @Test
    fun theAssertionProviderCarriesTheClientId() {
        val provider = AppConnection.assertionProvider("id", "bank-id", "bank-secret", "https://bank.example", { "s" }, http)
        assertTrue(provider is VeyraAssertionProvider && provider.clientId == "id")
        assertEquals(VeyraProviderType.AUTHENTICATION, provider.providerType)
    }

    @Test
    fun theAssertionProviderNeedsTheBankClient() {
        for ((bankClientId, bankClientSecret, missing) in listOf(
            Triple("", "bank-secret", "bankClientId"),
            Triple("bank-id", " ", "bankClientSecret"),
        )) {
            val e = runCatching {
                AppConnection.assertionProvider("id", bankClientId, bankClientSecret, "https://bank.example", { "s" }, http)
            }.exceptionOrNull()
            assertTrue(e is IllegalStateException && e.message!!.contains(missing))
        }
    }

    @Test
    fun theProxyProviderNeedsNoClientIdOrSecret() {
        val provider = AppConnection.proxyProvider("https://bank.example", { "s" }, http)
        assertTrue(provider is VeyraProxyProvider)
        assertEquals(VeyraProviderType.PROXY, provider.providerType)
    }

    @Test
    fun theClientSecretProviderNeedsNoBankBackend() {
        @Suppress("DEPRECATION")
        val provider = AppConnection.clientSecretProvider("id", "secret") as co.veyra.common.providers.VeyraClientSecretProvider
        assertEquals("id", provider.clientId)
        assertEquals("secret", provider.clientSecret)
    }

    @Test
    fun theBackendProvidersNeedTheBackendUrl() {
        for (build in listOf(
            { AppConnection.assertionProvider("id", "bank-id", "bank-secret", " ", { "s" }, http) },
            { AppConnection.proxyProvider("", { "s" }, http) },
        )) {
            val e = runCatching { build() }.exceptionOrNull()
            assertTrue(e is IllegalStateException && e.message!!.contains("bankBackendBaseUrl"))
        }
    }
}
