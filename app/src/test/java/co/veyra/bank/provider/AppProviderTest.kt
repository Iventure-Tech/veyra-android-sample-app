package co.veyra.bank.provider

import co.veyra.common.providers.VeyraAssertionProvider
import co.veyra.common.providers.VeyraProviderType
import co.veyra.common.providers.VeyraProxyProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each provider is built from its own values only; the SDK infers the method from its type. */
class AppProviderTest {

    private val http = OkHttpClient()

    @Test
    fun theSampleShipsTheProxyProvider() {
        // The values are compiled in from the local, untracked config: with them the default is
        // the proxy provider; without them (CI) it refuses, naming the missing bank backend.
        if (co.veyra.bank.BuildConfig.BANK_BACKEND_BASE_URL.isNotBlank() &&
            co.veyra.bank.BuildConfig.BANK_CLIENT_ID.isNotBlank() &&
            co.veyra.bank.BuildConfig.BANK_CLIENT_SECRET.isNotBlank()
        ) {
            assertTrue(AppProvider.provider() is VeyraProxyProvider)
        } else {
            val e = runCatching { AppProvider.provider() }.exceptionOrNull()
            assertTrue("$e", e is IllegalStateException && e.message.orEmpty().contains("must be set"))
        }
    }

    @Test
    fun theAssertionProviderCarriesTheClientId() {
        val provider = AppProvider.assertionProvider("id", "bank-id", "bank-secret", "https://bank.example", { "s" }, http)
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
                AppProvider.assertionProvider("id", bankClientId, bankClientSecret, "https://bank.example", { "s" }, http)
            }.exceptionOrNull()
            assertTrue(e is IllegalStateException && e.message!!.contains(missing))
        }
    }

    @Test
    fun theProxyProviderNeedsNoClientIdOrSecret() {
        val provider = AppProvider.proxyProvider("https://bank.example", { "s" }, http)
        assertTrue(provider is VeyraProxyProvider)
        assertEquals(VeyraProviderType.PROXY, provider.providerType)
    }

    @Test
    fun theClientSecretProviderNeedsNoBankBackend() {
        @Suppress("DEPRECATION")
        val provider = AppProvider.clientSecretProvider("id", "secret") as co.veyra.common.providers.VeyraClientSecretProvider
        assertEquals("id", provider.clientId)
        assertEquals("secret", provider.clientSecret)
    }

    @Test
    fun theBackendProvidersNeedTheBackendUrl() {
        for (build in listOf(
            { AppProvider.assertionProvider("id", "bank-id", "bank-secret", " ", { "s" }, http) },
            { AppProvider.proxyProvider("", { "s" }, http) },
        )) {
            val e = runCatching { build() }.exceptionOrNull()
            assertTrue(e is IllegalStateException && e.message!!.contains("bankBackendBaseUrl"))
        }
    }
}
