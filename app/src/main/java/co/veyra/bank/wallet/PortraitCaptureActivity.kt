package co.veyra.bank.wallet

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Portrait scan-to-pay scanner. The zxing-android-embedded default `CaptureActivity` is
 * declared `sensorLandscape`, so the QR preview opens sideways. This subclass is registered in the
 * manifest with `screenOrientation="portrait"` and pointed to via
 * `ScanOptions.setCaptureActivity(...)`, keeping the preview upright.
 */
class PortraitCaptureActivity : CaptureActivity()
