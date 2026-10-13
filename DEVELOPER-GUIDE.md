# Veyra SDK for Android — Developer Guide

This document describes the public API exposed by the Veyra SDK on **Android**. Only the classes and methods documented here are supported API; anything else you can reach in the artifacts is internal and may change without notice.

## Overview

The Veyra SDK turns a phone into either side of a contactless payment:

- **SoftPOS (merchant side)** — accept payments: NFC tap acceptance, get-paid QR codes (merchant-presented), scanning customer payment QRs (consumer-presented), merchant registration, transaction history and receipts.
- **Wallet (customer side)** — make payments: account tokenisation ("add card"), token activation, NFC tap-to-pay, scan-to-pay, show-QR-to-pay, transaction history and receipts.

Three ways to ship it:

| Integration | Android artifact | Use when |
|---|---|---|
| **SoftPOS only** | `co.veyra:softpos-sdk` | Your app only accepts payments |
| **Wallet only** | `co.veyra:wallet-sdk` | Your app only makes payments |
| **Combined** | `co.veyra:veyra-sdk` | One app does both — never at the same time; the SDK enforces an exclusive mode |

Building for iOS? See the iOS guide in the iOS sample repo: https://github.com/Iventure-Tech/veyra-ios-sample-app.

A combined app is always in exactly one **mode** — none, receiving (SoftPOS) or paying (Wallet). The wallet HCE service component is enabled **only** while paying, so outside the Pay screen the device is NFC-inert as Veyra (a terminal detects nothing from Veyra; taps route to the user's default wallet instead), and payment does not require being the system default payment app. The mode switches **implicitly** as the user enters and leaves your get-paid and pay screens — see [Exclusive mode](#exclusive-mode-combined-apps).

> **iOS note:** tap **acceptance** on iPhone reads the customer's Android Veyra wallet over CoreNFC. Tap-to-**pay** (card emulation) is not available on iOS — Apple restricts card emulation — so the iOS wallet pays by QR (scan-to-pay and show-QR-to-pay).

---

## Requirements

| Requirement | SoftPOS / Combined | Wallet only |
|---|---|---|
| `minSdk` | **28** (Android 9) | **24** (Android 7.0) |
| `compileSdk` | 34 | 34 |
| NFC hardware | Required for tap acceptance | Required for tap-to-pay (QR rails work without) |
| Google Play services | Not required — the SoftPOS SDK performs no Play Integrity calls | Required for attested operations (add card, key refresh) |
| Permissions | `INTERNET`, `NFC`, location (merged from the SDK manifests; location is a *runtime* permission — request it before opening your tap screen) | `INTERNET`, `NFC` (merged) |

The SDK manifests merge everything they need into your app (permissions, the NFC feature flag, the wallet HCE service, backup-exclusion rules). Your app must add on `<application>`:

```xml
android:networkSecurityConfig="@xml/network_security_config"
```

(the config XML ships inside the SDK). **No NFC intent filters or tech-filter meta-data are needed** — tap acceptance uses reader mode, which delivers cards via callback. In a combined app the HCE service's enabled state is owned by the SDK's mode manager — **never toggle `PaymentCardEmulationService` yourself**.

---

## Getting the SDK

### Maven

The Veyra repository is **authenticated** — use the repository credentials issued to you at
onboarding (keep them out of version control, e.g. in a git-ignored properties file; the
sample app reads them from `veyra.properties`).

Register the repository in `settings.gradle` (`dependencyResolutionManagement`) or, on older
layouts, the `repositories` block of your root `build.gradle`:

```gradle
// settings.gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url 'https://repo.veyra.co/releases'
            credentials {
                username = veyraRepoUsername   // from your onboarding pack
                password = veyraRepoPassword   // never commit these
            }
        }
        // Pre-release builds only, when Veyra asks you to verify a fix ahead of a release:
        // maven { url 'https://repo.veyra.co/snapshots' } (same credentials block)
    }
}
```

Then one dependency line — everything else resolves transitively:

```gradle
dependencies {
    implementation 'co.veyra:veyra-sdk:<version>'   // combined app
    // or: implementation 'co.veyra:softpos-sdk:<version>'   // softpos-only
    // or: implementation 'co.veyra:wallet-sdk:<version>'    // wallet-only
}
```

| Artifact | Contents | Min Android |
|---|---|---|
| `co.veyra:softpos-sdk` | SoftPOS SDK (pulls `veyra-core`, `veyra-kernel`, `veyra-common` transitively) | 28 |
| `co.veyra:wallet-sdk` | Wallet SDK (same transitive core) | 24 |
| `co.veyra:veyra-sdk` | Combined facade + exclusive mode manager; depends on both SDKs | 28 |
| `co.veyra:veyra-core` | Mode contract (`NfcMode`); no other dependencies | 24 |
| `co.veyra:veyra-common` | Shared infrastructure (`Environment`, the provider types, `VeyraSetupException`, the outcome constants `OutcomeStatus` / `OutcomeReason`, logging, token cache) | 24 |

There is deliberately **no fat AAR** — the combined offering is the thin `veyra-sdk` artifact that pulls the others transitively.

### File-based AARs

`implementation files(...)` resolves nothing transitively, so file-based deliveries are a bundle: the SDK AARs **plus** every runtime dependency, declared explicitly.

**1. Add the AARs** (bundle supplied by Veyra with each release):

```gradle
dependencies {
    // SoftPOS-only:
    implementation files('libs/softpos-sdk-release.aar')
    implementation files('libs/veyra-core-release.aar')
    implementation files('libs/veyra-kernel-release.aar')
    implementation files('libs/veyra-common-release.aar')
    // Wallet-only: swap softpos-sdk for wallet-sdk-release.aar (same core AARs).
    // Combined: all six — veyra-sdk-release.aar + both SDK AARs + the three core AARs.
}
```

**2. Add the required dependencies:**

```gradle
dependencies {
    // ── Shared (all integrations) ───────────────────────────────────────────
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'io.opentelemetry:opentelemetry-sdk:1.47.0'
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp:1.47.0'
    implementation 'io.opentelemetry:opentelemetry-exporter-sender-okhttp:1.47.0'

    // ── SoftPOS integrations ────────────────────────────────────────────────
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-process:2.6.2'
    implementation 'com.google.android.gms:play-services-location:21.3.0'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.76'
    implementation 'org.bouncycastle:bcpkix-jdk18on:1.76'
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    implementation 'com.google.zxing:core:3.5.2'

    // ── Wallet integrations ─────────────────────────────────────────────────
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    implementation 'androidx.security:security-crypto:1.1.0-alpha06'
    implementation 'org.bouncycastle:bcprov-jdk18on:1.76'
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.1.0'
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
    implementation 'com.google.android.gms:play-services-wallet:19.2.1'
    implementation 'androidx.biometric:biometric:1.1.0'
}
```

**Note:** with Maven consumption none of this is needed — the dependencies resolve transitively from the one `co.veyra:*` line.

---

## Main entry points

### `VeyraSdk` (combined apps)

Facade for a combined SoftPOS + Wallet app. One `initialize` sets up both member SDKs, owns the exclusive mode and hands out the two SDKs. Package `co.veyra.sdk`.

```kotlin
val sdk = VeyraSdk.initialize(this, customerId, VeyraSdkConfig(softposConfig, walletConfig), provider)
val wallet  = sdk.wallet    // VeyraWalletSdk
val softpos = sdk.softpos   // VeyraSoftPOSSdk
```

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, customerId, config, provider)` | `activity` — an `AppCompatActivity`; `customerId` — the customer your app has authenticated (the wallet sends it to Veyra on its card requests; see [Customers](#customers-signing-in-signing-out-switching)); `config` — `VeyraSdkConfig(softpos, wallet)`; `provider` — your `VeyraAssertionProvider` or `VeyraProxyProvider`, shared by both SDKs (see [Connecting to Veyra](#connecting-to-veyra)) | Installs the exclusive-mode arbiter, **sets up both SDKs** (SoftPOS and wallet) and signs `customerId` in for both. Neither SDK's NFC binds to `activity` (typically Home): each screen that pays or gets paid still calls its own SDK's `initialize` with itself, and that SDK's NFC follows that screen. Idempotent process singleton; always starts inert (no mode active). Must be called before either member SDK is used in a combined app, **on every launch** — see [Customers](#customers-signing-in-signing-out-switching). Throws `VeyraSetupException` (`INVALID_CONFIGURATION`) when `customerId` is blank or the provider is unusable (naming the problem). |
| `signOut()` (static) | — | The customer has logged out: stops everything both SDKs run for them and drops every observer registration. Their cards, keys, merchant and history stay on the device for when they sign in again. |
| `getInstance()` | — | The current instance, or `null` if not yet initialised. |
| `softpos` | — | The `VeyraSoftPOSSdk`, set up by `initialize`. Throws `VeyraSetupException` (`NOT_CONFIGURED`) before `initialize` has been called. |
| `wallet` | — | The `VeyraWalletSdk`. Same behaviour. |
| `currentMode()` | — | The current exclusive mode (`NfcMode.NONE` / `SOFTPOS` / `WALLET`). Read-only observation for UI state. |

`VeyraSdkConfig` is a simple pair of the two SDK configs: `VeyraSdkConfig(softpos: VeyraSoftPosSdkConfig, wallet: VeyraWalletSdkConfig)`.

**The bypass rule:** with both SDKs on the classpath, initialising them *without* `VeyraSdk.initialize` leaves the app fail-closed — every NFC operation throws `VeyraSetupException` (`INVALID_CONFIGURATION`). This is by design: a combined app that skipped the facade must fail loudly rather than risk receiving and paying at once. Standalone single-SDK apps are unaffected.

### `VeyraSoftPOSSdk` (merchant features)

Package `co.veyra.softpos.payment.sdk`. Process singleton; initialise on (or before) each screen that accepts payments, so reader arming binds to that screen's lifecycle.

```kotlin
val sdk = VeyraSoftPOSSdk.initialize(this, customerId, softposConfig, provider)
```

**Methods & services:**

| Member | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, customerId, config, provider)` | `activity` — `AppCompatActivity`; `customerId` — the customer your app has authenticated (SoftPOS never sends it off the device); `config` — `VeyraSoftPosSdkConfig`; `provider` — the same provider as the wallet's | Initialises (or re-binds) the singleton and signs `customerId` in. Idempotent; re-binds NFC reader + lifecycle to the new activity on every call. The same customer again changes nothing; a different customer stops the previous customer's work and opens the new customer's merchant and transactions. Throws `VeyraSetupException` (`INVALID_CONFIGURATION`) when the provider is unusable, a different kind of provider is already in use in this process, or `customerId` is blank; the message names the problem. |
| `signOut()` (static) | — | The customer has logged out: stops every poll and watch the SDK runs for them and drops every observer registration. Their merchant and transactions stay on the device. Until the next `initialize` there is no merchant. |
| `storedMerchant(context, customerId)` (static) | `context`; `customerId` | Init-free read of that customer's stored merchant — see [Merchant status](#merchant-status--isregistered--ismerchantactive--refreshstatus--activate--deactivate). |
| `isMerchantRegistered(context, customerId)` (static) | `context`; `customerId` | Init-free registration check for that customer. |
| `getInstance()` | — | Current instance or `null`. |
| `cardPaymentService` | — | Tap acceptance — [`makeCardPayment`](#makecardpayment) and friends. |
| `merchantService` | — | Merchant registration, status, profile, banks. |
| `transactionService` | — | Merchant transaction history + receipts (all rails). |
| `cpmCustomerQrService` | — | Charge a scanned customer payment QR. |
| `cancelPayment(merchantOrderId)` | `merchantOrderId` — the order ID the payment was started with | Cancel a tap, customer-QR charge or get-paid QR create while its request is still on the phone. Returns a `CancelResult` at once — see [Cancelling a call](#cancelling-a-call). |

The reader arms on resume and disarms on pause automatically. If NFC is unavailable or off, the SDK shows its own prompt dialogs at initialise.

### `VeyraWalletSdk` (wallet features)

Package `co.veyra.wallet.sdk`. Process singleton. The environment is set **once at initialisation** and applies to all subsequent SDK calls.

```kotlin
val wallet = VeyraWalletSdk.initialize(context, customerId, walletConfig, provider, activity = this)
```

Every wallet operation is called on this object — `wallet.digitise(...)`, `wallet.getTokens()`,
`wallet.observeTapEvents { }` — under the same names as on iOS and React Native (see
[Wallet — making payments](#wallet--making-payments)).

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(context, customerId, config, provider, activity?)` | `context` — app context; `customerId` — the customer your app has authenticated (sent to Veyra on the wallet's card requests; see [Customers](#customers-signing-in-signing-out-switching)); `config` — `VeyraWalletSdkConfig`; `provider` — the same provider as SoftPOS's; `activity` — optional, pass it for automatic NFC activation | Initialises the singleton and signs `customerId` in. Idempotent — a repeat call re-binds the NFC lifecycle to the new activity. The same customer again changes nothing and downloads nothing; a different customer stops the previous customer's work and opens the new customer's cards. Throws `VeyraSetupException` (`INVALID_CONFIGURATION`) when `customerId` is blank, the provider is unusable, or a different kind of provider is already in use in this process; the message names the problem. |
| `signOut()` (static) | — | The customer has logged out: stops every loop, poll, worker and observer the SDK runs for them. Their cards, keys, history and receipts stay on the device. Until the next `initialize` there are no cards and a tap is answered "application not found". |
| `getInstance()` | — | Current instance or `null`. |
| `openPaySession(activity)` | `activity` — your pay screen | Arms tap-to-pay on the pay screen until `closePaySession`; returns a `PaySessionResult`. See [Arming and disarming tap-to-pay](#arming-and-disarming-tap-to-pay-pay-session). |
| `closePaySession()` | — | Disarms tap-to-pay at once — on cancel, on your own timeout, or when a single-Activity app navigates away from the pay screen. Any thread. |
| `isPaySessionOpen()` | — | `true` while a session opened with `openPaySession` is open. |
| `getTransactions(tokenUniqueReference, limit = 50)` | `tokenUniqueReference` — the card; `limit` — maximum rows | The card's transaction history, most recent first. Local read. See [`getTransactions`](#gettransactions). |
| `getPaymentApplicationInstanceId()` | — | This install's `payment_application_instance_id` — **SDK-generated** (`VYRA` + 32 hex chars), minted on first use, persisted install-scoped, never backed up, new on reinstall. Read-only; the app cannot set or regenerate it. |
| Every other wallet operation | | Add card, activation, tokens, payments, history, receipts and the `observe…` / `stopObserving…` pairs — see [Wallet — making payments](#wallet--making-payments). |

---

## Customers: signing in, signing out, switching

The SDK keeps everything it stores **per customer**: wallet cards, payment keys, transaction history and receipts, the registered merchant and merchant transactions. One phone can be shared by several of your customers, and none of them sees another's data. Storage is named from a one-way hash of the `customerId` you pass, never the id itself. The wallet SDK also sends the id to Veyra on its card requests (adding a card, the eligibility check, card activation) as `customer_id`, so Veyra support can find a customer's requests when you raise a query. SDK logs only ever show it masked. SoftPOS never sends it.

Who is signed in is **your app's** to remember, not the SDK's. The SDK holds it only in memory, for the life of the process:

- **Pass the customer on every launch.** Call `initialize` with the authenticated customer's id each time your app starts (and on each screen that initialises an SDK, as today). Use a stable id from your own login — the same customer must always get the same id, or their data will not be found.
- **Same customer again — nothing happens.** Repeated `initialize` calls with the same id stop nothing and download nothing.
- **A different customer — the SDK switches.** The previous customer's work (key refreshes, status polls, pending-payment checks, background workers) is stopped, and the new customer's cards and merchant are opened from the device.
- **Sign out with `signOut()`** (`VeyraWalletSdk.signOut()`, `VeyraSoftPOSSdk.signOut()`, or `VeyraSdk.signOut()` in a combined app). It stops every loop, poll, worker and observer for the customer. Their cards, keys, history, merchant and transactions **stay on the device**: when they sign in again nothing is downloaded for a card that still has its keys, and a card without keys gets them in the background after sign-in.
- **Register observers again after `initialize`.** A sign-out and a switch to another customer both drop every observer registration (`observeTransactionResolved`, `observeTokenStatusChanged`, `observeCardKeyStateChanged`, `observeTapEvents`, `onMerchantStatusChanged`, credit confirmations, refusal handlers). Register them right after each `initialize` call that signs a customer in.
- **Nobody signed in means no data.** After `signOut()`, and in a fresh process where your app has not called `initialize` yet (the app was killed, the phone restarted), there are no cards and no merchant, and an NFC tap from a terminal is answered "application not found" — no payment is made. A wallet or SoftPOS call that needs the customer's data — cards, payments, the stored merchant, transactions — throws `VeyraSetupException` with `code = NOT_SIGNED_IN`; catch it to send the user to your sign-in. A request already on its way when the customer signs out still completes for that customer: its answer is kept with their data, and you hear about it when they sign in again. Initialise with the customer as soon as your app knows who they are.
- **The init-free reads take the customer.** `VeyraSoftPOSSdk.storedMerchant(context, customerId)` and `VeyraSoftPOSSdk.isMerchantRegistered(context, customerId)` read that customer's merchant without signing anyone in.
- **Re-registering a merchant needs no clear.** A successful registration overwrites the customer's stored merchant.

```kotlin
// Your login succeeded (or the app starts with a remembered session):
val wallet = VeyraWalletSdk.initialize(context, session.customerId, walletConfig, provider, activity = this)
wallet.observeTokenStatusChanged { change -> /* ... */ }  // register again after every initialize

// Your customer logs out:
VeyraWalletSdk.signOut()
session.clear()
```

---

## Connecting to Veyra

You pass **one provider** to `initialize`, and it says how the SDK reaches the Veyra backend. The
same provider serves both SDKs. There is **no default**: which kind you implement is your decision
as the payment app provider. The types live in `co.veyra.common.providers` (`VeyraProvider`,
`VeyraAssertionProvider`, `VeyraProxyProvider`, `VeyraProviderType`, `VeyraRelayException`).

### Choosing a provider

| Provider | `providerType` | The SDK… | Choose it when |
|---|---|---|---|
| `VeyraAssertionProvider` | `AUTHENTICATION` | calls Veyra itself, with a token it obtains by exchanging a short-lived **assertion your backend signs** for the signed-in user | your backend can sign a JWT for the logged-in user (recommended) |
| `VeyraProxyProvider` | `PROXY` | calls **nothing** itself: every call is handed to your provider, which forwards it through **your backend** | you want all traffic through your own backend, or cannot run a signer |

**The interface you implement is the method.** Each interface supplies its `providerType` by
default, so you never set a mode: the SDK reads it from the provider you pass.

Rules that hold for every provider:

- **One provider for both SDKs.** Pass the same object to `VeyraSdk.initialize` and to each SDK's
  own `initialize`. Each SDK still keeps its own token cache and device key.
- **Exactly one kind.** `initialize` refuses, naming the problem: no provider; a provider that
  implements both interfaces; a `providerType` that contradicts the interface it implements; and a
  blank `clientId`. Both SDKs and `VeyraSdk` throw `VeyraSetupException` with
  `code = INVALID_CONFIGURATION`. (Kotlin also refuses to compile a class that
  implements both, until it overrides `providerType` itself.)
- **One kind per process, with no fallback.** A `VeyraAssertionProvider` whose `assertion` returns
  nothing fails with `NOT_AUTHENTICATED`; the SDK never tries another method. Initialising again
  with the other kind throws `VeyraSetupException` (`INVALID_CONFIGURATION`).
- **Initialise re-binds the provider.** The provider you pass on each `initialize` is the one the
  SDK uses from then on — background work included.
- **A call is never sent without credentials.** When the SDK cannot obtain a token the call fails
  with `NOT_AUTHENTICATED` and nothing is sent: a payment or an add card ends `NOT_SENT` with reason
  `NOT_AUTHENTICATED`, any other call `FAILED` with the same reason (no response code, nothing stored
  or polled) in both SDKs.
- Every backend call (token requests included) carries `X-Veyra-Sdk-Version` and
  `X-Veyra-Provider-Type` (your provider's `providerType`: `AUTHENTICATION` or `PROXY`).

### Implementing a provider

**`VeyraAssertionProvider`** — your client id, and one function that gets an assertion from your
backend. The SDK calls it only when it needs a new token:

```kotlin
class MyAssertionProvider(private val bank: MyBankApi) : VeyraAssertionProvider {
    override val clientId = "your-client-id"          // public, issued at onboarding

    /** A JWT from your backend for the signed-in user, or null when nobody is signed in. */
    override suspend fun assertion(audience: String, jkt: String): String? =
        bank.sdkAssertion(audience, jkt)               // POST {your backend}/sdk-assertion, your session
}
```

**`VeyraProxyProvider`** — one function, `send`. The SDK calls it for every backend call; the
envelope it passes names the HTTP method:

```kotlin
class MyProxyProvider(private val bank: MyBankApi) : VeyraProxyProvider {
    override suspend fun send(request: String) = bank.veyraProxy(request)
}
```

`bank.veyraProxy` sends the envelope to `POST {your API gateway}/issuertokengateway/v1/proxy` and returns
Veyra's body unchanged. On failure it throws `VeyraRelayException`, saying whether the request was
sent — see [the failure contract](#the-proxy-providers-failure-contract).

**Proxy flow.** For the proxy provider, **deploy the Veyra Issuer Token Gateway (ITG): it supports
proxying all SDK requests.** It obtains an access token from the Veyra token endpoint and calls the
Veyra API for you, so you write no proxy code of your own on the server:

```
Your app ── VeyraProxyProvider.send(envelope)
  │
  ├─▶ Your (issuer) API gateway / authorisation server
  │     POST /issuertokengateway/v1/proxy — checks your app's session,
  │     removes the /issuertokengateway/v1 context
  │
  ├─▶ Veyra Issuer Token Gateway (ITG)          POST /proxy
  │     ├─▶ Veyra token endpoint                 client-credentials grant (the ITG's own
  │     │                                        credentials) → access token, cached until expiry
  │     └─▶ Veyra API                            method + service path, query, headers, body,
  │                                              with Authorization
  │
  ◀── Veyra's status and body, unchanged (or 200 PROXY_FAILED from the ITG itself)
```

### Initialising with your provider

```kotlin
val provider = MyAssertionProvider(bankApi)                // or MyProxyProvider(bankApi)

val softposConfig = VeyraSoftPosSdkConfig.builder(Environment.TEST, paymentAppProviderId).build()
val walletConfig = VeyraWalletSdkConfig.builder(Environment.TEST, paymentAppProviderId, tokenRequestorId).build()

// A combined app: the facade sets up both SDKs; each payment screen then initialises its own SDK
// with itself, so that SDK's NFC follows it — the same provider each time.
VeyraSdk.initialize(activity, customerId, VeyraSdkConfig(softposConfig, walletConfig), provider)
VeyraSoftPOSSdk.initialize(activity, customerId, softposConfig, provider)
VeyraWalletSdk.initialize(context, customerId, walletConfig, provider, activity)
```

The sample has no mode setting either: `AppProvider.provider()` in
`app/src/main/java/co/veyra/bank/provider/AppProvider.kt` returns one provider, and to switch
you return a different one — exactly what your own app does. Its two backend providers,
`BankBackendAssertionProvider` and `BankBackendRelay`, are short and meant to be copied; each reads
only its own values from `veyra.properties` (the assertion provider: `veyra.clientId`,
`veyra.bankBackendBaseUrl`, `veyra.bankClientId` and `veyra.bankClientSecret`; the proxy provider:
`veyra.bankBackendBaseUrl`, `veyra.bankClientId` and `veyra.bankClientSecret`; both also log the
user in with `veyra.username`/`veyra.password`). `veyra.bankClientId`/`veyra.bankClientSecret` are your bank's **own**
OAuth client at its authorization server, not the Veyra client, and never reach the SDK: the
assertion provider's only Veyra credential is `veyra.clientId`. The sample ships
returning the proxy provider (`BankBackendRelay`), so the app holds no Veyra secret. The deprecated
`ClientSecretCredentials` — a `VeyraClientSecretProvider` **for testing only** that needs just
`veyra.clientId` and `veyra.clientSecret` — is still there for testing before your backend has
either endpoint.

### Your bank backend — the two endpoints the sample calls

> **Signing the user in to your bank is your app's responsibility, not the SDK's.** The SDK never
> authenticates with your bank or its backend. It doesn't collect credentials, it doesn't obtain,
> refresh or end a bank session, and it doesn't call your login or token endpoints. It only asks your
> provider for what it needs: an assertion (`VeyraAssertionProvider`), or to send a request
> (`VeyraProxyProvider`). Your provider gets those using the session your app already holds. How the
> user signs in (password, biometrics, single sign-on, multi-factor), and how that session is stored,
> refreshed and revoked, is your app's own design. The sample's password-grant login, with credentials
> read from local config, is a demo stand-in only — not a pattern to ship.

`VeyraAssertionProvider` and `VeyraProxyProvider` each need one endpoint on **your** backend. Both
carry your app's **own** session: the sample logs in with `veyra.username`/`veyra.password` from `veyra.properties` — a password
grant at `{bankBackendBaseUrl}/oauth2/token` — and uses the returned access token; replace that with
your own login. Neither is a Veyra credential.

```
POST {your backend}/oauth2/token                           (VeyraAssertionProvider)
     RFC 8693 token exchange, form-encoded, your bank's client in HTTP Basic
     (veyra.bankClientId:veyra.bankClientSecret):
       grant_type=urn:ietf:params:oauth:grant-type:token-exchange
       subject_token=<your session>
       subject_token_type=urn:ietf:params:oauth:token-type:access_token
       requested_token_type=urn:ietf:params:oauth:token-type:jwt
       audience=<audience>
  →  200 {"access_token": "<compact JWT>", …}   401 when the session is refused (the provider returns null)

POST {your API gateway}/issuertokengateway/v1/proxy (VeyraProxyProvider)
     body: the SDK's envelope, unchanged, for every method
  →  your gateway checks the app's session, removes the `/issuertokengateway/v1` context and
     forwards the envelope to the Veyra Issuer Token Gateway (ITG) as `POST /proxy`
  →  the ITG gets an access token from the Veyra token endpoint with its own OAuth
     client credentials (held server-side; an API key is not accepted), calls the
     Veyra API (`service` + `path` with `method`, `query`, `headers` and `body`),
     and answers with Veyra's status and body unchanged
```

**Your authorization server signs** the assertion as a compact JWT, with the signing key held in an HSM or KMS:

**Required by Veyra — the minimum.** How you produce the assertion is up to you: your own
endpoint, your authorization server's token exchange (RFC 8693), or any identity provider. Veyra
checks only the following. An assertion that is missing any of them, or fails a check, is refused,
and the SDK reports `NOT_AUTHENTICATED`.

| Part | What to put in it | Why Veyra needs it |
|---|---|---|
| Signature (`alg`) | `ES256`, `ES384`, `PS256` or `RS256` (the ones agreed for your issuer), signed with a private key held in an HSM or KMS. Never `none` or an HMAC (`HS…`) algorithm | Veyra verifies it with your **public** key (your JWKS URI, or keys you registered). That proves the assertion came from you, and Veyra can never create one itself |
| `kid` (header) | The id of the signing key in your JWKS. It can be left out only if you publish exactly one key | Picks the key to verify with, so you can rotate: publish the new key, then start signing with it |
| `iss` | Exactly the issuer identifier registered with Veyra, character for character | Tells Veyra whose keys and rules apply. An unknown or disabled issuer is refused |
| `sub` | A stable, pairwise identifier for the signed-in user: the same value every time for that user. Never an account number, customer id, phone number or email | Veyra derives its own user id from `iss` + `sub`, so the same person is recognised across sessions and devices without you sharing who they are |
| `aud` | The `audience` the SDK passed to your provider (the Veyra API base URL: `https://api.uat.veyra.co` on `TEST`, `https://api.veyra.co` on `LIVE`), or the alias agreed at onboarding | Addresses the assertion to Veyra only, so no other service that trusts your key can accept it |
| `iat` | When you issued it, in seconds since the epoch | Must not be in the future (Veyra allows 60 seconds of clock difference). Together with `exp`, it bounds the assertion's life |
| `exp` | The expiry: at most your agreed maximum after `iat`, which is **5 minutes** unless agreed otherwise | Limits how long a leaked assertion could be used. An expired one is refused |
| `jti` | A new unique id for every assertion, e.g. a UUID | Veyra accepts each `iss` + `jti` only once, so a captured assertion can't be replayed |

**Optional:**

| Claim | What to put in it | Why |
|---|---|---|
| `cnf.jkt` *(recommended)* | Exactly the `jkt` the SDK passed to your provider | Binds the assertion to this device's key. If it's present, Veyra refuses the assertion unless it matches the key the SDK proves it holds, so a leaked assertion is useless on any other device. Veyra can make it mandatory for your issuer if you agree to that |
| `acr` | The authentication level of the user's session, from the values agreed at onboarding | Needed only if Veyra sets a minimum level for your issuer, e.g. to require multi-factor sign-in. Below the minimum, the assertion is refused |
| `nbf` | A not-before time | Honoured if present |

**Only the required claims above are mandatory.** `cnf.jkt`, `acr` and `nbf` are optional: include
`acr` only if Veyra has agreed a minimum sign-in level for your issuer. Any other claim is ignored.

**Device binding between your app and your backend is optional.** You don't have to bind the
assertion to the device, either with `cnf.jkt` or by using DPoP on your own `/sdk-assertion`
endpoint. Veyra accepts an unbound assertion that meets the minimum above. Either way, the SDK
always uses DPoP with Veyra: the access token it gets is bound to a key generated on this device
that cannot be exported. **What binding adds:** without it, an assertion that leaks in its few
minutes of life could be redeemed once by another device. With it, it can't be redeemed anywhere
else.

Issue it only for an authenticated session of the user it names, and rate-limit the endpoint. The
SDK calls the provider only when it holds no valid token (first call, expiry, or a token the server
refused), never more than once at a time. `jkt` is the thumbprint of this install's
proof-of-possession key; the access token is bound to that key, which is generated on the device
and cannot be exported (Android Keystore). Returning `null` or throwing fails the call with
`NOT_AUTHENTICATED` and sends nothing. `signOut` drops cached tokens but keeps the per-install key.

`audience` is the base URL of the Veyra API the SDK will redeem the assertion at
(`https://api.uat.veyra.co` on `TEST`, `https://api.veyra.co` on `LIVE`). **Check it before you
sign:** copy it into `aud` only when it is a Veyra base URL you expect for that environment, and
refuse anything else, so an assertion your backend signs can never be redeemed anywhere but Veyra.

**`/issuertokengateway/v1/proxy` receives the envelope (version 1, public API).** Each `request` your
proxy provider receives is one JSON string that says everything about the call:

```json
{ "version": 1,
  "service": "SOFTPOS",
  "method": "POST",
  "path": "/payment",
  "query": { "merchant_id": "…" },
  "headers": { "Content-Type": "application/json",
               "X-Veyra-Sdk-Version": "3.0.0",
               "X-Veyra-Provider-Type": "PROXY" },
  "body": "<the request JSON, as a string>" }
```

- `service` is the Veyra API the call is for: `WALLET` or `SOFTPOS`. It names the API, not your
  SDK — a wallet call to the payment API is `SOFTPOS`. Your proxy backend routes on it.
- `method` is the HTTP method the ITG uses towards Veyra. Because the envelope names it, the
  provider has a single `send` and every call goes to one endpoint.
- `path` is relative to that service — never a full URL, and without any API prefix.
- `query` is omitted when there is none (values are decoded). `body` is absent on `GET` and `DELETE`.
- New fields may be added under the same `version`; a breaking change bumps `version`.
- When your proxy backend itself cannot complete a call it answers `200` with
  `{"response_status": "PROXY_FAILED", "response_status_reason": "…", "never_sent": true|false}`.
  Return that body unchanged like any other: the SDK recognises it. `never_sent: true` ends the
  call as a refused connection does (below); `false` or absent, as a timeout does.
- **Return Veyra's response body exactly as Veyra returned it.** If Veyra answered with a non-200
  status, throw `VeyraRelayException(kind = OTHER, neverSent = false, httpStatus = <status>)` rather
  than inventing a body — the SDK treats it exactly as that status on a direct connection.

### The proxy provider's failure contract

A proxy provider signals failure by throwing `VeyraRelayException(kind, neverSent, httpStatus)`, and
**must say whether the request was sent**:

- `neverSent = true` **only** when the request provably never left the device — no network, or the
  connection was refused before anything was written. A payment or a digitisation then ends
  `NOT_SENT` / `SDK_UNABLE_TO_CONNECT` (retry with the same order ID); any other call ends `FAILED` /
  `SDK_UNABLE_TO_CONNECT`. When the kind is `NO_NETWORK` the reason is `NO_NETWORK_CONNECTION` instead.
- `neverSent = false` when it may have been delivered — a timeout, a dropped connection, a non-2xx
  answer, or whenever you cannot tell. The payment stays `PENDING` and the SDK reconciles it. **When
  in doubt, `false`**: it is the safe direction.
- `httpStatus` when your backend answered with a non-2xx status.
- **Any other exception** counts as `neverSent = false`, and so does a call that outlives the
  SDK's own timeout for that request.

The sample's `BankBackendRelay.classify` is the reference: `UnknownHostException` /
`NoRouteToHostException` → `NO_NETWORK`, never sent; `ConnectException` → `CONNECTION_REFUSED`,
never sent; `SocketTimeoutException` → `TIMEOUT`, may have been sent; anything else → `OTHER`, may
have been sent.

**Your proxy provider is called from background work too** — status polling, payment-key refresh, credit
confirmations and Android `WorkManager` jobs — not only from calls your app makes, so it must not
depend on a screen being up (the sample's holds no reference to any activity). If the OS
starts your process for background work before your app has initialised the SDK, there is no
provider: that cycle is skipped and nothing fails.

**What your backend can see.** Request and response bodies pass through your app and backend. Key
material is end-to-end encrypted to the device and payment proofs are MACed, so your backend cannot
read or forge either; it **can** read account and identity fields, and it could alter plain answers
such as a transaction status. That is acceptable only because you, the provider, already hold that
data. **Forward the bytes unmodified.** The SDK's log export does not go through your provider.

---

## Configuration

### `VeyraSoftPosSdkConfig` (SoftPOS)

> **Breaking change:** `builder(...)` now requires a `paymentAppProviderId` — the globally
> unique identifier issued to your organisation at onboarding, the same value your wallet
> configuration carries. The gateway links every merchant you register to it and resolves the
> acquirer id and MCC from it, so `acquirerId` no longer exists anywhere on the SoftPOS
> surface: not on this builder, and not on the registration form data.

```kotlin
val softposConfig = VeyraSoftPosSdkConfig.builder(
    Environment.TEST,
    paymentAppProviderId = "your-payment-app-provider-id",
)
    .enableNfc(true)
    .build()
```

**Builder parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `environment` | **Mandatory** | `Environment.TEST` or `Environment.LIVE`. Determines the server host; set once, applies to all SDK calls. |
| `paymentAppProviderId` | **Mandatory** | Your payment app provider id, issued at onboarding (the same identifier as the wallet config's). Sent on merchant registration/update; the gateway resolves your acquirer id and MCC from it. Must not be blank. |
| `enableNfc(Boolean)` | Optional | Arm the NFC reader capability at init. Default `true`. |
| `merchantId(String)` | Optional | Merchant ID override (same note). |
| `merchantNameAndLocation(String)` | Optional | Merchant name/location override for receipts and EMV data (same note). |
| `merchantCategoryCode(String)` | Optional | 4-digit MCC override (same note). |
| `countryCode(String)` | Optional | ISO 3166-1 numeric, 4-digit zero-padded (e.g. `"0566"` for Nigeria) override (same note). |

After merchant registration the terminal/merchant values come from the stored merchant profile — a typical app sets none of the optional overrides. The acquirer id is learned from the gateway's registration/update/status responses (resolved from your payment app provider) and stored with the merchant; it is never app-supplied.

### `VeyraWalletSdkConfig` (Wallet)

```kotlin
val walletConfig = VeyraWalletSdkConfig.builder(
    Environment.TEST,
    paymentAppProviderId = "your-provider-id",
    tokenRequestorId = "50100000001",
)
    .appVersion("1.2.0")
    .walletProviderTokenizationRecommendationStandardVersion("1.0")
    .allowedAcquirerIds(listOf("ACQ001"))
    .allowedMerchantIds(listOf("MERCHANT01"))
    .enableNfc(true)
    .build()
```

**Builder parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `environment` | **Mandatory** | `Environment.TEST` or `LIVE`. |
| `paymentAppProviderId` | **Mandatory** | Your payment-app provider identifier, assigned by Veyra. Sent in every tokenisation and eligibility request. |
| `tokenRequestorId` | **Mandatory** | Token requestor ID assigned by the scheme. |
| `enableNfc(Boolean)` | Optional | Enable NFC/HCE at init. Default `true`. |
| `appVersion(String)` | Optional | App version sent in digitise requests (falls back to your package version). |
| `walletProviderTokenizationRecommendationStandardVersion(String)` | **Required before digitising** | Standard version for the tokenisation recommendation (e.g. `"1.0"`). Digitise throws if unset. |
| `allowedAcquirerIds(List<String>)` | Optional | Provision context: acquirer IDs to restrict the token to. |
| `allowedMerchantIds(List<String>)` | Optional | Provision context: merchant IDs to restrict to. |

> **Note:** there is **no** `paymentApplicationInstanceId` parameter. The SDK generates and persists an install-scoped instance ID itself and sends it on every eligibility/digitise request — read it via `getPaymentApplicationInstanceId()`. A restricted provision-context dimension that a payment then falls outside of is declined by the server.

### `Environment`

One shared enum for both SDKs (`co.veyra.common.Environment`).

| Value | Description |
|-------|-------------|
| `TEST` | Test / staging servers. A provider is required at `initialize`. |
| `LIVE` | Production servers. A provider is required at `initialize`. |

Server hosts and endpoint paths are resolved by the SDK from the environment — you never supply URLs.

### Device type

**Detected, not configured** — the SDK detects the device form factor itself (Wear OS →
`WATCH`, smallest screen width ≥ 600dp → `TABLET`, otherwise `MOBILE`) and reports it in
digitise requests. There is no configuration parameter for it.

---

## Exclusive mode (combined apps)

A combined app is always in exactly one mode: **none**, **receiving** (SoftPOS) or **paying** (Wallet). The SDK manages this for you:

- **Claims are automatic.** Each SDK claims its mode while its screen is in the foreground and releases it on leaving — and each also claims at the point of use (arming a tap payment, activating the wallet card). This rides the SDKs' lifecycle observers.
- **Inertness is guaranteed by the SDK.** Whenever a screen that doesn't claim a mode reaches the foreground (your home screen, a deep link, back out of a nested flow), the SDK drops the process to inert by itself — there is no mode API to call and nothing for your app to guard. `currentMode()` is available read-only for UI state.
- **Starts inert, never persisted.** The mode derives from the foreground screen; the app always starts with no mode active — even after being killed mid-payment.
- **Atomic.** The outgoing capability is fully torn down before the incoming one arms. A merely-armed (untapped) tap payment is cancelled automatically on a switch; a genuinely mid-flight payment refuses the switch instead.
- **NFC-inert outside paying.** The wallet HCE component is enabled only while paying; otherwise a terminal cannot select a card from the device at all, and taps route to the user's system-default wallet — never Veyra. The wallet HCE vibrates once on a successful tap and twice on decline/error, from the SDK itself.

**Cross-mode refusals.** Payments claim their mode at the point of use, so the only way a claim is refused is while the *other* mode's payment is genuinely mid-flight — the payment then ends `NOT_SENT` / `MODE_REFUSED` (e.g. `makeCardPayment` during a wallet payment). Treat it as "finish or cancel the current payment first" and prompt the user. **This never occurs in a standalone single-product app.**

---

## SoftPOS — accepting payments

Service accessors: `sdk.merchantService`, `sdk.cardPaymentService`, `sdk.transactionService`, `sdk.cpmCustomerQrService`. All callbacks are delivered on the main thread.

### Merchant registration & profile

A device must have a **registered, active merchant** before it can accept payments. Registration persists the merchant on the device for the signed-in customer (SDK-owned storage, kept across sign-out, cleared on uninstall); the backend assigns the merchant ID, terminal ID and category code.

---

#### `registerPersonalMerchant` / `registerBusinessMerchant`

Register the merchant on this device. Personal merchants require a BVN; business merchants require a CAC number — and may supply a BVN too (the account holder behind a business has one). All other fields are mandatory for both, except `walletAccountId`.

```kotlin
val data = MerchantRegistrationData(
    merchantName = "Ada's Store",
    emailAddress = "ada@example.com",
    phoneNumber = "+2348012345678",
    addressLine1 = "12 Marina Road",
    city = "Lagos", state = "Lagos",
    countryCode = "0566",                  // ISO 3166-1 numeric, 4 digits
    accountNumber = "1234567890",          // settlement NUBAN account
    institutionCode = "000000",            // from getBanks
    bvn = "12345678901",                   // optional for both merchant types
    cacNumber = null,                      // business merchants only
    walletAccountId = null                 // optional
)
sdk.merchantService.registerPersonalMerchant(data) { response ->
    when (response.responseStatus) {
        OutcomeStatus.APPROVED -> {
            // Registered — merchant persisted on device; response.merchantId, response.terminalId,
            // response.merchantCategoryCode assigned by the backend. Unlock your Get-paid flow.
        }
        // Sent, no usable answer: the merchant may exist. Call refreshStatus() before registering again.
        OutcomeStatus.PENDING -> showHint("Still checking — please wait")
        // FAILED — the gateway refused a field, or it was refused on the phone / offline:
        // responseStatusReason names it, e.g. ACCOUNT_NUMBER_REQUIRED, NO_NETWORK_CONNECTION.
        else -> showError(response.message ?: response.responseStatusReason ?: "Registration failed")
    }
}
```

**`MerchantRegistrationData` fields:**

| Field | Required | Description |
|-------|----------|-------------|
| `merchantName` | **Mandatory** | Trading name shown on receipts. |
| `emailAddress` | **Mandatory** | Contact email. |
| `phoneNumber` | **Mandatory** | Contact phone (E.164). |
| `addressLine1` / `city` / `state` | **Mandatory** | Trading address. |
| `addressLine2` | Optional | Second address line. |
| `countryCode` | **Mandatory** | ISO 3166-1 numeric, 4-digit zero-padded (`"0566"`). |
| `accountNumber` | **Mandatory** | Settlement NUBAN account number. |
| `institutionCode` | **Mandatory** | Settlement bank's institution code (from `getBanks`). |
| `bvn` | Optional (both types) | 11-digit BVN — send it when you have it, for a personal or a business merchant. |
| `cacNumber` | Business only | CAC registration number. |
| `walletAccountId` | Optional | The merchant's wallet account id; stored verbatim by the gateway and echoed on responses. |

**`MerchantRegistrationResponse` fields:** `responseStatus` + `responseStatusReason` (the outcome — branch on these), `success: Boolean` (the same fact as `responseStatus == "APPROVED"`), `merchantId`, `terminalId`, `merchantCategoryCode`, `countryCode`, `acquirerId` (resolved by the gateway from your payment app provider — the SDK stores it and uses it on payments), `paymentAppProviderId`, `walletAccountId`, `merchantStatus`, `message`. A missing field is refused on the phone as `FAILED` with the field's reason (`MERCHANT_NAME_REQUIRED`, `CAC_NUMBER_REQUIRED`, …), and the gateway's own refusals come back `FAILED` too — nothing throws except a [setup error](#1-setup-errors-thrown).

---

#### `getBanks` (merchant)

Fetch the NUBAN settlement banks for the registration/update bank picker.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `callback` | **Mandatory** | `(NubanBankListResponse) -> Unit` — `responseStatus` `APPROVED` with `banks`, or `FAILED` with `responseStatusReason` (`NO_NETWORK_CONNECTION`, …) and an empty list. A read, so never `PENDING`: retrying as is is always safe. Each `NubanBank` has `slug`, `name`, `institutionCode`. |

```kotlin
sdk.merchantService.getBanks { response ->
    if (response.responseStatus != OutcomeStatus.APPROVED) {
        showError(response.message ?: "Could not load banks (${response.responseStatusReason})")
        return@getBanks
    }
    showBankPicker(response.banks)   // pass the chosen bank's institutionCode to registration
}
```

---

#### Merchant status — `isRegistered` / `isMerchantActive` / `refreshStatus` / `activate` / `deactivate`

| Method | Description |
|---------|-------------|
| `isRegistered(): Boolean` | `true` when a complete merchant (ID, terminal, name, acquirer, MCC, country) is stored on this device. |
| **`VeyraSoftPOSSdk.isMerchantRegistered(context, customerId)`** (static) | The same check for `customerId` **without initialising the SDK** — no lifecycle binding, no NFC arming, nobody signed in. Gate your Home screen's Get-paid entry on this (initialise the SDK only on payment screens). |
| **`VeyraSoftPOSSdk.storedMerchant(context, customerId)`** (static) | Init-free read of that customer's persisted `StoredMerchantData` (or `null`) — e.g. for pre-filling or merchant-type checks before any payment screen exists. |
| `isMerchantActive(): Boolean` | `true` when the last known backend status is `ACTIVE` (a merchant with no status yet counts active). Payments are refused for inactive merchants. |
| `refreshStatus()` | Refresh the backend status immediately (the SDK also polls it periodically while your app is foregrounded). Call at the activation moment. |
| `activate { response -> }` / `deactivate { response -> }` | Activate / deactivate this merchant on the backend. The callback always gets a `MerchantStatusResponse`: `APPROVED` with `merchantId` / `merchantStatus`; `FAILED` (e.g. `MERCHANT_NOT_FOUND`); `PENDING` (sent, no usable answer — read the status before trying again). Nothing sent is also `FAILED`, with the reason: `MISSING_MANDATORY_CONFIG` when no merchant is registered, `NO_NETWORK_CONNECTION` when offline. |
| `getStoredMerchantData(): StoredMerchantData?` | The merchant persisted by the last successful registration (all profile fields + backend-assigned ones), or `null`. |
| `getStoredMerchantId(): String?` / `hasStoredMerchant(): Boolean` | Convenience reads. |

There is no call to clear the stored merchant: a successful registration overwrites it, and when the customer logs out call `VeyraSoftPOSSdk.signOut()` — see [Customers](#customers-signing-in-signing-out-switching).

---

#### `updateMerchant`

Update the merchant profile (terminal ID, MCC and the gateway-resolved acquirer id are preserved; the response re-states them and the SDK refreshes its stored copy). All parameters are required except `addressLine2`, `walletAccountId` and `bvn`.

```kotlin
sdk.merchantService.updateMerchant(
    merchantName = "Ada's Store", emailAddress = "ada@example.com",
    phoneNumber = "+2348012345678", addressLine1 = "12 Marina Road",
    city = "Lagos", state = "Lagos", countryCode = "0566",
    accountNumber = "1234567890", institutionCode = "000000",
    walletAccountId = null,   // optional: stored verbatim by the gateway when supplied
    bvn = null                // optional: how an already-registered business supplies its BVN
) { response ->
    when (response.responseStatus) {
        OutcomeStatus.APPROVED -> showSaved(response.merchantStatus)  // stored copy already refreshed
        OutcomeStatus.PENDING -> showHint("Still checking — read the merchant again before retrying")
        else -> showError(response.message ?: response.responseStatusReason ?: "Update failed")  // FAILED (refused, or not sent)
    }
}
```

`MerchantUpdateResponse`: `responseStatus`, `responseStatusReason`, `message`, plus on `APPROVED` the gateway's `merchantId`, `merchantStatus`, `terminalId`, `acquirerId`, `paymentAppProviderId`, `walletAccountId`. No stored merchant ends `FAILED` / `MISSING_MANDATORY_CONFIG`.

---

### Tap acceptance

#### `makeCardPayment`

Arm the reader for one sale and wait for the customer's tap. **Non-terminal events keep the reader armed** — mirror a physical terminal: an unsupported card or lost contact shows a transient hint on the same waiting screen; only real outcomes (approved / declined / pending / failed) end the payment.

**The screen stays awake while a payment is live.** From the moment a tap payment starts until it
ends or is cancelled (waiting for a card, reading it, and contacting the bank), the SDK keeps
your get-paid screen on, because a phone whose screen has gone off cannot read a card. With no
payment running, the screen sleeps as usual. If your activity already keeps its screen on, the SDK
leaves that setting alone.

```kotlin
val request = TransactionRequest.Builder(
    amount = 32500L,                                   // MINOR units: ₦325.00
    currency = "0566",                                 // ISO 4217 numeric, 4 digits
    merchantOrderId = "ORDER-42",                      // required: YOUR order id, never a key
).build()                                              // txType defaults to PURCHASE

// The transaction reference is minted by the SDK — read it back off the response
// (`response.merchantTransactionReference`) and key your receipts and lookups off that.

sdk.cardPaymentService.makeCardPayment(
    request = request,
    // Terminal outcomes only. Combined apps: a wallet payment in flight ends NOT_SENT / MODE_REFUSED.
    callback = { response -> runOnUiThread { handleResult(response) } },
    onCardDetected = { runOnUiThread { showHint("Card detected — hold steady") } },
    onUnsupportedCard = { runOnUiThread { showHint("Card not supported — try another card") } },  // stays armed
    onCardContactLost = { runOnUiThread { showHint("Hold the card steady") } }                    // stays armed
)
```

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `request` | **Mandatory** | `TransactionRequest` — see below. |
| `callback` | **Mandatory** | `(TransactionResponse) -> Unit`. Fires **once**, with the terminal outcome only — branch on `responseStatus` (`APPROVED` / `DECLINED` / `FAILED` / `PENDING` / `RETRY` / `NOT_SENT`). Never fires for unsupported cards or lost contact. Nothing about a payment throws; only a [setup error](#1-setup-errors-thrown) does. |
| `onCardDetected` | Optional | Card entered the field, processing started — show "processing". Keep Cancel visible: the payment can still be cancelled until its request is sent (see [Cancelling a call](#cancelling-a-call)). |
| `onCardContactLost` | Optional | Contact lost before completion — reader **stays armed**; show "hold steady" and wait for a re-tap. |
| `onUnsupportedCard` | Optional | Non-payment target / unreadable card — reader **stays armed**; show "card not supported, try another". |
| `onCardReadingComplete` / `onSendingRequestOnline` / `onReceivingOnlineResponse` | Optional | Progress hooks: card read finished; online request sent; online response received. |
| `onCreditConfirmation` | Optional | `(CreditConfirmation) -> Unit`, main thread. Fires when the funds of an **approved** sale are confirmed in the merchant's bank account (`status = "RECEIVED"`), or once with `"UNABLE_TO_CONFIRM"` if 30 days pass unconfirmed. Settlement news only — it never changes the payment outcome. The SDK owns the polling (exponential backoff); registered SDK-wide (replaces any previous registration), so it can fire for a sale from an earlier session — match by `reference`. The payload also carries `creditTransactionId`, `amountMinorUnits`, `bankReference` and `creditedAt` (populated on `RECEIVED`). Fires only when the backend said the merchant's bank supports confirmation — and for sales on **every rail** (tap, customer-QR charge, merchant-presented QR), since the sweep works off the stored rows. **Recommended pattern — wait on the result screen:** when an approved `TransactionResponse` carries `isCreditConfirmationSupported == true`, show a status line on the payment-result screen ("Confirming credit with merchant bank…") and flip it from this callback — "Funds received by merchant bank" on `RECEIVED`, "Bank credit could not be confirmed" only on the final give-up (see the sample's `GetPaidActivity`). For an approved **merchant-QR (MPM)** sale the settle itself can't say whether confirmation is supported (its rail carries no credit fields — the SDK learns them from the transaction-status rail moments later), so watch the stored row (`getTransaction(ref)`) for `isCreditConfirmationSupported`/`creditConfirmationStatus` instead, as the sample does. **The polling is SDK-owned and app-scoped, never screen-scoped**: leaving the result screen changes nothing — the SDK keeps polling while the app runs, persists the answer onto the stored row, and any transaction view re-reading the store shows the updated state on return. **Platform note:** every merchant rail supports credit confirmation; the SDK polls on every platform and the callback/event fires on every platform (native iOS: `transactions.onCreditConfirmation`; React Native: `merchant.onCreditConfirmation`, now on iOS as well as Android). The remaining asymmetries are that iOS has no tap rail and that its sweep runs while the app is alive (no OS background execution — it suspends and resumes with the app). Registration is single-listener everywhere: **last registration wins**. |

**Holding the result screen (your app's decision, never the SDK's).** A terminal outcome is a destination, not a notification. The sample holds its result page for **60 seconds** — approved, declined, pending and failed alike — with **Done** visible for the whole hold and dismissing immediately to the get-paid screen; when the hold expires the page returns Home on its own. The single exception is an approved sale whose `isCreditConfirmationSupported` is (or later becomes) `true`: **cancel** the hold so the page cannot vanish while the merchant's bank is still being asked, show "Confirming credit with merchant bank…", and start a **fresh 60 seconds** once the confirmation is on screen (the merchant-QR rail learns the flag moments after the settle, so the cancel can happen while the hold is already running). The non-terminal events above (`onUnsupportedCard`, `onCardContactLost`) are not results — they hold nothing and dismiss nothing: the waiting screen stays up, armed for a re-tap. How long a result stays up and what dismisses it are app concerns end to end — the SDK has no concept of a screen and supplies no duration, and dismissing a screen never stops its app-scoped credit polling.

**`TransactionRequest.Builder`:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `amount` | **Mandatory** | **Minor units** (`Long`), e.g. ₦325.00 → `32500L`. Must be > 0. |
| `currency` | **Mandatory** | ISO 4217 numeric, 3–4 digits (padded to 4, e.g. `"0566"`). |
| `merchantOrderId` | **Mandatory** | **Your** order / basket / invoice id for this sale (`String`, non-blank, at most 255 characters). A blank one ends `NOT_SENT` / `INVALID_REQUEST`, before anything is sent. Stored and echoed by the gateway and shown on the transaction detail and receipt, on your side and on the paying wallet's. **One order id per sale, kept across all its attempts:** an order can have several attempts, and the same order id can be used again until the order is paid or possibly paid. The gateway refuses a new attempt — `94` / `FAILED` / `DUPLICATE_MERCHANT_ORDER_ID`, with the attempt holding the order in `originalPayment` — only while the order is `APPROVED` or `PENDING`. After `DECLINED`, `FAILED`, `RETRY` or `NOT_SENT`, retry under the **same** order id. See [The merchant order ID](#the-merchant-order-id). |
| `txType` | Optional | `TxType.PURCHASE`, `REFUND`, `CASH_ADVANCE`, `RECURRING_PURCHASE`, `PRE_AUTH_COMPLETION`, `OTHER`. **Defaults to `PURCHASE`** when omitted — pass a value only for a non-purchase transaction. |
| `.performed3ds(Boolean)` | Optional | Whether your app performed 3-D Secure. Default `false`. |

> **The transaction reference is no longer yours to supply.** `Builder` used to take a mandatory
> `merchantTransactionReference`; the SDK now mints it (`{terminalId}-YYYYMMDDHHmmssSSS`) so the
> gateway can guarantee it is unique per merchant. Read it off `response.merchantTransactionReference`
> and key receipts, status lookups and credit confirmation off **that**. An app that keeps generating
> its own is keying those lookups on a value no gateway has ever seen. Use `merchantOrderId` for your
> own identifier instead — and note the two differ in kind: the reference identifies *an attempt*, the
> order id identifies *your sale*, which is why a retry gets a new reference but keeps the order id.

**Also on `cardPaymentService`:** `isTransactionInProgress`, `isAwaitingCardTap`. To cancel a payment, call `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)` with its order ID — the callback then receives `NOT_SENT` / `CANCELLED` if it was still on the phone. See [Cancelling a call](#cancelling-a-call).

---

### Get paid by QR (merchant-presented)

The merchant keys the amount, the SDK creates a **gateway-signed payment context**, and your app renders the returned payload as a QR for the customer's wallet to scan. Poll the context until it settles.

#### `createContextPayment`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `merchantId` | **Mandatory** | Your registered merchant ID. |
| `amountMinorUnits` | **Mandatory** | Sale amount in minor units. |
| `currency` | **Mandatory** | ISO 4217 numeric (e.g. `"566"`; leading zeros accepted). |
| `merchantOrderId` | **Mandatory** | Your order id for the sale — the same rules as on a tap (non-blank; reusable until the order is paid or possibly paid). Several QRs may carry the same order id until one of them is paid. |
| `onExpired` | Optional | Fired **once, on the main thread**, when the QR reaches its expiry — blank or replace the code so it can't be scanned once lapsed (a dimmed QR is still machine-readable). A new create supersedes the watch; `cancelQrExpiry()` stops it. |

Always returns a `CreatedPaymentContext` — never `null`, never a throw for anything that happens to the request. Check `created` (or `responseStatus == "APPROVED"`) first: then `txRef` (poll key), `mpmPayload` (**render this string verbatim as the QR**), `expiry` (ISO-8601) and `kid` are set. Otherwise `txRef` and `mpmPayload` are `null` and `responseStatus` / `responseStatusReason` say why:

| `responseStatus` | Typical reasons | What to do |
|---|---|---|
| `FAILED` | `DUPLICATE_MERCHANT_ORDER_ID`, `MERCHANT_NOT_FOUND`, `AMOUNT_REQUIRED`, `INVALID_REQUEST` | The gateway refused it. Fix the reason and create it again with the same order id. For a duplicate, the order is already paid or a payment for it is still being confirmed (`originalPayment` says which) — don't take another payment for it |
| `PENDING` | `NO_RESPONSE_RECEIVED`, `UPSTREAM_ERROR` | Sent, no usable answer, so there is no QR to show. Create a new QR (the same order id is fine — no payment was taken) |
| `NOT_SENT` | `NO_NETWORK_CONNECTION`, `INVALID_REQUEST` (blank order id), `NOT_AUTHENTICATED` | Nothing was sent. Fix the reason and create it again with the **same** order id |

#### `contextStatus`

Poll `contextStatus(txRef)` on a short interval (the sample uses 2.5 s). It answers a `PaymentContextStatusResult`: the QR's `status`, or a `failureReason` (`NO_NETWORK_CONNECTION`, `UPSTREAM_ERROR`…) when the poll could not be made — keep the QR on screen and poll again; a failed poll never changes the QR's state and never throws. States: `PENDING` (QR live) → `IN_FLIGHT` (wallet push settling) → `APPROVED` / `DECLINED` (settled — `responseCode` carries the rail outcome) or `EXPIRED`. Convenience: `isSettled` (`APPROVED || DECLINED`), `isApproved`. On settlement the payment is also recorded in the merchant's local history under the same `txRef`, so receipts work like any other rail.

```kotlin
val client = ContextPaymentClient(context, Environment.TEST)   // uses the SoftPOS SDK's provider
val created = client.createContextPayment(
    merchantId, amountMinorUnits, "566",
    merchantOrderId = "ORDER-42",            // required: YOUR order id for this sale
    onExpired = { blankQr(); showHint("This payment code has expired — start a new payment") },
)
if (!created.created) {
    // FAILED / PENDING / NOT_SENT — see the table above. Keep the order id for the next try,
    // unless the reason is DUPLICATE_MERCHANT_ORDER_ID (the order is paid or being confirmed).
    showError(created.message ?: created.responseStatusReason ?: "Could not create payment QR")
    return
}

renderQrPayload(created.mpmPayload!!)        // encode the string verbatim into a QR bitmap
val txRef = created.txRef!!
while (isActive) {
    delay(2500)
    val status = client.contextStatus(txRef).status ?: continue   // poll not made: ask again
    when {
        status.isSettled -> {
            client.cancelQrExpiry()
            showResult(approved = status.isApproved, code = status.responseCode)
            break
        }
        status.state == "EXPIRED" -> { blankQr(); break }
    }
}
// teardown (screen leave): client.cancelQrExpiry()
```

---

### Charge a customer QR (consumer-presented)

The customer shows a payment QR from their Veyra wallet; the merchant scans it, **confirms the QR's own amount** (the amount is bound inside the QR's cryptogram — it is never keyed on the merchant side), and charges.

#### `cpmCustomerQrService.inspect`

Decode and validate a scanned payload. Returns a `CpmInspection` and never throws: `responseStatus == "APPROVED"` with the scanned QR in `qr`, or `FAILED` / `INVALID_REQUEST` when the payload is not a Veyra payment QR — show a transient hint and stay armed for another scan; it is not a terminal failure. Nothing is sent either way.

| `CpmInspection.qr` | Fields |
|---|---|
| `ScannedCpmQr` | `dpan` (customer token PAN — display last 4), `amountMinorUnits` (the QR's own amount — confirm, never re-key), `currencyNumeric4` (e.g. `"0566"`), `tokenExpiryYymm`, `cardholderName` (the paying card's display name, e.g. `AFRIGO ****1234` — the same value a tap presents; **display only**, it rides outside the QR's cryptogram, so never branch a payment decision on it; null when the QR carries none) |

#### `cpmCustomerQrService.charge`

Charge the confirmed QR synchronously over the standard payment rail. A tampered payload or altered amount declines at the server.

```kotlin
val inspection = sdk.cpmCustomerQrService.inspect(text)
val scanned = inspection.qr
if (inspection.responseStatus != OutcomeStatus.APPROVED || scanned == null) {
    showHint("Not a payment code — try again"); return   // FAILED / INVALID_REQUEST: stay armed
}
confirmScreen(scanned.amountMinorUnits, scanned.currencyNumeric4, "Card •••• ${scanned.dpan.takeLast(4)}")
// on merchant confirm:
lifecycleScope.launch {
    val response = sdk.cpmCustomerQrService.charge(scanned, merchantOrderId = "ORDER-42")
    // Branch on the status, never on responseCode. The SDK minted the reference; take it from
    // the response for the receipt lookup.
    when (response.responseStatus) {
        ResponseStatus.APPROVED -> showApproved(response.merchantTransactionReference)
        // The QR was used: a new attempt is a new scan — under the same order id, unless the
        // reason is DUPLICATE_MERCHANT_ORDER_ID (the order is already paid or still being confirmed).
        ResponseStatus.DECLINED, ResponseStatus.FAILED ->
            showDeclined(response.message, response.responseStatusReason)
        // Nothing was charged — NOT_SENT never reached Veyra, RETRY reached it but was not sent to
        // the bank: fix the reason, charge the SAME scanned QR with the SAME order id.
        ResponseStatus.NOT_SENT, ResponseStatus.RETRY -> offerRetry(response.responseStatusReason)
        else -> showProcessing(response.merchantTransactionReference)  // PENDING — never re-charge
    }
}
```

`charge(scanned, merchantOrderId = …)` returns `PaymentResponse`: the response triple — `responseCode` (the wire literal: display it, never branch on it), `responseStatus` (`APPROVED` / `DECLINED` / `FAILED` / `PENDING` / `RETRY` / `NOT_SENT` — **branch on this**; `PENDING` means the outcome is unknown, so never re-charge) and `responseStatusReason` — plus `message`, `transactionId`, `merchantStatus`, `merchantTransactionReference` (**the SDK-minted reference — fetch the receipt with `generateTransactionReceipt(it)`**) and `merchantOrderId` echoed back. It always returns: a device that is offline ends `NOT_SENT` / `NO_NETWORK_CONNECTION` and records nothing; a charge that left but got no usable answer ends `PENDING` (`68` / `06`) and is recorded and resolved by the SDK.

> **The order id is required on every merchant payment** — tap (`TransactionRequest.Builder`),
> customer QR (`charge(scanned, merchantOrderId)`) and merchant QR
> (`createContextPayment(…, merchantOrderId, …)`). Omitting it does not compile; a blank value ends
> `NOT_SENT` / `INVALID_REQUEST` before anything is sent. Pass `merchantOrderId`
> by name, as above: the pre-1.0.15 `charge(scanned, merchantTransactionReference, …)` overload is gone,
> so a reference passed positionally would now be taken as your order id.

---

### Merchant transactions & receipts

The SDK records every payment it takes — tap, get-paid QR and customer-QR charge — locally at its terminal outcome, so history needs no backend round trip.

#### `getLastTransactions` / `getTransaction`

```kotlin
val transactions = sdk.transactionService.getLastTransactions(50)   // most recent first, all rails
val tx = sdk.transactionService.getTransaction(reference)           // or null
// TransactionInfo: merchantTransactionReference, amount (minor units), transactionStatus
// (APPROVED / DECLINED / PENDING / FAILED / RETRY), responseCode, responseStatusReason (the stated
// cause, e.g. "INSUFFICIENT_FUNDS" — display, never parse), transactionTime, currencyCode,
// transactionId, merchantOrderId (your own order/basket id as supplied on the charge — your
// reconciliation key back to your POS/till, null on sales that carried none; receipts and
// status refreshes key off merchantTransactionReference),
// cardholderName (EMV 5F20 as the card presented it — null on QR-MPM),
// rail ("TAP" / "QR_MPM" / "QR_CPM"), railLabel ("Tap" / "QR" / "Scan"),
// creditTransactionId (the merchant-bank credit's identifier — null unless approved and
// supported), isCreditConfirmationSupported (true ⇒ the SDK is polling the confirmation rail
// for this sale — a result screen should show "confirming credit…" until the status resolves;
// on MPM rows it can be null for a few seconds after the settle while the SDK learns it),
// creditConfirmationStatus ("RECEIVED" once the merchant's bank confirmed the
// funds, "UNABLE_TO_CONFIRM" only as the 30-day give-up; null while unconfirmed — show
// nothing or "not confirmed yet", never "not received")
```

Each row records the rail that actually took the payment. Display `railLabel` — the SDK derives it
so the same rail reads identically on Android, iOS and React Native, and an unrecognised rail code
passes through unchanged rather than being shown as some other rail. Branch on `rail`, not on the
label.

`PENDING` means the outcome is not yet known (the SDK keeps polling and updates the stored row); `FAILED` means the gateway received the payment and could not perform it; `RETRY` means the gateway received it but did not send it to the bank, so nothing was charged. A `RETRY` attempt **is recorded** — it is a real attempt, with the gateway's code and reason — and is final, never polled. A payment that ended `NOT_SENT` never reached the gateway and is **not recorded** — there is no history row for it. Hide receipt affordances while a row is `PENDING`.

**Each attempt is its own row.** A sale retried after `RETRY` (or another card after a decline) leaves one row per attempt, each with its own `merchantTransactionReference` and the **same** `merchantOrderId`. To show a sale's history, group the rows by `merchantOrderId`; to say what happened to the order, apply [the order's status rule](#an-orders-status-comes-from-all-its-attempts) to the group — or ask the SDK with [`paymentStatus(merchantOrderId)`](#looking-up-a-payment), which also sees attempts made on another phone.

**How the SDK waits for a `PENDING` row.** You do not have to poll, schedule anything, or keep a screen open — the SDK asks on its own, with **exponential backoff**: the first re-checks come within seconds (most payments settle at once) and the interval doubles to a steady state of roughly **once an hour**. It keeps that up for **30 days** from the transaction date, and then stops asking.

**Stopping is not an outcome.** When the 30 days elapse the row keeps whatever status it has — still `PENDING`, which is still true — and the SDK simply takes it off the poll list. It never writes `FAILED`, `DECLINED` or any other verdict of its own: only the backend decides what a payment was. So treat a long-`PENDING` row as *unresolved*, not as failed, however old it is.

**Let the merchant ask on demand — `refreshTransactionStatus`.** The SDK polls a pending transaction for you with **exponential backoff**, and **stops after 30 days**. Polling never invents an outcome — a row that ages out simply stops being asked about and stays `PENDING`. Expose **`refreshTransactionStatus`** in your UI so the merchant can ask on demand, which is the route for anything still pending after the window closes.

A failed poll — device offline, gateway unreachable, an unreadable answer — changes nothing at all: the SDK backs off and asks again, and the row is left exactly as it was. "We could not reach the server" is never recorded as "the payment failed".

#### `refreshTransactionStatus`

```kotlin
// suspend — call from a coroutine. Never throws for anything that happens to the check.
val refresh: TransactionRefreshResult = sdk.transactionService.refreshTransactionStatus(reference)
when {
    refresh.failureReason != null ->           // the check could not be made; the row is unchanged
        showNote(if (refresh.failureReason == OutcomeReason.NO_NETWORK_CONNECTION) "You're offline" else "Couldn't check now")
    refresh.transaction?.transactionStatus == TransactionStatus.PENDING -> showNote("Still processing")
    else -> render(refresh.transaction)        // settled: re-render the detail
}
```

`TransactionRefreshResult`: `transaction` (the stored row as it now stands — updated when the check
was made, unchanged when not; `null` if this device has no row for the reference), `failureReason`
(`null` when the check was made; otherwise why not — `NO_NETWORK_CONNECTION`, `SDK_UNABLE_TO_CONNECT`,
`NOT_AUTHENTICATED`, `NO_RESPONSE_RECEIVED`, `UPSTREAM_ERROR`, …), `message`, and `checked`
(`failureReason == null`).

The on-demand counterpart to `getTransaction`, which only reads what the device already knows. It
asks the gateway about that one transaction now and writes the answer into the same local store the
background sweep writes, so an on-demand check and a background check can never disagree.

- **Show it only while the row is `PENDING`.** A settled row has nothing to ask, and offering the
  action implies the outcome might still change. Hide it the moment the row is terminal.
- **It works past the 30-day window**, and on a row the sweep never had on its list — that is what it
  is for.
- **It is not a way to force an outcome.** A payment that is still unsettled answers `PENDING` again.
  Show a brief "still processing" note; do not retry in a loop.
- **A failed lookup never changes the payment.** When the check itself cannot be made, the result
  carries a `failureReason` (e.g. `NO_NETWORK_CONNECTION` when the device is offline) beside the
  unchanged row. Show a short note and leave the row pending; that is the correct handling, not an
  error state on the transaction. A lookup is a read, so retrying it is always safe.
- **No SDK-side throttle.** Disable your button while a call is in flight, as the sample does.

#### `refreshCreditConfirmation`

The SDK polls for beneficiary credit confirmation with **exponential backoff** and **stops after 30
days**, finalising the row as `UNABLE_TO_CONFIRM` — which means "we stopped asking", never "the funds
were not received". Expose **`refreshCreditConfirmation`** in your UI so the merchant can ask on
demand; it works after the window closes, and a later `RECEIVED` replaces the give-up state.

**Check `isCreditConfirmationSupported` on the transaction first.** Not every merchant's bank is on
this rail. `true` means the SDK is polling and you may offer the manual check; `false`/`null` means
there is nothing to ask — do not call it, and show no credit UI for that transaction. Offer the
action only while

```kotlin
tx.transactionStatus == TransactionStatus.APPROVED &&
    tx.isCreditConfirmationSupported == true &&
    tx.creditConfirmationStatus != "RECEIVED"
```

```kotlin
// suspend — call from a coroutine. Returns a TransactionRefreshResult, like refreshTransactionStatus.
val refresh = sdk.transactionService.refreshCreditConfirmation(reference)
when {
    refresh.failureReason != null -> showNote("Couldn't check now")          // row unchanged
    refresh.transaction?.creditConfirmationStatus == "RECEIVED" -> render(refresh.transaction)
    else -> showNote("Not confirmed yet")                                     // never "not received"
}
```

- **A row outside that predicate is a no-op**, not an error: no request is made and the unchanged row
  comes back. The gateway refuses the same cases, so the SDK does not spend a round trip being told.
- **It works past the 30-day window**, including on a row already stamped `UNABLE_TO_CONFIRM` — that
  is the case it exists for. A later `RECEIVED` replaces the give-up; nothing ever replaces
  `RECEIVED`.
- **Only a confirmation is written.** An answer of `UNABLE_TO_CONFIRM`, or one this SDK version does
  not recognise, leaves the row exactly as it was — "not confirmed **yet**", never "not received".
- **Settlement only.** Nothing on this path can change `transactionStatus`, `responseCode` or
  `responseStatusReason`. A credit answer says whether the money reached your bank, not what the
  payment did.
- **It writes the store and fires `onCreditConfirmation`**, exactly as the background sweep does —
  both go through the same write — so a manual check and a swept one are indistinguishable once
  stored, and your existing observer needs no change.
- **A failed check changes nothing.** When it cannot be made the result carries a `failureReason`
  (e.g. `NO_NETWORK_CONNECTION` when the device is offline) beside the unchanged row. Leave the credit
  line reading "not confirmed yet".
- **No SDK-side throttle.** Disable your button while a call is in flight, as the sample does.

#### `generateTransactionReceipt`

Build the receipt for one transaction, including a **receipt QR the customer's Veyra wallet can scan** to store its own copy.

```kotlin
val receipt = sdk.transactionService.generateTransactionReceipt(reference) ?: return  // null: unknown ref / not registered
receiptView.render(receipt.merchantName, receipt.merchantAddress, receipt.totalAmountFormatted, receipt.maskedToken)
val qrBytes = Base64.decode(receipt.qrCodeBase64, Base64.DEFAULT)     // ready-made 512×512 PNG
receiptQrImage.setImageBitmap(BitmapFactory.decodeByteArray(qrBytes, 0, qrBytes.size))
```

The receipt comes with a ready-made QR image (`qrCodeBase64`) and carries `transactionHash` — the join key the customer wallet verifies against before storing the receipt.

### Retrying a payment safely

`merchantOrderId` is **your** reference for the sale — the order, basket or invoice in your POS —
and it is required on every SoftPOS rail that takes payment: the tap (`TransactionRequest`), the
merchant QR (`createContextPayment`) and the customer-QR charge (`cpmCustomerQrService.charge`). The
SDK mints a new `merchantTransactionReference` for each attempt; the order ID is what ties the
attempts of one sale together, and it is what lets you retry without charging twice.

| Outcome of the attempt | Money moved? | Next payment for the order |
|---|---|---|
| `NOT_SENT` | No — it never reached Veyra | Allowed — fix the reason, use the **same** order ID |
| `RETRY` | No — Veyra did not send it to the bank | Allowed — the **same** order ID |
| `DECLINED` | No — the issuer or bank refused | Allowed — another card or method, the **same** order ID |
| `FAILED` | No | Allowed — fix the reason, the **same** order ID (not after `94`, below) |
| `PENDING` | Maybe | **Refused** — `94` / `FAILED` / `DUPLICATE_MERCHANT_ORDER_ID`, with `originalPayment`. Don't take another payment; wait for the SDK to resolve it |
| `APPROVED` | Yes — the order is paid | **Refused** the same way. Don't take another payment; a new sale gets a new order ID |

Keep the order ID for the life of the sale and drop it only when the order is paid or might be:

```kotlin
private var saleOrderId: String? = null     // your POS order id for the sale on screen

fun orderIdForAttempt(): String = saleOrderId ?: newOrderIdFromYourPos().also { saleOrderId = it }

fun attemptEnded(status: ResponseStatus?) {
    // No money moved: the retry carries the SAME order id. APPROVED, PENDING or a status this
    // build doesn't know (treat it as pending): the order is paid or may be — never pay it again.
    if (status !in setOf(ResponseStatus.NOT_SENT, ResponseStatus.RETRY, ResponseStatus.DECLINED, ResponseStatus.FAILED)) {
        saleOrderId = null
    }
}

fun charge(amountMinor: Long) {
    val request = TransactionRequest.Builder(amountMinor, "0566", orderIdForAttempt()).build()
    sdk.cardPaymentService.makeCardPayment(request, callback = { r ->
        attemptEnded(r.responseStatus)
        runOnUiThread { onOutcome(r) }     // "Try again" simply calls charge() again
    })
}
```

The gateway enforces the rule, so reusing an order ID is always safe: the worst case is a `94`
whose `originalPayment` tells you the order is already paid (`APPROVED`) or still being confirmed
(`PENDING`). The full rule, with the order-status precedence behind it, is in
[The retry rule](#the-retry-rule) and [The merchant order ID](#the-merchant-order-id). The sample
does exactly this in `GetPaidActivity` (`orderIdForAttempt` / `attemptEnded`).

### Looking up a payment

"What happened to this order?" — one call, by **your** order ID, for every SoftPOS rail. You don't
say which rail took it.

```kotlin
// suspend — call from a coroutine. A read: never throws (except a setup mistake).
val lookup: OrderPaymentLookup = sdk.transactionService.paymentStatus(merchantOrderId)
val order = lookup.value                     // the order's status, or null when nothing is known
when (order?.responseStatus) {
    OutcomeStatus.APPROVED -> showPaid(order)                 // never take another payment
    OutcomeStatus.PENDING -> showStillConfirming(order)       // never retry; the SDK resolves it
    null -> showNothingKnown(lookup.failureReason)            // no record here, and the gateway could not be asked
    else -> offerPayment(sameOrderId = merchantOrderId)       // DECLINED / FAILED / RETRY: no money moved
}
lookup.failureReason?.let { showNote("Couldn't ask the gateway ($it)") }  // value = what this phone holds
```

**When to use it.** Whenever you hold an order ID but not a settled outcome: a payment that ended
`PENDING`, or your app restarting (or crashing, or losing the screen) before the result arrived. It
covers the card tap, the merchant QR (`createContextPayment`) and the customer-QR charge — the
same order ID, whichever rail was used, so your POS never needs to remember the rail.

**The result — `OrderPaymentStatus`.** `responseStatus` is the **order's** status, decided by
precedence over all its attempts:

| Rank | Rule | `responseStatus` | What to do |
|---|---|---|---|
| 1 | any attempt `APPROVED` | `APPROVED` | Paid. Never take another payment for it |
| 2 | otherwise, any attempt `PENDING` | `PENDING` | It may have moved money. **Never retry**; the SDK keeps checking |
| 3 | otherwise, the **latest** attempt | `DECLINED`, `FAILED` or `RETRY` | No money moved. Pay again with the **same** order ID |
| 4 | no attempts | `RETRY`, `responseCode` `25`, reason `UNABLE_TO_LOCATE_RECORD` | Nothing was charged. Pay with the same order ID |

The other top-level fields — `responseStatusReason`, `responseCode`, `merchantTransactionReference`,
`transactionId`, `amount` (minor units) — are the **deciding attempt**'s (the approved one, else the
pending one, else the latest). `attempts` lists every attempt, newest first, each with its own
`merchantTransactionReference`, `transactionId`, `amount`, `responseCode`, `responseStatus` and
`responseStatusReason`. `answeredBy` says who answered: `"DEVICE"` or `"GATEWAY"`.

**Who answers.** When this phone's own record of the order is final (`APPROVED`, `DECLINED` or
`FAILED`) the SDK answers from it, with no network call. Otherwise — a local `PENDING`, only `RETRY`
attempts, or no record at all (another phone took it, or the app was reinstalled) — it asks the
gateway, because an attempt this phone never recorded can outrank its own.

**A payment still on its way counts.** A tap or customer-QR charge is on the history as `PENDING` before its request leaves, and the gateway's answer is combined with this phone's own attempts — so while a payment is in flight the lookup answers `PENDING` (don't take another payment), even when the gateway has no record of it yet.

**A merchant QR that has not been paid yet** answers `PENDING` on the phone that created it — the QR is
on its history from creation, as an open attempt — until the customer pays it. That is not a failure,
and it means the same thing: don't take another payment for the order while the QR can still be paid.
Asked from a phone with no record of the QR (another phone, or after a reinstall), the gateway has no
attempt to report, so it answers rank 4 — `RETRY` / `25` / `UNABLE_TO_LOCATE_RECORD`.

**It is a read.** It changes nothing, so asking again is always safe, and it never answers with an
outcome of its own. When the gateway could not be asked, `failureReason` says why
(`NO_NETWORK_CONNECTION`, `SDK_UNABLE_TO_CONNECT`, `NO_RESPONSE_RECEIVED`, `UPSTREAM_ERROR`, …) and
`value` is what this phone already holds for the order, if anything — so a `null` value with a
`failureReason` means "not known here", **not** "not paid". A blank order ID answers
`failureReason = INVALID_REQUEST`; a device with no registered merchant answers
`MISSING_MANDATORY_CONFIG` (with the local value, if any). `checked` is `failureReason == null`.
The wallet has no lookup by order ID — order IDs belong to the merchant side.

The sample has a **Look up an order** button on its Transactions page (`GetPaidActivity.lookUpOrder`).

---

## Wallet — making payments

Every operation is called on the wallet object (`val wallet = VeyraWalletSdk.initialize(...)`, or `VeyraSdk.wallet` in a combined app), under the same names as on iOS and React Native; callbacks arrive on the main thread; every call ends in an outcome you branch on by `responseStatus` and `responseStatusReason` — see [Errors and outcomes](#errors-and-outcomes).

### Add token

| Call | What it does | Returns |
|---|---|---|
| `getBanks(accountNumber?) { }` | The banks a card can be added from, or the one bank for an account number | `BankList` |
| `verifyAccount(params) { }` | Checks with the issuer that the account can be added | `VerifyAccountResponse` |
| `digitise(params) { }` | Adds the card and stores its payment data on the phone | `DigitiseResult` |
| `cancelDigitise(clientRequestId)` | Cancels a `digitise` while it is still on the phone | `CancelResult` |

Flow: `getBanks` → `verifyAccount` → `digitise`. On success the SDK receives, decrypts and stores the payment material on-device — the card can pay immediately (`APPROVED`) or after activation (`APPROVE_REQUIRE_AUTH`).

#### `getBanks`

Fetch the supported NUBAN banks, optionally filtered by account number. Call as soon as the user finishes entering their account number so the list is ready for the bank picker.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `accountNumber` | No | 10-digit NUBAN. When supplied, returns only banks linked to that account; `null`/blank returns all supported banks (use as the "can't find my bank" fallback). |
| `callback` | **Mandatory** | `(BankList) -> Unit`. `responseStatus` `APPROVED` with `banks` (each `Bank` has `slug`, `name`, `institutionCode`), or `FAILED` with `responseStatusReason` (`INVALID_ACCOUNT_NUMBER`, `NO_NETWORK_CONNECTION`, …) and an empty list. A read, so never `PENDING`. |

```kotlin
wallet.getBanks(accountNumber = "1234567890") { list ->
    if (list.responseStatus != OutcomeStatus.APPROVED) {
        showError(list.message ?: "Could not load banks (${list.responseStatusReason})")
        return@getBanks
    }
    when {
        list.banks.isEmpty() -> fetchAllBanks()          // fallback to the unfiltered list
        list.banks.size == 1 -> proceedWithBank(list.banks.first())
        else                 -> showBankPicker(list.banks)
    }
}
```

#### `verifyAccount`

Check whether an account can be tokenised before digitising. Eligible when `responseStatus == "APPROVED"`; the callback always receives a `VerifyAccountResponse`, and the check is a read, so it is never `PENDING`.

**`VerifyAccountParams.Builder`:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `accountNumber` | **Mandatory** | The customer's bank account number (10-digit NUBAN). |
| `institutionCode` | **Mandatory** | From `Bank.institutionCode`. |
| `walletAccountId` | **Mandatory** | The customer's registered email address or phone number — a value the issuer already holds for this account, not an internal id of your own. The SDK derives a hash from it; it is not sent raw. The issuer checks the hash against its own records, so an identifier it has never seen (a GUID, a placeholder) fails eligibility. |
| `.accountHolderName(String)` | Optional | Full name of the account holder. |
| `.accountNumberSource(AccountNumberSource)` | Optional | How the number was captured: `MANUAL`, `SCAN`, `CARD_ON_FILE`, `RECENT`, `APPLICATION`, `EXISTING_TOKEN`, `OTHER`. Default `MANUAL`. |

```kotlin
val params = VerifyAccountParams.Builder(
    accountNumber = "1234567890",
    institutionCode = "000000",
    walletAccountId = "ada@example.com"
)
    .accountHolderName("Ada Obi")
    .accountNumberSource(AccountNumberSource.MANUAL)
    .build()

wallet.verifyAccount(params) { response ->
    when (response.responseStatus) {
        OutcomeStatus.APPROVED -> proceedToDigitise()
        // The check ran and the answer is no: responseStatusReason is the issuer's cause.
        OutcomeStatus.DECLINED -> showError(response.message ?: "This account is not eligible")
        // FAILED (could not be run, or never sent, e.g. NO_NETWORK_CONNECTION): retry is safe.
        else -> showError("Eligibility check failed: ${response.responseStatusReason}")
    }
}
```

`VerifyAccountResponse`: `responseStatus`, `responseStatusReason` (the issuer's cause on a decline), `responseCode` (the issuer's decision literal), `message`. The SDK counts the cards already in this wallet itself.

#### `digitise`

Add the card: the SDK attests the device, sends the request, and on success decrypts and stores the token material on-device. The **tokenisation recommendation is your app's business decision** — the SDK never assumes one.

**`DigitiseParams`** — the same fields, names and required set as on iOS and React Native:

| Parameter | Required | Description |
|-----------|----------|-------------|
| `accountNumber` / `institutionCode` | **Mandatory** | The account being added. |
| `accountHolderName` | **Mandatory** | Full name. |
| `walletAccountId` | **Mandatory** | Same identifier as in `verifyAccount` — the account's registered email or phone. |
| `emailAddress` | **Mandatory** | The customer's email. |
| `recommendation` | **Mandatory** | `TokenizationRecommendation.APPROVE` / `DECLINE` / `REQUIRE_ADDITIONAL_AUTHENTICATION` — **your** risk decision for this request. |
| `consumerIdentifier` | Optional | Your stable identifier for this consumer. |
| `bvn` | Optional | The customer's BVN. |
| `accountHolderAddress` | Optional | Postal address. |
| `mobileNumber` | Optional | The customer's mobile number. |
| `clientRequestId` | Optional | Your own ID for this attempt; set one to be able to cancel it with `cancelDigitise` — see [Cancelling a call](#cancelling-a-call). Default: SDK-generated, not cancellable. |
| `accountNumberSource` | Optional | Default `MANUAL`. |
| `deviceScore` / `accountScore` | Optional | `TrustScore`: `UNTRUSTED`, `LOW_TRUST`, `MODERATE_TRUST`, `TRUSTED`, `HIGHLY_TRUSTED`. Default `LOW_TRUST`. |
| `recommendationReasons` | Optional | `List<TokenizationRecommendationReason>`. Default `[UNABLE_TO_ASSESS]`. |
| `bankName` | Optional | The bank's display name, kept on the stored card (`Token.bankName`). Not sent. |

A required field left blank is an outcome, not a throw: the callback gets `NOT_SENT` / `INVALID_REQUEST` and nothing is sent. `digitise` throws only for missing configuration.

```kotlin
val params = DigitiseParams(
    accountNumber = accountNumber,
    institutionCode = institutionCode,
    accountHolderName = "Ada Obi",
    walletAccountId = "ada@example.com",
    emailAddress = "ada@example.com",
    recommendation = TokenizationRecommendation.APPROVE,
    consumerIdentifier = customerId,             // optional: your stable id for this customer
    bvn = bvn,
    accountHolderAddress = address,
    mobileNumber = mobileNumber,
    accountNumberSource = AccountNumberSource.MANUAL,
    deviceScore = TrustScore.TRUSTED,
    accountScore = TrustScore.HIGHLY_TRUSTED,
    recommendationReasons = listOf(TokenizationRecommendationReason.GOOD_ACTIVITY_HISTORY),
)

wallet.digitise(params) { result ->
    when (result.responseStatus) {
        OutcomeStatus.APPROVED ->
            // The card is provisioned. The add-card decision says whether it needs activation.
            if (result.responseCode == ActivationMethods.ResponseCode.APPROVE_REQUIRE_AUTH)
                showActivationMethods(result.tokenUniqueReference, result.activationMethods)
            else showCardAdded()                            // can pay now
        OutcomeStatus.DECLINED -> showError(result.message ?: "The bank declined this account")
        // FAILED (start a new add card — never PENDING) or NOT_SENT (fix the reason, retry):
        else -> showError(result.message ?: result.responseStatusReason ?: "Could not add card")
    }
}
```

`DigitiseResult`: `responseStatus` and `responseStatusReason` (the outcome — branch on these first; see [Table C](#table-c--veyra-token-service-wallet-add-card-activation-cards)), `responseCode` (the add-card decision: `APPROVED` / `APPROVE_REQUIRE_AUTH` / `DECLINED`), `tokenUniqueReference` (the card's identity for every later call), `activationMethods` (`DigitiseActivationMethod(medium, contact)`; empty unless activation is needed), `tokenStored` (the card's payment data is on the phone), `message`. The same type as on iOS and React Native.

#### `cancelDigitise`

`wallet.cancelDigitise(clientRequestId)` cancels the `digitise` started with that `clientRequestId`
while its request is still on the phone, and answers at once with a `CancelResult`. See
[Cancelling a call](#cancelling-a-call).

---

### Activation

| Call | What it does | Returns |
|---|---|---|
| `requestActivationCode(tokenUniqueReference, medium, reason = ADD_CARD) { }` | Sends an activation code to the method the customer picked | `ActivationCodeResponse` |
| `activate(tokenUniqueReference, activationCode) { }` | Activates the token with the code the customer entered | `ActivateResponse` |
| `observeActivation(tokenUniqueReference, onActivated, onTimeout, onError?)` | Polls a token activated out of band (call centre, website, issuer app) | — (callbacks) |
| `pauseActivationObserver(ref)` / `resumeActivationObserver(ref)` / `stopActivationObserver(ref)` | Pause, resume or stop that polling | — |
| `hasActivationObserver(ref)` | Whether an observer for that token is still polling | `Boolean` |

When digitise returns `APPROVE_REQUIRE_AUTH`, the response carries the issuer's **activation methods**. Branch on each entry's `medium`:

| Medium | UI | Then |
|---|---|---|
| `MASKED_EMAIL` / `MASKED_MOBILE_PHONE` | Show the masked contact, let the user pick | `requestActivationCode` → OTP entry → `activate` |
| `CALL_CENTER_PHONE` / `AUTOMATED_CALL_CENTER_PHONE` | Show the phone number + "Call now" | `observeActivation` while they call |
| `WEBSITE` | Show the domain + "Open website" | `observeActivation` |
| `MOBILE_APPLICATION` | "Open your bank's app" | `observeActivation` |

#### `requestActivationCode`

OTP delivery for the `MASKED_EMAIL` / `MASKED_MOBILE_PHONE` methods.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `tokenUniqueReference` | **Mandatory** | The card being activated. |
| `medium` | **Mandatory** | `ActivationMedium.MASKED_EMAIL` or `ActivationMedium.MASKED_MOBILE_PHONE` — the method the customer picked. The code goes to that method's contact. |
| `reason` | Optional | `ActivationReason.ADD_CARD` (default), `VERIFY_ACCOUNT` or `OTHER`. |
| `callback` | **Mandatory** | `(ActivationCodeResponse) -> Unit` — always an outcome. |

```kotlin
wallet.requestActivationCode(
    tokenUniqueReference = ref,
    medium = ActivationMedium.MASKED_MOBILE_PHONE,  // the method the customer picked
) { response ->
    when (response.responseStatus) {
        OutcomeStatus.APPROVED -> response.expirationDateTime?.let { startCountdown(it) }
        OutcomeStatus.FAILED -> when (response.failureCode) {        // reason = the failure code
            ActivationFailureCode.CODE_REQUEST_RATE_LIMITED -> disableResendForNow()
            ActivationFailureCode.ACTIVATION_LOCKED -> showLockedTerminal()
            else -> showError(response.message)
        }
        else -> showError(response.message)     // PENDING: nothing to enter yet
    }
}
```

`ActivationCodeResponse`: `responseStatus` + `responseStatusReason` (on `FAILED` the reason is the failure code), `tokenUniqueReference`, `expirationDateTime` (ISO-8601 — drive your countdown from it), `status` (`SUCCESS` / `FAILURE`), `message`, `failureCode` (typed `ActivationFailureCode`, null on success) + `failureCodeRaw`. Branch on `responseStatus`, then on `failureCode`, never on `message`: `CODE_REQUEST_RATE_LIMITED` means disable "resend" until later but keep the flow open; `ACTIVATION_LOCKED` is terminal — end the flow and point the user at their issuer. Codes are limited-attempt and rate-limited — see [Activation — `responseStatus` + `failureCode`](#activation--responsestatus--failurecode).

#### `activate`

Submit the code the customer received. Activated when `responseStatus == "APPROVED"`; the callback always receives an `ActivateResponse`.

```kotlin
wallet.activate(tokenUniqueReference = ref, activationCode = code) { response ->
    when {
        response.responseStatus == OutcomeStatus.APPROVED -> navigateToWallet()
        // FAILED / NO_NETWORK_CONNECTION (or SDK_UNABLE_TO_CONNECT): the code never reached the
        // issuer — the same code can be entered again once connected.
        response.responseStatusReason == OutcomeReason.NO_NETWORK_CONNECTION ||
            response.responseStatusReason == OutcomeReason.SDK_UNABLE_TO_CONNECT ->
            showError(response.message ?: "Connect and try again")
        response.failureCode == ActivationFailureCode.CODE_INVALID ->
            showError("Wrong code — ${response.attemptsRemaining} attempts left")
        response.failureCode == ActivationFailureCode.MAX_ATTEMPTS_EXCEEDED -> {
            // The cycle is closed. On RecommendDelete.MUST delete the token and restart
            // the add-card flow; on MAY, deletion is advisory.
            endActivationCycle(response.recommendDelete)
        }
        response.failureCode == ActivationFailureCode.ACTIVATION_LOCKED -> showLockedTerminal()
        response.failureCode == ActivationFailureCode.CODE_EXPIRED -> offerResend()
        else -> showError(response.message ?: "Wrong code — try again")
    }
}
```

`ActivateResponse` carries `responseStatus` / `responseStatusReason` (on `FAILED` the reason is the failure code), `tokenUniqueReference`, `status`, `message`, and these failure fields (all null on success): `failureCode` — typed `ActivationFailureCode`, one of `TOKEN_NOT_FOUND`, `TOKEN_NOT_ACTIVATABLE`, `ACTIVATION_LOCKED`, `NO_PENDING_ACTIVATION`, `CODE_EXPIRED`, `CODE_INVALID`, `MAX_ATTEMPTS_EXCEEDED`, `CODE_REQUEST_RATE_LIMITED`, `INVALID_REQUEST`, `ACTIVATION_FAILED`, or `UNKNOWN` for a code newer than this SDK (raw value in `failureCodeRaw`); `attemptsRemaining` — code attempts left where a cap applies (0 when exhausted/locked); `recommendDelete` — `RecommendDelete.MUST` / `MAY` after an exhausted cycle (delete the dead token rather than leaving it in the card list), null otherwise (raw in `recommendDeleteRaw`).

#### `observeActivation` (+ pause / resume / stop / `hasActivationObserver`)

For the out-of-band methods (call centre / website / issuer app) the activation happens elsewhere — observe the token until it activates. The SDK polls every 10 s for up to 5 minutes; callbacks arrive on the main thread; observing the same token again replaces the previous observer.

| Parameter | Description |
|-----------|-------------|
| `tokenUniqueReference` | The card being activated. |
| `onActivated` | Fires exactly once when the token becomes active — navigate to the wallet. |
| `onTimeout` | After 5 minutes without activation — show a fallback message (the SDK keeps checking in the background thereafter). |
| `onError` | Optional — each failed check (polling continues). |

Wire the lifecycle: `pauseActivationObserver(ref)` when the screen backgrounds, `resumeActivationObserver(ref)` on return (the timeout clock keeps running while paused — if it lapsed, `onTimeout` fires immediately), `stopActivationObserver(ref)` when the screen is dismissed. For a one-shot check, see [`tokenStatus`](#tokenstatus). `hasActivationObserver(ref)` says whether an observer for that card is still polling.

```kotlin
wallet.observeActivation(
    tokenUniqueReference = ref,
    onActivated = { refreshCards() },
    onTimeout = { showHint("Still pending — we'll keep checking") }
)
// onPause → pauseActivationObserver(ref); onResume → resumeActivationObserver(ref)
// onDestroy → stopActivationObserver(ref)
```

---

### Tokens

| Call | What it does | Returns |
|---|---|---|
| `getTokens()` | The tokens in this wallet that can pay, each with its last 3 payments. Local read | `List<Token>` |
| `getActiveToken()` | The token a tap or a payment QR pays with | `Token?` |
| `setActiveToken(tokenUniqueReference)` | Selects the token payments use; arms tap-to-pay when a pay session is open | `CardSelection` |
| `getToken(tokenUniqueReference)` | One token | `Token?` |
| `tokenStatus(tokenUniqueReference) { }` | Asks the server for the token's status | `TokenStatusResult` |
| `deactivateToken(tokenUniqueReference) { }` | Deactivates the token on the server and removes it from the phone | `TokenStatusUpdateResponse` |
| `getPaymentApplicationInstanceId()` | This install's SDK-generated instance ID | `String` |

#### `getTokens`

The wallet's cards, from the SDK's local registry — no network call.

```kotlin
// Synchronous local read — run off the main thread for large wallets:
val tokens = withContext(Dispatchers.IO) { wallet.getTokens() }
val active = tokens.firstOrNull { it.isActive }
// Grey out and disable pay actions while the active card requires online refresh:
payButtons.isEnabled = active?.requiresOnline != true
```

`Token`: `tokenUniqueReference` (the identifier every wallet call takes), `devicePAN`, `cardHolderName` (the card's display name — scheme label + masked last four, e.g. `AFRIGO ****1234`; not a person's name, and the same value the card presents in EMV tag `5F20`), `expiryDate`, `cardScheme`, `cardType`, `isActive` (the card payments use), `activationMethods` (non-null while activation is pending), `transactions` (last 3), `requiresOnline` (see below), `deviceNotBound` (see below), `bankName` (from `DigitiseParams.bankName`), helpers `getMaskedPAN()` / `getLastFourDigits()`. `getToken(tokenUniqueReference)` reads one card (or `null`). Only cards that can pay are listed: a card whose payment data is gone from the phone is dropped from the list.

**`requiresOnline`** — `true` when the card cannot pay until the wallet has been **online** to refresh it. Render the card greyed-out and non-tappable and prompt the user to connect; the flag derives fresh on every read and clears on its own once the SDK's automatic refresh succeeds. There is no manual "refresh keys" call — key management is entirely SDK-owned.

**`deviceNotBound`** — `true` when the backend refused to issue payment keys for this card to this device: the card was added on another phone, or on this one before the app was reinstalled or its data wiped. The SDK stops asking for keys for that card. Render it greyed-out and non-tappable with "Add this card again on this phone" — **not** "go online": while it is `true`, `requiresOnline` is always `false`, because connecting cannot fix it. Removing the card and adding it again on this phone is the only remedy, and removal is the only thing that clears the flag.

#### Handling token states in your UI

A card is not simply "there or not" — it can be awaiting activation, frozen for a refresh, or suspended server-side. Derive one display state per card, in this precedence order, every time you render the wallet:

| Precedence | State | How you observe it | UI treatment | What unblocks it |
|---|---|---|---|---|
| 1 | **Needs activation** | `token.activationMethods != null` | Show the card with an **"Activate"** badge/button that launches the [activation flow](#activation). Pay actions hidden. | `activate` succeeding, or `observeActivation` firing `onActivated`. |
| 2 | **Requires online** | `requiresOnline == true` | **Grey the card out and make it non-tappable**; overlay a "Connect to the internet" hint; disable every pay affordance (tap surface, scan-to-pay, show-QR buttons). | Nothing you call — the SDK refreshes the card itself the next time the device is online. Re-read the list and the flag has cleared. |
| 2 | **Not bound to this device** | `deviceNotBound == true` (`requiresOnline` is then always `false`) | Grey the card out and make it non-tappable; overlay "Add this card again on this phone" — **not** a connect hint; disable every pay affordance. | Only removing the card and adding it again on this phone. Going online does not help. |
| 3 | **Inactive server-side** (suspended, expired) | `token.isActive == false`, with `token.status` saying **why** (`SUSPENDED` / `PENDING_ACTIVATION` / `EXPIRED` — a pay attempt ends `NOT_SENT` / `TOKEN_NOT_ACTIVE`) | Grey the card out; word the indicator from `status` — "Suspended — contact your bank" vs "Expired — re-add the card". Disable pay affordances; don't offer retry — the state is issuer-controlled. | A later automatic status sync seeing the card active again. |
| 4 | **Payable** | None of the above | Normal rendering; pay affordances enabled for the active card. | — |

Two rules make this robust:

- **Derive, don't cache.** Every state above is computed fresh on each read and clears itself — re-read the card list whenever your wallet screen (re)appears and after any payment attempt, rather than storing state.
- **Gate the affordances, not just the card face.** Disabling only the card image but leaving a "Scan to pay" button live produces the refusals at pay time; disable the actions too, and treat the `NOT_SENT` outcomes (reason `ONLINE_REQUIRED` / `TOKEN_NOT_ACTIVE` / `DEVICE_NOT_BOUND`) as the backstop, not the primary UX.

The sample's card list + pay-screen gating:

```kotlin
val tokens = withContext(Dispatchers.IO) { wallet.getTokens() }

// Per-card rendering:
fun bind(card: Token) {
    when {
        card.activationMethods != null -> {   // 1. needs activation
            cardView.alpha = 1f
            activateBadge.isVisible = true
            activateBadge.setOnClickListener { launchActivationFlow(card) }
        }
        card.requiresOnline -> {              // 2. frozen until online
            cardView.alpha = 0.4f             // greyed out
            cardView.isClickable = false      // non-tappable
            stateHint.text = "Connect to the internet to use this card"
        }
        card.deviceNotBound -> {              // 2. keys refused to this device
            cardView.alpha = 0.4f
            cardView.isClickable = false
            stateHint.text = "Add this card again on this phone"   // not "go online"
        }
        !card.isActive -> {                   // 3. suspended/inactive server-side
            cardView.alpha = 0.4f
            cardView.isClickable = false
            stateHint.text = when (card.status) {   // say why, not just that
                TokenStatus.SUSPENDED -> "Card suspended — contact your bank"
                TokenStatus.EXPIRED -> "Card expired — re-add it"
                TokenStatus.PENDING_ACTIVATION -> "Activate this card to use it"
                else -> "Card unavailable — contact your bank"
            }
        }
        else -> renderNormally(card)          // 4. payable
    }
}

// Screen-level gating — disable the pay actions with the active card, not just its face:
val active = tokens.firstOrNull { it.isActive }
val blocked = active == null || active.requiresOnline || active.deviceNotBound
scanToPayButton.isEnabled = !blocked
showQrButton.isEnabled = !blocked
```

A deactivated card needs no rendering rule — the SDK removes it from the list entirely when your app calls `deactivateToken`.

#### `getActiveToken`

Read the currently selected card, or `null` when none is selected — the counterpart to
`setActiveToken`, and the way a screen answers "which card would pay right now?" without inferring
it from a list.

```kotlin
val active: Token? = wallet.getActiveToken()
```

Synchronous, and a read of stored state rather than a network call. It returns `null` both when no
card has ever been selected and when the selected card has since been removed, so treat `null` as
"prompt the customer to choose" rather than as an error.

#### `setActiveToken`

Select the card payments use (at most one card is active), by its `tokenUniqueReference`. Selecting it also arms it for tap-to-pay (unless your app has [closed its pay session](#arming-and-disarming-tap-to-pay-pay-session)). The selection happens on the phone — nothing is sent — and it returns a `CardSelection` instead of throwing (only a [setup error](#1-setup-errors-thrown) throws). Tap events and refusals are observed separately, once, not per selection:

```kotlin
// Once, after initialize — for whichever card is active. Main thread.
wallet.observeTapEvents { event ->
    when (event) {
        is WalletTapEvent.TransactionStarted -> showHoldStill()
        // response.status: "APPROVED" / "DECLINED" / "ERROR"; response.amount (minor units);
        // response.reference for the history row
        is WalletTapEvent.TransactionCompleted -> showTapResult(event.response)
    }
}

// Payments refused before anything was sent — see "Refused payments" below.
wallet.observePaymentRefusals(
    tokenUniqueReference = token.tokenUniqueReference!!,
    onRequireOnline = { event -> showError("Connect to the internet to pay ${event.amountMinorUnits}") },
    onAmountExceedCardLimit = { showError("That amount is too large for this card — try another card") },
    onDeviceNotBound = { showError("Add this card again on this phone to pay with it") },
)

val selection: CardSelection = wallet.setActiveToken(token.tokenUniqueReference!!)
if (!selection.selected) {
    // FAILED / CARD_NOT_FOUND (not in this wallet) or CARD_DATA_MISSING (remove and add it again)
    showError(selection.message ?: "Could not select this card")
}
```

| `CardSelection.responseStatus` | `responseStatusReason` | Meaning |
|---|---|---|
| `APPROVED` | — | The card is now the one payments use (`selected == true`) |
| `FAILED` | `CARD_NOT_FOUND` | No card with that `tokenUniqueReference` is in this wallet. Nothing changed |
| `FAILED` | `CARD_DATA_MISSING` | The card is listed but its payment data is not on this phone — remove it and add it again |

`stopObservingTapEvents()` and `stopObservingPaymentRefusals(tokenUniqueReference)` stop them — call them when the screen that registered them goes away. A card whose payment data is gone answers `CARD_DATA_MISSING` here, and is not in `getTokens()`.

#### `getToken`

`wallet.getToken(tokenUniqueReference)` reads one token from the phone, or `null` when it is not in
this wallet (or cannot pay — see [`getTokens`](#gettokens)).

#### `tokenStatus`

`wallet.tokenStatus(tokenUniqueReference) { result: TokenStatusResult -> }` asks the server for the
token's status once and updates the stored token. `responseStatus` `APPROVED` comes with
`lifecycleStatus` (`TokenStatus`: `ACTIVE` / `PENDING_ACTIVATION` / `SUSPENDED` / `DEACTIVATED` /
`EXPIRED`, or `UNKNOWN` for a value newer than this SDK; the raw value is in `tokenStatus`). It lets
you say "suspended — contact your bank" vs "pending — enter your code" vs "expired — add it again".
Otherwise `FAILED` with `responseStatusReason` and `lifecycleStatus == null` (a read, never `PENDING`).

#### `deactivateToken`

`deactivateToken(ref) { response: TokenStatusUpdateResponse -> }` deactivates on the backend, then wipes every on-device artefact for the card and promotes the next card to active. The callback always receives an outcome: `APPROVED` (removed); `FAILED` (`TOKEN_NOT_FOUND`, `TOKEN_STATE_CONFLICT`); `PENDING` (sent, no usable answer — re-read the card list before trying again). Never sent (e.g. offline) is also `FAILED`, with the reason (`NO_NETWORK_CONNECTION`) — retry once it is fixed.

```kotlin
wallet.deactivateToken(ref) { response ->
    when (response.responseStatus) {
        OutcomeStatus.APPROVED -> refreshCards()
        OutcomeStatus.PENDING -> { showHint("Still removing — check again shortly"); refreshCards() }
        else -> showError("Failed to remove card: ${response.message ?: response.responseStatusReason}")
    }
}
```

#### `getPaymentApplicationInstanceId`

`wallet.getPaymentApplicationInstanceId()` returns this install's `payment_application_instance_id` —
SDK-generated (`VYRA` + 32 hex characters), minted on first use, never backed up, new on reinstall.
Read-only: the app cannot set or regenerate it.

---

### Tap to pay

| Call | What it does | Returns |
|---|---|---|
| `openPaySession(activity)` | Arms tap-to-pay on your pay screen until `closePaySession` | `PaySessionResult` |
| `closePaySession()` | Disarms tap-to-pay at once | — |
| `isPaySessionOpen()` | Whether a session opened with `openPaySession` is open | `Boolean` |
| `observeTapEvents { }` / `stopObservingTapEvents()` | Tap started / completed at a terminal, for whichever token is active | `WalletTapEvent` (to the listener) |

#### Arming and disarming tap-to-pay (pay session)

Tap-to-pay is armed only while your pay screen is in front. If your pay screen is **its own
Activity** and you initialise the SDK from it (`activity = this`), the SDK follows that Activity
on its own: it arms on resume and disarms on pause. You need nothing else.

Use a **pay session** when that is not enough:

- **Your app has one Activity** (Jetpack Compose navigation, fragments). Navigating between
  destinations does not pause the Activity, so the SDK cannot tell that the user left the pay screen.
- **You disarm while the pay screen stays up**: a Cancel button, or your own "hold near the
  reader" countdown that expires.

```kotlin
// The pay screen is showing (onResume, or your navigation destination becoming visible):
val session = VeyraWalletSdk.getInstance()!!.openPaySession(activity)
if (!session.opened) {
    when (session.responseStatusReason) {
        PaySessionResult.NFC_DISABLED -> askToTurnOnNfc()
        PaySessionResult.NFC_UNAVAILABLE -> offerQrOnly()
        else -> showError(session.message ?: "Tap-to-pay is not available right now")
    }
}

// Cancel pressed, your countdown expired, or the user navigated away from the pay screen:
VeyraWalletSdk.getInstance()?.closePaySession()

// "Try again" on the same screen re-arms it:
VeyraWalletSdk.getInstance()!!.openPaySession(activity)
```

What each call does:

- **`openPaySession(activity)`** claims tap-to-pay for that screen. While the session is open, the
  SDK disarms when the screen goes to the background, re-arms when it comes back, and closes the
  session if the activity is destroyed. It also **keeps the screen awake**, because a phone whose
  screen has gone off cannot pay by tap. `closePaySession` lets the screen sleep again. If your
  activity already keeps its screen on, the SDK leaves that setting alone. If you call it before the activity has resumed, it arms on
  the resume. Calling it again on the same screen re-opens a closed session. Main thread only.
- **`closePaySession()`** disarms at once. From the moment it returns, a terminal that taps the phone
  gets "application not found". That holds **even if your app is the phone's default payment app**.
  A tap that a terminal has already started is allowed to finish. The selected card stays selected.
  You can call it from any thread, more than once, and with no session open.
- **A closed session stays closed** when the same screen goes to the background and comes back. Only
  `openPaySession` re-opens it, or another wallet screen that the SDK follows coming to the front.
  Selecting a card with `setActiveToken` while the session is closed does not re-arm it.

Nothing is sent. `openPaySession` returns a `PaySessionResult` instead of throwing:

| `responseStatus` | `responseStatusReason` | Meaning | What to do |
|---|---|---|---|
| `APPROVED` | — | The session is open (`opened == true`) | Show "Hold near the reader" |
| `FAILED` | `NFC_DISABLED` | NFC is switched off | Ask the user to turn NFC on, then call `openPaySession` again |
| `FAILED` | `NFC_UNAVAILABLE` | The device has no NFC (or is an emulator) | Offer the QR rails instead |
| `FAILED` | `MODE_REFUSED` | Combined apps: a SoftPOS payment is in progress | Let it finish, then call `openPaySession` again |
| `FAILED` | `TAP_ROUTING_REFUSED` | The phone would not route taps to your screen, and your app is not the default payment app | Ask the user to make your app the default payment app |
| `FAILED` | `SCREEN_NOT_ACTIVE` | The activity is finishing or destroyed | Call it from the live pay screen |

On `FAILED`, nothing changed: a closed session stays closed.

The same three calls exist with the same names on iOS (`VeyraWallet.shared`) and React Native
(`wallet.openPaySession()` / `wallet.closePaySession()` / `wallet.isPaySessionOpen()`). iPhone cannot
pay by tap, so there `openPaySession` always answers `FAILED` / `NFC_UNAVAILABLE`, and shared code
handles it the same way as an Android phone without NFC.
`openPaySession` throws `VeyraSetupException` (`INVALID_CONFIGURATION`) only for integration
mistakes: `enableNfc = false` in `VeyraWalletSdkConfig`, or a call that is not on the main thread.

#### `observeTapEvents`

`wallet.observeTapEvents { event -> }` registers one listener for the wallet, whichever token is
active; registering again replaces it, and `stopObservingTapEvents()` removes it. Events arrive on the
main thread: `WalletTapEvent.TransactionStarted(tokenUniqueReference)` when a terminal starts reading
the token, and `WalletTapEvent.TransactionCompleted(response)` with what the phone saw. That is a
card-reading event, not the payment's outcome — see [Wallet tap](#wallet-tap--transactioncompleted).
Register it again after each `initialize`. The example is under [`setActiveToken`](#setactivetoken).

---

### QR pay

| Call | What it does | Returns |
|---|---|---|
| `showQrToPay(amountMinorUnits, onExpired?) { }` | Renders a payment QR for the active token; `onExpired` fires when it lapses | `Result<CpmPaymentQr>` |
| `cancelQrExpiry()` | Stops the `onExpired` watch | — |
| `inspectScannedQr(payload)` | Verifies a scanned merchant QR on the phone | `MpmScanResult` |
| `payScannedContext(verified) { }` | Pays a verified merchant QR with the active token | `Result<PaymentOutcome>` |
| `cancelPayment(merchantOrderId)` | Cancels a `payScannedContext` while it is still on the phone | `CancelResult` |

#### Show QR to pay (customer-presented)

The customer keys nothing at the till: your app asks the amount first (the merchant states it), authenticates, and renders a **dynamic payment QR** with the amount cryptographically bound inside. Fully offline — the merchant's SoftPOS submits the payment; the outcome lands in history via reconciliation.

#### `showQrToPay` + `cancelQrExpiry`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `amountMinorUnits` | **Mandatory** | The merchant-stated amount — bound into the QR's cryptogram; the merchant's scan charges exactly this or fails. |
| `onExpired` | Optional | Fired **once, on the main thread**, when the QR lapses — blank or replace the code (a dimmed QR is still scannable). A new render supersedes the watch; `cancelQrExpiry()` stops it (call on screen teardown). |

The SDK raises the authentication sheet itself — **one gesture per QR**; regenerating after expiry is a fresh payment attempt and asks again.

```kotlin
wallet.showQrToPay(amountMinorUnits, onExpired = { blankQr() }) { result ->
    val qr = result.getOrElse { setupError -> throw setupError }   // VeyraSetupException only
    if (qr.responseStatus == OutcomeStatus.APPROVED && qr.payload != null) {
        renderQr(qr.payload); pollForSettlement(qr)
    } else {
        // NOT_SENT: no QR was made — AUTH_CANCELLED / AUTH_FAILED / AUTH_UNAVAILABLE, NO_ACTIVE_CARD,
        // CARD_CANNOT_SHOW_QR, ONLINE_REQUIRED, TOKEN_NOT_ACTIVE, DEVICE_NOT_BOUND…
        showError(qr.message ?: qr.responseStatusReason)
    }
}
// dialog/screen teardown:
wallet.cancelQrExpiry()
```

The result (`CpmPaymentQr`): `responseStatus` / `responseStatusReason` / `message` (the outcome — `APPROVED`, or `NOT_SENT` with the reason, when `payload` and the other QR fields are `null`), `payload` (**render as the QR**), `amountMinorUnits`, `currencyNumeric`, `expiresAtEpochMillis`, `transactionHash` — this render's unique hash. To show "paid ✓" on the customer's screen, check **this QR's own payment** while the QR is up: call [`refreshTransactionStatus`](#refreshtransactionstatus-1) with its `transactionHash` — one request per check — and stop on a final status:

```kotlin
val hash = qr.transactionHash ?: return
while (isActive) {
    delay(3000)
    // failureReason set when the check could not be made (offline, throttled…): just ask again next time
    val row = wallet.refreshTransactionStatus(hash).value
    when (row?.authorizationStatus) {
        "APPROVED" -> { onSettled(true); break }
        "DECLINED", "FAILED" -> { onSettled(false); break }
        else -> Unit   // PENDING (the QR's own row exists from the moment it is shown), RETRY, or not found yet: keep the QR up
    }
}
```

The QR's history row is created `PENDING` as soon as the QR is shown, so finding the row is not
the answer — only a final status is. **Don't loop `reconcilePendingTransactions` here:** it asks
about every open payment on the device back-to-back, so on a short loop it gets the wallet
throttled (`429`) by the gateway.

#### Scan to pay (merchant QR)

The customer scans a merchant's get-paid QR: **inspect** (on-device verification) → confirm screen → **authenticate** (biometric) → **pay**.

#### `inspectScannedQr`

Synchronous, on-device verification of the scanned payload — gateway signature against the SDK's pinned keys, plus expiry. **Only a verified result may reach your confirm screen; every rejection must end the flow.**

```kotlin
when (val scan = wallet.inspectScannedQr(payload)) {
    is MpmScanResult.Verified -> showConfirm(scan.context)   // merchant, amount from the QR
    is MpmScanResult.Rejected -> showRejected(
        when (scan.reason) {
            MpmScanResult.Reason.EXPIRED -> "This payment code has expired"
            else -> "This code could not be verified"        // MALFORMED / MISSING_SIGNATURE / UNKNOWN_KEY / BAD_SIGNATURE
        }
    )
}
```

The verified context carries `merchantName`, `merchantCity`, `amount` (display string), `amountMinorUnits`, `currencyNumeric`, `txRef`, `expiryEpochSeconds` — render these on the confirm screen; the customer never keys an amount.

#### Device authentication (CDCVM) — the SDK asks, you don't

**There is no authentication method to call.** `payScannedContext` and `showQrToPay` raise the
system `BiometricPrompt` sheet themselves — fingerprint or face, falling back to the device's own
PIN/pattern/password in the same sheet — before they build the payment. You cannot build a QR
payment flow that skips it, and you cannot forget to sequence it.

The SDK composes the prompt copy from the payment it is about to make, so the gesture is visibly
bound to what it authorises: *"Confirm payment / Pay ₦5,000.00 to Ada's Store"*. It asks **once per
payment attempt** — a retry, or regenerating an expired QR, asks again.

Three outcomes can come back on the same callback, all `NOT_SENT` (nothing was sent), and they need different UI — branch on `responseStatusReason`:

| `responseStatusReason` | What happened | What to do |
|---|---|---|
| `AUTH_CANCELLED` | The customer dismissed the sheet | Stay put and let them try again — nothing was sent |
| `AUTH_FAILED` | They tried and the device rejected it | Offer a retry |
| `AUTH_UNAVAILABLE` | This device has **no** enrolled biometric *and* **no** screen lock | Send them to system settings; a retry can never succeed |

The sheet appears only **after** the card checks pass, so a card that is out of keys, over its limit
or not active is refused without spending the customer's gesture.

**Changing the wording or the language.** Set these once on `VeyraWalletSdkConfig` — `{amount}` and
`{merchant}` are substituted:

```kotlin
VeyraWalletSdkConfig.builder(/* … */)
    .cdcvmPayPrompt(title = "Authorise payment", subtitle = "Send {amount} to {merchant}")
    .cdcvmShowQrPrompt(title = "Show payment code", subtitle = "Code for {amount}")
    .cdcvmAllowDeviceCredential(true)   // false = biometric only, no PIN fallback
    .build()
```

> **Tap-to-pay is different.** A contactless tap derives CDCVM from the device already being
> unlocked with a secure lock screen — there is no moment to show a sheet while the phone is held
> against the reader. This section is about the two QR rails only.

#### `payScannedContext`

Pay the verified context with the wallet's **active card**. The SDK raises the authentication sheet
itself first (above). Whatever the gateway states — approved, declined, failed or still pending — also lands in the card's history.

**Branch on `responseStatus`, not on `approved` or the response code.** The callback's `Result` fails only on a [setup error](#1-setup-errors-thrown); every refusal, including those made on the phone before sending (`AUTH_CANCELLED`, `ONLINE_REQUIRED`, `DEVICE_NOT_BOUND`, `NO_NETWORK_CONNECTION`, …), is a `NOT_SENT` outcome — nothing was pushed, so the customer can pay the **same** QR again once the reason is fixed. `RETRY` is handled the same way: the gateway received the push but did not send it to the bank (for example the merchant's account check failed), nothing was charged, and the merchant's QR is live again until it expires, so the customer can pay it again. The push is a synchronous call, but its *outcome* can still be unknown: the gateway answers `PENDING` when a hop below it timed out (`68`), errored (`06`/`96`) or is still settling (`09`). That is not a refusal — the SDK records the payment as unresolved and keeps polling it until the gateway states a final outcome, which then shows on the history row. `approved` is a convenience for the happy path only (`responseStatus == "APPROVED"`); it is `false` for a pending payment as well as a declined one.

```kotlin
wallet.payScannedContext(ctx) { result ->
    val outcome = result.getOrElse { setupError -> throw setupError }  // VeyraSetupException only
    when (outcome.responseStatus) {
        OutcomeStatus.APPROVED -> showApproved(outcome.message)
        OutcomeStatus.DECLINED, OutcomeStatus.FAILED -> showDeclined(outcome.message, outcome.responseStatusReason)
        // Nothing was charged (NOT_SENT: never reached Veyra; RETRY: not sent to the bank):
        // stay on the confirm screen, say what to fix, let them pay this QR again.
        OutcomeStatus.NOT_SENT, OutcomeStatus.RETRY ->
            showReasonOnConfirm(outcome.responseStatusReason, outcome.message)
        // PENDING, absent or anything else: not yet known. Say so, and point at history —
        // never show a refusal for a payment that may still settle, and never pay again.
        else -> showPending(outcome.responseCode)
    }
}
```

#### `cancelPayment`

`wallet.cancelPayment(merchantOrderId)` cancels the `payScannedContext` of the QR carrying that order
ID (`VerifiedPaymentContext.merchantOrderId`) while it is still on the phone, and answers at once with
a `CancelResult`. A QR that carries no order ID cannot be cancelled. See
[Cancelling a call](#cancelling-a-call).

---

### History, receipts and reconciliation

| Call | What it does | Returns |
|---|---|---|
| `getTransactions(tokenUniqueReference, limit = 50)` | A token's payments, most recent first. Local read | `List<TransactionSummary>` |
| `reconcilePendingTransactions { }` | Asks the backend about every pending payment, once | `ReconcileResult` |
| `refreshTransactionStatus(transactionHash)` | Asks about one pending payment now (`suspend`) | `VeyraLookup<TransactionSummary>` |
| `refreshCreditConfirmation(transactionHash)` | Asks now whether the merchant's bank received the funds (`suspend`) | `VeyraLookup<TransactionSummary>` |
| `processReceipt(qrPayload, expectedTransactionHash?)` | Verifies and stores a scanned merchant receipt | `ReceiptScanResult` |
| `getReceipts(limit = 100)` | The stored receipts, most recent first | `List<TransactionReceipt>` |
| `getReceiptForTransaction(transactionHash)` | The receipt for one payment | `TransactionReceipt?` |

#### `getTransactions`

The card's full local history across every rail (tap, scanned QR, shown QR), most recent first. No network call. Empty when no card on this device has that reference, or the card has no payments yet. The same name and default (`limit = 50`) as on iOS and React Native.

```kotlin
val transactions = wallet.getTransactions(tokenUniqueReference)          // last 50
val lastFive = wallet.getTransactions(tokenUniqueReference, limit = 5)
```

`TransactionSummary` fields: `merchantName`, `amountInMinorUnit`, `transactionCurrencyCode` (4-digit ISO 4217, e.g. `"0566"`), `authorizationStatus` (`PENDING` / `APPROVED` / `DECLINED` / `FAILED` / `RETRY` — `RETRY`: the payment reached Veyra but was not sent to the bank, nothing was charged; `null` on legacy rows — treat as indeterminate), `responseCode` (the outcome's code, e.g. `"00"`, `"51"` — verbatim from the rail that resolved the row; `null` until resolved; quote this literal in support conversations), `responseStatusReason` (the outcome's stated cause, e.g. `"INSUFFICIENT_FUNDS"` — a plain string to display, never parse; `null` until resolved), `entryMethod` (`"TAP"`, `"QR_GENERATED"` — showed a QR, `"QR_SCANNED"` — scanned a merchant QR; `null` legacy — show nothing rather than guess), `merchantLocation`, `transactionHash` (join key to a receipt), `localTransactionDateTime` / `atEpochMillis`, `merchantTransactionReference`, `merchantId`, `merchantOrderId` (the merchant's own order/basket id for the sale — the id the merchant's systems know it by, so a customer can quote it at the counter; a scanned-QR row carries it from payment time, tap and generated-QR rows learn it from the status poll, so `null` on a still-open row means "not learned yet", not "no order id"; for display and reconciliation — receipts and status refreshes key off `transactionHash` / `merchantTransactionReference`), plus the five beneficiary-credit fields below.

#### Merchant credit confirmation (wallet side)

Did the money actually reach the merchant's bank? The wallet asks the same question the merchant's own SDK asks about that sale, from the payer's side — **settlement confirmation only**, it never changes or restates the payment outcome.

**The SDK does the polling; your app renders the stored row.** After a payment is approved, the SDK asks the gateway on an exponential backoff for up to **30 days**, app-scoped: it keeps going across every screen, and no screen starts or stops it. There is deliberately **no wallet callback** for this — the stored row is the whole surface. Read it when a transaction detail view appears, and re-read while it is up if you want the line to flip live.

These three fields are the **eligibility contract**: they are how you decide whether to render a
credit line at all, and whether you may call `refreshCreditConfirmation` (below). They are not merely
a cue to wait.

| Field | What it means for you |
|---|---|
| `isCreditConfirmationSupported: Boolean?` | **The gate.** `true` ⇒ the merchant's bank is on the confirmation rail, the SDK is polling, and you should render the credit line **and may offer the manual check**. `false`/`null` ⇒ there is nothing to ask — render **no** credit UI for that transaction, and **do not call `refreshCreditConfirmation`**. |
| `creditConfirmationStatus: String?` | `null` = no answer yet (with the gate `true`, that is the "confirming…" state) · `"RECEIVED"` = terminal, the funds are confirmed in the merchant's account · `"UNABLE_TO_CONFIRM"` = the 30-day sweep stopped asking. |
| `creditTransactionId: String?` | The credit leg's id (NIP session id inter-bank, batch reference intra-bank) — **what you quote to a bank** when the merchant says the money never arrived. Display/support only; never pass it back to the SDK, and render it only where the gate above is `true` — a bare id with no confirmation line reads as a promise. |
| `creditedAt: String?` | When the beneficiary bank posted the credit. `"RECEIVED"` only. |
| `bankReference: String?` | The beneficiary bank's own reference for the credit. `"RECEIVED"` only. |

Two things to get right, because they are easy to get wrong in the user's favour and wrong in fact:

- **`"UNABLE_TO_CONFIRM"` does not mean the merchant was not paid.** It means we stopped asking after 30 days. Word it as "could not confirm", never as "not received".
- **No credit line at all is a normal state**, not an error: it means this transaction is not on the rail (an older row recorded before your app updated, a bank that does not support confirmation, or a payment that was not approved). Absence means "we cannot ask".

The same three core fields, with the same meanings, exist on the SoftPOS side of the SDK — so an app that implements both halves reads one contract.

**Cadence, stated plainly:** the wallet's periodic sweep rides WorkManager, whose floor is **15 minutes**, so the backoff ladder is measured in sweep cycles and the practical resolution is coarser than the merchant SDK's ten-second loop. A one-shot chain runs immediately after a payment resolves, so a credit that lands promptly is usually picked up within seconds; after that, expect minutes, not seconds.

#### `reconcilePendingTransactions`

Reconcile still-`PENDING` rows against the backend. It asks about **every** open row on the device, so call it on an occasion — on returning to the foreground, on pull-to-refresh, when history is opened — **never on a loop**. A screen waiting for one payment (a [shown QR](#show-qr-to-pay-customer-presented), say) calls [`refreshTransactionStatus`](#refreshtransactionstatus-1) with that payment's hash instead: one request rather than one per open row. If the gateway throttles (`429`), the pass stops there and the remaining rows wait for the SDK's next sweep.

```kotlin
wallet.reconcilePendingTransactions { result: ReconcileResult ->
    refreshList()                                   // read the updated rows either way
    result.failureReason?.let { showNote(if (it == OutcomeReason.NO_NETWORK_CONNECTION) "You're offline" else "Couldn't check now") }
}
```

The callback always receives a `ReconcileResult` — a read, so it states no status of its own:
`failureReason` (`null` when the reconcile ran; set only when the reconcile itself could not run, e.g.
`NO_NETWORK_CONNECTION`), `message`, and `checked` (`failureReason == null`). A row whose own check
fails is simply left as it is, and the background sweep keeps asking. Only a [setup error](#1-setup-errors-thrown) throws.

#### `refreshTransactionStatus`

```kotlin
// suspend — call from a coroutine. Never throws for anything that happens to the check.
val refresh = wallet.refreshTransactionStatus(summary.transactionHash)
refresh.value?.let { render(it) }      // the row as it now stands (null: no row has that hash)
refresh.failureReason?.let { showNote(if (it == OutcomeReason.NO_NETWORK_CONNECTION) "You're offline" else "Couldn't check now") }
```

`VeyraLookup<TransactionSummary>`: `value` (the row, or `null`), `failureReason` (`null` when the
check was made), `message`, and `checked` (`failureReason == null`) — the same type as iOS and React Native.

The **per-transaction** counterpart to `reconcilePendingTransactions`, which asks about every open
row and reports only whether it could run — this one answers about the row the customer is actually looking at, keyed
by its `transactionHash`.

The SDK polls a pending transaction for you with **exponential backoff**, and **stops after 30
days**. Polling never invents an outcome — a row that ages out simply stops being asked about and
stays `PENDING`. Expose **`refreshTransactionStatus`** in your UI so the customer can ask on demand,
which is the route for anything still pending after the window closes.

The same five rules as the merchant method above apply: show it only while the row is `PENDING`; it
works past the window and off the sweep's list; it never forces an outcome; a lookup that cannot be
made returns the unchanged row with a `failureReason` (`NO_NETWORK_CONNECTION` when offline) and never
changes the payment; and there is no SDK-side throttle, so disable the button while a call is in flight.

#### `refreshCreditConfirmation`

The SDK polls for beneficiary credit confirmation with **exponential backoff** and **stops after 30
days**, finalising the row as `UNABLE_TO_CONFIRM` — which means "we stopped asking", never "the funds
were not received". Expose **`refreshCreditConfirmation`** in your UI so the user can ask on demand;
it works after the window closes, and a later `RECEIVED` replaces the give-up state.

**Check `isCreditConfirmationSupported` on the transaction first.** Not every merchant's bank is on
this rail. `true` means the SDK is polling and you may offer the manual check; `false`/`null` means
there is nothing to ask — do not call it, and show no credit UI for that transaction. Offer the
action only while

```kotlin
tx.authorizationStatus == "APPROVED" &&
    tx.isCreditConfirmationSupported == true &&
    tx.creditConfirmationStatus != "RECEIVED"
```

```kotlin
// suspend — call from a coroutine. Keyed by the row's transaction hash, never by a credit id.
val refresh: VeyraLookup<TransactionSummary> = wallet.refreshCreditConfirmation(transactionHash)
// refresh.value — the row as it now stands; refresh.failureReason — null when the check was made
```

- **A row outside that predicate is a no-op**, not an error: no request is made and the unchanged row
  comes back.
- **It works past the 30-day window**, including on a row already stamped `UNABLE_TO_CONFIRM` — that
  is the case it exists for. Nothing ever replaces `RECEIVED`.
- **Only a confirmation is written.** Anything else leaves the row exactly as it was.
- **Settlement only.** Nothing on this path can change `authorizationStatus`, `responseCode` or
  `responseStatusReason`.
- **Still no callback** — the returned row and the stored history are the wallet's whole credit
  surface, by design.
- **A failed check changes nothing.** When it cannot be made the result carries a `failureReason`
  (e.g. `NO_NETWORK_CONNECTION` when the device is offline) beside the unchanged row. Leave the credit
  line reading "not confirmed yet".

#### `processReceipt` / `getReceipts` / `getReceiptForTransaction`

Scan a merchant's **receipt QR** to store the customer's copy. The SDK decodes, validates that the receipt matches a payment this wallet actually made, de-duplicates and stores it. It is checked on the phone — nothing is sent — and the callback always receives a `ReceiptScanResult` (the `suspend` variant returns it); only a [setup error](#1-setup-errors-thrown) throws.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `qrPayload` | **Mandatory** | The scanned contents — raw JSON or base64. |
| `expectedTransactionHash` | Optional | Set it when the scan is launched **from a specific transaction's screen** — a receipt for a different transaction is rejected instead of silently attaching elsewhere. `null` = unscoped. |

```kotlin
val base64 = Base64.encodeToString(scanned.toByteArray(), Base64.NO_WRAP)
wallet.processReceipt(base64, expectedTransactionHash = summary.transactionHash) { result: ReceiptScanResult ->
    when {
        result.stored -> showReceipt(result.receipt!!)                       // APPROVED
        result.responseStatusReason == ReceiptScanResult.PAYMENT_NOT_CONFIRMED ->
            showError("This payment isn't confirmed yet — scan again shortly")
        else -> showError(result.message ?: "Receipt didn't match this transaction")   // FAILED
    }
}

val receipts = wallet.getReceipts(limit = 100)              // most recent first, local
val receipt  = wallet.getReceiptForTransaction(hash)         // by transactionHash, or null
```

| `ReceiptScanResult.responseStatus` | `responseStatusReason` | Meaning |
|---|---|---|
| `APPROVED` | — | The receipt belongs to a payment this wallet made, agrees with it, and is stored in `receipt` (`stored == true`) |
| `FAILED` | `INVALID_RECEIPT` | Not a Veyra receipt QR, or one with a missing or unreadable field |
| `FAILED` | `RECEIPT_NOT_FROM_THIS_WALLET` | No payment made by this wallet matches it |
| `FAILED` | `RECEIPT_MISMATCH` | It matches a payment, but its amount, currency, date, merchant or reference disagree with this wallet's record |
| `FAILED` | `PAYMENT_NOT_CONFIRMED` | The matching payment's outcome is not confirmed yet — scan again shortly |
| `FAILED` | `RECEIPT_FOR_ANOTHER_TRANSACTION` | Scoped scan (`expectedTransactionHash`): the receipt is for a different transaction |
| `FAILED` | `SDK_INTERNAL_ERROR` | The SDK failed inside itself |

---

### Observers

The SDK tells you when stored truth changes. Each observer is one listener for the wallet —
registering again replaces it, its `stopObserving…` call removes it — delivered on the main thread,
with no replay: read the store (`getTokens`, `getTransactions`) when a screen appears. Register them
again after each `initialize`, because a sign-out or a customer switch drops them. The same names
exist on iOS and React Native.

| Call | What it does | Delivers |
|---|---|---|
| `observeTokenStatusChanged { }` / `stopObservingTokenStatusChanged()` | A token's status changed — suspended, reactivated, expired, deactivated | `TokenStatusChange` |
| `observeTransactionResolved { }` / `stopObservingTransactionResolved()` | A pending payment reached its final outcome | `WalletTransactionResolution` |
| `observeCardKeyStateChanged { }` / `stopObservingCardKeyStateChanged()` | A token ran out of payment keys, or got them back | `CardKeyState` |
| `observePaymentRefusals(tokenUniqueReference, …)` / `stopObservingPaymentRefusals(tokenUniqueReference)` | A payment with that token was refused before anything was sent | `RequireOnlineEvent` / `AmountExceedCardLimitEvent` / `DeviceNotBoundEvent` |

#### `observeTokenStatusChanged`

The sweep can apply `SUSPENDED` while the customer is *looking at* a card screen. Subscribe and you
are told the moment it is applied:

```kotlin
wallet.observeTokenStatusChanged { change ->
    // change.tokenUniqueReference — which card (it fires for any stored card)
    // change.status               — ACTIVE / SUSPENDED / EXPIRED / DEACTIVATED /
    //                               PENDING_ACTIVATION / UNKNOWN
    // change.rawStatus            — the literal as stored; log this one
    // change.canPay               — whether the card can pay right now
    // change.previousRawStatus    — what it held before, or null if this is its first status
}
```

- **Branch on `canPay`, not on `status`.** It is the same check the SDK's own payment gates use, so a
  status added after your build shipped is correctly reported as *not* payable.
- **Register once, after each `initialize`** — not per card screen (a sign-out or customer switch
  drops it).
- **It does not replay.** Read `getTokens()` at start-up.
- **Only genuine changes fire.** A sweep that re-applies the status a card already had wakes nothing.
- **Last registration wins.** `stopObservingTokenStatusChanged()` stops it. Delivered on the main thread.

The same channel exists with the same name on iOS (`VeyraWallet.shared.observeTokenStatusChanged { … }`)
and React Native (`wallet.observeTokenStatusChanged(listener)`).

#### `observeTransactionResolved`

A wallet payment that gets no immediate answer is stored and polled by the SDK until the backend
settles it — seconds, or days. Unresolved rows are visible in history, so the customer can be
looking at the row at the moment it settles. This is how your app hears it without polling:

```kotlin
wallet.observeTransactionResolved { resolution ->
    // resolution.transactionHash        — which payment (match your row on this)
    // resolution.tokenUniqueReference   — the card that paid
    // resolution.status                 — APPROVED / DECLINED / FAILED (never PENDING)
    // resolution.responseCode           — the wire literal, for receipts and support
    // resolution.reason                 — e.g. INSUFFICIENT_FUNDS — display, never parse
    // resolution.amountInMinorUnit, resolution.merchantName
}
```

**This is not the same channel as the merchant SDK's `TransactionResolvedObserver`**, and the two
are not interchangeable: that one is the *merchant's* side of a payment and identifies a sale by the
reference the merchant's own app supplied — a value a wallet never sees. The wallet keys on
`transactionHash`.

Fires only on a genuine `PENDING` → final transition: a poll that leaves the row pending, and a
later write that backfills merchant details onto an already-final row, both wake nothing. Register
once after each `initialize` (a sign-out or customer switch drops it), no replay (read `getTransactions` on appear), last registration wins, delivered
on the main thread. `stopObservingTransactionResolved()` stops it.

#### `observeCardKeyStateChanged`

```kotlin
wallet.observeCardKeyStateChanged { state ->
    // state.tokenUniqueReference, state.requiresOnline, state.deviceNotBound
}
// stopObservingCardKeyStateChanged() stops it
```

`requiresOnline` here is **the same value `getTokens()` reports**, so a callback can never contradict
the list you are about to draw. `CardKeyState.deviceNotBound` mirrors `Token.deviceNotBound`: render
it as "add this card again on this phone", not as a connect prompt.

**Know its limit before you word your UI.** It fires when a payment uses a key and when a refresh
delivers new ones. Payment keys *also* expire by clock, with no SDK code running — **nothing fires
for that**, and such a card simply reads as `requiresOnline` on your next `getTokens()`. Keep reading
the card list when a screen appears. There is deliberately no API to trigger a key refresh — the SDK
owns when keys are replenished.

#### `observePaymentRefusals`

What each refusal means, and what to tell the payer, is under
[Refused payments](#refused-payments--onrequireonline-onamountexceedcardlimit-and-ondevicenotbound).

Register them per card — the active one, or several, for a wallet list that reflects refusals across
cards:

```kotlin
wallet.observePaymentRefusals(
    tokenUniqueReference = card.tokenUniqueReference!!,
    onRequireOnline = { event -> promptToConnect(event.amountMinorUnits) },
    onAmountExceedCardLimit = { event -> offerSmallerAmount(event.cardLimitMinorUnits) },
    onDeviceNotBound = { event -> promptToAddCardAgain(event.tokenUniqueReference) },
)
// when the screen goes:
wallet.stopObservingPaymentRefusals(card.tokenUniqueReference!!)
```

Registering the same card again replaces its handlers; other cards are unaffected.

---

## Setup — migrating from 3.0.0

The Android signatures keep their 3.0.0 shape: `VeyraSdk.initialize(activity, customerId, VeyraSdkConfig(softpos, wallet), provider)`,
`VeyraSoftPOSSdk.initialize(activity, customerId, config, provider)` and
`VeyraWalletSdk.initialize(context, customerId, config, provider, activity)`. What changed is that
`VeyraSdk.initialize` now sets up and signs in **both** SDKs, so wallet and SoftPOS calls work right
after it, from any screen; payment screens still call their own SDK's `initialize` with themselves,
which is what makes that SDK's NFC follow the screen.
iOS and React Native now take the same `(customerId, config, provider)` order, so the three
platforms' setup code reads alike.

## Wallet — migrating from 3.0.0

The wallet API now has one name, one owner and one shape per operation on Android, iOS and React
Native. Every operation is called on the wallet object (`VeyraWalletSdk`, or `VeyraSdk.wallet`);
`tokenisationService` is gone. The old names are removed, not deprecated.

| 3.0.0 | Now |
|---|---|
| `sdk.tokenisationService.<call>(…)` | `wallet.<call>(…)` — every operation below lives on `VeyraWalletSdk` |
| `checkAccountEligibility(params) { }` | `verifyAccount(params) { }` |
| `VerifyAccountParams.Builder(...).numberOfActiveTokens(n)` | Removed — the SDK counts the cards in the wallet itself |
| `digitizeAccount(TokenizationRequestParams) { TokenisationResponse -> }` | `digitise(DigitiseParams) { DigitiseResult -> }` |
| `TokenizationRequestParams.builder(…)` (ten mandatory) | `DigitiseParams(…)` — six required (`accountNumber`, `institutionCode`, `accountHolderName`, `walletAccountId`, `emailAddress`, `recommendation`); `consumerIdentifier`, `bvn`, `accountHolderAddress`, `mobileNumber` are optional, as on iOS and React Native |
| `walletProviderTokenizationRecommendation` / `walletProviderDeviceScore` / `walletProviderAccountScore` / `walletProviderTokenizationRecommendationReasons` | `recommendation` / `deviceScore` / `accountScore` / `recommendationReasons` |
| `build()` threw `TokenizationRequestValidationException` for a blank required field | `digitise` answers `NOT_SENT` / `INVALID_REQUEST` |
| `TokenisationResponse.activationMethods` / `isSuccess` / `status` / `error` | `DigitiseResult.activationMethods` (`DigitiseActivationMethod`, never null) / `responseStatus == "APPROVED"` / removed / removed |
| `requestActivationCode(ref, selectedActivationMethod, preferredActivationChannel, reasonCode) { }` | `requestActivationCode(ref, medium: ActivationMedium, reason: ActivationReason = ADD_CARD) { }` — the channel is the method |
| `ActivationCodeReason.ADD_CARD` / `CHECK_ACCOUNT_ELIGIBILITY` / `OTHER` | `ActivationReason.ADD_CARD` / `VERIFY_ACCOUNT` / `OTHER` |
| `checkTokenStatus(ref) { Result<Boolean> }` | `tokenStatus(ref) { TokenStatusResult -> }` |
| `Token.tokenId` | Removed — use `Token.tokenUniqueReference`, the identifier every wallet call takes |
| `getToken(tokenId)` | `getToken(tokenUniqueReference)` |
| `setActiveToken(tokenId, activity, onTransactionStarted, onTransactionCompleted, onActivationFailed, onRequireOnline, onAmountExceedCardLimit, onDeviceNotBound)` | `setActiveToken(tokenUniqueReference)`. Tap events: `observeTapEvents { WalletTapEvent -> }` once. Refusals: `observePaymentRefusals(tokenUniqueReference, …)`. `onActivationFailed`: the returned `CardSelection` (`CARD_DATA_MISSING`) |
| `hasProvisioningData(tokenId)` | Removed — `getTokens()` returns only cards that can pay, and `setActiveToken` answers `CARD_DATA_MISSING` for one whose data is gone |
| `payScannedContext(ctx) { Result<MpmPushOutcome> -> }` | `payScannedContext(ctx) { Result<PaymentOutcome> -> }` — same fields |
| `refreshTransactionStatus(hash)` / `refreshCreditConfirmation(hash)` → `TransactionRefresh` (`.transaction`) | → `VeyraLookup<TransactionSummary>` (`.value`) — the type iOS and React Native return |
| `getLastReceipts(limit)` | `getReceipts(limit)` |
| `isPaySessionOpen` (property) | `isPaySessionOpen()` — a function, as on iOS and React Native |
| `tokenisationService.getTransactions(ref, limit)` | `getTransactions(ref, limit)` |
| `TokenLifecycleObserver.onTokenStatusChanged { }` / `.clear()` | `observeTokenStatusChanged { }` / `stopObservingTokenStatusChanged()` |
| `WalletTransactionResolvedObserver.onTransactionResolved { }` / `.clear()` | `observeTransactionResolved { }` / `stopObservingTransactionResolved()` |
| `WalletKeyStateObserver.onKeyStateChanged { }` / `.clear()` | `observeCardKeyStateChanged { }` / `stopObservingCardKeyStateChanged()` |
| The observers' `isObserving` | Removed |
| `getTokenRequestorId()` / `getAppVersion()` | Removed — both only echoed the configuration your app supplied |
| `app_version` in your app's `config.xml` | No longer read: set `VeyraWalletSdkConfig.appVersion`, or the SDK sends your package version |

---

## Cancelling a call

A payment or an add-card can be cancelled **only while its request is still on the phone**. Each
call has one point of no return: the moment its request is handed to the network. Before it, a
cancel stops the call and nothing is sent. After it, the cancel is refused and the call carries on
to its normal result: an approved, declined or pending payment cannot be taken back from the phone.

Every cancel names the call by a **reference you already hold**:

| Call | Cancel with | Reference |
|---|---|---|
| Card tap (`cardPaymentService.makeCardPayment`), customer-QR charge (`cpmCustomerQrService.charge`), get-paid QR create (`createContextPayment`) | `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)` | The `merchantOrderId` the payment was started with. You don't say which rail: the order ID identifies the payment |
| Scan to pay (`wallet.payScannedContext`) | `wallet.cancelPayment(merchantOrderId)` | `VerifiedPaymentContext.merchantOrderId` — the order ID signed into the scanned QR. `null` on a QR that carries none: that payment cannot be cancelled (disable your Cancel button while it is paying) |
| Add a card (`wallet.digitise`) | `wallet.cancelDigitise(clientRequestId)` | The `clientRequestId` you set in `DigitiseParams`. Set none and the SDK generates one, and that add-card cannot be cancelled |

The merchant's and the wallet's payments are kept apart: in a combined app the wallet's
`cancelPayment("ORDER-1")` can never cancel the merchant's own payment with the same order ID.

### API

| Member | Returns | Description |
|---|---|---|
| `VeyraSoftPOSSdk.cancelPayment(merchantOrderId: String)` | `CancelResult` | Cancel the merchant payment started with this order ID — tap, customer-QR charge or get-paid QR create — while it is still on the phone. Answers at once, from any thread |
| `wallet.cancelPayment(merchantOrderId: String)` | `CancelResult` | Cancel the scan-to-pay of the QR carrying this order ID while it is still on the phone. Answers at once, from any thread |
| `wallet.cancelDigitise(clientRequestId: String)` | `CancelResult` | Cancel the add-card started with this `clientRequestId` while it is still on the phone. Answers at once, from any thread |
| `CancelResult` | — | `co.veyra.common.cancel.CancelResult`, the answer to a cancel: `CANCELLED`, `TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT`, `TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED`, `NO_PAYMENT_IN_PROGRESS_FOR_ORDER_ID`, `NO_DIGITISATION_IN_PROGRESS_FOR_CLIENT_REQUEST_ID` |

### What the cancel answers, and what to show

The cancel answers at once with a `CancelResult`. **That is the answer to the cancel, never the
call's outcome**: the call still delivers its own result to its callback (or return value), exactly
once.

| `CancelResult` | What happened | What your app shows |
|---|---|---|
| `CANCELLED` | The call was stopped before anything was sent. Its result arrives as `NOT_SENT` / `CANCELLED` | Go back to the previous screen when that result arrives. The same order ID or `clientRequestId` can be used again |
| `TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT` | The request has already left the phone. The call carries on | "This can no longer be cancelled" — keep waiting, and show the call's own result (approved, declined, pending…) when it arrives |
| `TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED` | The call had already finished | Nothing to do: show the result it delivered |
| `NO_PAYMENT_IN_PROGRESS_FOR_ORDER_ID` | No payment with that order ID is armed or running on this phone (wrong ID, never started, already finished, or a scanned QR with no order ID) | Just go back |
| `NO_DIGITISATION_IN_PROGRESS_FOR_CLIENT_REQUEST_ID` | No add-card with that `clientRequestId` is running (wrong ID, started without one, or already finished) | Just go back |

```kotlin
// Merchant: the Cancel button on the tap / charge / QR waiting screen.
when (softposSdk.cancelPayment(orderId)) {
    CancelResult.CANCELLED -> Unit                    // the payment's own result is NOT_SENT / CANCELLED → go back there
    CancelResult.TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT ->
        showHint("This can no longer be cancelled")   // keep waiting for the result
    CancelResult.TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED -> Unit  // its result is already on screen
    else -> goBack()                                  // nothing in flight under that order ID
}

// …and where the result arrives (makeCardPayment callback, charge, createContextPayment):
if (response.responseStatusReason == OutcomeReason.CANCELLED) {
    goBack()                                          // nothing was charged; the order ID is still unused
    return
}

// Wallet add-card: set your own clientRequestId so the attempt can be cancelled.
val requestId = UUID.randomUUID().toString()
val params = DigitiseParams(/* … */ clientRequestId = requestId)
wallet.digitise(params) { result -> /* … */ }
// Cancel button, while that digitisation is running:
val answer = wallet.cancelDigitise(requestId)

// Wallet scan-to-pay: cancel by the order ID in the QR (no order ID → cannot be cancelled).
verified.merchantOrderId?.let { orderId ->
    val answer = wallet.cancelPayment(orderId)
}
```

The sample's screens (`GetPaidActivity`, `AccountEligibilityActivity`, `ScanToPayActivity`) share one
rule for the answer, in `CancelAnswer.kt`.

### Which steps can be cancelled

Everything **before sending** can be cancelled; **once the request is sent, nothing can**.

| Rail | Cancellable (still on the phone) | Not cancellable (sent) |
|---|---|---|
| Card tap (merchant) | Arming the reader and waiting for a tap; reading the card; the window after the card is read (`onCardReadingComplete`) until the request is sent — the card's cryptogram is discarded and never sent later. Keep Cancel visible after `onCardDetected` | From `onSendingRequestOnline` on |
| Customer-QR charge (merchant) | Building the charge | Once the charge is sent |
| Get-paid QR create (merchant) | Building the create request | Once it is sent. A QR that is already live has nothing in flight: the cancel answers `NO_PAYMENT_IN_PROGRESS_FOR_ORDER_ID` — just leave the screen (and call `cancelQrExpiry()`) |
| Scan to pay (wallet) | Checking the card and its keys; the key top-up beforehand; the device authentication sheet; building the payment | Once the payment is sent |
| Add a card (wallet) | Device attestation; building the request | Once the request is sent to the token service |
| Show QR to pay (wallet) | — no wallet-side cancel | The wallet sends nothing: the **merchant's** charge is the request, under an order ID the merchant assigns. Only the merchant can cancel it, with `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)`. Taking the QR off the screen does not stop a merchant who has already scanned it |

If the authentication sheet is showing when a scan-to-pay is cancelled, it stays until the customer
dismisses it, but nothing is paid. When your get-paid screen goes away with a tap still armed, the SDK
ends that tap itself (`NOT_SENT` / `CANCELLED`); a payment that has already been sent runs to its
result, which the SDK records in history.

### A cancelled call ends `NOT_SENT` / `CANCELLED`

A cancel that wins ends the call with `responseStatus` `NOT_SENT`, `responseStatusReason`
`CANCELLED` and no `responseCode`. It is an ordinary `NOT_SENT` outcome — nothing reached Veyra, the
order ID (or `clientRequestId`) is still unused and may be used again. See
[Errors and outcomes](#errors-and-outcomes) and
[Table A](#table-a--outcomes-the-sdk-itself-produces).

### Cancelling: migrating from 2.x

| 2.x | 3.0.0 |
|---|---|
| `cardPaymentService.cancelPendingPayment()` | **Removed.** It cancelled whatever payment happened to be armed. Call `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)` with the sale's order ID: it reaches only that payment, works while the card is being read and until the request is sent, and tells you when it is too late |
| No way to cancel a customer-QR charge, a get-paid QR create, a scan to pay or an add-card | `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)` (merchant), `wallet.cancelPayment(merchantOrderId)` and `wallet.cancelDigitise(clientRequestId)` (wallet) |
| `clientRequestId` was only a request identifier | It is also the reference `cancelDigitise` names — set one per add-card attempt to make it cancellable |

## Errors and outcomes

Every SDK call ends in one of two ways:

1. **It throws** — only when your app has not set the SDK up correctly. These are bugs in your
   integration, found during development. You fix the code; you never show them to a user. See
   [Setup errors (thrown)](#1-setup-errors-thrown).
2. **It returns an outcome** — for everything that happens while processing a request: approved,
   declined, failed, still pending, not sent to the bank (retry), or never sent. The outcome is always the same fields:
   `responseStatus` and `responseStatusReason`, plus `responseCode` on payments and add card. See
   [Processing outcomes](#2-processing-outcomes-the-response-triple).

### 1. Setup errors (thrown)

**What they mean.** The SDK cannot run the call at all, because of how your app called it. Nothing
was sent. Retrying will never help until the code changes.

**How to use them.** Let them surface in development: crash, log, assert. Fix the integration. Do not
build user-facing screens for them, and do not offer the user a retry.

Android throws one type for all of them: `co.veyra.common.VeyraSetupException`, an
`IllegalStateException` whose `code` (`VeyraSetupCode`) is one of five values. The same five names are
used on iOS and React Native.

| `code` | When it happens | Fix |
|---|---|---|
| `NOT_CONFIGURED` | Any call before `initialize` — including `VeyraSdk.softpos` / `VeyraSdk.wallet` before that member SDK's `initialize`, and a cold process the app has not initialised yet | Initialise the SDK first, at app start, and use the instance `initialize` returns. |
| `NOT_SIGNED_IN` | No customer is signed in: a call that needs the customer's data (cards, payments, the stored merchant, transactions) after `signOut()`, or before `initialize` named a customer | Call `initialize` with the customer before using the SDK. See [Customers](#customers-signing-in-signing-out-switching). |
| `INVALID_CONFIGURATION` | An unusable provider (none, one that implements both kinds, a `providerType` that doesn't match the interface, a blank `clientId`); a provider of a different kind from an earlier `initialize`; a blank `customerId`; missing required config (environment, payment app provider ID, token requestor ID, recommendation standard version); both SDKs initialised without the `VeyraSdk` facade | Fix the configuration. The message names the exact problem. See [Choosing a provider](#choosing-a-provider) and [Exclusive mode](#exclusive-mode-combined-apps). |
| `SESSION_REQUIRED` | A payment call made without its screen session | Declared on Android for parity; in practice only React Native raises it. The native SDK follows the screen lifecycle itself. |
| `UNSUPPORTED_ON_PLATFORM` | A method this platform can't perform | Declared on Android for parity; it is raised only for a method a platform cannot perform (for example wallet tap-to-pay on iOS). |

```kotlin
try {
    val sdk = VeyraWalletSdk.initialize(context, customerId, config, provider, activity)
    // ... use sdk
} catch (e: VeyraSetupException) {
    when (e.code) {
        VeyraSetupCode.NOT_SIGNED_IN -> goToSignIn()                 // e.g. after signOut()
        else -> {
            Log.e("Veyra", "SDK setup error ${e.code}: ${e.message}")  // a bug to fix, not a user error
            throw e                                                    // let it fail loudly in development
        }
    }
}
```

Two conditions that used to throw are now outcomes, because they can happen in a correctly built app:
the other mode's payment being in flight (combined apps) ends `NOT_SENT` / `MODE_REFUSED`, and a
payment for a merchant that is not registered ends `NOT_SENT` / `MISSING_MANDATORY_CONFIG` (see
[Table A](#table-a--outcomes-the-sdk-itself-produces)).

**Cancelling is not an error.** When you cancel a call (`cancelPayment` / `cancelDigitise`, see
[Cancelling a call](#cancelling-a-call)), the `CancelResult` — including "too late"
(`TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT`, `TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED`) — is the
answer to the cancel, not an error and not an outcome of the call you tried to cancel.

### 2. Processing outcomes (the response triple)

**What they mean.** The SDK ran the call. The outcome tells you what happened and, through the
status, whether to retry and with which order ID.

**How to use them.**

- **Branch on `responseStatus` first, then on `responseStatusReason`.**
- Use `responseCode` only for receipts, disputes and support.
- Never branch on the message text. It is for display and logs.
- Treat a reason you don't recognise as a failure you can't classify: show the message. Never treat
  it as a parse error. New reasons are added without notice, and `responseStatusReason` is a plain
  `String` so that a new one always reaches you.

#### Where the triple lives on Android

The three fields have the same meaning everywhere. A few result types name them differently:

| Call | Result type | Code | Status | Reason |
|---|---|---|---|---|
| `cardPaymentService.makeCardPayment(...)` (tap) | `TransactionResponse` | `transactionCode` | `responseStatus` (`ResponseStatus`) | `responseStatusReason` |
| `cpmCustomerQrService.charge(...)` (customer QR) | `PaymentResponse` | `responseCode` | `responseStatus` (`ResponseStatus`) | `responseStatusReason` |
| `cpmCustomerQrService.inspect(...)` | `CpmInspection` | — | `responseStatus` | `responseStatusReason` |
| `ContextPaymentClient.createContextPayment(...)` (merchant QR) | `CreatedPaymentContext` | — | `responseStatus` | `responseStatusReason` |
| `contextStatus(txRef)` (merchant QR) | `PaymentContextStatusResult` → `status` (`PaymentContextStatus`) | `responseCode` | `responseStatus` | `responseStatusReason` |
| `merchantService.registerPersonalMerchant` / `registerBusinessMerchant` | `MerchantRegistrationResponse` | — | `responseStatus` | `responseStatusReason` |
| `merchantService.activate` / `deactivate` | `MerchantStatusResponse` | — | `responseStatus` | `responseStatusReason` |
| `merchantService.updateMerchant` | `MerchantUpdateResponse` | — | `responseStatus` | `responseStatusReason` |
| `merchantService.getBanks` | `NubanBankListResponse` | — | `responseStatus` | `responseStatusReason` |
| `transactionService.getTransaction` / `getLastTransactions` | `TransactionInfo` | `responseCode` | `transactionStatus` | `responseStatusReason` |
| `transactionService.refreshTransactionStatus` / `refreshCreditConfirmation` | `TransactionRefreshResult` | (on `transaction`) | (on `transaction`) | `failureReason` when the check could not be made |
| `transactionService.paymentStatus(merchantOrderId)` (look up an order) | `OrderPaymentLookup` → `value` (`OrderPaymentStatus`) | `responseCode` (the deciding attempt's) | `responseStatus` (the **order's**) | `responseStatusReason`; `failureReason` on the lookup when the gateway could not be asked |
| `TransactionResolvedObserver.onTransactionResolved` | the resolution | `responseCode` | `status` | `reason` |
| `wallet.payScannedContext(ctx)` (scan to pay) | `PaymentOutcome` | `responseCode` | `responseStatus` | `responseStatusReason` |
| `wallet.showQrToPay(...)` | `CpmPaymentQr` | — | `responseStatus` | `responseStatusReason` |
| `getTransactions(...)` (wallet history) | `TransactionSummary` | `responseCode` | `authorizationStatus` | `responseStatusReason` |
| `wallet.refreshTransactionStatus` / `refreshCreditConfirmation` | `VeyraLookup<TransactionSummary>` | (on `value`) | (on `value`) | `failureReason` when the check could not be made |
| `digitise(...)` (add card) | `DigitiseResult` | add-card decision | `responseStatus` | `responseStatusReason` |
| `verifyAccount(...)` | `VerifyAccountResponse` | issuer decision | `responseStatus` | `responseStatusReason` |
| wallet `getBanks(...)` | `BankList` | — | `responseStatus` | `responseStatusReason` |
| `requestActivationCode(...)` / `activate(...)` | `ActivationCodeResponse` / `ActivateResponse` | — | `responseStatus` | `responseStatusReason` (= the `failureCode` on `FAILED`) |
| `tokenStatus(...)` | `TokenStatusResult` | — | `responseStatus` | `responseStatusReason` |
| `deactivateToken(...)` | `TokenStatusUpdateResponse` | — | `responseStatus` | `responseStatusReason` |
| `setActiveToken(...)` | `CardSelection` | — | `responseStatus` | `responseStatusReason` |
| `processReceipt(...)` | `ReceiptScanResult` | — | `responseStatus` | `responseStatusReason` |
| `reconcilePendingTransactions(...)` | `ReconcileResult` | — | — (a read) | `failureReason` when the reconcile could not run |

Status values are plain strings on every type except the two SoftPOS payment types, which use the
`ResponseStatus` enum (it gained `NOT_SENT` and `RETRY`; `isFinal` is true for every status except
`PENDING` and `Unknown`). Compare strings with the constants in
`co.veyra.common.outcome.OutcomeStatus` (`APPROVED`, `DECLINED`, `FAILED`, `PENDING`, `RETRY`, `NOT_SENT`); the
reasons the SDK itself originates are constants in `OutcomeReason`.

On `DigitiseResult` and `VerifyAccountResponse`, `responseCode` is **not** an ISO payment code:
it is the add-card decision (`APPROVED` / `APPROVE_REQUIRE_AUTH` / `DECLINED` — see
[Table C](#table-c--veyra-token-service-wallet-add-card-activation-cards)). The activation calls also
keep a typed `failureCode` (see [Activation](#activation--responsestatus--failurecode)).

**Two things are deliberately not outcomes.** The wallet tap's `WalletTapEvent.TransactionCompleted`
(`APPROVED` / `DECLINED` / `ERROR`) is a card-reading event — no call reaches Veyra; the payment's
outcome arrives later on the history row and through `observeTransactionResolved` (see
[Wallet tap](#wallet-tap--transactioncompleted)). And the status checks
(`refreshTransactionStatus` / `refreshCreditConfirmation`, and the order lookup `paymentStatus`) state
**no** status of their own when they cannot be made — the status there is the payment's (or the
order's) — so they return the unchanged row, or what the phone holds, with a `failureReason` instead.

#### The six statuses

| `responseStatus` | What it means | Did the request reach Veyra? | Merchant order ID reusable? (payments) | What your app does |
|---|---|---|---|---|
| `APPROVED` | It was done | Yes | **No** — the order is paid | Complete the sale / show the card |
| `DECLINED` | It was done, and someone with authority refused (issuer, bank, risk rules). No money moved | Yes | Yes | Show the reason. Don't retry automatically; the customer may try another card or method under the **same** order ID |
| `FAILED` | Veyra received it and didn't perform it, and retrying with the same reference won't help. For payments that is mainly `94`: the order is already paid or possibly paid | Yes | Yes, except after `94` | Fix what the reason names. After `94`, don't take another payment for the order |
| `PENDING` | It may have been done; the outcome is not known yet | Maybe | **No** — it might be paid | **Never retry.** The SDK keeps checking and tells you when it's final (`onTransactionResolved`); you can also ask with `refreshTransactionStatus(merchantTransactionReference)` |
| `RETRY` | It reached Veyra, but the payment was never sent to the bank. Nothing was charged | Yes | Yes | Fix what the reason names, then retry with the **same** order ID |
| `NOT_SENT` | **Payments and add card only.** It never reached Veyra (the phone stopped it). Nothing was done | No | Yes | Fix what the reason names (connect, authenticate, …), then retry with the **same** order ID |

**`NOT_SENT` is only for payment processing and add card (digitisation).** The calls that can answer
it: the card tap (`makeCardPayment`), the customer-QR charge (`cpmCustomerQrService.charge`), creating
a payment QR (`createContextPayment`), scan to pay (`payScannedContext`), show QR to pay
(`showQrToPay`) and add card (`digitise`). **Every other call answers `FAILED` with the same reason**
when it stops on the phone or never reaches Veyra — merchant registration, activate / deactivate /
update / status and settlement banks, wallet banks, account eligibility, activation code / activate,
token status, card removal (`deactivateToken`), customer-QR inspection (`inspect`), receipt scan
(`processReceipt`) and card selection (`setActiveToken`). The reasons keep their names
(`NO_NETWORK_CONNECTION`, `SDK_UNABLE_TO_CONNECT`, `NOT_AUTHENTICATED`, `INVALID_REQUEST`,
`MISSING_MANDATORY_CONFIG`, …); only the status differs. `NOT_SENT` exists to tell you a payment or an
add card never happened, so its reference is still unused; a call with no reference to protect has
nothing to gain from it, and a call checked on the phone (inspection, receipt scan, card selection)
never sends anything at all.

**`NOT_SENT` and `RETRY` are handled the same way.** In both, nothing was charged and the retry
carries the same order ID. The only difference is where the attempt stopped, and the reason tells
you: `NOT_SENT` stopped on the phone (`NO_NETWORK_CONNECTION`, `CARD_READ_FAILED`, …); `RETRY`
stopped inside Veyra, before the bank was asked (`NAME_ENQUIRY_FAILED`,
`ISSUER_SWITCH_NOT_AVAILABLE`, …). A `RETRY` attempt carries the gateway's `responseCode` and is
recorded in history; a `NOT_SENT` one has no code and no history row.

Five rules follow from this table:

- **`NOT_SENT` never has a `responseCode`.** A code only ever means something on the network
  answered, or failed to.
- **`PENDING` is never a failure, and it is now the only status where the outcome is unknown.** Never
  show it as failed and never charge again. Show "We're checking this payment" and wait for the SDK's
  update. See [Holding a `PENDING` payment](#holding-a-pending-payment-and-being-told-when-it-settles).
- **`RETRY` is final.** The SDK never polls a `RETRY` attempt — there is nothing to wait for.
- **Add a card (digitisation) is different.** There is no reference and no lookup. A digitisation
  that gets no answer ends `FAILED`, never `PENDING`, because the card's keys can never be delivered
  later. Start a new digitisation.
- **Reads are never `PENDING`.** Bank lists, account eligibility, token status and QR inspection
  change nothing, so any no-answer — sent or not — is `FAILED` (`NO_RESPONSE_RECEIVED`,
  `UPSTREAM_ERROR`, `NO_NETWORK_CONNECTION`, …), and retrying as is is always safe. The status
  checks (`refreshTransactionStatus`, `refreshCreditConfirmation`, `reconcilePendingTransactions`,
  `paymentStatus`) state no status at all — only a `failureReason`.

`RETRY` applies to payments only. Calls that are not payments (merchant registration, add card,
activation, bank lookups, QR creation) have no order to retry, so a refusal there stays `FAILED`.

#### The retry rule

| Outcome | Retry? | With which merchant order ID? |
|---|---|---|
| `NOT_SENT` (payments, add card) | Yes, once the reason is fixed (connect, authenticate, tap again…) | The **same** one |
| `RETRY` | Yes, once the reason is fixed (or shortly, for `ISSUER_SWITCH_NOT_AVAILABLE`) | The **same** one |
| `FAILED` | Only after fixing what the reason names. Never after `94` | The **same** one (after `94` the order is already paid or still being confirmed) |
| `DECLINED` | Not automatically; another card or method is the customer's choice | The **same** one |
| `PENDING` | **Never** — wait for the SDK to resolve it | — |
| `APPROVED` | — | The order is paid; a new sale gets a new one |

The "same reference" rule is about payments (and add card, which starts again). A call that is not
a payment and ends `FAILED` — no network, not authenticated, an invalid argument — has no reference
to keep: fix what the reason names and call it again.

Add card has no order ID: a `FAILED` or `NOT_SENT` digitisation is simply started again. The sample
shows the pattern in `GetPaidActivity`: it keeps the sale's order ID after an attempt ends `NOT_SENT`,
`RETRY`, `DECLINED` or `FAILED` and offers **Try again** on the same order, drops it after `APPROVED` or
`PENDING` (or a status it doesn't know), and never offers a retry for `PENDING` or a `94`. See
[Retrying a payment safely](#retrying-a-payment-safely).

#### The merchant order ID

`merchantOrderId` is **your** id for the sale: the order, basket or invoice in your POS. The SDK mints
a new `merchantTransactionReference` for every attempt; the order ID is what ties the attempts of one
sale together.

- **An order can have several attempts.** A tap that ends `RETRY`, then a second tap that is
  approved, are two attempts of one order.
- **The same order ID can be used again until the order is paid or possibly paid.** The gateway
  refuses a new attempt — `94` / `FAILED` / `DUPLICATE_MERCHANT_ORDER_ID` — only when the order is
  `APPROVED` or `PENDING` (including an attempt still in flight). That refusal is what stops a double
  charge.
- **After `DECLINED`, `FAILED`, `RETRY` or `NOT_SENT`, try again under the same order ID** — another
  card after a decline; fix the reason after `FAILED`, `RETRY` or `NOT_SENT`.
- **On a `94`, read `originalPayment`.** It is the attempt that holds the order:
  `originalPayment.responseStatus` `APPROVED` means the order is already paid; `PENDING` means a
  payment for it is still being confirmed. Either way, don't take another payment for it. The SDK's
  `message` says which.

##### An order's status comes from all its attempts

When an order has several attempts, Veyra decides the order's status with a fixed precedence, never
"the latest attempt wins" — a later attempt must never hide a payment that went through or might
have:

| Rank | Rule | The order is… |
|---|---|---|
| 1 | any attempt `APPROVED` | `APPROVED` — paid |
| 2 | otherwise, any attempt `PENDING` (including one still in flight) | `PENDING` — might be paid |
| 3 | otherwise, the **latest** attempt | that attempt's status: `DECLINED`, `FAILED` or `RETRY` |
| 4 | no attempts | not found: `25` / `RETRY` / `UNABLE_TO_LOCATE_RECORD` — nothing was charged, the order can be paid |

The attempt that sets the order's status is the **deciding attempt**: the approved one, else the
pending one, else the latest. It is the attempt the gateway returns as `originalPayment` on a `94`,
and the rule is exactly when a new attempt is refused: ranks 1 and 2 refuse it, ranks 3 and 4 allow
it. A `RETRY` order means "nothing was debited; this order can be paid again".

Your own history shows the attempts: every attempt that reached Veyra is a row in
`getLastTransactions()` — `RETRY` ones included — carrying the order ID in `merchantOrderId`. Group
the rows by `merchantOrderId` to show a sale with all its tries, and apply the table above to the
group to label the sale. To have the SDK apply it — including attempts this phone never recorded —
call `transactionService.paymentStatus(merchantOrderId)`; see
[Looking up a payment](#looking-up-a-payment).

#### Handling an outcome in code

Branch on the status first — it decides what happens next — and only then on the reason, for the
words and for the few reasons that need their own UI:

```kotlin
import co.veyra.common.outcome.OutcomeReason
import co.veyra.softpos.payment.sdk.payment.ResponseStatus

// Your POS order id for the sale on screen: one per sale, kept across every attempt.
// The gateway refuses a reused one only while the order is APPROVED or PENDING, so reusing it is
// always safe — the worst case is a 94 that tells you the order is already paid.
private var saleOrderId: String? = null

fun startNewSale() { saleOrderId = newOrderIdFromYourPos() }

fun charge(amountMinor: Long) {
    val request = TransactionRequest.Builder(amountMinor, "0566", saleOrderId!!).build()
    sdk.cardPaymentService.makeCardPayment(request, callback = { r -> runOnUiThread { onOutcome(r) } })
}

fun onOutcome(response: TransactionResponse) {
    when (response.responseStatus) {
        ResponseStatus.APPROVED -> showApproved(response.merchantTransactionReference)   // order paid
        ResponseStatus.DECLINED -> showDeclined(response.message)       // another card, same order id
        ResponseStatus.FAILED -> when (response.responseStatusReason) {
            "DUPLICATE_MERCHANT_ORDER_ID" ->                              // don't take another payment
                if (response.originalPayment?.responseStatus == "PENDING") showStillConfirming()
                else showAlreadyPaid()
            else -> showFailed(response.message, retry = true)            // fix the reason; same order id
        }
        // Nothing was charged. NOT_SENT stopped on the phone, RETRY inside Veyra before the bank.
        // Same handling: say what to fix, retry with the SAME order id.
        ResponseStatus.NOT_SENT, ResponseStatus.RETRY -> when (response.responseStatusReason) {
            OutcomeReason.CANCELLED -> return                             // your own cancel — not a result
            OutcomeReason.NO_NETWORK_CONNECTION -> showNotCharged("Connect to the internet", retry = true)
            OutcomeReason.CARD_READ_FAILED -> showNotCharged("Ask the customer to tap again", retry = true)
            "MODE_REFUSED" -> showNotCharged("Finish the other payment first", retry = true)
            "NAME_ENQUIRY_FAILED", "ACCOUNT_VALIDATION_FAILED" ->
                showNotCharged("Check the merchant's account details, then try again", retry = true)
            "ISSUER_SWITCH_NOT_AVAILABLE" -> showNotCharged("The bank can't be reached — try again shortly", retry = true)
            else -> showNotCharged(response.message, retry = true)
        }
        // PENDING, Unknown (a status newer than this build) or null (an old backend): unresolved.
        // Never retry; the SDK resolves it and onTransactionResolved tells you.
        else -> showPending(response.merchantTransactionReference)
    }
}
```

`response.message` already carries the right sentence for each status — for `RETRY` it is
"Payment not sent to the bank - retry with the same order ID", followed by the reason.

Every other call follows the same pattern with its own result type (see [Where the triple lives](#where-the-triple-lives-on-android)); the
non-payment results carry the status as a `String`, so compare with `OutcomeStatus.*`.

#### Worked example: `RETRY`, then approved under the same order ID

1. The merchant keys ₦325.00 for order `ORDER-42` and the customer taps. Veyra checks the
   merchant's account before asking the bank for the debit, and that check fails. The callback
   gets `responseStatus = RETRY`, `responseStatusReason = "NAME_ENQUIRY_FAILED"`,
   `transactionCode = "21"` and the reference `T1-…001`. Nothing was charged.
2. Your app shows "Payment not sent to the bank — try again", keeps `saleOrderId = "ORDER-42"`, and
   offers **Try again**. The attempt is in `getLastTransactions()` as a `RETRY` row for `ORDER-42`.
   No status check is needed: `RETRY` is final.
3. The merchant taps **Try again**; the app builds a new `TransactionRequest` with the **same**
   `merchantOrderId = "ORDER-42"`. The SDK mints a new reference, `T1-…002`. The order's status was
   `RETRY` (rank 3), so the gateway accepts the new attempt.
4. The second attempt is approved: `APPROVED` / `00`. History now has two rows for `ORDER-42` —
   `RETRY` (`T1-…001`) and `APPROVED` (`T1-…002`) — and the order is `APPROVED` (rank 1). Any further
   attempt under `ORDER-42` would be refused `94`, with `T1-…002` as `originalPayment`.

#### Table A — Outcomes the SDK itself produces

Rows marked `NOT_SENT` or `FAILED` depend on the call: a payment or an add card answers `NOT_SENT`;
every other call answers `FAILED` with the same reason (see [The six statuses](#the-six-statuses)).
The remaining `NOT_SENT` rows are payment-only reasons.

| Status | Code | Reason | When | What your app does |
|---|---|---|---|---|
| `NOT_SENT` or `FAILED` | — | `INVALID_REQUEST` | An argument is invalid (e.g. blank merchant order ID, amount ≤ 0, a missing digitisation field) | Fix the call |
| `NOT_SENT` or `FAILED` | — | `NO_NETWORK_CONNECTION` | The phone has no internet connection | Ask the user to connect; retry (a payment with the same order ID) |
| `NOT_SENT` or `FAILED` | — | `SDK_UNABLE_TO_CONNECT` | The phone is online but could not connect to Veyra | Try again shortly (a payment with the same order ID) |
| `NOT_SENT` or `FAILED` | — | `NOT_AUTHENTICATED` | The SDK could not get credentials (your assertion or proxy provider returned nothing, or the token endpoint refused) | Check your provider; retry |
| `NOT_SENT` | — | `CANCELLED` | Your app cancelled the call before it was sent (`cancelPayment` / `cancelDigitise` answered `CANCELLED`; see [Cancelling a call](#cancelling-a-call)) | Back to the previous screen; the same order ID / `clientRequestId` can be used again |
| `NOT_SENT` | — | `AUTH_CANCELLED` | The customer dismissed the device authentication sheet | Stay on the confirm screen; let them try again |
| `NOT_SENT` | — | `AUTH_FAILED` | Device authentication did not succeed | Stay on the confirm screen |
| `NOT_SENT` | — | `AUTH_UNAVAILABLE` | The device has no screen lock and no biometrics | Send the customer to Settings |
| `NOT_SENT` | — | `ONLINE_REQUIRED` | The card has run out of payment keys | Ask the user to go online; the SDK refreshes the card |
| `NOT_SENT` | — | `TOKEN_NOT_ACTIVE` | The card is not active (e.g. suspended by the issuer) | Show the card's state |
| `NOT_SENT` | — | `AMOUNT_EXCEEDS_CARD_LIMIT` | The amount is larger than this card can pay in one go | Offer a smaller amount or another card |
| `NOT_SENT` | — | `DEVICE_NOT_BOUND` | The card was added on another device | Remove the card and add it again on this phone |
| `NOT_SENT` | — | `MERCHANT_NOT_ACTIVE` | The merchant account is not active | Contact support |
| `NOT_SENT` | — | `MERCHANT_PROFILE_INCOMPLETE` | The merchant's account details are missing | Finish merchant setup |
| `NOT_SENT` or `FAILED` | — | `MISSING_MANDATORY_CONFIG` | The merchant is not registered, or terminal / merchant config is missing | Register the merchant / pass the config |
| `NOT_SENT` | — | `MODE_REFUSED` | Combined apps: the other mode's payment is in flight (e.g. `makeCardPayment` during a wallet payment) | Wait for it to finish, or prompt the user to finish or cancel it |
| `NOT_SENT` | — | `CARD_READ_FAILED` | Card tap: the card could not be read, so the payment ended before anything was sent | Ask the customer to tap again |
| `NOT_SENT` | — | `TRANSACTION_IN_PROGRESS` | Card tap: another payment is already armed or in flight on this device | Finish or cancel it first |
| `NOT_SENT` | — | `NO_ACTIVE_CARD` | Wallet: no card is set as active (e.g. show QR to pay) | Ask the user to choose a card |
| `NOT_SENT` | — | `CARD_CANNOT_SHOW_QR` | Wallet: the active card can't produce a QR | Use another card, or tap |
| `NOT_SENT` | — | `CARD_CANNOT_PAY` | Wallet scan to pay: the active card's material can't make a payment | Remove the card and add it again |
| `FAILED` | — | `NO_RESPONSE_RECEIVED` / `UPSTREAM_ERROR` | **Reads only** (bank lists, eligibility, token status): sent, no usable answer | Retry as is — a read changes nothing |
| `NOT_SENT` or `FAILED` | — | `SDK_INTERNAL_ERROR` | The SDK failed before sending | Report to support; retry is safe |
| `FAILED` | — | `CARD_NOT_FOUND` | Card selection (`setActiveToken`): no card with that id is in this wallet | Refresh the card list |
| `FAILED` | — | `CARD_DATA_MISSING` | Card selection (`setActiveToken`): the card's payment data is not on this phone | Remove the card and add it again |
| `FAILED` | — | `NFC_DISABLED` / `NFC_UNAVAILABLE` / `MODE_REFUSED` / `TAP_ROUTING_REFUSED` / `SCREEN_NOT_ACTIVE` | Opening a pay session (`openPaySession`): tap-to-pay could not be armed — see [the pay session table](#arming-and-disarming-tap-to-pay-pay-session) | Per that table; nothing changed |
| `FAILED` | — | `INVALID_RECEIPT` / `RECEIPT_NOT_FROM_THIS_WALLET` / `RECEIPT_MISMATCH` / `RECEIPT_FOR_ANOTHER_TRANSACTION` | Receipt scan (`processReceipt`): the receipt was refused — see [`processReceipt`](#processreceipt--getreceipts--getreceiptfortransaction) | Show the reason |
| `FAILED` | — | `PAYMENT_NOT_CONFIRMED` | Receipt scan: the matching payment's outcome is not confirmed yet | Scan again shortly |
| `PENDING` | `68` | `NO_RESPONSE_RECEIVED` | The request was sent but no answer came back (timeout, connection lost) | Wait; the SDK resolves it |
| `PENDING` | `06` | `UPSTREAM_ERROR` | Veyra answered with an error or something unreadable | Wait; the SDK resolves it |
| `PENDING` | — | `SDK_INTERNAL_ERROR` | The SDK failed after sending | Wait; the SDK still resolves the payment |
| `FAILED` | — | `NO_RESPONSE_RECEIVED` / `UPSTREAM_ERROR` | **Add card only**: no answer after sending | Start a new digitisation |
| `FAILED` | — | `UNRECOGNISED_RESPONSE_CODE` | **Add card only**: the Veyra token service answered with a code this SDK version doesn't know; the card was discarded, nothing stored | Start a new add card; update the SDK if it persists |

#### The triple is the same on every Veyra service

The triple is not payment-only. Every Veyra service the SDK talks to — the Veyra payment gateway, the
Veyra token service, and through them the switch and the issuer's gateway — answers with the same
fields: `responseStatus` (`APPROVED` / `DECLINED` / `FAILED` / `PENDING`, and `RETRY` on payments) and `responseStatusReason`,
plus a human `message`. **Payments** also carry `responseCode`, an ISO 8583-style code. **Add card**
carries its own decision in `responseCode`: `APPROVED`, `APPROVE_REQUIRE_AUTH` or `DECLINED` (not an
ISO number). Other non-payment calls carry no `responseCode`. The SDK passes all of these to your app
unchanged.

#### Table B — Payments (Veyra payment gateway; codes from the bank, the issuer's gateway and the switch)

One code always means one reason.

| Code | Status | Reason | Meaning | What your app does |
|---|---|---|---|---|
| `00` | `APPROVED` | `APPROVED` | Approved | Complete |
| `05` | `DECLINED` | `DO_NOT_HONOR` | The bank refused without a specific reason | Show it; another payment method |
| `07` | `RETRY` | `ACCOUNT_VALIDATION_FAILED` | The merchant's (beneficiary) account failed validation before the debit was requested; nothing was charged | Check the merchant's account details, then retry with the same order ID |
| `14` | `DECLINED` | `INVALID_TOKEN` | The card token is not valid | The customer should re-add the card |
| `21` | `RETRY` | `NAME_ENQUIRY_FAILED` | The merchant's (beneficiary) account lookup could not be completed; the debit was never requested | Check the merchant's account details, then retry with the same order ID |
| `51` | `DECLINED` | `INSUFFICIENT_FUNDS` | Not enough funds | Another payment method |
| `54` | `DECLINED` | `EXPIRED_CARD_OR_TOKEN` | The card or token has expired | Re-add the card |
| `58` | `DECLINED` | `DOMAIN_RESTRICTION_FAILED` | The card can't be used this way (wrong channel / domain) | Another payment method |
| `61` | `DECLINED` | `LIMIT_EXCEEDED` | An amount limit was exceeded | Smaller amount |
| `63` | `DECLINED` | `SUSPECTED_FRAUD` | Refused by risk checks | Don't retry |
| `65` | `DECLINED` | `VELOCITY_LIMIT_EXCEEDED` | Too many payments in a short time | Try later |
| `09` | `PENDING` | `TRANSACTION_IN_PROCESS` | Still being processed | Wait |
| `09` | `PENDING` | `TRANSACTION_IN_PROCESS_ESCALATED` | A person is reconciling it | Show "we're looking into this"; the SDK re-checks less often |
| `68` | `PENDING` | `NO_RESPONSE_RECEIVED` | A hop timed out | Wait |
| `06` | `PENDING` | `UPSTREAM_ERROR` | A hop answered with an error | Wait |
| `96` | `PENDING` | `SYSTEM_MALFUNCTION` | The Veyra payment gateway hit an internal error while processing | Wait; the SDK resolves it |
| `12` | `RETRY` | `QR_EXPIRED` | QR payments: the payment code expired before the debit was requested | Show a new QR for the same order ID |
| `13` | `RETRY` | `AMOUNT_MISMATCH` | QR payments: amount or currency doesn't match the code | Show a new QR for the same order ID |
| `25` | `RETRY` | `UNABLE_TO_LOCATE_RECORD` | A status lookup found no such payment | Nothing was charged; the order can be paid (same order ID) |
| `30` | `RETRY` | `INVALID_REQUEST` | The gateway refused the request before sending it on (validation, caller or device checks) | Fix the request, then retry with the same order ID |
| `91` | `RETRY` | `ISSUER_SWITCH_NOT_AVAILABLE` | Veyra could not reach the bank, so the debit was never requested | Try again shortly with the same order ID |
| `94` | `FAILED` | `DUPLICATE_MERCHANT_ORDER_ID` | The order is already paid or possibly paid: an attempt for this order ID is `APPROVED` or `PENDING`. `originalPayment` carries that attempt | Do not take another payment for the order. `originalPayment.responseStatus` `PENDING`: still being confirmed; otherwise already paid. The SDK's `message` says which |

`RETRY` keeps the code and reason the stopping point gave; only the status says "nothing was sent to
the bank". A QR payment whose code expired or doesn't match is always `12` / `QR_EXPIRED` or
`13` / `AMOUNT_MISMATCH`, wherever it was caught; branch on the status and reason. After a `RETRY` on a merchant QR, the QR goes back to unpaid
(`PENDING`) and stays payable until it expires.

**What `FAILED` means for payments now:** Veyra received the request and didn't perform it, and
retrying with the same reference won't help. For payments that is mainly `94`. A `FAILED` payment
doesn't use up the order ID; after `94`, the order is already paid or still being confirmed, so there
is nothing to retry.

Payment status lookup (`refreshTransactionStatus` in either SDK, or the stored row by reference)
returns the stored payment's triple from this table, or `25` / `RETRY` / `UNABLE_TO_LOCATE_RECORD`
(no such payment: nothing was charged, and the order can be paid).
If the lookup itself fails, the result carries a `failureReason` beside the unchanged row and the SDK
keeps asking in the background — a failed lookup is never the payment's outcome.

#### Table C — Veyra token service (wallet: add card, activation, cards)

Wallet SDK only, grouped by call.

| Call | Status | Code | Reason | Meaning / what your app does |
|---|---|---|---|---|
| Add card (`digitise`) | `APPROVED` | `APPROVED` | — | The card is added and active |
| Add card | `APPROVED` | `APPROVE_REQUIRE_AUTH` | — (or why step-up is needed) | Added, but the customer must activate it; `activationMethods` lists how |
| Add card | `DECLINED` | `DECLINED` | The issuer's reason | The issuer refused this account; show the reason (see [the issuer's causes](#add-a-card--the-issuers-decision-and-causes)) |
| Add card | `FAILED` | — | `INTERNAL_ERROR` | The Veyra token service could not complete it; start a new add card |
| Add card | `FAILED` | — | `NO_RESPONSE_RECEIVED` / `UPSTREAM_ERROR` | No answer (Table A); start a new add card |
| Add card | `FAILED` | any other value | `UNRECOGNISED_RESPONSE_CODE` | A code this SDK version doesn't know; the card was discarded (Table A). Start a new add card |
| Activation code request (`requestActivationCode`) | `FAILED` | — | `TOKEN_NOT_FOUND`, `ACTIVATION_LOCKED`, `CODE_REQUEST_RATE_LIMITED`, `ACTIVATION_FAILED` | A code can't be sent now. Locked or rate-limited: wait, or contact the issuer |
| Activation code verify (`activate`) | `FAILED` | — | `CODE_INVALID`, `CODE_EXPIRED`, `MAX_ATTEMPTS_EXCEEDED`, `NO_PENDING_ACTIVATION`, `TOKEN_NOT_ACTIVATABLE`, `ACTIVATION_LOCKED`, `TOKEN_NOT_FOUND`, `ACTIVATION_FAILED` | Wrong or expired code: ask again (`attemptsRemaining` is included). Max attempts or locked: remove the card and add it again |
| Remove card (`deactivateToken`) | `FAILED` | — | `TOKEN_NOT_FOUND`, `TOKEN_STATE_CONFLICT` | The card isn't known, or the change isn't allowed from its current state |
| Bank lookup (`getBanks`, `verifyAccount`) | `FAILED` | — | `INVALID_ACCOUNT_NUMBER` | Check the account number |

**Activation on Android.** `ActivationCodeResponse` and `ActivateResponse` carry the outcome in
`responseStatus` / `responseStatusReason` like every other call; on `FAILED` the reason is the failure
code, which is also available typed as `failureCode` (raw value in `failureCodeRaw`) — see
[Activation](#activation--responsestatus--failurecode). When nothing was sent they end `FAILED` with
the reason (e.g. `NO_NETWORK_CONNECTION`) and `failureCode == null` — they are not payments, so never
`NOT_SENT`.

The SDK refreshes a card's payment keys itself; there is no call for it. A refresh refused because
the card is expired or inactive shows up as the card's state (`Token.isActive`,
`Token.requiresOnline`, `Token.deviceNotBound`), never as a result your app receives. An expired card
is never refreshed — add it again.

#### Table D — Veyra payment gateway, non-payment calls (SoftPOS: merchant and QR codes)

SoftPOS SDK only. No `responseCode`. A refusal by the gateway is `FAILED`; the same field checks made
on the phone before sending (a blank merchant name, a missing CAC number for a business, …) also end
`FAILED`, with the same reason name — these calls are not payments, so they never answer `NOT_SENT`. A state change (`activate`, `deactivate`, `updateMerchant`,
registration) that was sent but got no usable answer ends `PENDING` — read the merchant's status
before trying again. A merchant call with no stored merchant ends `FAILED` / `MISSING_MANDATORY_CONFIG`.

| Call | Reasons | What your app does |
|---|---|---|
| Merchant registration (`registerPersonalMerchant` / `registerBusinessMerchant`) | `MERCHANT_TYPE_REQUIRED`, `MERCHANT_NAME_REQUIRED`, `EMAIL_ADDRESS_REQUIRED`, `PHONE_NUMBER_REQUIRED`, `ACCOUNT_NUMBER_REQUIRED`, `ADDRESS_LINE_1_REQUIRED`, `CITY_REQUIRED`, `BANK_INSTITUTION_CODE_REQUIRED`, `PAYMENT_APP_PROVIDER_ID_REQUIRED`, `CAC_NUMBER_REQUIRED` | Ask for the missing field |
| Merchant update / status / activate / deactivate (`updateMerchant`, `refreshStatus`, `activate`, `deactivate`) | `MERCHANT_ID_REQUIRED`, `MERCHANT_NOT_FOUND`, `INVALID_REQUEST` (the change is not allowed from the current state) | Fix the request / check the merchant's state |
| QR payment code creation (`createContextPayment`) | `AMOUNT_REQUIRED`, `MERCHANT_NOT_FOUND`, `INVALID_REQUEST`, `DUPLICATE_MERCHANT_ORDER_ID` (includes the original payment) | Fix the request and create it again with the same order ID. A duplicate means the order is already paid or still being confirmed — don't take another payment for it |
| Bank lookup (merchant `getBanks`) | `INVALID_ACCOUNT_NUMBER` | Check the account number |

#### Generic fallbacks (any Veyra service, any call)

`INVALID_REQUEST`, `NOT_FOUND`, `INTERNAL_ERROR` and `AUTHENTICATION_FAILURE`, with status `FAILED` —
used when nothing more specific applies.

#### Where the codes come from

Payment outcomes travel unchanged from the bank, through the issuer's gateway, the switch and the
Veyra payment gateway, to the SDK. No hop re-interprets them, so the code your app sees is the code
the bank gave. The SDK only produces an outcome itself when it got no answer (Table A).

### Handling particular outcomes

#### Holding a `PENDING` payment, and being told when it settles

A payment that gets no answer hands you `responseStatus == PENDING`. **That is not a failure and not
a decline** — the payment may well have completed, so the one thing you must not do is charge again.

1. **Stay on the confirmation screen** and show "processing". Do not navigate away and do not print a
   receipt yet.
2. **Let the SDK resolve it.** It stores the transaction and polls with backoff; you do not have to.
3. **Finish when it settles** — from `onTransactionResolved` (below), or by reading the row with
   `getTransaction(reference)` / `getLastTransactions()`.

A pending row always converges: it becomes `APPROVED`, `DECLINED`, `FAILED` or `RETRY` when the backend
settles it, or it stays `PENDING`. It never turns into an outcome the SDK made up, and there is no
attempt cap that gives up on it.

**`TRANSACTION_IN_PROCESS_ESCALATED`** is the one reason that changes what *you* do. Automated
reconciliation has stopped and a person will settle the payment. Stop any tight loop of your own,
tell the merchant "we're looking into this", and re-check lazily — next app open, or a long backoff.
It will still resolve; it just will not resolve in seconds.

##### `onTransactionResolved` — the SDK pushes the answer

```kotlin
TransactionResolvedObserver.onTransactionResolved { resolution ->
    // resolution.reference    — which payment (you may have more than one pending)
    // resolution.status       — APPROVED / DECLINED / FAILED / RETRY (never PENDING)
    // resolution.reason       — e.g. INSUFFICIENT_FUNDS
    // resolution.responseCode — the code, for receipts and support
}
```

- **Register once, after each `initialize`** — not per payment. A sign-out or a switch to another
  customer drops the registration, so register again after the `initialize` that signs the customer
  in. It fires for *any* transaction that resolves, including one started in an earlier app session
  and settled by a later poll — the case that matters most is a tap that resolves after your app was
  backgrounded or killed.
- **It does not replay.** If your app was not running when the row settled, nothing is queued for
  you — read `getLastTransactions()` at start-up. Keep the read path.
- **Last registration wins.** Calling it again *replaces* the previous observer;
  `TransactionResolvedObserver.clear()` stops it. If two parts of your app want the answer, fan it
  out yourself from one registration.
- **The payment callback still fires exactly once**, possibly with `PENDING`. The resolution arrives
  on this separate channel.
- It is delivered on the main thread, like the payment callback.

The same channel exists on the other platforms with the same semantics: iOS
`VeyraSoftPOS.shared.transactions.onTransactionResolved { … }`, React Native
`merchant.onTransactionResolved(listener)`.

#### Card-read failures — "card not supported, tap again"

A tapped card that cannot be turned into a payment (a foreign card scheme, another phone tapped by
mistake, a card that lost contact) is not a result. **The SDK keeps the reader armed** and fires
`onUnsupportedCard` / `onCardContactLost` instead; the payment callback does not run.

**Give the merchant one message** — "Card not supported — try another card" — and keep the waiting
screen up. Don't write per-cause copy, and never end the sale on one. When a card-read failure does
end the payment, it is `NOT_SENT` / `CARD_READ_FAILED`: nothing was sent, so ask for another tap
and keep the same order ID. The message names the card-level cause for support.

#### Wallet tap — `TransactionCompleted`

When the customer's phone is tapped on a terminal, `observeTapEvents` delivers
`WalletTapEvent.TransactionCompleted`, whose `response.status` is:

| Status | Meaning | What to do |
|---|---|---|
| `"APPROVED"` | The card produced an approval cryptogram for the terminal | Show the in-wallet confirmation. This is the **offline leg** — the authorised outcome lands on the card's history row (`authorizationStatus`, with the full triple) once the SDK resolves it. |
| `"DECLINED"` | The card declined the tap (e.g. terminal risk limits, or a refusal below) | Show the decline. |
| `"ERROR"` | The tap could not be processed | Ask the user to tap again. |

The SDK also vibrates the phone itself — once on success, twice on decline or error — so your UI
feedback is supplementary.

#### Refused payments — `onRequireOnline`, `onAmountExceedCardLimit` and `onDeviceNotBound`

A wallet payment can be refused **before anything is sent**, when the card's payment keys cannot
carry it. On the QR rails the outcome is `NOT_SENT` with reason `ONLINE_REQUIRED`,
`AMOUNT_EXCEEDS_CARD_LIMIT` or `DEVICE_NOT_BOUND`. On the tap rail it ends the tap at the protocol
level, so the tap's `TransactionCompleted` event carries `"DECLINED"`. On every rail these callbacks tell you
*why*, because the advice differs:

| Callback | Meaning | What to tell the payer |
|---|---|---|
| `onRequireOnline` | The card's keys need refreshing and the wallet could not reach the server | "Connect to the internet and try again" — this genuinely fixes it |
| `onAmountExceedCardLimit` | The amount is over the card's per-payment cap | "Pay a smaller amount or use another card". **Never say "go online"** — a refreshed key carries the same cap |
| `onDeviceNotBound` | The backend issues this card's payment keys only to the device it was added on, and this is not that device (or the app was reinstalled or its data wiped) | "Add this card again on this phone". Going online does not help, nor does a smaller amount |

All three carry `amountMinorUnits` and `rail` (`"TAP"`, `"CPM_QR"` or `"MPM_QR"`).
`onAmountExceedCardLimit` also carries `cardLimitMinorUnits` — the cap when the SDK can read it, or
`null`; show the figure only when it is non-null. `onDeviceNotBound` delivers a
`DeviceNotBoundEvent(tokenId, tokenUniqueReference, amountMinorUnits, rail, message)`. Register them
with `wallet.observePaymentRefusals` (below).

**`onDeviceNotBound` is a card state too.** When the SDK's automatic key request is refused this way,
it stops every automatic key request for that card and sets `Token.deviceNotBound`. From then on a
payment on that card is refused before anything is sent, so it is right to grey the card out (see
[Handling card states in your UI](#handling-token-states-in-your-ui)).

**Timing on the tap rail.** `onRequireOnline` arrives at the earliest moment it is actually true:
immediately if the device is already offline, otherwise only after the SDK's automatic background
refresh has failed. If that refresh succeeds — the usual case, within about a second — **nothing
fires** and the next tap simply works. So treat the absence of this callback after a declined tap as
"ask them to tap again".

**These describe the payment, not the card.** `Token.requiresOnline` answers a different question —
"can this card pay *anything* offline?" — and stays `false` for a card that can still make smaller
payments. Don't grey the card out on the strength of one refused amount.

**Refusals are per card.** A handler registered for one card never hears about another's. A refusal
the SDK could not attribute to a card (`tokenUniqueReference` is null) reaches **every** registered
handler. iOS registers with `observePaymentRefusals(forTokenUniqueReference:…)` and React Native with
`wallet.observePaymentRefusals(tokenUniqueReference, listener)`; iOS fires from the QR rails only, having
no tap-to-pay.

##### Registering refusals

Register them with [`observePaymentRefusals`](#observepaymentrefusals) — see the wallet's Observers.

#### Three things end with "get online", and they are not the same

- **`NO_NETWORK_CONNECTION`** (`NOT_SENT` on a payment or add card, `FAILED` on any other call) — the
  *phone* has no connection. Every call fails the same way until the user reconnects. Retrying is
  safe: nothing was sent.
- **`ONLINE_REQUIRED`** (`NOT_SENT`) — the *card* has run out of payment keys. The phone is usually
  online already; the SDK refreshes the card itself, typically within seconds. Don't grey a card out
  on a `NO_NETWORK_CONNECTION` — the card is fine.
- **`91` / `RETRY` / `ISSUER_SWITCH_NOT_AVAILABLE`** — the request reached Veyra, and Veyra could not
  reach the bank, so nothing was charged. The user's connection is not the problem; telling them to
  check it wastes their time. Try again shortly with the same order ID.

### Migrating from 2.x

3.0.0 changes how every call reports what happened. The rules above are the whole model; this is the
checklist for code written against 2.x:

(The wallet names below are the 3.0.0 ones; [Wallet — migrating from 3.0.0](#wallet--migrating-from-300)
maps them to the current calls.)

| 2.x | 3.0.0 |
|---|---|
| `Result<…>` / `onError` / `null` on failure (`getBanks`, `checkAccountEligibility`, `requestActivationCode`, `activate`, `tokenStatus`, `deactivateToken`, merchant `getBanks` / `activate` / `deactivate` / `updateMerchant`, `createContextPayment`) | The callback (or return) is always an outcome type — `BankList`, `VerifyAccountResponse`, `ActivationCodeResponse`, `ActivateResponse`, `TokenStatusResult`, `TokenStatusUpdateResponse`, `NubanBankListResponse`, `MerchantStatusResponse`, `MerchantUpdateResponse`, `CreatedPaymentContext`. Branch on `responseStatus`. |
| `cardPaymentService.cancelPendingPayment()` | Removed: `VeyraSoftPOSSdk.cancelPayment(merchantOrderId)`, which answers a `CancelResult` — see [Cancelling a call](#cancelling-migrating-from-2x) |
| `cpmCustomerQrService.inspect` threw `IllegalArgumentException` | Returns `CpmInspection`; not a payment QR is `FAILED` / `INVALID_REQUEST` |
| `processReceipt` called back `Result<TransactionReceipt>` (failure on a refused receipt) | Calls back (or, `suspend`, returns) `ReceiptScanResult`: `APPROVED` with `receipt`, or `FAILED` with `INVALID_RECEIPT` / `RECEIPT_NOT_FROM_THIS_WALLET` / `RECEIPT_MISMATCH` / `PAYMENT_NOT_CONFIRMED` / `RECEIPT_FOR_ANOTHER_TRANSACTION` / `SDK_INTERNAL_ERROR` |
| `reconcilePendingTransactions` called back `Result<Unit>` | Calls back `ReconcileResult`: `failureReason` set only when the reconcile could not run |
| `setActiveToken` returned nothing (an unknown card was not reported) | Returns `CardSelection`: `APPROVED`, or `FAILED` / `CARD_NOT_FOUND` / `CARD_DATA_MISSING` (`onActivationFailed` still fires for the latter) |
| A non-payment call stopped on the phone or offline could look like a payment's "never sent" | Only payments and add card answer `NOT_SENT`; every other call answers `FAILED` with the same reason — see [The six statuses](#the-six-statuses) |
| `cpmCustomerQrService.charge` threw on a transport failure | Always returns `PaymentResponse`; offline is `NOT_SENT` / `NO_NETWORK_CONNECTION` |
| `refreshTransactionStatus` / `refreshCreditConfirmation` threw, or returned the row | Return `TransactionRefreshResult` (SoftPOS) / `TransactionRefresh` (wallet): the row plus `failureReason` |
| `TransactionResponse.sdkErrorCode` | Deprecated alias. `PAYMENT_CANCELLED` → reason `CANCELLED`; `NFC_MODE_REFUSED` → `MODE_REFUSED`; the card-read codes → `CARD_READ_FAILED`; all `NOT_SENT` |
| `CardPaymentService.typedPreDispatchErrors` | Removed — every pre-dispatch refusal is a `NOT_SENT` outcome with its reason |
| `TokenisationResponse.error` (`TOKENIZATION_ERROR`, `CONFIG_ERROR`, `UNEXPECTED_ERROR`) | Deprecated; those codes are retired. Use `responseStatus` / `responseStatusReason` |
| Matching `message` text (`"Payment cancelled"`, `"NO_NETWORK_CONNECTION"` inside an exception message, `ONLINE_REQUIRED:` prefixes) | Compare `responseStatusReason` with the reason name (`OutcomeReason.*` for the SDK's own) |
| `SdkModeException`, `NoActiveCustomerException`, `IllegalArgumentException` / `IllegalStateException` / `VeyraSdkException(MISSING_MANDATORY_CONFIG)` from `initialize` | One type: `VeyraSetupException` with `code` `NOT_CONFIGURED` / `NOT_SIGNED_IN` / `INVALID_CONFIGURATION` / `SESSION_REQUIRED` / `UNSUPPORTED_ON_PLATFORM`. A mode clash during a payment is now `NOT_SENT` / `MODE_REFUSED` |
| `ResponseStatus` had four values (+ `Unknown`) | It gains `NOT_SENT` and `RETRY`. Add the branches (handle them alike: nothing charged, same order ID): an exhaustive `when` without them no longer compiles |
| Nothing checked a reused merchant order ID: it was never validated, and never refused | An order can have several attempts. The same order ID is refused (`94`) only while the order is `APPROVED` or `PENDING`; after `DECLINED`, `FAILED`, `RETRY` or `NOT_SENT`, retry with the **same** order ID. See [The merchant order ID](#the-merchant-order-id) |
| Payments stopped before the bank was asked ended `FAILED`: `30` `INVALID_REQUEST`, `12` `QR_EXPIRED`, `13` `AMOUNT_MISMATCH`, `07` `ACCOUNT_VALIDATION_FAILED`, `21` `NAME_ENQUIRY_FAILED`, `91` `ISSUER_SWITCH_NOT_AVAILABLE`; a status lookup with no record was `25` / `FAILED` | The same codes and reasons now come with status `RETRY` (nothing was charged, same order ID); `25` on a lookup is `RETRY`. `FAILED` on a payment is now mainly `94` |
| History rows were `APPROVED` / `DECLINED` / `FAILED` / `PENDING` | `RETRY` rows appear too, one per attempt; group a sale's attempts by `merchantOrderId` |

Unchanged: the wallet tap callback `onTransactionCompleted` (`APPROVED` / `DECLINED` / `ERROR`) and the
observers. Fields that only make sense on success (`CreatedPaymentContext.txRef` / `mpmPayload`, the
`CpmPaymentQr` fields) are now nullable, because the same type also carries a refusal.

### Other status vocabularies (not outcomes)

These fields describe the state of a QR code, a card or a merchant. They are not outcomes of a call,
and they never contradict one.

#### QR context lifecycle — `contextStatus().state`

| State | Meaning | What to do |
|---|---|---|
| `PENDING` | QR live, unpaid | Keep polling. |
| `IN_FLIGHT` | A wallet claimed it; settling | Keep polling. |
| `APPROVED` / `DECLINED` | Settled — the triple carries the payment's outcome | Stop polling; result screen + receipt. |
| `EXPIRED` | Lapsed unpaid (your `onExpired` callback has blanked the QR) | Offer a fresh QR. An expired context is never recorded in history. |

`state` is the *QR's* lifecycle; `responseStatus` is the *payment's* outcome. Branch on
`responseStatus` for the payment.

#### Add a card — the issuer's decision and causes

`responseCode` on `DigitiseResult` (from `digitise`) and `VerifyAccountResponse` (from
`verifyAccount`) is the **issuer's decision** about this account and device:

| `responseCode` | Meaning | What to do |
|---|---|---|
| `"APPROVED"` | Eligible / provisioned and active | The card is ready — show it in the wallet. |
| `"APPROVE_REQUIRE_AUTH"` | Provisioned, needs activation | Run the activation flow with the returned `activationMethods`. `responseStatusReason` says why step-up is needed. |
| `"DECLINED"` | Refused | Show the reason. The flow ends. |

Any other code ends `FAILED` / `UNRECOGNISED_RESPONSE_CODE` and the token is **discarded** —
nothing provisioned, no card added — even if the response carried full token data.

The issuer states a cause for every non-approval, and you receive it as `responseStatusReason`.
`message` is the same cause worded for a person: display it, but never match on it. **Keep a default
branch** — the list grows without an SDK release.

**Identity did not match — these ask for step-up rather than refusing** (`APPROVE_REQUIRE_AUTH`):

| Cause | The `message` you receive |
|---|---|
| `ACCOUNT_NAME_MISMATCH` | "Account name does not match; step-up authentication required" — on the earlier availability check it is the plain "Account name does not match" |
| `BVN_MISMATCH` | "BVN does not match; step-up authentication required" |
| `ACCOUNT_NOT_LINKED_TO_BVN` | "Account is not linked to the supplied BVN; step-up authentication required" |
| `ACCOUNT_ADDRESS_MISMATCH` | "Account address does not match; step-up authentication required" |

A mismatch usually means the details your app sent do not match the bank's record. The account
holder name, address and BVN you pass are compared with the bank's records, so check what you
collected before telling the customer their bank has a problem.

**The account cannot be tokenised** (`DECLINED` — the flow ends):

| Cause | The `message` you receive |
|---|---|
| `ACCOUNT_TYPE_NOT_ALLOWED` | "Account type is not permitted for token digitisation" |
| `JOINT_ACCOUNT_NOT_ALLOWED` | "Joint accounts are not permitted for token digitisation" |
| `ACCOUNT_INACTIVE` | "Account is not active" |
| `ACCOUNT_BLOCKED` | "Account is blocked" |
| `ACCOUNT_CLOSED` | "Account is closed" |
| `ACCOUNT_DND` | "Account has a Do Not Digitise flag" |
| `ACCOUNT_DNC` | "Account has a Do Not Contact flag" |
| `UNKNOWN_ACCOUNT` | "Unable to retrieve account details" |

**This device, wallet or product is not permitted** (`DECLINED` — retrying the same way cannot help):

| Cause | The `message` you receive |
|---|---|
| `DEVICE_NOT_ALLOWED` | "Device type is not permitted" |
| `DEVICE_REGION_NOT_ALLOWED` | "Device region is not permitted" |
| `WALLET_NOT_ALLOWED` | "Wallet is not permitted" |
| `TOKEN_REQUESTOR_NOT_ALLOWED` | "Token requestor is not permitted" |
| `STORAGE_TECH_NOT_ALLOWED` | "Storage technology is not permitted" |
| `ACCOUNT_SOURCE_NOT_ALLOWED` | "Account number source is not permitted" |
| `PAYMENT_APPLICATION_NOT_ALLOWED` | "Payment application is not permitted" |
| `TOKEN_TYPE_NOT_ALLOWED` | "Token type is not permitted" |
| `CUSTOMER_ID_NOT_ALLOWED` | "Customer identifier is not permitted" |
| `MAX_ACTIVE_TOKENS_EXCEEDED` | "Maximum number of active tokens has been exceeded" — the customer must remove a card before adding another |
| `NO_VALID_ACTIVATION_METHOD` | "No valid activation method is available" — the issuer has no way to reach this customer for step-up |

**Risk refused it** (`DECLINED`):

| Cause | The `message` you receive |
|---|---|
| `RISK_SCORE_BELOW_THRESHOLD` | "Risk score exceeds the configured threshold" |
| `WALLET_PROVIDER_DEVICE_SCORE_TOO_LOW` | "Wallet provider device score is too low" |
| `WALLET_PROVIDER_ACCOUNT_SCORE_TOO_LOW` | "Wallet provider account score is too low" |
| `WALLET_PROVIDER_ACCOUNT_NOT_RECOGNISED` | "Wallet provider account could not be recognised" |

The last three are scored partly from what **your app** supplies — the device and account trust
scores, the recommendation and its reasons, and the wallet account identifier. Pass the account's
registered email or phone, never an internal id: the issuer hashes it and compares it with its own
record, so a value the bank does not hold matches nothing.

Anything else — including a cause added after your build — arrives as "Account is not eligible for
tokenisation" / "Account is not available for tokenisation". Show the message; never assume a
specific cause from a `DECLINED` alone.

#### Activation — `responseStatus` + `failureCode`

Branch on `responseStatus` first: `APPROVED` (code sent / card activated), `FAILED` (the issuer
refused — `responseStatusReason` is the failure code; or never sent, e.g. `NO_NETWORK_CONNECTION`
when offline, with `failureCode == null` — retry once connected), `PENDING` (`activate` only: sent, no
usable answer — read the token status before trying again).
`status` (`"SUCCESS"` / `"FAILURE"`) is still populated. On `FAILED`, branch on the typed `failureCode`
(`message` is display text — never match on it):

| `failureCode` | Meaning | What to do |
|---|---|---|
| `CODE_EXPIRED` | Code lapsed; attempts may remain | Offer "resend code". |
| `CODE_INVALID` | Wrong code, attempts remain | Stay on entry; show `attemptsRemaining`. |
| `MAX_ATTEMPTS_EXCEEDED` | The attempt limit for this code is used up | The cycle is closed; honour `recommendDelete` (`MUST`: delete the token and restart add card; `MAY`: advisory). |
| `CODE_REQUEST_RATE_LIMITED` | Too many code requests for this card in the last hour | Disable "resend" with a cool-down message — do **not** end the flow. |
| `NO_PENDING_ACTIVATION` | No live code (never requested, or the pending window lapsed) | Request a code first. |
| `ACTIVATION_LOCKED` | Locked after repeated failed cycles | Terminal — hide both retry and resend; the issuer must unlock; direct the user to their bank. |
| `TOKEN_NOT_FOUND` / `TOKEN_NOT_ACTIVATABLE` | No activatable token behind the reference | End the flow; add the card again or contact the issuer. |
| `INVALID_REQUEST` / `ACTIVATION_FAILED` | Malformed request / activation failed on the server | Show `message`; safe to retry `ACTIVATION_FAILED` later. |
| `UNKNOWN` | A code newer than this SDK | Show `message`; log `failureCodeRaw`. |

#### Card lifecycle statuses

The wallet syncs each card's server status automatically (foreground sweeps and around payments).

| Server status | Effect in the SDK | What to do |
|---|---|---|
| `ACTIVE` | Card pays normally | — |
| `SUSPENDED` / `EXPIRED` / `PENDING_ACTIVATION` | Card refuses to pay (`NOT_SENT` / `TOKEN_NOT_ACTIVE`); `Token.isActive` flips false | Render the card as unavailable. **Not sticky** — a later sync unfreezes it automatically. |
| `DEACTIVATED` | The card and all its on-device material are wiped when your app calls `deactivateToken`, and it disappears from `getTokens()` | Refresh your card list; the user re-adds the card if needed. |

##### Token status changed

See [`observeTokenStatusChanged`](#observetokenstatuschanged) under the wallet's Observers.

##### Card key state changed

See [`observeCardKeyStateChanged`](#observecardkeystatechanged) under the wallet's Observers.

#### Merchant statuses

`ACTIVE` / `INACTIVE` / `SUSPENDED` / `DEACTIVATED` (on registration results, status responses and
every payment response's `merchantStatus`). A payment for a merchant that is not `ACTIVE` ends
`NOT_SENT` / `MERCHANT_NOT_ACTIVE` — gate your get-paid entry on `isRegistered` +
`isMerchantActive()` and call `refreshStatus()` while awaiting activation.

##### `onMerchantStatusChanged` — the SDK tells you when it changes

```kotlin
MerchantStatusObserver.onMerchantStatusChanged { change ->
    // change.merchantId, change.status, change.previousStatus
    if (!change.canAcceptPayments) disableGetPaid()
}
```

Use it to stop offering to take payments the moment a merchant is deactivated mid-session, and to
catch the **activation** moment after registration without polling yourself. **Branch on
`canAcceptPayments`, not on `status`** — anything that is not `ACTIVE`, including a status newer than
your build, is `false`. Only genuine changes fire, last registration wins (and it is dropped on
sign-out or a customer switch — register again after `initialize`), there is no replay, and delivery
is on the main thread. Same channel on iOS (`VeyraSoftPOS.shared.merchant.onMerchantStatusChanged { … }`)
and React Native (`merchant.onMerchantStatusChanged(listener)`).

#### History status vocabularies

| Field | Values |
|---|---|
| Wallet `TransactionSummary.authorizationStatus` | `PENDING` (the SDK is still checking) / `APPROVED` / `DECLINED` / `FAILED` / `null` (old row — outcome not known) |
| Merchant `TransactionInfo.transactionStatus` | `APPROVED` / `DECLINED` / `PENDING` (outcome not known yet, the SDK keeps checking) / `FAILED` / `RETRY` (reached Veyra, not sent to the bank; nothing was debited — retry with the same order id) |
| Credit confirmation (`TransactionInfo.creditConfirmationStatus`) | `RECEIVED` (funds landed in the merchant's account), `UNABLE_TO_CONFIRM` (30 days passed with no answer), or `null` while unconfirmed — show "not confirmed yet", never "not received". A settlement fact, never a payment outcome |
| Wallet scan rejection (`inspectScannedQr` → `MpmScanResult.Rejected`) | `MALFORMED` / `MISSING_SIGNATURE` / `UNKNOWN_KEY` / `BAD_SIGNATURE` (show "couldn't verify this code") / `EXPIRED` (show "code expired — ask the merchant for a fresh one"). Every rejection ends the flow; no payment was attempted |

---

## Data models

Reference for the public models. All are immutable value types; fields not listed here are not public API.

### Wallet

```kotlin
data class Token(
    val tokenUniqueReference: String?,      // the card's identity for every wallet call — always set on a token the SDK returns
    val devicePAN: String,
    val cardHolderName: String,           // "AFRIGO ****1234" — scheme + masked last four, not a person
    val expiryDate: String,                 // "MM/YY"
    val cvv: String?,
    val cardType: CardType,                 // DEBIT, CREDIT
    val cardScheme: CardScheme?,            // IVENTUREPAY, VISA, MASTERCARD, AMEX, DISCOVER, UNKNOWN
    val isActive: Boolean,                  // the card payments use (at most one)
    val activationMethods: List<ActivationMethod>?,  // non-null while activation is pending
    val transactions: List<TransactionSummary>,      // last 3
    val requiresOnline: Boolean,            // true: card can't pay until the wallet has been online — grey it out
    val deviceNotBound: Boolean = false,    // true: keys refused to this device — re-add the card here; requiresOnline is then false
    val status: TokenStatus?,               // ACTIVE / PENDING_ACTIVATION / SUSPENDED / DEACTIVATED / EXPIRED / UNKNOWN;
                                            // null = never status-synced. A value this build doesn't know reads as UNKNOWN
    val statusRaw: String?,                 // the stored wire value behind status — logs / forward compatibility
    val bankName: String?                   // from DigitiseParams.bankName; null when none was given
) {
    fun getMaskedPAN(): String              // "**** **** **** 1234"
    fun getLastFourDigits(): String
}

data class ActivationMethod(val medium: String, val contact: String)
// medium: MASKED_EMAIL, MASKED_MOBILE_PHONE, CALL_CENTER_PHONE,
//         AUTOMATED_CALL_CENTER_PHONE, WEBSITE, MOBILE_APPLICATION
// contact: the masked phone/email, phone number, URL or app package to act on

data class Bank(val slug: String, val name: String, val institutionCode: String)

data class BankList(                        // getBanks callback (a read: never PENDING)
    val banks: List<Bank>,                  // empty unless responseStatus == APPROVED
    val responseStatus: String,             // APPROVED / FAILED
    val responseStatusReason: String?,
    val message: String?
)

data class VerifyAccountResponse(           // verifyAccount callback (a read: never PENDING)
    val responseCode: String?,              // the issuer's decision literal
    val message: String?,
    val responseStatus: String?,            // APPROVED (eligible) / DECLINED / FAILED — branch on this
    val responseStatusReason: String?       // the issuer's cause on a decline; why, otherwise
)

data class DigitiseResult(                  // digitise callback
    val tokenUniqueReference: String?,
    val responseCode: String?,              // APPROVED / APPROVE_REQUIRE_AUTH / DECLINED
    val message: String?,
    val responseStatus: String?,            // APPROVED / DECLINED / FAILED / NOT_SENT — branch on this first
    val responseStatusReason: String?,      // why — branch on this second; see Errors and outcomes
    val activationMethods: List<DigitiseActivationMethod>,  // empty unless activation is needed
    val tokenStored: Boolean                // the card's payment data is on the phone
)
data class DigitiseActivationMethod(val medium: String, val contact: String?)

data class ActivationCodeResponse(
    val tokenUniqueReference: String?,
    val expirationDateTime: String?,        // ISO-8601 — drive the OTP countdown
    val status: String?,                    // SUCCESS / FAILURE
    val message: String?,
    val failureCode: ActivationFailureCode?,  // null on success; UNKNOWN for a code newer than this SDK
    val failureCodeRaw: String?,
    val responseStatus: String?,            // APPROVED / FAILED / PENDING — branch on this
    val responseStatusReason: String?       // on FAILED: the failure code (or NO_NETWORK_CONNECTION… when never sent)
)

data class ActivateResponse(
    val tokenUniqueReference: String?,
    val status: String?,                    // SUCCESS / FAILURE
    val message: String?,
    val failureCode: ActivationFailureCode?,  // null on success; UNKNOWN for a code newer than this SDK
    val failureCodeRaw: String?,
    val attemptsRemaining: Int?,            // code attempts left where a cap applies (0 when exhausted/locked)
    val recommendDelete: RecommendDelete?,  // MUST / MAY after an exhausted cycle; null otherwise
    val recommendDeleteRaw: String?,
    val responseStatus: String?,            // APPROVED / FAILED / PENDING — branch on this
    val responseStatusReason: String?       // on FAILED: the failure code (or NO_NETWORK_CONNECTION… when never sent)
)

data class TokenStatusUpdateResponse(       // deactivateToken callback
    val tokenUniqueReference: String?, val status: String?, val message: String?,
    val responseStatus: String?,            // APPROVED / FAILED / PENDING
    val responseStatusReason: String?
)

data class TokenStatusResult(               // tokenStatus callback (a read: never PENDING)
    val tokenStatus: String?,               // raw lifecycle value
    val responseStatus: String,             // APPROVED / FAILED
    val responseStatusReason: String?, val message: String?
) { val lifecycleStatus: TokenStatus? }     // typed; null unless APPROVED

data class VeyraLookup<T>(                 // refreshTransactionStatus / refreshCreditConfirmation
    val value: T?,                          // the row as it now stands
    val failureReason: String?,             // null when the check was made
    val message: String?
) { val checked: Boolean }                  // failureReason == null

data class ReconcileResult(                 // reconcilePendingTransactions callback (a read: no status)
    val failureReason: String?,             // null when the reconcile ran; set only when it could not run
    val message: String?
) { val checked: Boolean }                  // failureReason == null

data class CardSelection(                   // setActiveToken return — on the phone, nothing is sent
    val responseStatus: String,             // APPROVED / FAILED
    val responseStatusReason: String?,      // on FAILED: CARD_NOT_FOUND / CARD_DATA_MISSING
    val message: String?
) { val selected: Boolean }                 // responseStatus == APPROVED

data class ReceiptScanResult(               // processReceipt — checked on the phone, nothing is sent
    val receipt: TransactionReceipt?,       // the stored receipt; null unless APPROVED
    val responseStatus: String,             // APPROVED / FAILED
    val responseStatusReason: String?,      // on FAILED: INVALID_RECEIPT / RECEIPT_NOT_FROM_THIS_WALLET /
                                            //   RECEIPT_MISMATCH / PAYMENT_NOT_CONFIRMED /
                                            //   RECEIPT_FOR_ANOTHER_TRANSACTION / SDK_INTERNAL_ERROR
    val message: String?
) { val stored: Boolean }                   // APPROVED with a receipt

data class TransactionSummary(
    val merchantName: String,
    val amountInMinorUnit: Long,            // 150000 = ₦1,500.00 (Long since 1.0.15; was Int)
    val transactionCurrencyCode: String?,   // ISO 4217 numeric 4-digit, e.g. "0566"
    val authorizationStatus: String?,       // PENDING / APPROVED / DECLINED / FAILED; null = legacy
    val entryMethod: String?,               // "TAP" / "QR_GENERATED" / "QR_SCANNED"; null = legacy
    val merchantLocation: String?,          // "city, state" when known
    val transactionHash: String?,           // join key to a TransactionReceipt
    val localTransactionDateTime: String?,  // ISO 8601 (tap rows)
    val atEpochMillis: Long?,               // (QR rows)
    val merchantTransactionReference: String?,
    val merchantId: String?,
    val merchantOrderId: String?,           // the merchant's own order id; display only, never a key
    // Beneficiary credit confirmation — settlement only, never the payment outcome.
    val creditTransactionId: String?,            // credit-leg id; display/support only
    val isCreditConfirmationSupported: Boolean?, // THE GATE: true ⇒ SDK is polling, render the line
    val creditConfirmationStatus: String?,       // null = no answer yet / "RECEIVED" / "UNABLE_TO_CONFIRM"
    val creditedAt: String?,                     // when the bank posted the credit (RECEIVED only)
    val bankReference: String?                   // the bank's own reference   (RECEIVED only)
) { fun toJson(): String; companion object { fun fromJson(json: String): TransactionSummary? } }

data class TransactionReceipt(
    val merchantName: String, val merchantId: String?, val merchantAddress: String?,
    val transactionType: String, val transactionStatus: String, val transactionTime: String,
    val totalAmount: String,                // minor units, as a string
    val totalAmountFormatted: String,       // "100.00"
    val currency: String?, val maskedToken: String?,
    val merchantTransactionReference: String?,
    val cdcvmApprovedByWallet: Boolean?, val cdcvmOutcome: String?,
    val transactionId: String?, val transactionHash: String?
) { fun toJson(): String; companion object { fun fromJson(json: String): TransactionReceipt? } }

// Tap events (observeTapEvents):
sealed class WalletTapEvent {
    data class TransactionStarted(val tokenUniqueReference: String) : WalletTapEvent()
    data class TransactionCompleted(val response: TransactionResponse) : WalletTapEvent()
}
// The tap outcome inside TransactionCompleted:
data class TransactionResponse(
    val status: String,                     // "APPROVED", "DECLINED", "ERROR"
    val message: String?,
    val amount: Long?,                      // minor units
    val tokenId: String?,
    val cardScheme: CardScheme?,
    val reference: String?
) { val isApproved: Boolean; val isDeclined: Boolean; val isError: Boolean }

// Scan-to-pay:
sealed interface MpmScanResult {
    data class Verified(val context: VerifiedPaymentContext) : MpmScanResult
    data class Rejected(val reason: Reason, val detail: String? = null) : MpmScanResult
    enum class Reason { MALFORMED, MISSING_SIGNATURE, UNKNOWN_KEY, BAD_SIGNATURE, EXPIRED }
}
data class VerifiedPaymentContext(
    val txRef: String, val merchantId: String, val merchantName: String, val merchantCity: String?,
    val amount: String,                     // display, major units: "5000.00"
    val amountMinorUnits: Long, val currencyNumeric: String,
    val expiryEpochSeconds: Long, /* … */
    val merchantOrderId: String?,           // the merchant's order ID signed into the QR; null when it carries none.
                                            // The reference wallet.cancelPayment names.
)
data class PaymentOutcome(                  // payScannedContext callback
    val responseCode: String?,              // "00", "51", "68"… — always populated, quote it verbatim
    val responseStatus: String?,            // APPROVED · DECLINED · FAILED · PENDING · RETRY · NOT_SENT — what the payment IS
    val message: String?,
    val merchantName: String?,              // registered name from the gateway (beats the QR copy)
    val merchantLocation: String?,          // "city, state" from the gateway; null if not supplied
    val responseStatusReason: String?,      // stated cause: INSUFFICIENT_FUNDS, NO_RESPONSE_RECEIVED…
) { val approved: Boolean }                 // derived: responseStatus == "APPROVED"; false for PENDING too

// Show-QR-to-pay:
data class CpmPaymentQr(
    val tokenUniqueReference: String?,
    val payload: String?,                   // render as the QR; null unless responseStatus == APPROVED
    val amountMinorUnits: Long, val currencyNumeric: String?,
    val expiresAtEpochMillis: Long,
    val transactionHash: String?,           // this render's unique hash — match against history to reconcile
    val responseStatus: String,             // APPROVED, or NOT_SENT (no QR made — see the reason)
    val responseStatusReason: String?,      // AUTH_CANCELLED, NO_ACTIVE_CARD, ONLINE_REQUIRED, …
    val message: String?
)
```

`CurrencyUtils` (wallet): `getSymbol(code)`, `formatAmount(minorUnits, code)`, `formatAmountWithCode(minorUnits, code)` — display helpers for ISO 4217 numeric codes. **`minorUnits` is a `Long` from 1.0.15** (it was `Int`), matching `TransactionSummary.amountInMinorUnit`: a 32-bit amount could not carry a payment above ₦21,474,836.47. If you parse an amount out of a `TransactionReceipt.totalAmount` string, use `toLongOrNull()`.

### SoftPOS

```kotlin
data class NubanBank(val slug: String, val name: String, val institutionCode: String)
data class NubanBankListResponse(           // merchant getBanks callback (a read: never PENDING)
    val banks: List<NubanBank>, val responseStatus: String?, val responseStatusReason: String?, val message: String?
)

data class MerchantRegistrationResponse(
    val responseStatus: String?,            // APPROVED / FAILED / PENDING — branch on this
    val responseStatusReason: String?,      // e.g. ACCOUNT_NUMBER_REQUIRED, NO_NETWORK_CONNECTION
    val success: Boolean,                   // == (responseStatus == "APPROVED")
    val merchantId: String?, val terminalId: String?, val merchantCategoryCode: String?,
    val countryCode: String?, val acquirerId: String?, val paymentAppProviderId: String?,
    val walletAccountId: String?, val merchantStatus: String?, val message: String?
)
data class MerchantStatusResponse(          // activate / deactivate callback
    val responseStatus: String?, val responseStatusReason: String?, val message: String?,
    val merchantId: String?, val merchantStatus: String?,
    val terminalId: String?, val acquirerId: String?, val paymentAppProviderId: String?, val walletAccountId: String?
)
data class MerchantUpdateResponse(          // updateMerchant callback — same fields as MerchantStatusResponse
    val responseStatus: String?, val responseStatusReason: String?, val message: String?,
    val merchantId: String?, val merchantStatus: String?,
    val terminalId: String?, val acquirerId: String?, val paymentAppProviderId: String?, val walletAccountId: String?
)

data class StoredMerchantData(
    val merchantId: String, val merchantType: String,   // "PERSONAL" / "BUSINESS"
    val merchantName: String, val emailAddress: String, val phoneNumber: String,
    val addressLine1: String, val addressLine2: String, val city: String, val state: String,
    val countryCode: String, val bvn: String?, val cacNumber: String?,
    val accountNumber: String, val institutionCode: String,
    val acquirerId: String,                 // backend-assigned (resolved from your payment app provider)
    val merchantCategoryCode: String,       // backend-assigned
    val terminalId: String,                 // backend-assigned
    val merchantStatus: String?,            // last known ("ACTIVE", …)
    val currencyCode: String?,
    val walletAccountId: String?            // as stored by the gateway, if supplied
)

data class TransactionResponse(             // tap terminal outcome (makeCardPayment callback)
    val transactionCode: String,            // the payment's response code (empty on NOT_SENT) — receipts/support only
    val message: String?,
    val amount: String?,                    // minor units as string
    val cardScheme: String?,                // "VISA", "MASTERCARD", …
    val cardExpiry: String?,                // YYMM
    val merchantTransactionReference: String?,  // SDK-MINTED reference — receipt/status lookup key
    val transactionType: String?, val maskedTokenLast4: String?,
    val merchantStatus: String?, val transactionId: String?, val aid: String?,
    val responseStatus: ResponseStatus?,        // APPROVED / DECLINED / FAILED / PENDING / RETRY / NOT_SENT / Unknown — branch on this
    val responseStatusReason: String?,          // why (e.g. INSUFFICIENT_FUNDS, NO_NETWORK_CONNECTION) — branch on this second
    val creditTransactionId: String?,           // merchant-bank credit id (approved + supported only)
    val isCreditConfirmationSupported: Boolean?, // true ⇒ the SDK will poll and fire onCreditConfirmation
    val originalPayment: OriginalPayment?,      // on DUPLICATE_MERCHANT_ORDER_ID only — see below
    @Deprecated val sdkErrorCode: SdkErrorCode? // alias only — branch on responseStatus / responseStatusReason
)

data class OriginalPayment(                 // the attempt that decides the order (see "The merchant order ID")
    val responseCode: String?,
    val responseStatus: String?,            // "APPROVED" (already paid) / "PENDING" (still being confirmed)
    val responseStatusReason: String?,
    val amount: Long?,                      // minor units
    val merchantTransactionReference: String?, val transactionId: String?
)

data class TransactionInfo(                 // history row
    val merchantTransactionReference: String,
    val amount: Long,                       // minor units
    val transactionStatus: TransactionStatus,   // APPROVED / DECLINED / PENDING / FAILED / RETRY
    val responseCode: String?,
    val responseStatusReason: String?,      // the stated cause ("INSUFFICIENT_FUNDS"...); display, never parse
    val transactionTime: String?,           // "yyyy-MM-dd HH:mm:ss"
    val currencyCode: String?, val transactionId: String?,
    val merchantOrderId: String?,           // your own order id as supplied on the charge; groups a sale's attempts
    val cardholderName: String?,            // EMV 5F20, e.g. "AFRIGO ****1234"; null on QR-MPM
    val rail: String,                       // "TAP" / "QR_MPM" / "QR_CPM" — use for logic
    val railLabel: String,                  // "Tap" / "QR" / "Scan" — use for display
    val creditTransactionId: String?,       // merchant-bank credit id; null unless approved + supported
    val isCreditConfirmationSupported: Boolean?, // true ⇒ SDK is polling; show "confirming credit…" until resolved
    val creditConfirmationStatus: String?   // "RECEIVED" / final "UNABLE_TO_CONFIRM"; null while unconfirmed
)

data class TransactionResolution(           // onTransactionResolved payload (a pending sale settled)
    val reference: String,                  // the sale's merchantTransactionReference — match on this
    val responseCode: String?,              // the wire literal, for receipts and support
    val status: String,                     // "APPROVED" / "DECLINED" / "FAILED" / "RETRY" — never "PENDING"
    val reason: String?                     // the stated cause ("INSUFFICIENT_FUNDS"...); display, never parse
)

data class CreditConfirmation(              // onCreditConfirmation payload (settlement news, not an outcome)
    val reference: String,                  // the sale's merchantTransactionReference — match on this
    val creditTransactionId: String?,
    val status: String,                     // "RECEIVED" (funds landed) / "UNABLE_TO_CONFIRM" (30-day give-up)
    val amountMinorUnits: Long?,            // credited amount as the bank reported it (RECEIVED only)
    val bankReference: String?,             // the merchant bank's own reference (RECEIVED only)
    val creditedAt: String?                 // when the bank posted the credit (RECEIVED only)
)

data class TransactionReceiptResult(
    val merchantName: String, val merchantAddress: String,
    val transactionType: String,
    val totalAmount: Long,                  // minor units
    val totalAmountFormatted: String,       // "10.00"
    val maskedToken: String,                // "****1234"
    val merchantTransactionReference: String,
    val qrCodeBase64: String,               // ready-made 512×512 receipt-QR PNG
    val transactionHash: String?
)

data class TransactionRefreshResult(        // refreshTransactionStatus / refreshCreditConfirmation
    val transaction: TransactionInfo?,      // the row as it now stands
    val failureReason: String?,             // null when the check was made
    val message: String?
) { val checked: Boolean }                  // failureReason == null

data class OrderPaymentLookup(              // transactionService.paymentStatus(merchantOrderId) — a read
    val value: OrderPaymentStatus?,         // the order's status; when failureReason is set, what this phone holds
    val failureReason: String?,             // null when answered; NO_NETWORK_CONNECTION, INVALID_REQUEST, MISSING_MANDATORY_CONFIG…
    val message: String?
) { val checked: Boolean }                  // failureReason == null
data class OrderPaymentStatus(
    val merchantOrderId: String,
    val responseStatus: String,             // the ORDER's: APPROVED / PENDING / DECLINED / FAILED / RETRY
    val responseStatusReason: String?,      // the deciding attempt's (UNABLE_TO_LOCATE_RECORD with no attempts)
    val responseCode: String?,              // the deciding attempt's ("25" with no attempts)
    val merchantTransactionReference: String?, val transactionId: String?,
    val amount: Long?,                      // minor units
    val attempts: List<OrderPaymentAttempt>,  // newest first; empty when there are none
    val answeredBy: String                  // "DEVICE" (this phone's final record) or "GATEWAY"
)
data class OrderPaymentAttempt(
    val merchantTransactionReference: String?, val transactionId: String?, val amount: Long?,
    val responseCode: String?, val responseStatus: String?, val responseStatusReason: String?
)

data class CreatedPaymentContext(
    val txRef: String?, val expiry: String?, val kid: String?,
    val mpmPayload: String?,                // render as the QR; null unless APPROVED
    val responseStatus: String,             // APPROVED / FAILED / PENDING / NOT_SENT
    val responseStatusReason: String?, val message: String?,
    val originalPayment: OriginalPayment?   // on DUPLICATE_MERCHANT_ORDER_ID only
) { val created: Boolean }                  // APPROVED with txRef and mpmPayload
data class PaymentContextStatusResult(      // contextStatus — a read: never PENDING, never throws
    val status: PaymentContextStatus?,      // null when the poll could not be made
    val failureReason: String?,             // null when it was made
    val message: String?
) { val checked: Boolean }                  // failureReason == null
data class PaymentContextStatus(
    val txRef: String,
    val state: String,                      // PENDING / IN_FLIGHT / APPROVED / DECLINED / EXPIRED — the QR's lifecycle
    val responseCode: String?, val expiry: String?, val transactionHash: String?,
    val responseStatus: String?, val responseStatusReason: String?   // the payment's outcome once settled
) { val isSettled: Boolean; val isApproved: Boolean }

data class CpmInspection(                   // cpmCustomerQrService.inspect
    val qr: ScannedCpmQr?,                  // set when APPROVED
    val responseStatus: String,             // APPROVED, or FAILED / INVALID_REQUEST (not a payment QR)
    val responseStatusReason: String?, val message: String?
)

data class ScannedCpmQr(
    val dpan: String, val tokenExpiryYymm: String?,
    val amountMinorUnits: Long,             // the QR's own, cryptogram-bound amount
    val currencyNumeric4: String,
    val cardholderName: String?,           // "AFRIGO ****1234" — display only, outside the cryptogram
    /* … */
)
data class PaymentResponse(                 // cpmCustomerQrService.charge — always returned
    val responseCode: String?, val responseStatus: ResponseStatus?, val responseStatusReason: String?,
    val message: String?, val merchantStatus: String?, val transactionId: String?,
    val merchantTransactionReference: String?, val merchantOrderId: String?,
    val originalPayment: OriginalPayment?,  // on DUPLICATE_MERCHANT_ORDER_ID only
    /* … */
)
```

`CurrencyUtils` (SoftPOS): `getSymbol`, `formatAmount`, `parseDisplayAmountToMinorUnits("325.98") // → 32598L` — use it to convert keyed amounts safely.

---

## Complete flows

### Add a card (wallet)

```
User enters account number
        │
        ▼
  getBanks(accountNumber)  ──►  user picks their bank (institutionCode)
        │
        ▼
verifyAccount
        │
        ├─ not APPROVED ──► show error (flow ends)
        │
        ▼ APPROVED
digitise  (your clientRequestId; Cancel until sent: cancelDigitise(id)
        │                 → NOT_SENT / CANCELLED → leave)
        ├────────────────┬──────────────────────────┐
        ▼                ▼                          ▼
    APPROVED     APPROVE_REQUIRE_AUTH           DECLINED
        │                │                          │
        ▼                ▼                          ▼
  Card added ✓   Show activation methods       Show error
   (flow ends)    (branch on medium)            (flow ends)
                         │
        ┌────────────────┴───────────────────────┐
        ▼                                        ▼
 MASKED_EMAIL /                      CALL_CENTER_PHONE / WEBSITE /
 MASKED_MOBILE_PHONE                 MOBILE_APPLICATION
        │                                        │
        ▼                                        ▼
 requestActivationCode                Show contact info + action button
        │                             ("Call now" / "Open website" / "Open app")
        ▼                                        │
 OTP entry screen                                ▼
 (masked contact +                       observeActivation
  countdown from                    polls every 10 s, up to 5 min
  expirationDateTime)                            │
        │                          ┌─────────────┴───────────┐
        ▼                          ▼                         ▼
    activate                  onActivated                onTimeout
        │                  → wallet home ✓         → "still pending" hint;
        ├─ SUCCESS ──► card active ✓                 SDK keeps checking
        └─ else ────► wrong code — retry
```

### Get paid (merchant) — pick the rail per sale

```
Merchant registered & ACTIVE? ──no──► register / activate first
        │ yes
        ▼
 Amount entry (minor units)
        │
        ├─────────────── Tap ───────────────► makeCardPayment
        │                                       ├─ Cancel (until sent): cancelPayment(orderId)
        │                                       │    → NOT_SENT / CANCELLED → back to amount entry
        │                                       ├─ onCardDetected: "hold steady"
        │                                       ├─ onUnsupportedCard / contact lost:
        │                                       │    transient hint, stays armed
        │                                       └─ terminal outcome → result screen
        │
        ├─────────── Show a QR ─────────────► createContextPayment
        │                                       render mpmPayload; poll contextStatus
        │                                       until APPROVED / DECLINED / EXPIRED
        │                                       (onExpired blanks the code)
        │
        └────── Scan the customer's QR ─────► inspect → confirm the QR's own amount
                                                → charge → approved iff code "00"
        After any settled rail:
        generateTransactionReceipt → show receipt + receipt QR
        (customer scans it with their wallet's processReceipt)
        Lost the result (PENDING, app restarted)?
        paymentStatus(merchantOrderId) → APPROVED: paid · PENDING: wait ·
        DECLINED / FAILED / RETRY: pay again with the same order ID
```

### Pay by scanning a merchant QR (wallet)

```
Camera scan ──► inspectScannedQr
                    ├─ rejected (malformed / bad signature / expired) ──► end flow, show reason
                    └─ verified ──► confirm screen (merchant + the QR's amount)
                                        │ user confirms
                                        ▼
                              payScannedContext  (the SDK raises the biometric sheet itself)
                                        │   Cancel while paying ──► cancelPayment(merchantOrderId)
                                        │     CANCELLED → NOT_SENT / CANCELLED → leave
                                        │     too late  → "can no longer be cancelled", keep waiting
                                        ├─ APPROVED ──► success screen; history row APPROVED
                                        ├─ DECLINED / FAILED ──► refused screen; history row says why
                                        ├─ PENDING ──► "still processing" — never pay again; the SDK resolves it
                                        └─ NOT_SENT (AUTH_CANCELLED, ONLINE_REQUIRED, NO_NETWORK_CONNECTION…)
                                           or RETRY (reached Veyra, not sent to the bank)
                                                 ──► show the reason, stay on confirm; paying again pays this QR
```

## Building with React Native?

Use the official React Native SDK —
[`veyra-sdk-react-native`](https://www.npmjs.com/package/veyra-sdk-react-native) — and
its [sample app](https://github.com/Iventure-Tech/veyra-react-native-sample-app), whose
`DEVELOPER-GUIDE.md` is the canonical React Native guide. Do **not** integrate the
artifacts documented here directly from React Native: the SDK's automatic payment-mode
arming follows native screen lifecycle, which a React Native app's JavaScript
navigation does not exercise — the React Native SDK's session hooks exist precisely to
bridge that gap.
