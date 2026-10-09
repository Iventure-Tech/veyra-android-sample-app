# Veyra Bank — Android sample app

A complete working integration of the **Veyra SDK** on Android, built against the published
artifacts exactly the way a third-party app consumes them. One app demonstrates both sides
of a contactless payment:

- **Get paid (SoftPOS merchant):** registration & profile, NFC tap acceptance, get-paid QR
  (merchant-presented), charging a customer's payment QR (consumer-presented), transaction
  history and receipt QRs.
- **Pay (wallet customer):** add card (account tokenisation), token activation, NFC
  tap-to-pay, scan-to-pay, show-QR-to-pay, card states, transaction history and receipts.

The full **[Developer Guide](DEVELOPER-GUIDE.md)** — platform requirements, install
variants, the complete public API reference with samples, and the response-code catalogue
with per-outcome guidance — lives in this repository.

> **Building with React Native? Do not integrate these AARs directly — use the official
> React Native SDK (`veyra-sdk-react-native`) instead.** The SDK arms and disarms the
> device's NFC payment modes automatically by following native screen (Activity) lifecycle.
> A React Native app runs its entire UI in a single Activity, so JavaScript navigation
> never triggers those lifecycle signals — after a user leaves your payment screen, the
> device can silently **stay armed as a payment card** until the app is backgrounded or the
> screen locks. The React Native SDK bridges screen focus into the SDK's mode management so
> arming and release follow your JS screens correctly. See
> https://github.com/Iventure-Tech/veyra-react-native-sample-app.

## Prerequisites

- Android Studio (or the Android SDK + JDK 17) and a physical NFC-capable device running
  Android 9+ (API 28) — NFC and device attestation don't work on the emulator.
- **Veyra onboarding credentials**: Maven repository username/password (the SDK repository
  is authenticated), payment app provider id, token requestor id, and whatever your
  [provider](#choose-a-provider) needs. The app talks to the Veyra TEST
  environment.
- The test account details from your onboarding pack (the prefill identity in
  `app/src/main/res/values/sample_data.xml` is a placeholder — digitisation is checked
  against the issuer's test records).

## Run it (5 minutes)

1. Clone this repository.
2. Copy the credential template and fill in your onboarding values:

   ```bash
   cp veyra.properties.example veyra.properties
   # edit veyra.properties
   ```

3. Set `veyra.connection.mode` in `veyra.properties` — there is no default, and the app
   refuses to start until it is set (see [Choose a provider](#choose-a-provider)).
4. Optionally update `app/src/main/res/values/sample_data.xml` with your test account
   details so the forms prefill usefully.
5. Connect your device and run:

   ```bash
   ./gradlew :app:installDebug
   ```

   or open the project in Android Studio and press Run.

The SDK artifacts resolve from the Veyra Maven repository
(`https://repo.veyra.co/releases`) using the repository credentials in your
`veyra.properties` — no local files, no extra setup.

## Choose a provider

Both SDKs share one **provider** — how they reach Veyra. The sample builds it from the mode read
from `veyra.connection.mode` in `veyra.properties`:

| Mode | Provider it builds | What it needs | Your bank backend serves |
|---|---|---|---|
| `directWithAssertion` (recommended) | `VeyraAuthProvider` | `veyra.clientId`, `veyra.bankBackendBaseUrl` | `POST /sdk-assertion` `{"audience": …, "jkt": …}` → `{"assertion": "<JWT>"}` (401 when nobody is signed in) |
| `viaAppBackend` | `VeyraProxyProvider` | `veyra.bankBackendBaseUrl` | `POST /veyra-relay/{post\|get\|put\|delete\|patch}` — forwards the SDK's envelope to Veyra unmodified and answers with Veyra's body |
| `directWithClientSecret` (**deprecated**) | `VeyraClientSecretProvider` | `veyra.clientId`, `veyra.clientSecret` | nothing — the secret sits in the app, which is why this mode is being retired |

`veyra.bankSessionToken` is a **placeholder** for your app's own login session, sent to your bank
backend as a bearer token. The two providers that call your backend are in
`app/src/main/java/co/veyra/bank/connection/` — short, and meant to be copied. The proxy provider is called
from the SDK's background work too, not only from screens. The full contract — the assertion's
claims, the request envelope, and how a proxy provider reports a failure — is in
[Connecting to Veyra](DEVELOPER-GUIDE.md#connecting-to-veyra).

> **Upgrading from SDK 2.x?** The config builders no longer take `clientId` / `clientSecret`;
> `initialize` takes one provider instead: a `VeyraAuthProvider` (recommended) or a
> `VeyraProxyProvider`. See
> [Migrating from 2.x to 3.0.0](DEVELOPER-GUIDE.md#migrating-from-2x-to-300). An existing
> `veyra.properties` keeps its keys; add `veyra.connection.mode` (and the bank-backend values for
> the backend modes) from `veyra.properties.example`.

## Where things are

| Path | What it shows |
|---|---|
| `app/src/main/java/co/veyra/bank/VeyraBank.kt` | SDK configuration & initialisation (both SDKs via the combined facade) |
| `app/src/main/java/co/veyra/bank/connection/` | How the SDKs reach Veyra: mode selection, and the two providers (`VeyraAuthProvider`, `VeyraProxyProvider`) that call your bank backend |
| `app/src/main/java/co/veyra/bank/HomeActivity.kt` | Home: entry to both flows, mode readout |
| `app/src/main/java/co/veyra/bank/softpos/` | The merchant (Get paid) flow — all three acceptance rails |
| `app/src/main/java/co/veyra/bank/wallet/` | The wallet (Pay) flow — add card, activation, payments, history |
| `DEVELOPER-GUIDE.md` | The full Android developer guide |

Building for **iOS**? See https://github.com/Iventure-Tech/veyra-ios-sample-app.
