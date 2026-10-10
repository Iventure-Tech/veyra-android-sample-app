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

3. The sample ships using the deprecated `VeyraClientSecretProvider`, **for testing only**, so
   it runs with just your `veyra.clientId` and `veyra.clientSecret`. To try the providers a real
   app ships, change the one line in `AppProvider.provider()` (see
   [Choose a provider](#choose-a-provider)).
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

Both SDKs share one **provider** — how they reach Veyra. There is no mode to set: the SDK works
out the method from the kind of provider it is given. The sample picks one in code —
`AppProvider.provider()` in `app/src/main/java/co/veyra/bank/provider/AppProvider.kt` returns
it — and switching is returning a different one:

| `provider()` returns | Provider | What it needs (`veyra.properties`) | Your bank backend serves |
|---|---|---|---|
| `assertionProvider(…)` (recommended) | `VeyraAssertionProvider` | `veyra.clientId` (the only value the SDK receives), `veyra.bankBackendBaseUrl`, `veyra.bankClientId`, `veyra.bankClientSecret` | `POST /oauth2/token`: an RFC 8693 token exchange of the user's session for the assertion, authenticated with your bank's own client (`bankClientId`/`bankClientSecret`, HTTP Basic) → `{"access_token": "<JWT>"}` (401 when nobody is signed in) |
| `proxyProvider(…)` | `VeyraProxyProvider` | `veyra.bankBackendBaseUrl` | `POST /issuertokengateway/v1` for every method — your API gateway forwards the SDK's envelope to your issuer token gateway, which calls Veyra and answers with Veyra's body |
| `clientSecretProvider(…)` (**deprecated**, what the sample ships with) | `VeyraClientSecretProvider` | `veyra.clientId`, `veyra.clientSecret` | nothing — the secret sits in the app, which is why this provider is being retired |

`veyra.bankSessionToken` is a **placeholder** for your app's own login session, sent to your bank
backend as a bearer token. The two providers that call your backend are in
`app/src/main/java/co/veyra/bank/provider/` — short, and meant to be copied. The proxy provider is called
from the SDK's background work too, not only from screens. The full contract — the assertion's
claims, the request envelope, and how a proxy provider reports a failure — is in
[Connecting to Veyra](DEVELOPER-GUIDE.md#connecting-to-veyra).

## Where things are

| Path | What it shows |
|---|---|
| `app/src/main/java/co/veyra/bank/VeyraBank.kt` | SDK configuration & initialisation (both SDKs via the combined facade) |
| `app/src/main/java/co/veyra/bank/provider/` | How the SDKs reach Veyra: the one provider the app passes (`AppProvider.provider()`), and the two providers (`VeyraAssertionProvider`, `VeyraProxyProvider`) that call your bank backend |
| `app/src/main/java/co/veyra/bank/HomeActivity.kt` | Home: entry to both flows, mode readout |
| `app/src/main/java/co/veyra/bank/softpos/` | The merchant (Get paid) flow — all three acceptance rails |
| `app/src/main/java/co/veyra/bank/wallet/` | The wallet (Pay) flow — add card, activation, payments, history |
| `DEVELOPER-GUIDE.md` | The full Android developer guide |

Building for **iOS**? See https://github.com/Iventure-Tech/veyra-ios-sample-app.
