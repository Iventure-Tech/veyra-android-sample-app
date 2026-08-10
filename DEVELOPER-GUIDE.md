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
| `co.veyra:veyra-common` | Shared infrastructure (`Environment`, logging, OAuth token cache) | 24 |

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
val sdk = VeyraSdk.initialize(this, VeyraSdkConfig(softposConfig, walletConfig))
val wallet  = sdk.wallet    // VeyraWalletSdk
val softpos = sdk.softpos   // VeyraSoftPOSSdk
```

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, config)` | `activity` — an `AppCompatActivity`; `config` — `VeyraSdkConfig(softpos, wallet)` | Initialises the facade and installs the exclusive-mode arbiter. Idempotent process singleton; always starts inert (no mode active). Must be called before either member SDK is used in a combined app. |
| `getInstance()` | — | The current instance, or `null` if not yet initialised. |
| `softpos` | — | The `VeyraSoftPOSSdk`. Throws `IllegalStateException` if that SDK has not been initialised yet (each of your screens initialises its own SDK — see the samples). |
| `wallet` | — | The `VeyraWalletSdk`. Same behaviour. |
| `currentMode()` | — | The current exclusive mode (`NfcMode.NONE` / `SOFTPOS` / `WALLET`). Read-only observation for UI state. |

`VeyraSdkConfig` is a simple pair of the two SDK configs: `VeyraSdkConfig(softpos: VeyraSoftPosSdkConfig, wallet: VeyraWalletSdkConfig)`.

**The bypass rule:** with both SDKs on the classpath, initialising them *without* `VeyraSdk.initialize` leaves the app fail-closed — every NFC operation throws `SdkModeException`. This is by design: a combined app that skipped the facade must fail loudly rather than risk receiving and paying at once. Standalone single-SDK apps are unaffected and never see `SdkModeException`.

### `VeyraSoftPOSSdk` (merchant features)

Package `co.veyra.softpos.payment.sdk`. Process singleton; initialise on (or before) each screen that accepts payments, so reader arming binds to that screen's lifecycle.

```kotlin
val sdk = VeyraSoftPOSSdk.initialize(this, softposConfig)
```

**Methods & services:**

| Member | Parameters | Description |
|--------|------------|-------------|
| `initialize(activity, config)` | `activity` — `AppCompatActivity`; `config` — `VeyraSoftPosSdkConfig` | Initialises (or re-binds) the singleton. Idempotent; re-binds NFC reader + lifecycle to the new activity on every call. Throws `VeyraSdkException` (`MISSING_MANDATORY_CONFIG`) when credentials are missing for the environment. |
| `getInstance()` | — | Current instance or `null`. |
| `cardPaymentService` | — | Tap acceptance — [`makeCardPayment`](#makecardpayment) and friends. |
| `merchantService` | — | Merchant registration, status, profile, banks. |
| `transactionService` | — | Merchant transaction history + receipts (all rails). |
| `cpmCustomerQrService` | — | Charge a scanned customer payment QR. |

The reader arms on resume and disarms on pause automatically. If NFC is unavailable or off, the SDK shows its own prompt dialogs at initialise.

### `VeyraWalletSdk` (wallet features)

Package `co.veyra.wallet.sdk`. Process singleton. The environment is set **once at initialisation** and applies to all subsequent SDK calls.

```kotlin
val sdk = VeyraWalletSdk.initialize(context, walletConfig, activity = this)
```

**Methods:**

| Method | Parameters | Description |
|--------|------------|-------------|
| `initialize(context, config, activity?)` | `context` — app context; `config` — `VeyraWalletSdkConfig`; `activity` — optional, pass it for automatic NFC activation | Initialises the singleton. Idempotent — a repeat call re-binds the NFC lifecycle to the new activity. Throws `IllegalArgumentException` when `clientId`/`clientSecret` are missing for TEST/LIVE. |
| `getInstance()` | — | Current instance or `null`. |
| `tokenisationService` | — | The wallet service — every wallet operation hangs off it. |
| `getTokenRequestorId()` | — | The token requestor ID from config. |
| `getPaymentApplicationInstanceId()` | — | This install's `payment_application_instance_id` — **SDK-generated** (`VYRA` + 32 hex chars), minted on first use, persisted install-scoped, never backed up, new on reinstall. Read-only; the app cannot set or regenerate it. |
| `getClientId()` / `getClientSecret()` | — | The OAuth credentials in effect. |
| `getAppVersion()` | — | The app version reported to the backend. |

---

## Configuration

### `VeyraSoftPosSdkConfig` (SoftPOS)

```kotlin
val softposConfig = VeyraSoftPosSdkConfig.builder(
    Environment.TEST,
    clientId = "your-client-id",
    clientSecret = "your-client-secret"
)
    .enableNfc(true)
    .build()
```

**Builder parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `environment` | **Mandatory** | `Environment.TEST` or `Environment.LIVE`. Determines the server host; set once, applies to all SDK calls. |
| `clientId` | **Mandatory** | OAuth client ID issued by Veyra. |
| `clientSecret` | **Mandatory** | OAuth client secret. |
| `enableNfc(Boolean)` | Optional | Arm the NFC reader capability at init. Default `true`. |
| `terminalId(String)` | Optional | Terminal ID override. Normally comes from the stored merchant after registration — set only if your app pre-configures terminals. |
| `merchantId(String)` | Optional | Merchant ID override (same note). |
| `merchantNameAndLocation(String)` | Optional | Merchant name/location override for receipts and EMV data (same note). |
| `acquirerId(String)` | Optional | Acquirer ID override (same note). |
| `merchantCategoryCode(String)` | Optional | 4-digit MCC override (same note). |
| `countryCode(String)` | Optional | ISO 3166-1 numeric, 4-digit zero-padded (e.g. `"0566"` for Nigeria) override (same note). |

After merchant registration the terminal/merchant values come from the stored merchant profile — a typical app sets none of the optional overrides.

### `VeyraWalletSdkConfig` (Wallet)

```kotlin
val walletConfig = VeyraWalletSdkConfig.builder(
    Environment.TEST,
    paymentAppProviderId = "your-provider-id",
    tokenRequestorId = "50100000001",
    allowedCountryCodes = listOf("0566"),      // ISO 3166-1 numeric, 4 digits
    clientId = "your-client-id",
    clientSecret = "your-client-secret"
)
    .appVersion("1.2.0")
    .walletProviderTokenizationRecommendationStandardVersion("1.0")
    .allowedAcquirerIds(listOf("ACQ001"))
    .allowedMerchantIds(listOf("MERCHANT01"))
    .allowedMccs(listOf("5411"))
    .enableNfc(true)
    .build()
```

**Builder parameters:**

| Parameter | Required | Description |
|-----------|----------|-------------|
| `environment` | **Mandatory** | `Environment.TEST` or `LIVE`. |
| `paymentAppProviderId` | **Mandatory** | Your payment-app provider identifier, assigned by Veyra. Sent in every tokenisation and eligibility request. |
| `tokenRequestorId` | **Mandatory** | Token requestor ID assigned by the scheme. |
| `allowedCountryCodes` | **Mandatory** (may be empty) | Provision context: ISO 3166-1 **numeric, 4-digit** codes (`"0566"` Nigeria, `"0826"` UK) — never alpha codes. |
| `clientId` / `clientSecret` | **Mandatory** for TEST/LIVE | OAuth client credentials — `initialize` throws if missing. |
| `enableNfc(Boolean)` | Optional | Enable NFC/HCE at init. Default `true`. |
| `appVersion(String)` | Optional | App version sent in digitise requests (falls back to your package version). |
| `walletProviderTokenizationRecommendationStandardVersion(String)` | **Required before digitising** | Standard version for the tokenisation recommendation (e.g. `"1.0"`). Digitise throws if unset. |
| `allowedAcquirerIds(List<String>)` | Optional | Provision context: acquirer IDs to restrict the token to. |
| `allowedMerchantIds(List<String>)` | Optional | Provision context: merchant IDs to restrict to. |
| `allowedMccs(List<String>)` | Optional | Provision context: merchant category codes to restrict to. |

> **Note:** there is **no** `paymentApplicationInstanceId` parameter. The SDK generates and persists an install-scoped instance ID itself and sends it on every eligibility/digitise request — read it via `getPaymentApplicationInstanceId()`. A restricted provision-context dimension that a payment then falls outside of is declined by the server.

### `Environment`

One shared enum for both SDKs (`co.veyra.common.Environment`).

| Value | Description |
|-------|-------------|
| `TEST` | Test / staging servers. OAuth credentials required. |
| `LIVE` | Production servers. OAuth credentials required. |

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

A device must have a **registered, active merchant** before it can accept payments. Registration persists the merchant on the device (SDK-owned storage, cleared on uninstall); the backend assigns the merchant ID, terminal ID and category code.

---

#### `registerPersonalMerchant` / `registerBusinessMerchant`

Register the merchant on this device. Personal merchants require a BVN; business merchants require a CAC number. All other fields are mandatory for both.

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
    acquirerId = "ACQ001",
    bvn = "12345678901",                   // personal merchants only
    cacNumber = null                       // business merchants only
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
| `acquirerId` | **Mandatory** | Acquirer ID from your scheme onboarding. |
| `bvn` | Personal only | 11-digit BVN. |
| `cacNumber` | Business only | CAC registration number. |

**`MerchantRegistrationResponse` fields:** `success: Boolean`, `merchantId`, `terminalId`, `merchantCategoryCode`, `countryCode`, `acquirerId`, `merchantStatus`, `message`. Validation problems come back as `success = false` with a message — nothing throws.

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
| **`VeyraSoftPOSSdk.isMerchantRegistered(context)`** (static) | The same check **without initialising the SDK** — no lifecycle binding, no NFC arming. Gate your Home screen's Get-paid entry on this (initialise the SDK only on payment screens). |
| **`VeyraSoftPOSSdk.storedMerchant(context)`** (static) | Init-free read of the persisted `StoredMerchantData` (or `null`) — e.g. for pre-filling or merchant-type checks before any payment screen exists. |
| `isMerchantActive(): Boolean` | `true` when the last known backend status is `ACTIVE` (a merchant with no status yet counts active). Payments are refused for inactive merchants. |
| `refreshStatus()` | Refresh the backend status immediately (the SDK also polls it periodically while your app is foregrounded). Call at the activation moment. |
| `activate { response -> }` / `deactivate { response -> }` | Activate / deactivate this merchant on the backend. Callback gets `MerchantStatusResponse(merchantId, merchantStatus)` or `null` on failure. |
| `getStoredMerchantData(): StoredMerchantData?` | The merchant persisted by the last successful registration (all profile fields + backend-assigned ones), or `null`. |
| `getStoredMerchantId(): String?` / `hasStoredMerchant(): Boolean` | Convenience reads. |
| `clearStoredMerchant()` | Clear the stored merchant (logout / re-registration). |

---

#### `updateMerchant`

Update the merchant profile (terminal ID and MCC are preserved). All parameters are required except `addressLine2`.

```kotlin
sdk.merchantService.updateMerchant(
    merchantName = "Ada's Store", emailAddress = "ada@example.com",
    phoneNumber = "+2348012345678", addressLine1 = "12 Marina Road",
    city = "Lagos", state = "Lagos", countryCode = "0566",
    accountNumber = "1234567890", institutionCode = "000000"
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
    merchantTransactionReference = uniqueReference,    // your unique reference, echoed back
    currency = "0566",                                 // ISO 4217 numeric, 4 digits
    txType = TransactionRequest.TxType.PURCHASE
).build()

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
| `merchantTransactionReference` | **Mandatory** | Your unique reference for this sale — echoed on the response and used to fetch the receipt afterwards. |
| `currency` | **Mandatory** | ISO 4217 numeric, 3–4 digits (padded to 4, e.g. `"0566"`). |
| `txType` | **Mandatory** | `TxType.PURCHASE`, `REFUND`, `CASH_ADVANCE`, `RECURRING_PURCHASE`, `PRE_AUTH_COMPLETION`, `OTHER`. |
| `.performed3ds(Boolean)` | Optional | Whether your app performed 3-D Secure. Default `false`. |

**Also on `cardPaymentService`:** `cancelPendingPayment()` (cancels an armed, untapped payment — the callback receives code `"06"` with message `"Payment cancelled"`; no-op when nothing is pending), `isTransactionInProgress`, `isAwaitingCardTap`.

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
val client = ContextPaymentClient(context, Environment.TEST, clientId, clientSecret)
val created = client.createContextPayment(
    merchantId, amountMinorUnits, "566",
    onExpired = { blankQr(); showHint("This payment code has expired — start a new payment") },
) ?: run { showError("Could not create payment QR"); return }

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
val reference = uniqueReference()                        // supply your own reference so the
lifecycleScope.launch {                                  // receipt can be fetched afterwards
    val response = sdk.cpmCustomerQrService.charge(scanned, reference)
    val approved = response.responseCode == "00"
    showResult(approved, reference)
}
```

`charge(scanned, merchantTransactionReference?)` returns `PaymentResponse`: `responseCode` (`"00"` approved), `transactionId`, `merchantStatus`. Pass your own `merchantTransactionReference` — with it you can fetch the receipt via `generateTransactionReceipt(reference)`; if you omit it the SDK generates one internally that your app cannot observe. A transport failure throws and records nothing.

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
// transactionId, cardholderName (EMV 5F20 as the card presented it — null on QR-MPM),
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

Everything hangs off `sdk.tokenisationService`; callbacks arrive on the main thread; async failures come back as `Result.failure` (match on the message — see [Response codes](#response-codes--error-handling)).

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
| `walletAccountId` | **Mandatory** | The customer's identifier with **your** wallet service — email, phone or GUID. The SDK derives a hash from it; it is not sent raw. Must match the value registered with your wallet provider. |
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
    consumerIdentifier = UUID.randomUUID().toString(),
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

`ActivationCodeResponse`: `tokenUniqueReference`, `expirationDateTime` (ISO-8601 — drive your countdown from it), `status` (`SUCCESS` / `FAILURE`), `message`, `failureCode` (typed `ActivationFailureCode`, null on success) + `failureCodeRaw`. **Check `status` even inside a successful result**, and branch on `failureCode`, never on `message`: `CODE_REQUEST_RATE_LIMITED` means disable "resend" until later but keep the flow open; `ACTIVATION_LOCKED` is terminal — end the flow and point the user at their issuer. Codes are limited-attempt and rate-limited — see [Response codes](#response-codes--error-handling).

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

`ActivateResponse` failure fields (all null on success): `failureCode` — typed `ActivationFailureCode`, one of `TOKEN_NOT_FOUND`, `TOKEN_NOT_ACTIVATABLE`, `ACTIVATION_LOCKED`, `NO_PENDING_ACTIVATION`, `CODE_EXPIRED`, `CODE_INVALID`, `MAX_ATTEMPTS_EXCEEDED`, `INVALID_REQUEST`, `ACTIVATION_FAILED`, or `UNKNOWN` for a code newer than this SDK (raw value in `failureCodeRaw`); `attemptsRemaining` — code attempts left where a cap applies (0 when exhausted/locked); `recommendDelete` — `RecommendDelete.MUST` / `MAY` after an exhausted cycle (delete the dead token rather than leaving it in the card list), null otherwise (raw in `recommendDeleteRaw`).

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

`Token`: `tokenId`, `tokenUniqueReference`, `devicePAN`, `cardHolderName` (the card's display name — scheme label + masked last four, e.g. `AFRIGO ****1234`; not a person's name, and the same value the card presents in EMV tag `5F20`), `expiryDate`, `cardScheme`, `cardType`, `isActive` (the card payments use), `activationMethods` (non-null while activation is pending), `transactions` (last 3), `requiresOnline` (see below), helpers `getMaskedPAN()` / `getLastFourDigits()`.

**`requiresOnline`** — `true` when the card cannot pay until the wallet has been **online** to refresh it. Render the card greyed-out and non-tappable and prompt the user to connect; the flag derives fresh on every read and clears on its own once the SDK's automatic refresh succeeds. There is no manual "refresh keys" call — key management is entirely SDK-owned.

#### Handling card states in your UI

A card is not simply "there or not" — it can be awaiting activation, frozen for a refresh, or suspended server-side. Derive one display state per card, in this precedence order, every time you render the wallet:

| Precedence | State | How you observe it | UI treatment | What unblocks it |
|---|---|---|---|---|
| 1 | **Needs activation** | `token.activationMethods != null` | Show the card with an **"Activate"** badge/button that launches the [activation flow](#activation). Pay actions hidden. | `activate` succeeding, or `observeActivation` firing `onActivated`. |
| 2 | **Requires online** | `requiresOnline == true` | **Grey the card out and make it non-tappable**; overlay a "Connect to the internet" hint; disable every pay affordance (tap surface, scan-to-pay, show-QR buttons). | Nothing you call — the SDK refreshes the card itself the next time the device is online. Re-read the list and the flag has cleared. |
| 3 | **Inactive server-side** (suspended, expired) | `token.isActive == false`, with `token.status` saying **why** (`SUSPENDED` / `PENDING_ACTIVATION` / `EXPIRED` — a pay attempt refuses with the typed `WalletRefusalException.TokenNotActive`) | Grey the card out; word the indicator from `status` — "Suspended — contact your bank" vs "Expired — re-add the card". Disable pay affordances; don't offer retry — the state is issuer-controlled. | A later automatic status sync seeing the card active again. |
| 4 | **Payable** | None of the above | Normal rendering; pay affordances enabled for the active card. | — |

Two rules make this robust:

- **Derive, don't cache.** Every state above is computed fresh on each read and clears itself — re-read the card list whenever your wallet screen (re)appears and after any payment attempt, rather than storing state.
- **Gate the affordances, not just the card face.** Disabling only the card image but leaving a "Scan to pay" button live produces the refusal errors at pay time; disable the actions too, and treat the typed refusals (the `ONLINE_REQUIRED:` / `TOKEN_NOT_ACTIVE:` message prefixes) as the backstop, not the primary UX.

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
val blocked = active == null || active.requiresOnline
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
    }
)
```

Tap callbacks arrive on an SDK thread — wrap UI updates in `runOnUiThread`. Guard with `hasProvisioningData(tokenId)` if you keep display records for cards whose material may be gone.

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

#### `authenticateForScannedPayment`

Biometric confirmation (system prompt; passcode/credential fallback allowed by default). On success the SDK records a **fresh, single-use** authentication — exactly one payment (or one QR render) consumes it. Put the merchant and amount in the prompt so the gesture is visibly bound to what it authorises.

```kotlin
sdk.tokenisationService.authenticateForScannedPayment(
    activity = this,
    title = "Confirm payment",
    subtitle = "Pay ${CurrencyUtils.formatAmount(ctx.amountMinorUnits, ctx.currencyNumeric)} to ${ctx.merchantName}",
) { auth -> if (auth.isSuccess) pushPayment(ctx) /* else stay on the confirm screen */ }
```

#### `payScannedContext`

Pay the verified context with the wallet's **active card**. Requires the fresh authentication above (the SDK enforces one per payment). The delivered outcome — approved or declined — also lands in the card's history.

```kotlin
sdk.tokenisationService.payScannedContext(ctx) { result ->
    result.fold(
        onSuccess = { outcome -> showResult(outcome.approved, outcome.message) },  // approved ⇔ rail code "00"
        onFailure = { e -> showResult(false, e.message) }   // see Response codes for the refusal strings
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

Requires a fresh `authenticateForScannedPayment` first — **one authentication per QR**; regenerating after expiry needs a new one.

```kotlin
sdk.tokenisationService.authenticateForScannedPayment(activity = this, title = "Show QR to pay",
    subtitle = "Pay $amountText by QR") { auth ->
    auth.fold(
        onSuccess = {
            sdk.tokenisationService.showQrToPay(amountMinorUnits, onExpired = { blankQr() }) { result ->
                result.fold(
                    onSuccess = { qr -> renderQr(qr.payload); pollForSettlement(qr) },
                    onFailure = { e -> showError(e.message) }
                )
            }
        },
        onFailure = { e -> showError(e.message) }
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

`TransactionSummary` fields: `merchantName`, `amountInMinorUnit`, `transactionCurrencyCode` (4-digit ISO 4217, e.g. `"0566"`), `authorizationStatus` (`PENDING` / `APPROVED` / `DECLINED` / `FAILED`; `null` on legacy rows — treat as indeterminate), `responseCode` (the outcome's code, e.g. `"00"`, `"51"` — verbatim from the rail that resolved the row; `null` until resolved; quote this literal in support conversations), `responseStatusReason` (the outcome's stated cause, e.g. `"INSUFFICIENT_FUNDS"` — a plain string to display, never parse; `null` until resolved), `entryMethod` (`"TAP"`, `"QR_GENERATED"` — showed a QR, `"QR_SCANNED"` — scanned a merchant QR; `null` legacy — show nothing rather than guess), `merchantLocation`, `transactionHash` (join key to a receipt), `localTransactionDateTime` / `atEpochMillis`, `merchantTransactionReference`, `merchantId`.

#### `reconcilePendingTransactions`

Reconcile still-`PENDING` rows against the backend. Call it opportunistically: on returning to the foreground, on pull-to-refresh, and on a short loop while a shown QR is on screen (that rail is offline — reconciliation is how its outcome arrives).

```kotlin
sdk.tokenisationService.reconcilePendingTransactions { /* refresh the list */ }
```

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

## Response codes & error handling

Two kinds of surface, marked throughout:

- **Typed** — enum cases / exception types. Stable contract; branch on these.
- **Observable string codes** — documented values of `String` fields. Stable vocabularies, but your code matches on strings.

### Typed errors

| Exception | Thrown by | What to do |
|---|---|---|
| `SdkModeException` | Combined apps only: a payment's mode claim refused while the other mode's payment is mid-flight (e.g. `makeCardPayment` during a wallet payment), or both SDKs initialised without the `VeyraSdk` facade | Prompt the user to finish/cancel the other payment first. **Never occurs in a standalone single-SDK app.** |
| `TokenizationRequestValidationException` | `digitizeAccount` params with a blank required field (synchronous; carries `fieldName`) | Fix the missing input before calling. |
| `VeyraSdkException` (`errorCode = MISSING_MANDATORY_CONFIG`) | SoftPOS initialise / payment without environment or credentials | Fix your configuration. |
| `IllegalArgumentException` | `cpmCustomerQrService.inspect` on a payload that isn't a Veyra payment QR | Not an error — show "not a payment code, try again" and stay armed for another scan. |

**Wallet refusal codes (observable message prefixes).** Wallet failures arrive as `Result.failure(Exception)`; the machine-matchable part is a stable prefix on the message:

| Message prefix | Match with |
|---|---|
| `ONLINE_REQUIRED:` | `error.message?.contains("ONLINE_REQUIRED") == true` |
| `AMOUNT_EXCEEDS_CARD_LIMIT:` | `error.message?.contains("AMOUNT_EXCEEDS_CARD_LIMIT") == true` |
| `TOKEN_NOT_ACTIVE:` | `error.message?.contains("TOKEN_NOT_ACTIVE") == true` |
| `CDCVM required:` | You skipped `authenticateForScannedPayment` (one authentication per payment / per QR render). Authenticate, then retry. |
| `Authentication cancelled:` / `Authentication failed:` | Stay on the confirm screen; let the user retry. |
| `No active card to pay with (or card unsupported on the QR rail)` | The wallet is empty, or no card is selected — send the user to add/select a card. A card added before QR payments were provisioned must be removed and re-added. |
| `This card can't show a payment QR — no active card, or it was added before QR payments (re-add it)` | Same treatment, from `showQrToPay`. |

### Tap acceptance — `transactionCode`

Terminal outcomes only — unsupported cards and lost contact **never** produce one of these; they fire the re-tap hints and the reader stays armed.

> **Read `response_status`, not the code (STORY-98 / ISSUE-140).** Every payment outcome now carries a
> triple: `response_code` (what the wire said), `response_status` (**what to do**) and
> `response_status_reason` (why). Branch on `response_status` only — `APPROVED`, `DECLINED`, `FAILED`
> or `PENDING`. Only the first three are final; `PENDING` always means "ask again". The SDK no longer
> derives a status from the code, and neither should your app: a code you do not recognise is not a
> decline. `"99"` is retired — an unheard outcome is now `68` (no reply), `06` (the hop we called
> failed) or `96` (the SDK/service itself threw), all `PENDING`, while `91` (never connected) and
> `25` (no such transaction) are `FAILED`, meaning nothing happened and a retry is safe.


| Code | Meaning | Terminal? | What to do |
|---|---|---|---|
| `"00"` | Approved | Yes | Success screen + receipt (look it up by your echoed `merchantTransactionReference`). |
| `"05"` | Declined by the issuer/server | Yes | Show decline; try another card. A stale customer QR also surfaces as `"05"` on the CPM rail — if the customer's code sat on screen a while, ask them to regenerate and rescan. |
| `"06"` | Failed before reaching the issuer — validation, cancellation, merchant not active, wrong mode, read failure after the online boundary | Yes (no money moved) | Fix the input/config and re-initiate; `message` says which check failed. Cancellation returns `"06"` with message `"Payment cancelled"`. |
| `"68"` (was `"99"`) | Pending — sent, no reply received (timeout/network) | Callback fires, outcome unresolved | **Do not charge again.** The SDK stores the transaction as `PENDING` and keeps polling; show "processing" and let the history row resolve. |
| `"91"` | Never connected — the request provably never left | **`FAILED`** — nothing happened, retry is safe | Same — poll, don't retry-charge. |
| `"12"` / `"14"` / `"51"` / `"54"` | Invalid transaction / invalid card / insufficient funds / expired card | Yes | Hard declines — show the reason, try another card. |
| `"96"` | System malfunction — **ambiguous**: the payment may have failed *or* succeeded with the response lost | Yes, but unresolved | Don't assume failure: poll the transaction status briefly before telling the merchant it failed. |

### Holding a `PENDING` payment, and being told when it settles

Because the SDK no longer invents terminal outcomes, a tap that gets no answer hands you
`responseStatus == PENDING`. **That is not a failure and not a decline** — the payment may well have
completed, so the one thing you must not do is charge again.

What the app should do:

1. **Stay on the confirmation screen** and show "processing". Do not navigate away and do not print a
   receipt yet.
2. **Let the SDK resolve it.** It stores the transaction and polls with backoff; you do not have to.
3. **Finish when it settles** — either from `onTransactionResolved` (below) or by reading the row with
   `getTransaction(reference)` / `getLastTransactions()`.

A pending row always converges: it becomes `APPROVED`, `DECLINED` or `FAILED` when the backend settles
it, or it stays `PENDING`. It never turns into a terminal outcome the SDK made up, and there is no
attempt cap that gives up on it.

**`TRANSACTION_IN_PROCESS_ESCALATED`** is the one reason that changes what *you* do. It means automated
reconciliation has stopped and a human will settle the payment. Stop any tight loop of your own, tell
the merchant "we're looking into this", and re-check lazily — next app open, or a long backoff. It will
still resolve; it just will not resolve in seconds.

#### `onTransactionResolved` — the SDK pushes the answer

```kotlin
TransactionResolvedObserver.onTransactionResolved { resolution ->
    // resolution.reference — which payment (you may have more than one pending)
    // resolution.status    — APPROVED / DECLINED / FAILED (never PENDING)
    // resolution.reason    — e.g. INSUFFICIENT_FUNDS
    // resolution.responseCode — the wire literal, for receipts and support
}
```

Four things worth knowing before you rely on it:

- **Register once, at start-up** — not per payment. It fires for *any* transaction that resolves,
  including one started in an earlier app session and settled by a later poll. That is the case that
  matters most: a tap that resolves after your app was backgrounded or killed.
- **It does not replay.** If your app was not running when the row settled, nothing is queued for you —
  read `getLastTransactions()` at start-up. The observer is a convenience over the store, not a delivery
  guarantee, so keep the read path.
- **Registration is single-listener: last registration wins.** Calling it again *replaces* the previous
  observer rather than adding a second one, and `TransactionResolvedObserver.clear()` stops it. There is
  no subscription token and no listener list — if two parts of your app want the answer, fan it out
  yourself from one registration.
- **The payment callback still fires exactly once**, possibly with `PENDING`. The resolution arrives on
  this separate channel; the two are not alternatives.
- It is delivered on the main thread, like the payment callback.

The same channel exists on the other platforms, with the same semantics: iOS calls
`VeyraSoftPOS.shared.transactions.onTransactionResolved { … }` (with
`stopObservingTransactionResolved()`), and React Native subscribes with
`merchant.onTransactionResolved(listener)` on both of its platforms.

#### When the SDK could not start a payment at all

`sdkErrorCode` is set when nothing was ever attempted — request validation, cancellation, merchant not
active or not fully onboarded, a mode/arming refusal — or when the SDK itself failed. The codes name the
cause: `INVALID_REQUEST`, `PAYMENT_CANCELLED`, `TRANSACTION_IN_PROGRESS`, `MERCHANT_NOT_ACTIVE`,
`MERCHANT_PROFILE_INCOMPLETE`, `NFC_MODE_REFUSED`. Fix the input and re-initiate — nothing needs
reconciling, because nothing was sent.

Branch on `sdkErrorCode != null` **before** you look at the code. For compatibility the SDK still puts
the legacy `"06"` in `transactionCode` on these responses by default; opt in to the honest shape — an
**empty** code and no status — with:

```kotlin
CardPaymentService.typedPreDispatchErrors = true
```

The typed shape is deliberate: a response code asserts that a payment was attempted and something
answered or failed to, so a fabricated one invites you to retry something that never left the device
(and puts a made-up code on a receipt). New integrations should opt in from day one; the legacy `"06"`
exists only so that apps written before this field keep behaving exactly as they did.

### Wallet tap outcome — `onTransactionCompleted`

When the customer's phone is tapped on a terminal, the wallet's `onTransactionCompleted` callback (registered via `setActiveToken`) delivers `TransactionResponse.status`:

| Status | Meaning | What to do |
|---|---|---|
| `"APPROVED"` | The card produced an approval cryptogram for the terminal | Show the in-wallet confirmation. This is the **offline leg** — the final authorisation lands in the card's history (`authorizationStatus`) once the SDK's polling resolves it. |
| `"DECLINED"` | The card declined the tap (e.g. terminal risk limits) | Show the decline. |
| `"ERROR"` | The tap could not be processed | Ask the user to tap again. |

The SDK also vibrates the phone itself — once on success, twice on decline/error — so your UI feedback is supplementary.

### Refused payments — `onRequireOnline` and `onAmountExceedCardLimit` (1.0.12+)

A payment can be refused **before anything is sent**, when the card's payment keys cannot carry the amount. This is not a terminal decline and has no response code: on the tap rail it ends the tap at the protocol level, so `onTransactionCompleted` fires with `"DECLINED"` and these callbacks tell you *why*.

They are two callbacks rather than one because the advice differs, and giving the payer the wrong one wastes their time:

| Callback | Meaning | What to tell the payer |
|---|---|---|
| `onRequireOnline` | The card's keys need refreshing and the wallet could not reach the server | "Connect to the internet and try again" — this genuinely fixes it |
| `onAmountExceedCardLimit` | The amount is over the card's per-payment cap | "Pay a smaller amount or use another card". **Never say "go online"** — a refreshed key carries the same cap, so they would connect, retry and fail identically |

Both carry `amountMinorUnits` (name the amount that failed) and `rail` (`"TAP"`, `"CPM_QR"` or `"MPM_QR"`). `onAmountExceedCardLimit` also carries `cardLimitMinorUnits` — the cap, when the SDK can read it, or `null`; show the figure only when it is non-null rather than printing a guess.

**Timing on the tap rail.** `onRequireOnline` arrives at the earliest moment it is actually true: immediately if the device is already offline, otherwise only after the SDK's automatic background refresh has failed. If that refresh succeeds — the usual case on a working connection, within about a second — **nothing fires** and the next tap simply works. So treat the absence of this callback after a declined tap as "ask them to tap again", not as an error.

**These describe the payment, not the card.** `Token.requiresOnline` answers a different question — "can this card pay *anything* offline?" — and stays `false` for a card that can still make smaller payments. Show a message about the payment that just failed; don't grey the card out on the strength of one refused amount.

On iOS the same two signals are delivered by `observePaymentRefusals`; both fire from the QR rails only, since iOS has no tap-to-pay. On React Native they arrive as `requireOnline` / `amountExceedCardLimit` phases of the `walletTap` event.

**One difference worth knowing before you port an Android integration to iOS.** On Android these two refusals are **per token**: you pass them to the card you are arming, so different cards can carry different handlers at the same time. On iOS and React Native they are a **single SDK-wide registration** — `observePaymentRefusals` replaces whatever was registered before, and the refusal's `tokenUniqueReference` tells you which card it was about. Nothing is lost (the payload identifies the card either way), but code that relies on "this handler only ever hears about *this* card" has to start filtering on `tokenUniqueReference` when it moves to iOS. It is the same single-listener rule the deferred-answer observers follow.

### QR context lifecycle — `contextStatus().state`

| State | Meaning | What to do |
|---|---|---|
| `PENDING` | QR live, unpaid | Keep polling. |
| `IN_FLIGHT` | A wallet claimed it; settling | Keep polling. |
| `APPROVED` / `DECLINED` | Settled — `responseCode` carries the rail outcome | Stop polling; result screen + receipt. |
| `EXPIRED` | Lapsed unpaid (your `onExpired` callback has blanked the QR) | Offer a fresh QR. An expired context is never recorded in history. |

### Rail response codes (all QR + settlement legs)

| Code | Meaning | What to do |
|---|---|---|
| `"00"` | Approved | `approved` convenience fields on every outcome type are exactly this check. |
| `"05"` | Definitive decline (issuer/token provider refused — includes stale/tampered customer QRs, restriction and limit breaches) | Show decline. On a customer-QR charge, a code that sat on screen may simply be stale — ask the customer to regenerate. |
| `"96"` | System error — **outcome ambiguous** (may settle later via reconciliation) | Keep polling briefly (merchant: `contextStatus`; wallet: `reconcilePendingTransactions`) before declaring failure. |
| `null` | Not settled yet | Keep polling. |

### Digitisation & eligibility — `responseCode`

Exactly three values on both eligibility and digitise responses:

| Code | Meaning | What to do |
|---|---|---|
| `"APPROVED"` | Eligible / provisioned and active | Card is ready — show it in the wallet. |
| `"APPROVE_REQUIRE_AUTH"` | Provisioned, needs activation | Run the activation flow with the returned `activationMethods`. |
| `"DECLINED"` | Refused | Show `message` (it carries the reason — e.g. the account falls outside your configured provision-context allow-lists). Flow ends. |

Digitise failures additionally carry `error.code`: `CONFIG_ERROR` (fix your configuration), `TOKENIZATION_ERROR` (server refused — show message), `UNEXPECTED_ERROR` (retry later).

### Activation — `status` + `failureCode`

`ActivationCodeResponse.status` / `ActivateResponse.status` are `"SUCCESS"` / `"FAILURE"` — **check `status` even when the call itself succeeds.** On failure, branch on the typed `failureCode` (`message` is display text — never string-match it):

| `failureCode` | Meaning | What to do |
|---|---|---|
| `CODE_EXPIRED` | Code lapsed; attempts may remain | Offer "resend code". |
| `CODE_INVALID` | Wrong code, attempts remain | Stay on entry; show `attemptsRemaining`. |
| `MAX_ATTEMPTS_EXCEEDED` | The 3-attempt limit for this code is exhausted | The cycle is closed; honour `recommendDelete` (`MUST`: delete the token and restart add-card; `MAY`: advisory). |
| `CODE_REQUEST_RATE_LIMITED` | Re-request rate cap (per token, per hour) | Disable "resend" with a cool-down message — do **not** end the flow. |
| `NO_PENDING_ACTIVATION` | No live code (never requested, or the pending window lapsed) | Request a code first. |
| `ACTIVATION_LOCKED` | Locked after repeated exhausted cycles | Terminal — hide both retry and resend; the issuer must unlock; direct the user to their bank. |
| `TOKEN_NOT_FOUND` / `TOKEN_NOT_ACTIVATABLE` | No activatable token behind the reference | End the flow; re-digitise or contact the issuer. |
| `INVALID_REQUEST` / `ACTIVATION_FAILED` | Malformed request / server-side activation error | Show `message`; safe to retry `ACTIVATION_FAILED` later. |
| `UNKNOWN` | A code newer than this SDK | Show `message`; log `failureCodeRaw`. |

### Card lifecycle statuses

The wallet syncs each card's server status automatically (foreground sweeps and around payments). What your app observes:

| Server status | Effect in the SDK | What to do |
|---|---|---|
| `ACTIVE` | Card pays normally | — |
| `SUSPENDED` / `EXPIRED` / `PENDING_ACTIVATION` | Card refuses to pay (`TOKEN_NOT_ACTIVE:`); `Token.isActive` flips false | Render the card as unavailable. **Not sticky** — a later sync unfreezes it automatically. |
| `DEACTIVATED` | The card and all its on-device material are wiped when your app calls `deactivateToken`, and it disappears from `getTokens()` | Refresh your card list; the user re-adds the card if needed. |

### Merchant statuses

`ACTIVE` / `INACTIVE` / `SUSPENDED` / `DEACTIVATED` (on registration results, status responses and every payment response's `merchantStatus`). Payments are refused client-side unless the merchant is `ACTIVE` — gate your get-paid entry on `isRegistered` + `isMerchantActive()` and call `refreshStatus()` while awaiting activation.

### History status vocabularies

| Field | Values |
|---|---|
| Wallet `TransactionSummary.authorizationStatus` | `PENDING` (still polling) / `APPROVED` / `DECLINED` / `FAILED` / `null` (legacy — indeterminate) |
| Merchant history status | `APPROVED` / `DECLINED` / `PENDING` (outcome unknown, SDK keeps polling) / `FAILED` (never reached the server) |
| Wallet scan rejection (typed) | `MALFORMED` / `MISSING_SIGNATURE` / `UNKNOWN_KEY` / `BAD_SIGNATURE` (show "couldn't verify this code") / `EXPIRED` (show "code expired — ask the merchant for a fresh one"); every rejection ends the flow |

### Quick reference — handling failed & declined responses

The consolidated playbook. "Safe to retry" means no money can have moved.

| You receive | Where | Safe to retry? | Do this |
|---|---|---|---|
| Tap code `"05"` / status `DECLINED` | Merchant tap / rails | Yes (new attempt) | Show decline; try another card or rail. |
| Tap code `"06"` / status `FAILED` | Merchant tap | Yes | Nothing reached the issuer — fix what `message` names (input, config, merchant inactive, wrong mode) and re-initiate. |
| Status `PENDING` (codes `68`/`06`/`96`/`09`) | Merchant tap | **No — never re-charge** | Outcome unknown at the issuer. Show "processing"; the SDK polls and resolves the history row. Re-charging risks a double charge. |
| Code `"96"` | Any rail | **No — not yet** | Ambiguous: may have succeeded with the response lost. Poll briefly (context status / transaction status / reconcile) before reporting failure. |
| `EXPIRED` context / `onExpired` fired | Get-paid QR | Yes | The QR died unpaid (never recorded). Blank it, offer a fresh one. |
| `inspect` throws (customer QR) | Merchant CPM scan | Yes | Not a payment QR — transient hint, stay armed for another scan. |
| `"05"` on a customer-QR charge | Merchant CPM | Yes (fresh QR) | Could be a stale/hoarded QR: ask the customer to regenerate and rescan before treating it as a funds decline. |
| Scan rejected (`EXPIRED` / `BAD_SIGNATURE` / …) | Wallet MPM scan | Yes (fresh scan) | End the flow; ask the merchant for a fresh code. Never show a rejected payment on a confirm screen. |
| `Authentication cancelled:` / `Authentication failed:` | Wallet payments | Yes | Nothing was sent. Stay on the confirm screen; let the user retry the biometric. |
| `WalletRefusalException.OnlineRequired` (message prefix `ONLINE_REQUIRED:`) | Wallet payments | After going online | Prompt to connect; the SDK refreshes the card itself. Pre-empt with `requiresOnline` (grey the card out). |
| `WalletRefusalException.AmountExceedsCardLimit` (message prefix `AMOUNT_EXCEEDS_CARD_LIMIT:`) | Wallet payments | **Not by retrying** | The amount is larger than this card can carry in one payment. Going online does **not** help — offer a smaller amount or another card. |
| `WalletRefusalException.TokenNotActive` (message prefix `TOKEN_NOT_ACTIVE:`) | Wallet payments | No (until active) | Card is suspended/inactive server-side. Show why; it unfreezes automatically when a sync sees it active. Don't build retry loops. |

The three pre-proof refusals above are **typed exceptions** — `when` on the `WalletRefusalException` subtype instead of string-matching the message (the message prefixes are unchanged, so existing string checks keep working):

```kotlin
onFailure = { e ->
    when (e) {
        is WalletRefusalException.OnlineRequired -> promptToConnect()
        is WalletRefusalException.AmountExceedsCardLimit -> offerSmallerAmountOrOtherCard()
        is WalletRefusalException.TokenNotActive -> showCardUnavailable()
        else -> showError(e.message)
    }
}
```
| `CDCVM required:` | Wallet QR payments | Yes | Call `authenticateForScannedPayment` first — one authentication per payment / per QR render. |
| Digitise `"DECLINED"` | Add card | Per `message` | Show the server's message; the flow ends. Common cause: the account falls outside your provision-context allow-lists. |
| Activation `"FAILURE"` | Activation | Per `failureCode` | Branch on the typed [`failureCode`](#activation--status--failurecode): resend on `CODE_EXPIRED`, cool-down on `CODE_REQUEST_RATE_LIMITED`, stop entirely on `ACTIVATION_LOCKED` ("contact your issuer"). |
| `SdkModeException` | Combined apps | Yes (after mode settles) | The other mode's payment is mid-flight — prompt to finish/cancel it. |

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
    val requiresOnline: Boolean             // true: card can't pay until the wallet has been online — grey it out
) {
    fun getMaskedPAN(): String              // "**** **** **** 1234"
    fun getLastFourDigits(): String
}

data class ActivationMethod(val medium: String, val contact: String)
// medium: MASKED_EMAIL, MASKED_MOBILE_PHONE, CALL_CENTER_PHONE,
//         AUTOMATED_CALL_CENTER_PHONE, WEBSITE, MOBILE_APPLICATION
// contact: the masked phone/email, phone number, URL or app package to act on

data class Bank(val slug: String, val name: String, val institutionCode: String)

data class VerifyAccountResponse(val responseCode: String?, val message: String?)
// responseCode "APPROVED" = eligible

data class TokenisationResponse(
    val responseCode: String?,              // APPROVED / APPROVE_REQUIRE_AUTH / DECLINED
    val tokenUniqueReference: String?,
    val activationMethods: List<ActivationMethod>?,
    val isSuccess: Boolean,
    val status: String?,                    // SUCCESS / PENDING / FAILED
    val message: String?,
    val error: TokenisationError?           // code: CONFIG_ERROR / TOKENIZATION_ERROR / UNEXPECTED_ERROR
)

data class ActivationCodeResponse(
    val tokenUniqueReference: String?,
    val expirationDateTime: String?,        // ISO-8601 — drive the OTP countdown
    val status: String?,                    // SUCCESS / FAILURE — check it even on a successful Result
    val message: String?
)

data class ActivateResponse(val tokenUniqueReference: String?, val status: String?, val message: String?)
data class TokenStatusUpdateResponse(val tokenUniqueReference: String?, val status: String?, val message: String?)

data class TransactionSummary(
    val merchantName: String,
    val amountInMinorUnit: Int,             // 150000 = ₦1,500.00
    val transactionCurrencyCode: String?,   // ISO 4217 numeric 4-digit, e.g. "0566"
    val authorizationStatus: String?,       // PENDING / APPROVED / DECLINED / FAILED; null = legacy
    val entryMethod: String?,               // "TAP" / "QR_GENERATED" / "QR_SCANNED"; null = legacy
    val merchantLocation: String?,          // "city, state" when known
    val transactionHash: String?,           // join key to a TransactionReceipt
    val localTransactionDateTime: String?,  // ISO 8601 (tap rows)
    val atEpochMillis: Long?,               // (QR rows)
    val merchantTransactionReference: String?,
    val merchantId: String?
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
data class MpmPushOutcome(val approved: Boolean, val responseCode: String?, val message: String?)

// Show-QR-to-pay:
data class CpmPaymentQr(
    val tokenUniqueReference: String,
    val payload: String,                    // render as the QR
    val amountMinorUnits: Long, val currencyNumeric: String,
    val expiresAtEpochMillis: Long,
    val transactionHash: String             // this render's unique hash — match against history to reconcile
)
```

`CurrencyUtils` (wallet): `getSymbol(code)`, `formatAmount(minorUnits, code)` — display helpers for ISO 4217 numeric codes.

### SoftPOS

```kotlin
data class NubanBank(val slug: String, val name: String, val institutionCode: String)

data class StoredMerchantData(
    val merchantId: String, val merchantType: String,   // "PERSONAL" / "BUSINESS"
    val merchantName: String, val emailAddress: String, val phoneNumber: String,
    val addressLine1: String, val addressLine2: String, val city: String, val state: String,
    val countryCode: String, val bvn: String?, val cacNumber: String?,
    val accountNumber: String, val institutionCode: String, val acquirerId: String,
    val merchantCategoryCode: String,       // backend-assigned
    val terminalId: String,                 // backend-assigned
    val merchantStatus: String?,            // last known ("ACTIVE", …)
    val currencyCode: String?
)

data class TransactionResponse(             // tap terminal outcome (makeCardPayment callback)
    val transactionCode: String,            // see Response codes
    val message: String?,
    val amount: String?,                    // minor units as string
    val cardScheme: String?,                // "VISA", "MASTERCARD", …
    val cardExpiry: String?,                // YYMM
    val merchantTransactionReference: String?,  // your echoed reference — receipt lookup key
    val transactionType: String?, val maskedTokenLast4: String?,
    val merchantStatus: String?, val transactionId: String?, val aid: String?,
    val creditTransactionId: String?,           // merchant-bank credit id (approved + supported only)
    val isCreditConfirmationSupported: Boolean? // true ⇒ the SDK will poll and fire onCreditConfirmation
)

data class TransactionInfo(                 // history row
    val merchantTransactionReference: String,
    val amount: Long,                       // minor units
    val transactionStatus: TransactionStatus,   // APPROVED / DECLINED / PENDING / FAILED
    val responseCode: String?,
    val responseStatusReason: String?,      // the stated cause ("INSUFFICIENT_FUNDS"...); display, never parse
    val transactionTime: String?,           // "yyyy-MM-dd HH:mm:ss"
    val currencyCode: String?, val transactionId: String?,
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
                          authenticateForScannedPayment (biometric)
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
