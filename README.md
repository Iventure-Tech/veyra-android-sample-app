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
  is authenticated), OAuth client id/secret, payment app provider id, and token requestor
  id. The app talks to the Veyra TEST environment.
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

3. Optionally update `app/src/main/res/values/sample_data.xml` with your test account
   details so the forms prefill usefully.
4. Connect your device and run:

   ```bash
   ./gradlew :app:installDebug
   ```

   or open the project in Android Studio and press Run.

The SDK artifacts resolve from the Veyra Maven repository
(`https://repo.veyra.co/releases`) using the repository credentials in your
`veyra.properties` — no local files, no extra setup.

## Where things are

| Path | What it shows |
|---|---|
| `app/src/main/java/co/veyra/bank/VeyraBank.kt` | SDK configuration & initialisation (both SDKs via the combined facade) |
| `app/src/main/java/co/veyra/bank/HomeActivity.kt` | Home: entry to both flows, mode readout |
| `app/src/main/java/co/veyra/bank/softpos/` | The merchant (Get paid) flow — all three acceptance rails |
| `app/src/main/java/co/veyra/bank/wallet/` | The wallet (Pay) flow — add card, activation, payments, history |
| `DEVELOPER-GUIDE.md` | The full Android developer guide |

Building for **iOS**? See https://github.com/Iventure-Tech/veyra-ios-sample-app.
