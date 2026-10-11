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
| `co.veyra:veyra-core` | Mode contract (`NfcMode`, `SdkModeException`); no other dependencies | 24 |
| `co.veyra:veyra-common` | Shared infrastructure (`Environment`, the provider types, logging, token cache) | 24 |

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

Facade for a combined SoftPOS + Wallet app. It owns the exclusive mode and hands out the two member SDKs. Package `co.veyra.sdk`.

```kotlin
val sdk = VeyraSdk.initialize(this, customerId, VeyraSdkConfig(softposConfig, walletConfig), provider)
val wallet  = sdk.wallet    // VeyraWalletSdk
val softpos = sdk.softpos   // VeyraSoftPOSSdk
```

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, customerId, config, provider)` | `activity` — an `AppCompatActivity`; `customerId` — the customer your app has authenticated (never leaves the device); `config` — `VeyraSdkConfig(softpos, wallet)`; `provider` — your `VeyraAssertionProvider` or `VeyraProxyProvider`, shared by both SDKs (see [Connecting to Veyra](#connecting-to-veyra)) | Initialises the facade and installs the exclusive-mode arbiter, and signs `customerId` in for both SDKs. Idempotent process singleton; always starts inert (no mode active). Must be called before either member SDK is used in a combined app, **on every launch** — see [Customers](#customers-signing-in-signing-out-switching). Throws `IllegalArgumentException` when `customerId` is blank or the provider is unusable (naming the problem). |
| `signOut()` (static) | — | The customer has logged out: stops everything both SDKs run for them and drops every observer registration. Their cards, keys, merchant and history stay on the device for when they sign in again. |
| `getInstance()` | — | The current instance, or `null` if not yet initialised. |
| `softpos` | — | The `VeyraSoftPOSSdk`. Throws `IllegalStateException` if that SDK has not been initialised yet (each of your screens initialises its own SDK — see the samples). |
| `wallet` | — | The `VeyraWalletSdk`. Same behaviour. |
| `currentMode()` | — | The current exclusive mode (`NfcMode.NONE` / `SOFTPOS` / `WALLET`). Read-only observation for UI state. |

`VeyraSdkConfig` is a simple pair of the two SDK configs: `VeyraSdkConfig(softpos: VeyraSoftPosSdkConfig, wallet: VeyraWalletSdkConfig)`.

**The bypass rule:** with both SDKs on the classpath, initialising them *without* `VeyraSdk.initialize` leaves the app fail-closed — every NFC operation throws `SdkModeException`. This is by design: a combined app that skipped the facade must fail loudly rather than risk receiving and paying at once. Standalone single-SDK apps are unaffected and never see `SdkModeException`.

### `VeyraSoftPOSSdk` (merchant features)

Package `co.veyra.softpos.payment.sdk`. Process singleton; initialise on (or before) each screen that accepts payments, so reader arming binds to that screen's lifecycle.

```kotlin
val sdk = VeyraSoftPOSSdk.initialize(this, customerId, softposConfig, provider)
```

**Methods & services:**

| Member | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, customerId, config, provider)` | `activity` — `AppCompatActivity`; `customerId` — the customer your app has authenticated (never leaves the device); `config` — `VeyraSoftPosSdkConfig`; `provider` — the same provider as the wallet's | Initialises (or re-binds) the singleton and signs `customerId` in. Idempotent; re-binds NFC reader + lifecycle to the new activity on every call. The same customer again changes nothing; a different customer stops the previous customer's work and opens the new customer's merchant and transactions. Throws `VeyraSdkException` (`MISSING_MANDATORY_CONFIG`) when the provider is unusable (naming the problem), `IllegalStateException` when a different kind of provider is already in use in this process, and `IllegalArgumentException` when `customerId` is blank. |
| `signOut()` (static) | — | The customer has logged out: stops every poll and watch the SDK runs for them and drops every observer registration. Their merchant and transactions stay on the device. Until the next `initialize` there is no merchant. |
| `storedMerchant(context, customerId)` (static) | `context`; `customerId` | Init-free read of that customer's stored merchant — see [Merchant status](#merchant-status--isregistered--ismerchantactive--refreshstatus--activate--deactivate). |
| `isMerchantRegistered(context, customerId)` (static) | `context`; `customerId` | Init-free registration check for that customer. |
| `getInstance()` | — | Current instance or `null`. |
| `cardPaymentService` | — | Tap acceptance — [`makeCardPayment`](#makecardpayment) and friends. |
| `merchantService` | — | Merchant registration, status, profile, banks. |
| `transactionService` | — | Merchant transaction history + receipts (all rails). |
| `cpmCustomerQrService` | — | Charge a scanned customer payment QR. |

The reader arms on resume and disarms on pause automatically. If NFC is unavailable or off, the SDK shows its own prompt dialogs at initialise.

### `VeyraWalletSdk` (wallet features)

Package `co.veyra.wallet.sdk`. Process singleton. The environment is set **once at initialisation** and applies to all subsequent SDK calls.

```kotlin
val sdk = VeyraWalletSdk.initialize(context, customerId, walletConfig, provider, activity = this)
```

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(context, customerId, config, provider, activity?)` | `context` — app context; `customerId` — the customer your app has authenticated (never leaves the device); `config` — `VeyraWalletSdkConfig`; `provider` — the same provider as SoftPOS's; `activity` — optional, pass it for automatic NFC activation | Initialises the singleton and signs `customerId` in. Idempotent — a repeat call re-binds the NFC lifecycle to the new activity. The same customer again changes nothing and downloads nothing; a different customer stops the previous customer's work and opens the new customer's cards. Throws `IllegalArgumentException` when `customerId` is blank or the provider is unusable (naming the problem), and `IllegalStateException` when a different kind of provider is already in use in this process. |
| `signOut()` (static) | — | The customer has logged out: stops every loop, poll, worker and observer the SDK runs for them. Their cards, keys, history and receipts stay on the device. Until the next `initialize` there are no cards and a tap is answered "application not found". |
| `getInstance()` | — | Current instance or `null`. |
| `tokenisationService` | — | The wallet service — every wallet operation hangs off it. |
| `getTokenRequestorId()` | — | The token requestor ID from config. |
| `getPaymentApplicationInstanceId()` | — | This install's `payment_application_instance_id` — **SDK-generated** (`VYRA` + 32 hex chars), minted on first use, persisted install-scoped, never backed up, new on reinstall. Read-only; the app cannot set or regenerate it. |
| `getAppVersion()` | — | The app version reported to the backend. |

---

## Customers: signing in, signing out, switching

The SDK keeps everything it stores **per customer**: wallet cards, payment keys, transaction history and receipts, the registered merchant and merchant transactions. One phone can be shared by several of your customers, and none of them sees another's data. The `customerId` you pass never leaves the device — the SDK only uses a hash of it to name that customer's storage.

Who is signed in is **your app's** to remember, not the SDK's. The SDK holds it only in memory, for the life of the process:

- **Pass the customer on every launch.** Call `initialize` with the authenticated customer's id each time your app starts (and on each screen that initialises an SDK, as today). Use a stable id from your own login — the same customer must always get the same id, or their data will not be found.
- **Same customer again — nothing happens.** Repeated `initialize` calls with the same id stop nothing and download nothing.
- **A different customer — the SDK switches.** The previous customer's work (key refreshes, status polls, pending-payment checks, background workers) is stopped, and the new customer's cards and merchant are opened from the device.
- **Sign out with `signOut()`** (`VeyraWalletSdk.signOut()`, `VeyraSoftPOSSdk.signOut()`, or `VeyraSdk.signOut()` in a combined app). It stops every loop, poll, worker and observer for the customer. Their cards, keys, history, merchant and transactions **stay on the device**: when they sign in again nothing is downloaded for a card that still has its keys, and a card without keys gets them in the background after sign-in.
- **Register observers again after `initialize`.** A sign-out and a switch to another customer both drop every observer registration (`onTransactionResolved`, `onTokenStatusChanged`, `onKeyStateChanged`, `onMerchantStatusChanged`, credit confirmations, refusal handlers). Register them right after each `initialize` call that signs a customer in.
- **Nobody signed in means no data.** After `signOut()`, and in a fresh process where your app has not called `initialize` yet (the app was killed, the phone restarted), there are no cards and no merchant, and an NFC tap from a terminal is answered "application not found" — no payment is made. A wallet or SoftPOS call that needs the customer's data — cards, payments, the stored merchant, transactions — throws `co.veyra.common.customer.NoActiveCustomerException` (an `IllegalStateException`); catch it to send the user to your sign-in. A request already on its way when the customer signs out still completes for that customer: its answer is kept with their data, and you hear about it when they sign in again. Initialise with the customer as soon as your app knows who they are.
- **The init-free reads take the customer.** `VeyraSoftPOSSdk.storedMerchant(context, customerId)` and `VeyraSoftPOSSdk.isMerchantRegistered(context, customerId)` read that customer's merchant without signing anyone in.
- **Re-registering a merchant needs no clear.** A successful registration overwrites the customer's stored merchant.

```kotlin
// Your login succeeded (or the app starts with a remembered session):
val sdk = VeyraWalletSdk.initialize(context, session.customerId, walletConfig, provider, activity = this)
TokenLifecycleObserver.onTokenStatusChanged { change -> /* ... */ }  // register again after every initialize

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
  blank `clientId`. SoftPOS throws `VeyraSdkException` with `MISSING_MANDATORY_CONFIG`; the wallet
  and `VeyraSdk` throw `IllegalArgumentException`. (Kotlin also refuses to compile a class that
  implements both, until it overrides `providerType` itself.)
- **One kind per process, with no fallback.** A `VeyraAssertionProvider` whose `assertion` returns
  nothing fails with `NOT_AUTHENTICATED`; the SDK never tries another method. Initialising again
  with the other kind throws `IllegalStateException`.
- **Initialise re-binds the provider.** The provider you pass on each `initialize` is the one the
  SDK uses from then on — background work included.
- **A call is never sent without credentials.** When the SDK cannot obtain a token the call fails
  with `NOT_AUTHENTICATED` and nothing is sent: the outcome is `NOT_SENT` with reason
  `NOT_AUTHENTICATED` (no response code, nothing stored or polled) in both SDKs.
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

// A combined app: the facade first, then each SDK on its own screen — the same provider each time.
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
  Return that body unchanged like any other: the SDK recognises it.
- **Return Veyra's response body exactly as Veyra returned it.** If Veyra answered with a non-200
  status, throw `VeyraRelayException(kind = OTHER, neverSent = false, httpStatus = <status>)` rather
  than inventing a body — the SDK treats it exactly as that status on a direct connection.

### The proxy provider's failure contract

A proxy provider signals failure by throwing `VeyraRelayException(kind, neverSent, httpStatus)`, and
**must say whether the request was sent**:

- `neverSent = true` **only** when the request provably never left the device — no network, or the
  connection was refused before anything was written. A payment then ends `FAILED` (`91`), or with
  `NO_NETWORK_CONNECTION` when the kind is `NO_NETWORK`.
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

**Cross-mode refusals.** Payments claim their mode at the point of use, so the only way a claim is refused is while the *other* mode's payment is genuinely mid-flight — the SDK then throws `SdkModeException` (e.g. from `makeCardPayment` during a wallet payment). Treat it as "finish or cancel the current payment first" and prompt the user. **This never occurs in a standalone single-product app.**

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
    if (response.success) {
        // Registered — merchant persisted on device; response.merchantId, response.terminalId,
        // response.merchantCategoryCode assigned by the backend. Unlock your Get-paid flow.
    } else {
        showError(response.message ?: "Registration failed")
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

**`MerchantRegistrationResponse` fields:** `success: Boolean`, `merchantId`, `terminalId`, `merchantCategoryCode`, `countryCode`, `acquirerId` (resolved by the gateway from your payment app provider — the SDK stores it and uses it on payments), `paymentAppProviderId`, `walletAccountId`, `merchantStatus`, `message`. Validation problems come back as `success = false` with a message — nothing throws.

---

#### `getBanks` (merchant)

Fetch the NUBAN settlement banks for the registration/update bank picker.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `callback` | **Mandatory** | `(List<NubanBank>?) -> Unit` — `null` on failure. Each `NubanBank` has `slug`, `name`, `institutionCode`. |

```kotlin
sdk.merchantService.getBanks { banks ->
    if (banks == null) { showError("Could not load banks"); return@getBanks }
    showBankPicker(banks)   // pass the chosen bank's institutionCode to registration
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
| `activate { response -> }` / `deactivate { response -> }` | Activate / deactivate this merchant on the backend. Callback gets `MerchantStatusResponse(merchantId, merchantStatus)` or `null` on failure. |
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
    if (response == null) showError("Update failed")
    // else response.merchantStatus
}
```

---

### Tap acceptance

#### `makeCardPayment`

Arm the reader for one sale and wait for the customer's tap. **Non-terminal events keep the reader armed** — mirror a physical terminal: an unsupported card or lost contact shows a transient hint on the same waiting screen; only real outcomes (approved / declined / pending / failed) end the payment.

```kotlin
val request = TransactionRequest.Builder(
    amount = 32500L,                                   // MINOR units: ₦325.00
    currency = "0566",                                 // ISO 4217 numeric, 4 digits
    merchantOrderId = "ORDER-42",                      // required: YOUR order id, never a key
).build()                                              // txType defaults to PURCHASE

// The transaction reference is minted by the SDK — read it back off the response
// (`response.merchantTransactionReference`) and key your receipts and lookups off that.

try {
    sdk.cardPaymentService.makeCardPayment(
        request = request,
        callback = { response -> runOnUiThread { handleResult(response) } },   // terminal outcomes only
        onCardDetected = { runOnUiThread { showHint("Card detected — hold steady") } },
        onUnsupportedCard = { runOnUiThread { showHint("Card not supported — try another card") } },  // stays armed
        onCardContactLost = { runOnUiThread { showHint("Hold the card steady") } }                    // stays armed
    )
} catch (e: SdkModeException) {
    // Combined apps only: wrong mode — prompt to finish/cancel the other flow first
}
```

**Parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `request` | **Mandatory** | `TransactionRequest` — see below. |
| `callback` | **Mandatory** | `(TransactionResponse) -> Unit`. Fires **once**, with the terminal outcome only (approved/declined/pending/error). Never fires for unsupported cards or lost contact. |
| `onCardDetected` | Optional | Card entered the field, processing started — hide the Cancel button, show "processing". |
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
| `merchantOrderId` | **Mandatory** | **Your** order / basket / invoice id for this sale (`String`, non-blank, at most 255 characters). A blank one ends `NOT_SENT` / `INVALID_REQUEST`, before anything is sent. Stored and echoed by the gateway and shown on the transaction detail and receipt, on your side and on the paying wallet's. **Unique per merchant** across all its payments, whatever their outcome: if another payment already uses it — approved, pending, declined or failed — the gateway refuses the payment with `94` / `FAILED` / `DUPLICATE_MERCHANT_ORDER_ID` and nothing is sent. A retry needs a new order id. |
| `txType` | Optional | `TxType.PURCHASE`, `REFUND`, `CASH_ADVANCE`, `RECURRING_PURCHASE`, `PRE_AUTH_COMPLETION`, `OTHER`. **Defaults to `PURCHASE`** when omitted — pass a value only for a non-purchase transaction. |
| `.performed3ds(Boolean)` | Optional | Whether your app performed 3-D Secure. Default `false`. |

> **The transaction reference is no longer yours to supply.** `Builder` used to take a mandatory
> `merchantTransactionReference`; the SDK now mints it (`{terminalId}-YYYYMMDDHHmmssSSS`) so the
> gateway can guarantee it is unique per merchant. Read it off `response.merchantTransactionReference`
> and key receipts, status lookups and credit confirmation off **that**. An app that keeps generating
> its own is keying those lookups on a value no gateway has ever seen. Use `merchantOrderId` for your
> own identifier instead — and note the two differ in kind: the reference identifies *an attempt*, the
> order id identifies *your sale*, which is why a retry gets a new reference but keeps the order id.

**Also on `cardPaymentService`:** `cancelPendingPayment()` (cancels an armed, untapped payment — the callback receives `NOT_SENT` / `CANCELLED`; no-op when nothing is pending), `isTransactionInProgress`, `isAwaitingCardTap`.

---

### Get paid by QR (merchant-presented)

The merchant keys the amount, the SDK creates a **gateway-signed payment context**, and your app renders the returned payload as a QR for the customer's wallet to scan. Poll the context until it settles.

#### `createContextPayment`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `merchantId` | **Mandatory** | Your registered merchant ID. |
| `amountMinorUnits` | **Mandatory** | Sale amount in minor units. |
| `currency` | **Mandatory** | ISO 4217 numeric (e.g. `"566"`; leading zeros accepted). |
| `onExpired` | Optional | Fired **once, on the main thread**, when the QR reaches its expiry — blank or replace the code so it can't be scanned once lapsed (a dimmed QR is still machine-readable). A new create supersedes the watch; `cancelQrExpiry()` stops it. |

Returns (`CreatedPaymentContext`): `txRef` (poll key), `mpmPayload` (**render this string verbatim as the QR**), `expiry` (ISO-8601), `kid`. The call returns `null` on failure (show retry).

#### `contextStatus`

Poll `contextStatus(txRef)` on a short interval (the sample uses 2.5 s). States: `PENDING` (QR live) → `IN_FLIGHT` (wallet push settling) → `APPROVED` / `DECLINED` (settled — `responseCode` carries the rail outcome) or `EXPIRED`. Convenience: `isSettled` (`APPROVED || DECLINED`), `isApproved`. On settlement the payment is also recorded in the merchant's local history under the same `txRef`, so receipts work like any other rail.

```kotlin
val client = ContextPaymentClient(context, Environment.TEST)   // uses the SoftPOS SDK's provider
val created = client.createContextPayment(
    merchantId, amountMinorUnits, "566",
    merchantOrderId = "ORDER-42",            // required: YOUR order id, unique per merchant
    onExpired = { blankQr(); showHint("This payment code has expired — start a new payment") },
) ?: run { showError("Could not create payment QR"); return }
// Throws VeyraSdkException(DUPLICATE_MERCHANT_ORDER_ID) when a payment already uses the order id,
// whatever its outcome — create the QR with a different one.

renderQrPayload(created.mpmPayload)          // encode the string verbatim into a QR bitmap
while (isActive) {
    delay(2500)
    val status = client.contextStatus(created.txRef) ?: continue
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

Decode and validate a scanned payload. **A throw means "not a payment QR"** — show a transient hint and stay armed for another scan; it is not a terminal failure.

| Returns | Fields |
|---|---|
| `ScannedCpmQr` | `dpan` (customer token PAN — display last 4), `amountMinorUnits` (the QR's own amount — confirm, never re-key), `currencyNumeric4` (e.g. `"0566"`), `tokenExpiryYymm`, `cardholderName` (the paying card's display name, e.g. `AFRIGO ****1234` — the same value a tap presents; **display only**, it rides outside the QR's cryptogram, so never branch a payment decision on it; null when the QR carries none) |

#### `cpmCustomerQrService.charge`

Charge the confirmed QR synchronously over the standard payment rail. A tampered payload or altered amount declines at the server.

```kotlin
val scanned = try {
    sdk.cpmCustomerQrService.inspect(text)
} catch (e: IllegalArgumentException) {
    showHint("Not a payment code — try again"); return   // stay armed for another scan
}
confirmScreen(scanned.amountMinorUnits, scanned.currencyNumeric4, "Card •••• ${scanned.dpan.takeLast(4)}")
// on merchant confirm:
lifecycleScope.launch {
    val response = sdk.cpmCustomerQrService.charge(scanned, merchantOrderId = "ORDER-42")
    // Branch on the status, never on responseCode. The SDK minted the reference; take it from
    // the response for the receipt lookup.
    when (response.responseStatus) {
        ResponseStatus.APPROVED -> showApproved(response.merchantTransactionReference)
        ResponseStatus.DECLINED, ResponseStatus.FAILED ->
            showDeclined(response.message, response.responseStatusReason)
        else -> showProcessing(response.merchantTransactionReference)  // PENDING — never re-charge
    }
}
```

`charge(scanned, merchantOrderId = …)` returns `PaymentResponse`: the response triple — `responseCode` (the wire literal: display it, never branch on it), `responseStatus` (`APPROVED` / `DECLINED` / `FAILED` / `PENDING` — **branch on this**; `PENDING` means the outcome is unknown, so never re-charge) and `responseStatusReason` — plus `message`, `transactionId`, `merchantStatus`, `merchantTransactionReference` (**the SDK-minted reference — fetch the receipt with `generateTransactionReceipt(it)`**) and `merchantOrderId` echoed back. A transport failure throws and records nothing.

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
// (APPROVED / DECLINED / PENDING / FAILED), responseCode, responseStatusReason (the stated
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

`PENDING` means the outcome is not yet known (the SDK keeps polling and updates the stored row); `FAILED` means the payment never reached the server. Hide receipt affordances while a row is `PENDING`.

**How the SDK waits for a `PENDING` row.** You do not have to poll, schedule anything, or keep a screen open — the SDK asks on its own, with **exponential backoff**: the first re-checks come within seconds (most payments settle at once) and the interval doubles to a steady state of roughly **once an hour**. It keeps that up for **30 days** from the transaction date, and then stops asking.

**Stopping is not an outcome.** When the 30 days elapse the row keeps whatever status it has — still `PENDING`, which is still true — and the SDK simply takes it off the poll list. It never writes `FAILED`, `DECLINED` or any other verdict of its own: only the backend decides what a payment was. So treat a long-`PENDING` row as *unresolved*, not as failed, however old it is.

**Let the merchant ask on demand — `refreshTransactionStatus`.** The SDK polls a pending transaction for you with **exponential backoff**, and **stops after 30 days**. Polling never invents an outcome — a row that ages out simply stops being asked about and stays `PENDING`. Expose **`refreshTransactionStatus`** in your UI so the merchant can ask on demand, which is the route for anything still pending after the window closes.

A failed poll — device offline, gateway unreachable, an unreadable answer — changes nothing at all: the SDK backs off and asks again, and the row is left exactly as it was. "We could not reach the server" is never recorded as "the payment failed".

#### `refreshTransactionStatus`

```kotlin
// suspend — call from a coroutine. Returns the updated stored row, or null if unknown here.
val updated = sdk.transactionService.refreshTransactionStatus(reference)
```

The on-demand counterpart to `getTransaction`, which only reads what the device already knows. It
asks the gateway about that one transaction now and writes the answer into the same local store the
background sweep writes, so an on-demand check and a background check can never disagree.

- **Show it only while the row is `PENDING`.** A settled row has nothing to ask, and offering the
  action implies the outcome might still change. Hide it the moment the row is terminal.
- **It works past the 30-day window**, and on a row the sweep never had on its list — that is what it
  is for.
- **It is not a way to force an outcome.** A payment that is still unsettled answers `PENDING` again.
  Show a brief "still processing" note; do not retry in a loop.
- **A failed call throws and changes nothing** — `NO_NETWORK_CONNECTION` when the device is offline.
  Show the error and leave the row pending; that is the correct handling, not an error state on the
  transaction.
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
// suspend — call from a coroutine. Returns the updated stored row, or null if unknown here.
val updated = sdk.transactionService.refreshCreditConfirmation(reference)
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
- **A failed call throws and changes nothing** — `SdkErrorCode.NO_NETWORK_CONNECTION` when the device
  is offline. Show the error and leave the credit line reading "not confirmed yet".
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

---

## Wallet — making payments

Everything hangs off `sdk.tokenisationService`; callbacks arrive on the main thread; every call ends in an outcome you branch on by `responseStatus` and `responseStatusReason` — see [Errors and outcomes](#errors-and-outcomes).

### Add a card (digitisation)

Flow: `getBanks` → `checkAccountEligibility` → `digitizeAccount`. On success the SDK receives, decrypts and stores the payment material on-device — the card can pay immediately (`APPROVED`) or after activation (`APPROVE_REQUIRE_AUTH`).

---

#### `getBanks`

Fetch the supported NUBAN banks, optionally filtered by account number. Call as soon as the user finishes entering their account number so the list is ready for the bank picker.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `accountNumber` | No | 10-digit NUBAN. When supplied, returns only banks linked to that account; `null`/blank returns all supported banks (use as the "can't find my bank" fallback). |
| `callback` | **Mandatory** | `(Result<List<Bank>>) -> Unit`. Each `Bank` has `slug`, `name`, `institutionCode`. |

```kotlin
sdk.tokenisationService.getBanks(accountNumber = "1234567890") { result ->
    result.fold(
        onSuccess = { banks ->
            when {
                banks.isEmpty()   -> fetchAllBanks()          // fallback to the unfiltered list
                banks.size == 1   -> proceedWithBank(banks.first())
                else              -> showBankPicker(banks)
            }
        },
        onFailure = { error -> showError("Could not load banks: ${error.message}") }
    )
}
```

---

#### `checkAccountEligibility`

Check whether an account can be tokenised before digitising. Eligible when `responseCode == "APPROVED"`.

**`VerifyAccountParams.Builder`:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `accountNumber` | **Mandatory** | The customer's bank account number (10-digit NUBAN). |
| `institutionCode` | **Mandatory** | From `Bank.institutionCode`. |
| `walletAccountId` | **Mandatory** | The customer's registered email address or phone number — a value the issuer already holds for this account, not an internal id of your own. The SDK derives a hash from it; it is not sent raw. The issuer checks the hash against its own records, so an identifier it has never seen (a GUID, a placeholder) fails eligibility. |
| `.accountHolderName(String)` | Optional | Full name of the account holder. |
| `.accountNumberSource(AccountNumberSource)` | Optional | How the number was captured: `MANUAL`, `SCAN`, `CARD_ON_FILE`, `RECENT`, `APPLICATION`, `EXISTING_TOKEN`, `OTHER`. Default `MANUAL`. |
| `.numberOfActiveTokens(Int)` | Optional | Tokens already active on this device. Default `0`. |

```kotlin
val params = VerifyAccountParams.Builder(
    accountNumber = "1234567890",
    institutionCode = "000000",
    walletAccountId = "ada@example.com"
)
    .accountHolderName("Ada Obi")
    .accountNumberSource(AccountNumberSource.MANUAL)
    .build()

sdk.tokenisationService.checkAccountEligibility(params) { result ->
    result.fold(
        onSuccess = { response ->
            if (response.responseCode?.uppercase() == "APPROVED") proceedToDigitise()
            else showError(response.message ?: "This account is not eligible")
        },
        onFailure = { error -> showError("Eligibility check failed: ${error.message}") }
    )
}
```

`VerifyAccountResponse`: `responseCode` (`"APPROVED"` = eligible), `message`.

---

#### `digitizeAccount`

Tokenise the account: the SDK attests the device, sends the request, and on success decrypts and stores the token material on-device. The **tokenisation recommendation is your app's business decision** — the SDK never assumes one.

**`TokenizationRequestParams.builder` (all ten mandatory):**

| Parameter | Description |
|-----------|-------------|
| `accountNumber` / `institutionCode` | The account being tokenised. |
| `accountHolderName` | Full name. |
| `walletProviderTokenizationRecommendation` | `TokenizationRecommendation.APPROVE` / `DECLINE` / `REQUIRE_ADDITIONAL_AUTHENTICATION` — **your** risk decision for this request. |
| `consumerIdentifier` | Your stable identifier for this consumer (e.g. a GUID). |
| `bvn` | The customer's BVN. |
| `accountHolderAddress` | Postal address. |
| `mobileNumber` | The customer's mobile number. |
| `walletAccountId` | Same identifier as in eligibility. |
| `emailAddress` | The customer's email. |

Optional chains: `.clientRequestId(String)` (default: SDK-generated UUID), `.accountNumberSource(...)`, `.walletProviderDeviceScore(TrustScore)` (default `LOW_TRUST`), `.walletProviderAccountScore(TrustScore)` (default `LOW_TRUST`), `.walletProviderTokenizationRecommendationReasons(List<TokenizationRecommendationReason>)` (default `[UNABLE_TO_ASSESS]`). `TrustScore`: `UNTRUSTED`, `LOW_TRUST`, `MODERATE_TRUST`, `TRUSTED`, `HIGHLY_TRUSTED`. A blank required field throws `TokenizationRequestValidationException` synchronously.

```kotlin
val params = TokenizationRequestParams.builder(
    accountNumber = accountNumber,
    institutionCode = institutionCode,
    accountHolderName = "Ada Obi",
    walletProviderTokenizationRecommendation = TokenizationRecommendation.APPROVE,
    consumerIdentifier = customerId,             // your stable id for this customer
    bvn = bvn,
    accountHolderAddress = address,
    mobileNumber = mobileNumber,
    walletAccountId = "ada@example.com",
    emailAddress = "ada@example.com",
)
    .accountNumberSource(AccountNumberSource.MANUAL)
    .walletProviderDeviceScore(TrustScore.TRUSTED)
    .walletProviderAccountScore(TrustScore.HIGHLY_TRUSTED)
    .walletProviderTokenizationRecommendationReasons(listOf(TokenizationRecommendationReason.GOOD_ACTIVITY_HISTORY))
    .build()

sdk.tokenisationService.digitizeAccount(params) { response ->
    when (response.responseCode?.uppercase()) {
        "APPROVED" -> showCardAdded()                       // provisioned; can pay now
        "APPROVE_REQUIRE_AUTH" ->                           // provisioned; needs activation
            showActivationMethods(response.tokenUniqueReference, response.activationMethods)
        else -> showError(response.message ?: response.error?.message ?: "Could not add card")
    }
}
```

`TokenisationResponse`: `responseCode` (`APPROVED` / `APPROVE_REQUIRE_AUTH` / `DECLINED`), `tokenUniqueReference` (the card's identity for every later call), `activationMethods` (non-null when activation is needed), `isSuccess`, `status`, `message`, `error` (`TokenisationError(code, message, details)`).

---

### Activation

When digitise returns `APPROVE_REQUIRE_AUTH`, the response carries the issuer's **activation methods**. Branch on each entry's `medium`:

| Medium | UI | Then |
|---|---|---|
| `MASKED_EMAIL` / `MASKED_MOBILE_PHONE` | Show the masked contact, let the user pick | `requestActivationCode` → OTP entry → `activate` |
| `CALL_CENTER_PHONE` / `AUTOMATED_CALL_CENTER_PHONE` | Show the phone number + "Call now" | `observeActivation` while they call |
| `WEBSITE` | Show the domain + "Open website" | `observeActivation` |
| `MOBILE_APPLICATION` | "Open your bank's app" | `observeActivation` |

---

#### `requestActivationCode`

OTP delivery for the `MASKED_EMAIL` / `MASKED_MOBILE_PHONE` methods.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `tokenUniqueReference` | **Mandatory** | The card being activated. |
| `selectedActivationMethod` | **Mandatory** | The chosen medium string (e.g. `ActivationMethods.MASKED_MOBILE_PHONE`). |
| `reasonCode` | **Mandatory** | `ADD_CARD`, `VERIFY_ACCOUNT` or `OTHER`. |

```kotlin
sdk.tokenisationService.requestActivationCode(
    tokenUniqueReference = ref,
    selectedActivationMethod = method,          // e.g. ActivationMethods.MASKED_MOBILE_PHONE
    preferredActivationChannel = method,
    reasonCode = ActivationCodeReason.ADD_CARD
) { result ->
    result.fold(
        onSuccess = { response ->
            if (response.status?.uppercase() == "FAILURE") showError(response.message)  // check status even on success!
            else response.expirationDateTime?.let { startCountdown(it) }
        },
        onFailure = { e -> showError(e.message) }
    )
}
```

`ActivationCodeResponse`: `tokenUniqueReference`, `expirationDateTime` (ISO-8601 — drive your countdown from it), `status` (`SUCCESS` / `FAILURE`), `message`, `failureCode` (typed `ActivationFailureCode`, null on success) + `failureCodeRaw`. **Check `status` even inside a successful result**, and branch on `failureCode`, never on `message`: `CODE_REQUEST_RATE_LIMITED` means disable "resend" until later but keep the flow open; `ACTIVATION_LOCKED` is terminal — end the flow and point the user at their issuer. Codes are limited-attempt and rate-limited — see [Activation — `status` + `failureCode`](#activation--status--failurecode).

#### `activate`

Submit the code the customer received. Success when `status == "SUCCESS"`.

```kotlin
sdk.tokenisationService.activate(tokenUniqueReference = ref, activationCode = code) { result ->
    result.fold(
        onSuccess = { response ->
            when {
                response.status?.uppercase() == "SUCCESS" -> navigateToWallet()
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
        },
        onFailure = { e -> showError(e.message) }
    )
}
```

`ActivateResponse` failure fields (all null on success): `failureCode` — typed `ActivationFailureCode`, one of `TOKEN_NOT_FOUND`, `TOKEN_NOT_ACTIVATABLE`, `ACTIVATION_LOCKED`, `NO_PENDING_ACTIVATION`, `CODE_EXPIRED`, `CODE_INVALID`, `MAX_ATTEMPTS_EXCEEDED`, `CODE_REQUEST_RATE_LIMITED`, `INVALID_REQUEST`, `ACTIVATION_FAILED`, or `UNKNOWN` for a code newer than this SDK (raw value in `failureCodeRaw`); `attemptsRemaining` — code attempts left where a cap applies (0 when exhausted/locked); `recommendDelete` — `RecommendDelete.MUST` / `MAY` after an exhausted cycle (delete the dead token rather than leaving it in the card list), null otherwise (raw in `recommendDeleteRaw`).

#### `observeActivation` (+ pause / resume / stop)

For the out-of-band methods (call centre / website / issuer app) the activation happens elsewhere — observe the token until it activates. The SDK polls every 10 s for up to 5 minutes; callbacks arrive on the main thread; observing the same token again replaces the previous observer.

| Parameter | Description |
|-----------|-------------|
| `tokenUniqueReference` | The card being activated. |
| `onActivated` | Fires exactly once when the token becomes active — navigate to the wallet. |
| `onTimeout` | After 5 minutes without activation — show a fallback message (the SDK keeps checking in the background thereafter). |
| `onError` | Optional — each failed check (polling continues). |

Wire the lifecycle: `pauseActivationObserver(ref)` when the screen backgrounds, `resumeActivationObserver(ref)` on return (the timeout clock keeps running while paused — if it lapsed, `onTimeout` fires immediately), `stopActivationObserver(ref)` when the screen is dismissed. Extras: `tokenStatus(ref) { Result<TokenStatus> }` for a one-shot five-valued check (`ACTIVE` / `PENDING_ACTIVATION` / `SUSPENDED` / `DEACTIVATED` / `EXPIRED`, plus `UNKNOWN` for a value newer than this SDK — lets you say "suspended — contact your bank" vs "pending — enter your code" vs "expired — re-add the card"), `checkTokenStatus(ref) { Result<Boolean> }` (deprecated — flattens the same read to a boolean), `hasActivationObserver(ref)`.

```kotlin
sdk.tokenisationService.observeActivation(
    tokenUniqueReference = ref,
    onActivated = { refreshCards() },
    onTimeout = { showHint("Still pending — we'll keep checking") }
)
// onPause → pauseActivationObserver(ref); onResume → resumeActivationObserver(ref)
// onDestroy → stopActivationObserver(ref)
```

---

### Cards & tokens

#### `getTokens`

The wallet's cards, from the SDK's local registry — no network call.

```kotlin
// Synchronous local read — run off the main thread for large wallets:
val tokens = withContext(Dispatchers.IO) { sdk.tokenisationService.getTokens() }
val active = tokens.firstOrNull { it.isActive }
// Grey out and disable pay actions while the active card requires online refresh:
payButtons.isEnabled = active?.requiresOnline != true
```

`Token`: `tokenId`, `tokenUniqueReference`, `devicePAN`, `cardHolderName` (the card's display name — scheme label + masked last four, e.g. `AFRIGO ****1234`; not a person's name, and the same value the card presents in EMV tag `5F20`), `expiryDate`, `cardScheme`, `cardType`, `isActive` (the card payments use), `activationMethods` (non-null while activation is pending), `transactions` (last 3), `requiresOnline` (see below), `deviceNotBound` (see below), helpers `getMaskedPAN()` / `getLastFourDigits()`.

**`requiresOnline`** — `true` when the card cannot pay until the wallet has been **online** to refresh it. Render the card greyed-out and non-tappable and prompt the user to connect; the flag derives fresh on every read and clears on its own once the SDK's automatic refresh succeeds. There is no manual "refresh keys" call — key management is entirely SDK-owned.

**`deviceNotBound`** — `true` when the backend refused to issue payment keys for this card to this device: the card was added on another phone, or on this one before the app was reinstalled or its data wiped. The SDK stops asking for keys for that card. Render it greyed-out and non-tappable with "Add this card again on this phone" — **not** "go online": while it is `true`, `requiresOnline` is always `false`, because connecting cannot fix it. Removing the card and adding it again on this phone is the only remedy, and removal is the only thing that clears the flag.

#### Handling card states in your UI

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
- **Gate the affordances, not just the card face.** Disabling only the card image but leaving a "Scan to pay" button live produces the refusal errors at pay time; disable the actions too, and treat the typed refusals (the `ONLINE_REQUIRED:` / `TOKEN_NOT_ACTIVE:` / `DEVICE_NOT_BOUND:` message prefixes) as the backstop, not the primary UX.

The sample's card list + pay-screen gating:

```kotlin
val tokens = withContext(Dispatchers.IO) { sdk.tokenisationService.getTokens() }

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

#### `setActiveToken`

Select the card payments use (at most one card is active). Selecting the active card also arms it for tap-to-pay and registers the tap outcome callbacks:

```kotlin
sdk.tokenisationService.setActiveToken(
    tokenId = token.tokenId,
    activity = this,
    onTransactionStarted = { tokenId -> /* tap started */ },
    onTransactionCompleted = { response ->
        runOnUiThread {
            // response.status: "APPROVED" / "DECLINED" / "ERROR"; response.amount (minor units);
            // response.reference for the history row
            showTapResult(response)
        }
    },
    onActivationFailed = { message ->
        // e.g. "Card needs to be re-digitized. Please remove and add again."
        runOnUiThread { showError(message); refreshCards() }
    },
    // Payments refused before anything was sent (1.0.12+) — see "Refused payments" below.
    onRequireOnline = { event ->
        runOnUiThread { showError("Connect to the internet to pay ${event.amountMinorUnits}") }
    },
    onAmountExceedCardLimit = { event ->
        runOnUiThread { showError("That amount is too large for this card — try another card") }
    },
    onDeviceNotBound = { event ->
        runOnUiThread { showError("Add this card again on this phone to pay with it") }
    }
)
```

Tap callbacks arrive on an SDK thread — wrap UI updates in `runOnUiThread`. Guard with `hasProvisioningData(tokenId)` if you keep display records for cards whose material may be gone.

#### `getActiveToken`

Read the currently selected card, or `null` when none is selected — the counterpart to
`setActiveToken`, and the way a screen answers "which card would pay right now?" without inferring
it from a list.

```kotlin
val active: Token? = sdk.tokenisationService.getActiveToken()
```

Synchronous, and a read of stored state rather than a network call. It returns `null` both when no
card has ever been selected and when the selected card has since been removed, so treat `null` as
"prompt the customer to choose" rather than as an error.

#### `deactivateToken`

`deactivateToken(ref) { Result<TokenStatusUpdateResponse> }` deactivates on the backend, then wipes every on-device artefact for the card and promotes the next card to active.

```kotlin
sdk.tokenisationService.deactivateToken(ref) { result ->
    result.fold(
        onSuccess = { refreshCards() },
        onFailure = { e -> showError("Failed to remove card: ${e.message}") }
    )
}
```

---

### Scan to pay (merchant QR)

The customer scans a merchant's get-paid QR: **inspect** (on-device verification) → confirm screen → **authenticate** (biometric) → **pay**.

#### `inspectScannedQr`

Synchronous, on-device verification of the scanned payload — gateway signature against the SDK's pinned keys, plus expiry. **Only a verified result may reach your confirm screen; every rejection must end the flow.**

```kotlin
when (val scan = sdk.tokenisationService.inspectScannedQr(payload)) {
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

Three failures can come back on the same callback, and they need different UI:

| Code in the message | What happened | What to do |
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

**Branch on `responseStatus`, not on `approved` or the response code.** The push is a synchronous call, but its *outcome* can still be unknown: the gateway answers `PENDING` when a hop below it timed out (`68`), errored (`06`/`96`) or is still settling (`09`). That is not a refusal — the SDK records the payment as unresolved and keeps polling it until the gateway states a final outcome, which then shows on the history row. `approved` is a convenience for the happy path only (`responseStatus == "APPROVED"`); it is `false` for a pending payment as well as a declined one.

```kotlin
sdk.tokenisationService.payScannedContext(ctx) { result ->
    result.fold(
        onSuccess = { outcome ->
            when (outcome.responseStatus?.uppercase()) {
                "APPROVED" -> showApproved(outcome.message)
                "DECLINED", "FAILED" -> showDeclined(outcome.message, outcome.responseStatusReason)
                // Absent or anything else: not yet known. Say so, and point at history —
                // never show a refusal for a payment that may still settle.
                else -> showPending(outcome.responseCode)
            }
        },
        onFailure = { e -> showDeclined(e.message, null) }  // see Errors and outcomes
    )
}
```

---

### Show QR to pay (customer-presented)

The customer keys nothing at the till: your app asks the amount first (the merchant states it), authenticates, and renders a **dynamic payment QR** with the amount cryptographically bound inside. Fully offline — the merchant's SoftPOS submits the payment; the outcome lands in history via reconciliation.

#### `showQrToPay` + `cancelQrExpiry`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `amountMinorUnits` | **Mandatory** | The merchant-stated amount — bound into the QR's cryptogram; the merchant's scan charges exactly this or fails. |
| `onExpired` | Optional | Fired **once, on the main thread**, when the QR lapses — blank or replace the code (a dimmed QR is still scannable). A new render supersedes the watch; `cancelQrExpiry()` stops it (call on screen teardown). |

The SDK raises the authentication sheet itself — **one gesture per QR**; regenerating after expiry is a fresh payment attempt and asks again.

```kotlin
sdk.tokenisationService.showQrToPay(amountMinorUnits, onExpired = { blankQr() }) { result ->
    result.fold(
        onSuccess = { qr -> renderQr(qr.payload); pollForSettlement(qr) },
        onFailure = { e -> showError(e.message) }   // includes AUTH_CANCELLED / AUTH_FAILED / AUTH_UNAVAILABLE
    )
}
// dialog/screen teardown:
sdk.tokenisationService.cancelQrExpiry()
```

The result (`CpmPaymentQr`): `payload` (**render as the QR**), `amountMinorUnits`, `currencyNumeric`, `expiresAtEpochMillis`, `transactionHash` — this render's unique hash. To show "paid ✓" on the customer's screen, poll while the QR is up: call `reconcilePendingTransactions`, then look for the history row whose `transactionHash` matches:

```kotlin
while (isActive) {
    delay(3000)
    val done = CompletableDeferred<Unit>()
    sdk.tokenisationService.reconcilePendingTransactions { done.complete(Unit) }
    done.await()
    val row = withContext(Dispatchers.IO) {
        sdk.tokenisationService.getTransactions(tokenUniqueReference, 10)
            .firstOrNull { it.transactionHash == qr.transactionHash }
    }
    if (row != null) { onSettled(row.authorizationStatus == "APPROVED"); break }
}
```

---

### History, receipts & maintenance

#### `getTransactions`

The card's full local history across every rail (tap, scanned QR, shown QR), most recent first. No network call.

```kotlin
val transactions = sdk.tokenisationService.getTransactions(tokenUniqueReference, 50)
```

`TransactionSummary` fields: `merchantName`, `amountInMinorUnit`, `transactionCurrencyCode` (4-digit ISO 4217, e.g. `"0566"`), `authorizationStatus` (`PENDING` / `APPROVED` / `DECLINED` / `FAILED`; `null` on legacy rows — treat as indeterminate), `responseCode` (the outcome's code, e.g. `"00"`, `"51"` — verbatim from the rail that resolved the row; `null` until resolved; quote this literal in support conversations), `responseStatusReason` (the outcome's stated cause, e.g. `"INSUFFICIENT_FUNDS"` — a plain string to display, never parse; `null` until resolved), `entryMethod` (`"TAP"`, `"QR_GENERATED"` — showed a QR, `"QR_SCANNED"` — scanned a merchant QR; `null` legacy — show nothing rather than guess), `merchantLocation`, `transactionHash` (join key to a receipt), `localTransactionDateTime` / `atEpochMillis`, `merchantTransactionReference`, `merchantId`, `merchantOrderId` (the merchant's own order/basket id for the sale — the id the merchant's systems know it by, so a customer can quote it at the counter; a scanned-QR row carries it from payment time, tap and generated-QR rows learn it from the status poll, so `null` on a still-open row means "not learned yet", not "no order id"; for display and reconciliation — receipts and status refreshes key off `transactionHash` / `merchantTransactionReference`), plus the five beneficiary-credit fields below.

##### `onTransactionResolved` (wallet) — a `PENDING` payment reached its outcome

A wallet payment that gets no immediate answer is stored and polled by the SDK until the backend
settles it — seconds, or days. Unresolved rows are visible in history, so the customer can be
looking at the row at the moment it settles. This is how your app hears it without polling:

```kotlin
WalletTransactionResolvedObserver.onTransactionResolved { resolution ->
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
on the main thread.

##### Merchant credit confirmation (wallet side)

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

##### `refreshCreditConfirmation` — let the customer ask on demand

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
val updated = sdk.tokenisationService.refreshCreditConfirmation(transactionHash)
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
- **A failed call throws and changes nothing** — the message carries `NO_NETWORK_CONNECTION` when the
  device is offline. Show the error and leave the credit line reading "not confirmed yet".

#### `reconcilePendingTransactions`

Reconcile still-`PENDING` rows against the backend. Call it opportunistically: on returning to the foreground, on pull-to-refresh, and on a short loop while a shown QR is on screen (that rail is offline — reconciliation is how its outcome arrives).

```kotlin
sdk.tokenisationService.reconcilePendingTransactions { /* refresh the list */ }
```

#### `refreshTransactionStatus`

```kotlin
// suspend — call from a coroutine. Returns the updated row, or null if no row carries that hash.
val updated = sdk.tokenisationService.refreshTransactionStatus(summary.transactionHash)
```

The **per-transaction** counterpart to `reconcilePendingTransactions`, which asks about every open
row and returns nothing — this one answers about the row the customer is actually looking at, keyed
by its `transactionHash`.

The SDK polls a pending transaction for you with **exponential backoff**, and **stops after 30
days**. Polling never invents an outcome — a row that ages out simply stops being asked about and
stays `PENDING`. Expose **`refreshTransactionStatus`** in your UI so the customer can ask on demand,
which is the route for anything still pending after the window closes.

The same five rules as the merchant method above apply: show it only while the row is `PENDING`; it
works past the window and off the sweep's list; it never forces an outcome; a failed call throws
(`NO_NETWORK_CONNECTION` when offline) and leaves the row untouched; and there is no SDK-side
throttle, so disable the button while a call is in flight.

#### `processReceipt` / `getLastReceipts` / `getReceiptForTransaction`

Scan a merchant's **receipt QR** to store the customer's copy. The SDK decodes, validates that the receipt matches a payment this wallet actually made, de-duplicates and stores it.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `qrPayload` | **Mandatory** | The scanned contents — raw JSON or base64. |
| `expectedTransactionHash` | Optional | Set it when the scan is launched **from a specific transaction's screen** — a receipt for a different transaction is rejected instead of silently attaching elsewhere. `null` = unscoped. |

```kotlin
val base64 = Base64.encodeToString(scanned.toByteArray(), Base64.NO_WRAP)
sdk.tokenisationService.processReceipt(base64, expectedTransactionHash = summary.transactionHash) { result ->
    result.fold(
        onSuccess = { receipt -> showReceipt(receipt) },
        onFailure = { e -> showError("Receipt didn't match this transaction") }
    )
}

val receipts = sdk.tokenisationService.getLastReceipts(limit = 100)      // most recent first, local
val receipt  = sdk.tokenisationService.getReceiptForTransaction(hash)    // by transactionHash, or null
```

---

## Errors and outcomes

Every SDK call ends in one of two ways:

1. **It throws** — only when your app has not set the SDK up correctly. These are bugs in your
   integration, found during development. You fix the code; you never show them to a user. See
   [Setup errors (thrown)](#1-setup-errors-thrown).
2. **It returns an outcome** — for everything that happens while processing a request: approved,
   declined, failed, still pending, or never sent. The outcome is always the same fields:
   `responseStatus` and `responseStatusReason`, plus `responseCode` on payments and add card. See
   [Processing outcomes](#2-processing-outcomes-the-response-triple).

### 1. Setup errors (thrown)

**What they mean.** The SDK cannot run the call at all, because of how your app called it. Nothing
was sent. Retrying will never help until the code changes.

**How to use them.** Let them surface in development: crash, log, assert. Fix the integration. Do not
build user-facing screens for them, and do not offer the user a retry.

| Error | When it happens | Fix |
|---|---|---|
| **Not initialised** — `VeyraSdk.softpos` / `VeyraSdk.wallet` throw `IllegalStateException`; `VeyraSoftPOSSdk.getInstance()` / `VeyraWalletSdk.getInstance()` return `null` | A call is made before `initialize` | Initialise the SDK first, at app start, and use the instance `initialize` returns. |
| **Not signed in** — `co.veyra.common.customer.NoActiveCustomerException` (an `IllegalStateException`) | A call that needs the customer's data (cards, payments, the stored merchant, transactions) is made after `signOut()`, or in a fresh process before `initialize` | Call `initialize` with the customer before using the SDK. See [Customers](#customers-signing-in-signing-out-switching). |
| **Invalid configuration** — wallet: `IllegalArgumentException`; SoftPOS: `VeyraSdkException` with `errorCode = MISSING_MANDATORY_CONFIG`; both: `IllegalArgumentException` for a blank `customerId` | `initialize` was given a provider it can't use: none, one that implements both kinds, a `providerType` that doesn't match the interface, or a blank `clientId` | Pass a valid provider. The message names the problem. |
| **Provider kind changed** — `IllegalStateException` | `initialize` was given a provider of a different kind from an earlier `initialize` in the same process (an assertion provider, then a proxy provider) | Use one kind of provider for the life of the process. See [Choosing a provider](#choosing-a-provider). |
| **Combined app set up without the facade** — `SdkModeException` | Both SDKs are in the app but were initialised directly rather than through `VeyraSdk`, so the SDK cannot arbitrate NFC between them | Initialise through `VeyraSdk` and let it own the mode. See [Exclusive mode](#exclusive-mode-combined-apps). **Never occurs in a single-SDK app.** |

**Cancelling is not an error.** When you cancel an armed payment with
`cardPaymentService.cancelPendingPayment()`, the reply to the cancel — including "too late"
(`TOO_LATE_TO_CANCEL_REQUEST_ALREADY_SENT`, `TOO_LATE_TO_CANCEL_TASK_ALREADY_PROCESSED`) — is the
answer to the cancel, not an error and not an outcome of the payment you tried to cancel.

### 2. Processing outcomes (the response triple)

**What they mean.** The SDK ran the call. The outcome tells you what happened and, through the
status, whether it is safe to retry.

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
| `contextStatus(txRef)` (merchant QR) | `PaymentContextStatus` | `responseCode` | `responseStatus` | `responseStatusReason` |
| `transactionService.getTransaction` / `getLastTransactions` / `refreshTransactionStatus` | `TransactionInfo` | `responseCode` | `transactionStatus` | `responseStatusReason` |
| `TransactionResolvedObserver.onTransactionResolved` | the resolution | `responseCode` | `status` | `reason` |
| `tokenisationService.payScannedContext(ctx)` (scan to pay) | `MpmPushOutcome` | `responseCode` | `responseStatus` | `responseStatusReason` |
| `getTransactions(...)` (wallet history) | `TransactionSummary` | `responseCode` | `authorizationStatus` | `responseStatusReason` |
| `digitizeAccount(...)` (add card) | `TokenisationResponse` | — | `responseStatus` | `responseStatusReason` |
| `checkAccountEligibility(...)` | `VerifyAccountResponse` | — | `responseStatus` | `responseStatusReason` |

On `TokenisationResponse` and `VerifyAccountResponse`, `responseCode` is **not** an ISO payment code:
it is the add-card decision (`APPROVED` / `APPROVE_REQUIRE_AUTH` / `DECLINED` — see
[Table C](#table-c--veyra-token-service-wallet-add-card-activation-cards)). The activation calls carry
their reason in a typed `failureCode` instead (see [Activation](#activation--status--failurecode)).

#### The five statuses

| `responseStatus` | What it means | Did the request reach Veyra? | Merchant order ID used up? (payments) | What your app does |
|---|---|---|---|---|
| `APPROVED` | It was done | Yes | Yes | Complete the sale / show the card |
| `DECLINED` | It was done, and someone with authority refused (issuer, bank, risk rules) | Yes | Yes | Show the reason. Don't retry automatically; a new attempt needs a **new** order ID |
| `FAILED` | Veyra received it but could not perform it | Yes | Yes | Fix what the reason names; retry with a **new** order ID |
| `PENDING` | It may have been done; the outcome is not known yet | Maybe | Yes | **Never retry.** The SDK keeps checking and tells you when it's final; you can also look it up by merchant order ID |
| `NOT_SENT` | It never reached Veyra. Nothing was done | No | **No** | Fix what the reason names (connect, authenticate, …) and retry with the **same** order ID |

Four rules follow from this table:

- **`NOT_SENT` never has a `responseCode`.** A code only ever means something on the network
  answered, or failed to.
- **`PENDING` is never a failure.** Never show it as failed and never charge again. Show "We're
  checking this payment" and wait for the SDK's update. See
  [Holding a `PENDING` payment](#holding-a-pending-payment-and-being-told-when-it-settles).
- **Add a card (digitisation) is different.** There is no reference and no lookup. A digitisation
  that gets no answer ends `FAILED`, never `PENDING`, because the card's keys can never be delivered
  later. Start a new digitisation.
- **The merchant order ID.** A payment that reached Veyra uses its order ID up, whatever the
  outcome. A retry with the same ID is refused as `DUPLICATE_MERCHANT_ORDER_ID`, and that refusal
  includes the original payment's outcome. Look the payment up by merchant order ID to find out what
  happened to it.

#### Table A — Outcomes the SDK itself produces

| Status | Code | Reason | When | What your app does |
|---|---|---|---|---|
| `NOT_SENT` | — | `INVALID_REQUEST` | An argument is invalid (e.g. blank merchant order ID, amount ≤ 0, a missing digitisation field) | Fix the call |
| `NOT_SENT` | — | `NO_NETWORK_CONNECTION` | The phone has no internet connection | Ask the user to connect; retry with the same order ID |
| `NOT_SENT` | — | `SDK_UNABLE_TO_CONNECT` | The phone is online but could not connect to Veyra | Try again shortly with the same order ID |
| `NOT_SENT` | — | `NOT_AUTHENTICATED` | The SDK could not get credentials (your assertion or proxy provider returned nothing, or the token endpoint refused) | Check your provider; retry |
| `NOT_SENT` | — | `CANCELLED` | Your app cancelled the call before it was sent (`cancelPendingPayment()`) | Back to the previous screen |
| `NOT_SENT` | — | `AUTH_CANCELLED` | The customer dismissed the device authentication sheet | Stay on the confirm screen; let them try again |
| `NOT_SENT` | — | `AUTH_FAILED` | Device authentication did not succeed | Stay on the confirm screen |
| `NOT_SENT` | — | `AUTH_UNAVAILABLE` | The device has no screen lock and no biometrics | Send the customer to Settings |
| `NOT_SENT` | — | `ONLINE_REQUIRED` | The card has run out of payment keys | Ask the user to go online; the SDK refreshes the card |
| `NOT_SENT` | — | `TOKEN_NOT_ACTIVE` | The card is not active (e.g. suspended by the issuer) | Show the card's state |
| `NOT_SENT` | — | `AMOUNT_EXCEEDS_CARD_LIMIT` | The amount is larger than this card can pay in one go | Offer a smaller amount or another card |
| `NOT_SENT` | — | `DEVICE_NOT_BOUND` | The card was added on another device | Remove the card and add it again on this phone |
| `NOT_SENT` | — | `MERCHANT_NOT_ACTIVE` | The merchant account is not active | Contact support |
| `NOT_SENT` | — | `MERCHANT_PROFILE_INCOMPLETE` | The merchant's account details are missing | Finish merchant setup |
| `NOT_SENT` | — | `MISSING_MANDATORY_CONFIG` | Terminal / merchant config is missing | Register the merchant / pass the config |
| `NOT_SENT` | — | `MODE_REFUSED` | Combined apps: the other mode's payment is in progress | Wait for it to finish |
| `NOT_SENT` | — | `UNRECOGNISED_RESPONSE_CODE` | Add card was answered with a code this SDK version doesn't know; nothing was stored | Retry; update the SDK if it persists |
| `NOT_SENT` | — | `SDK_INTERNAL_ERROR` | The SDK failed before sending | Report to support; retry is safe |
| `PENDING` | `68` | `NO_RESPONSE_RECEIVED` | The request was sent but no answer came back (timeout, connection lost) | Wait; the SDK resolves it |
| `PENDING` | `06` | `UPSTREAM_ERROR` | Veyra answered with an error or something unreadable | Wait; the SDK resolves it |
| `PENDING` | — | `SDK_INTERNAL_ERROR` | The SDK failed after sending | Wait; the SDK still resolves the payment |
| `FAILED` | — | `NO_RESPONSE_RECEIVED` / `UPSTREAM_ERROR` | **Add card only**: no answer after sending | Start a new digitisation |

#### The triple is the same on every Veyra service

The triple is not payment-only. Every Veyra service the SDK talks to — the Veyra payment gateway, the
Veyra token service, and through them the switch and the issuer's gateway — answers with the same
fields: `responseStatus` (`APPROVED` / `DECLINED` / `FAILED` / `PENDING`) and `responseStatusReason`,
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
| `07` | `DECLINED` | `ACCOUNT_VALIDATION_FAILED` | The account could not be validated | Show it |
| `14` | `DECLINED` | `INVALID_TOKEN` | The card token is not valid | The customer should re-add the card |
| `21` | `DECLINED` | `NAME_ENQUIRY_FAILED` | The beneficiary account lookup failed | Check the merchant's account details |
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
| `12` | `FAILED` | `QR_EXPIRED` | QR payments: the payment code expired | Ask for a fresh code |
| `13` | `FAILED` | `AMOUNT_MISMATCH` | QR payments: amount or currency doesn't match the code | Ask for a fresh code |
| `25` | `FAILED` | `UNABLE_TO_LOCATE_RECORD` | A status lookup found no such payment | It never arrived; safe to pay again |
| `30` | `FAILED` | `INVALID_REQUEST` | The gateway refused the request before sending it on | Fix the request; new order ID |
| `91` | `FAILED` | `ISSUER_SWITCH_NOT_AVAILABLE` | Veyra could not reach the bank | Try again later with a new order ID |
| `94` | `FAILED` | `DUPLICATE_MERCHANT_ORDER_ID` | This merchant order ID is already used; the response includes the original payment | Read the original's outcome; use a new order ID for a new payment |

Payment status lookup (`refreshTransactionStatus` in either SDK, or the stored row by reference)
returns the stored payment's triple from this table, or `25` / `FAILED` / `UNABLE_TO_LOCATE_RECORD`.
If the lookup itself fails, the SDK leaves the payment as it was and tries again — a failed lookup is
never the payment's outcome.

#### Table C — Veyra token service (wallet: add card, activation, cards)

Wallet SDK only, grouped by call.

| Call | Status | Code | Reason | Meaning / what your app does |
|---|---|---|---|---|
| Add card (`digitizeAccount`) | `APPROVED` | `APPROVED` | — | The card is added and active |
| Add card | `APPROVED` | `APPROVE_REQUIRE_AUTH` | — (or why step-up is needed) | Added, but the customer must activate it; `activationMethods` lists how |
| Add card | `DECLINED` | `DECLINED` | The issuer's reason | The issuer refused this account; show the reason (see [the issuer's causes](#add-a-card--the-issuers-decision-and-causes)) |
| Add card | `FAILED` | — | `INTERNAL_ERROR` | The Veyra token service could not complete it; start a new add card |
| Add card | `FAILED` | — | `NO_RESPONSE_RECEIVED` / `UPSTREAM_ERROR` | No answer (Table A); start a new add card |
| Activation code request (`requestActivationCode`) | `FAILED` | — | `TOKEN_NOT_FOUND`, `ACTIVATION_LOCKED`, `CODE_REQUEST_RATE_LIMITED`, `ACTIVATION_FAILED` | A code can't be sent now. Locked or rate-limited: wait, or contact the issuer |
| Activation code verify (`activate`) | `FAILED` | — | `CODE_INVALID`, `CODE_EXPIRED`, `MAX_ATTEMPTS_EXCEEDED`, `NO_PENDING_ACTIVATION`, `TOKEN_NOT_ACTIVATABLE`, `ACTIVATION_LOCKED`, `TOKEN_NOT_FOUND`, `ACTIVATION_FAILED` | Wrong or expired code: ask again (`attemptsRemaining` is included). Max attempts or locked: remove the card and add it again |
| Remove card (`deactivateToken`) | `FAILED` | — | `TOKEN_NOT_FOUND`, `TOKEN_STATE_CONFLICT` | The card isn't known, or the change isn't allowed from its current state |
| Bank lookup (`getBanks`, `checkAccountEligibility`) | `FAILED` | — | `INVALID_ACCOUNT_NUMBER` | Check the account number |

**Activation on Android.** `ActivationCodeResponse` and `ActivateResponse` state the failure as
`status = "FAILURE"` with the reason in the typed `failureCode` (raw value in `failureCodeRaw`) — see
[Activation](#activation--status--failurecode).

The SDK refreshes a card's payment keys itself; there is no call for it. A refresh refused because
the card is expired or inactive shows up as the card's state (`Token.isActive`,
`Token.requiresOnline`, `Token.deviceNotBound`), never as a result your app receives. An expired card
is never refreshed — add it again.

#### Table D — Veyra payment gateway, non-payment calls (SoftPOS: merchant and QR codes)

SoftPOS SDK only. The status is `FAILED` for every row, with no `responseCode`.

| Call | Reasons | What your app does |
|---|---|---|
| Merchant registration (`registerPersonalMerchant` / `registerBusinessMerchant`) | `MERCHANT_TYPE_REQUIRED`, `MERCHANT_NAME_REQUIRED`, `EMAIL_ADDRESS_REQUIRED`, `PHONE_NUMBER_REQUIRED`, `ACCOUNT_NUMBER_REQUIRED`, `ADDRESS_LINE_1_REQUIRED`, `CITY_REQUIRED`, `BANK_INSTITUTION_CODE_REQUIRED`, `PAYMENT_APP_PROVIDER_ID_REQUIRED`, `CAC_NUMBER_REQUIRED` | Ask for the missing field |
| Merchant update / status / activate / deactivate (`updateMerchant`, `refreshStatus`, `activate`, `deactivate`) | `MERCHANT_ID_REQUIRED`, `MERCHANT_NOT_FOUND`, `INVALID_REQUEST` (the change is not allowed from the current state) | Fix the request / check the merchant's state |
| QR payment code creation (`createContextPayment`) | `AMOUNT_REQUIRED`, `MERCHANT_NOT_FOUND`, `INVALID_REQUEST`, `DUPLICATE_MERCHANT_ORDER_ID` (includes the original payment) | Fix the request; a new order ID for a duplicate |
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

A pending row always converges: it becomes `APPROVED`, `DECLINED` or `FAILED` when the backend
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
    // resolution.status       — APPROVED / DECLINED / FAILED (never PENDING)
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
end the payment, it is `NOT_SENT`: nothing was sent, so retry with the same order ID. The message
names the card-level cause for support.

#### Wallet tap — `onTransactionCompleted`

When the customer's phone is tapped on a terminal, the wallet's `onTransactionCompleted` callback
(registered via `setActiveToken`) delivers `TransactionResponse.status`:

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
level, so `onTransactionCompleted` fires with `"DECLINED"`. On every rail these callbacks tell you
*why*, because the advice differs:

| Callback | Meaning | What to tell the payer |
|---|---|---|
| `onRequireOnline` | The card's keys need refreshing and the wallet could not reach the server | "Connect to the internet and try again" — this genuinely fixes it |
| `onAmountExceedCardLimit` | The amount is over the card's per-payment cap | "Pay a smaller amount or use another card". **Never say "go online"** — a refreshed key carries the same cap |
| `onDeviceNotBound` | The backend issues this card's payment keys only to the device it was added on, and this is not that device (or the app was reinstalled or its data wiped) | "Add this card again on this phone". Going online does not help, nor does a smaller amount |

All three carry `amountMinorUnits` and `rail` (`"TAP"`, `"CPM_QR"` or `"MPM_QR"`).
`onAmountExceedCardLimit` also carries `cardLimitMinorUnits` — the cap when the SDK can read it, or
`null`; show the figure only when it is non-null. `onDeviceNotBound` delivers a
`DeviceNotBoundEvent(tokenId, tokenUniqueReference, amountMinorUnits, rail, message)`.

**`onDeviceNotBound` is a card state too.** When the SDK's automatic key request is refused this way,
it stops every automatic key request for that card and sets `Token.deviceNotBound`. From then on a
payment on that card is refused before anything is sent, so it is right to grey the card out (see
[Handling card states in your UI](#handling-card-states-in-your-ui)).

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
`wallet.onPaymentRefusal(tokenUniqueReference, listener)`; iOS fires from the QR rails only, having
no tap-to-pay.

##### Registering refusals without arming a card — `observePaymentRefusals`

`setActiveToken` takes these handlers for the card it arms. When you want them for a card you are
**not** arming — a wallet list that reflects refusals across several cards, say — register them on
their own:

```kotlin
sdk.tokenisationService.observePaymentRefusals(
    tokenUniqueReference = card.tokenUniqueReference!!,
    onRequireOnline = { event -> promptToConnect(event.amountMinorUnits) },
    onAmountExceedCardLimit = { event -> offerSmallerAmount(event.cardLimitMinorUnits) },
    onDeviceNotBound = { event -> promptToAddCardAgain(event.tokenUniqueReference) },
)
// when the screen goes:
sdk.tokenisationService.stopObservingPaymentRefusals(card.tokenUniqueReference!!)
```

Registering the same card again replaces its handlers; other cards are unaffected.

#### Three things end with "get online", and they are not the same

- **`NO_NETWORK_CONNECTION`** (`NOT_SENT`) — the *phone* has no connection. Every call fails the same
  way until the user reconnects. Retrying is safe: nothing was sent.
- **`ONLINE_REQUIRED`** (`NOT_SENT`) — the *card* has run out of payment keys. The phone is usually
  online already; the SDK refreshes the card itself, typically within seconds. Don't grey a card out
  on a `NO_NETWORK_CONNECTION` — the card is fine.
- **`91` / `FAILED` / `ISSUER_SWITCH_NOT_AVAILABLE`** — the request reached Veyra, and Veyra could not
  reach the bank. The user's connection is not the problem; telling them to check it wastes their
  time. Try again later with a new order ID.

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

`responseCode` on `TokenisationResponse` (from `digitizeAccount`) and `VerifyAccountResponse` (from
`checkAccountEligibility`) is the **issuer's decision** about this account and device:

| `responseCode` | Meaning | What to do |
|---|---|---|
| `"APPROVED"` | Eligible / provisioned and active | The card is ready — show it in the wallet. |
| `"APPROVE_REQUIRE_AUTH"` | Provisioned, needs activation | Run the activation flow with the returned `activationMethods`. `responseStatusReason` says why step-up is needed. |
| `"DECLINED"` | Refused | Show the reason. The flow ends. |

Any other code ends `NOT_SENT` / `UNRECOGNISED_RESPONSE_CODE` and the token is **discarded** —
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

#### Activation — `status` + `failureCode`

`ActivationCodeResponse.status` / `ActivateResponse.status` are `"SUCCESS"` / `"FAILURE"` — **check
`status` even when the call itself succeeds.** On failure, branch on the typed `failureCode`
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

##### `onTokenStatusChanged` — the SDK tells you when a card's status changes

The sweep can apply `SUSPENDED` while the customer is *looking at* a card screen. Subscribe and you
are told the moment it is applied:

```kotlin
TokenLifecycleObserver.onTokenStatusChanged { change ->
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
- **Last registration wins.** `TokenLifecycleObserver.clear()` stops it. Delivered on the main thread.

The same channel exists on iOS (`VeyraWallet.shared.tokenisation.observeTokenLifecycle { … }`) and
React Native (`wallet.onTokenStatusChanged(listener)`).

##### `onKeyStateChanged` — a card ran out of payment keys, or got them back

```kotlin
WalletKeyStateObserver.onKeyStateChanged { state ->
    // state.tokenUniqueReference, state.requiresOnline, state.deviceNotBound
}
```

`requiresOnline` here is **the same value `getTokens()` reports**, so a callback can never contradict
the list you are about to draw. `CardKeyState.deviceNotBound` mirrors `Token.deviceNotBound`: render
it as "add this card again on this phone", not as a connect prompt.

**Know its limit before you word your UI.** It fires when a payment uses a key and when a refresh
delivers new ones. Payment keys *also* expire by clock, with no SDK code running — **nothing fires
for that**, and such a card simply reads as `requiresOnline` on your next `getTokens()`. Keep reading
the card list when a screen appears. There is deliberately no API to trigger a key refresh — the SDK
owns when keys are replenished.

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
| Merchant `TransactionInfo.transactionStatus` | `APPROVED` / `DECLINED` / `PENDING` (outcome not known yet, the SDK keeps checking) / `FAILED` |
| Credit confirmation (`TransactionInfo.creditConfirmationStatus`) | `RECEIVED` (funds landed in the merchant's account), `UNABLE_TO_CONFIRM` (30 days passed with no answer), or `null` while unconfirmed — show "not confirmed yet", never "not received". A settlement fact, never a payment outcome |
| Wallet scan rejection (`inspectScannedQr` → `MpmScanResult.Rejected`) | `MALFORMED` / `MISSING_SIGNATURE` / `UNKNOWN_KEY` / `BAD_SIGNATURE` (show "couldn't verify this code") / `EXPIRED` (show "code expired — ask the merchant for a fresh one"). Every rejection ends the flow; no payment was attempted |

---

## Data models

Reference for the public models. All are immutable value types; fields not listed here are not public API.

### Wallet

```kotlin
data class Token(
    val tokenId: String,                    // the card's identity for getToken / setActiveToken
    val tokenUniqueReference: String?,      // the server-side identity for activation / deactivation / history
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
    val statusRaw: String?                  // the stored wire value behind status — logs / forward compatibility
) {
    fun getMaskedPAN(): String              // "**** **** **** 1234"
    fun getLastFourDigits(): String
}

data class ActivationMethod(val medium: String, val contact: String)
// medium: MASKED_EMAIL, MASKED_MOBILE_PHONE, CALL_CENTER_PHONE,
//         AUTOMATED_CALL_CENTER_PHONE, WEBSITE, MOBILE_APPLICATION
// contact: the masked phone/email, phone number, URL or app package to act on

data class Bank(val slug: String, val name: String, val institutionCode: String)

data class VerifyAccountResponse(
    val responseCode: String?,              // "APPROVED" = eligible
    val message: String?,
    val responseStatus: String?,            // APPROVED / DECLINED / FAILED / PENDING — what the call did (1.2.4+)
    val responseStatusReason: String?       // the symbolic cause — branch on this; null = none stated (1.2.4+)
)

data class TokenisationResponse(
    val responseCode: String?,              // APPROVED / APPROVE_REQUIRE_AUTH / DECLINED
    val tokenUniqueReference: String?,
    val activationMethods: List<ActivationMethod>?,
    val isSuccess: Boolean,
    val status: String?,                    // SUCCESS / PENDING / FAILED
    val message: String?,
    val responseStatus: String?,            // APPROVED / DECLINED / FAILED / PENDING — what the call did (1.2.4+)
    val responseStatusReason: String?,      // the symbolic cause — branch on this; null = none stated (1.2.4+)
    val error: TokenisationError?           // code: CONFIG_ERROR / TOKENIZATION_ERROR / UNEXPECTED_ERROR
)

data class ActivationCodeResponse(
    val tokenUniqueReference: String?,
    val expirationDateTime: String?,        // ISO-8601 — drive the OTP countdown
    val status: String?,                    // SUCCESS / FAILURE — check it even on a successful Result
    val message: String?,
    val failureCode: ActivationFailureCode?,  // null on success; UNKNOWN for a code newer than this SDK
    val failureCodeRaw: String?
)

data class ActivateResponse(
    val tokenUniqueReference: String?,
    val status: String?,                    // SUCCESS / FAILURE
    val message: String?,
    val failureCode: ActivationFailureCode?,  // null on success; UNKNOWN for a code newer than this SDK
    val failureCodeRaw: String?,
    val attemptsRemaining: Int?,            // code attempts left where a cap applies (0 when exhausted/locked)
    val recommendDelete: RecommendDelete?,  // MUST / MAY after an exhausted cycle; null otherwise
    val recommendDeleteRaw: String?
)
data class TokenStatusUpdateResponse(val tokenUniqueReference: String?, val status: String?, val message: String?)

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

// Tap outcome (delivered via setActiveToken's onTransactionCompleted):
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
)
data class MpmPushOutcome(
    val responseCode: String?,              // "00", "51", "68"… — always populated, quote it verbatim
    val responseStatus: String?,            // APPROVED · DECLINED · FAILED · PENDING — what the payment IS
    val message: String?,
    val merchantName: String?,              // registered name from the gateway (beats the QR copy)
    val merchantLocation: String?,          // "city, state" from the gateway; null if not supplied
    val responseStatusReason: String?,      // stated cause: INSUFFICIENT_FUNDS, NO_RESPONSE_RECEIVED…
) { val approved: Boolean }                 // derived: responseStatus == "APPROVED"; false for PENDING too

// Show-QR-to-pay:
data class CpmPaymentQr(
    val tokenUniqueReference: String,
    val payload: String,                    // render as the QR
    val amountMinorUnits: Long, val currencyNumeric: String,
    val expiresAtEpochMillis: Long,
    val transactionHash: String             // this render's unique hash — match against history to reconcile
)
```

`CurrencyUtils` (wallet): `getSymbol(code)`, `formatAmount(minorUnits, code)`, `formatAmountWithCode(minorUnits, code)` — display helpers for ISO 4217 numeric codes. **`minorUnits` is a `Long` from 1.0.15** (it was `Int`), matching `TransactionSummary.amountInMinorUnit`: a 32-bit amount could not carry a payment above ₦21,474,836.47. If you parse an amount out of a `TransactionReceipt.totalAmount` string, use `toLongOrNull()`.

### SoftPOS

```kotlin
data class NubanBank(val slug: String, val name: String, val institutionCode: String)

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
    val transactionCode: String,            // see Response codes
    val message: String?,
    val amount: String?,                    // minor units as string
    val cardScheme: String?,                // "VISA", "MASTERCARD", …
    val cardExpiry: String?,                // YYMM
    val merchantTransactionReference: String?,  // SDK-MINTED reference — receipt/status lookup key
    val transactionType: String?, val maskedTokenLast4: String?,
    val merchantStatus: String?, val transactionId: String?, val aid: String?,
    val responseStatus: ResponseStatus?,        // APPROVED / DECLINED / FAILED / PENDING / Unknown — branch on this
    val responseStatusReason: String?,          // the stated cause; display, never parse
    val creditTransactionId: String?,           // merchant-bank credit id (approved + supported only)
    val isCreditConfirmationSupported: Boolean?, // true ⇒ the SDK will poll and fire onCreditConfirmation
    val sdkErrorCode: SdkErrorCode?             // set ⇒ never attempted / SDK failure, not a payment outcome
)

data class TransactionInfo(                 // history row
    val merchantTransactionReference: String,
    val amount: Long,                       // minor units
    val transactionStatus: TransactionStatus,   // APPROVED / DECLINED / PENDING / FAILED
    val responseCode: String?,
    val responseStatusReason: String?,      // the stated cause ("INSUFFICIENT_FUNDS"...); display, never parse
    val transactionTime: String?,           // "yyyy-MM-dd HH:mm:ss"
    val currencyCode: String?, val transactionId: String?,
    val merchantOrderId: String?,           // your own order id as supplied on the charge; display only
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
    val status: String,                     // "APPROVED" / "DECLINED" / "FAILED" — never "PENDING"
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

data class CreatedPaymentContext(val txRef: String, val expiry: String?, val kid: String?, val mpmPayload: String)
data class PaymentContextStatus(
    val txRef: String,
    val state: String,                      // PENDING / IN_FLIGHT / APPROVED / DECLINED / EXPIRED
    val responseCode: String?, val expiry: String?, val transactionHash: String?
) { val isSettled: Boolean; val isApproved: Boolean }

data class ScannedCpmQr(
    val dpan: String, val tokenExpiryYymm: String?,
    val amountMinorUnits: Long,             // the QR's own, cryptogram-bound amount
    val currencyNumeric4: String,
    val cardholderName: String?,           // "AFRIGO ****1234" — display only, outside the cryptogram
    /* … */
)
data class PaymentResponse(val responseCode: String?, val merchantStatus: String?, val transactionId: String?, /* … */)
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
checkAccountEligibility
        │
        ├─ not APPROVED ──► show error (flow ends)
        │
        ▼ APPROVED
digitizeAccount
        │
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
```

### Pay by scanning a merchant QR (wallet)

```
Camera scan ──► inspectScannedQr
                    ├─ rejected (malformed / bad signature / expired) ──► end flow, show reason
                    └─ verified ──► confirm screen (merchant + the QR's amount)
                                        │ user confirms
                                        ▼
                          SDK raises the biometric sheet itself
                                        ├─ failed/cancelled ──► stay on confirm screen
                                        ▼ success (single-use)
                              payScannedContext
                                        ├─ approved ──► success screen; history row APPROVED
                                        ├─ declined ──► declined screen; history row DECLINED
                                        └─ ONLINE_REQUIRED ──► "connect to the internet", stay on confirm
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
