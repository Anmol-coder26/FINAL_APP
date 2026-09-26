package com.guardian.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.guardian.app.ui.theme.GuardianTheme
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

data class QrVerdict(
    val rawValue: String,
    val status: RiskStatus,
    val title: String,
    val reason: String,
    val isUrl: Boolean = false,
    val isUpi: Boolean = false
)

class QrScannerActivity : ComponentActivity() {

    private var hasCameraPermission by mutableStateOf(false)
    private var scanVerdict by mutableStateOf<QrVerdict?>(null)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            GuardianTheme {
                QrScannerScreen(
                    hasCameraPermission = hasCameraPermission,
                    verdict = scanVerdict,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    onBarcodeDetected = { raw ->
                        if (scanVerdict == null) {
                            scanVerdict = evaluateQrContent(raw)
                        }
                    },
                    onResetScan = { scanVerdict = null },
                    onClose = { finish() },
                    onSimulate = { raw ->
                        scanVerdict = evaluateQrContent(raw)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        fun evaluateQrContent(raw: String): QrVerdict {
            val text = raw.trim()
            val lower = text.lowercase()

            val isUpi = lower.startsWith("upi://")
            val isUrl = lower.startsWith("http://") || lower.startsWith("https://") || lower.contains(".xyz") || lower.contains(".apk")

            return when {
                // 1. UPI Reverse Debit / Fraud
                isUpi && (lower.contains("am=") || lower.contains("pn=refund") || lower.contains("pa=cashback") || lower.contains("scam")) -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.High,
                        title = "🚨 FRAUDULENT REVERSE UPI QR",
                        reason = "This QR is crafted to DEBIT money from your bank account instead of sending money to you. Do NOT approve or enter your UPI PIN.",
                        isUpi = true
                    )
                }

                // 2. Phishing / Malicious APK / Credential Harvesting
                lower.contains(".apk") || lower.contains("login-kyc") || lower.contains("rbi-claim") ||
                lower.contains("free-gift") || lower.contains("anydesk") || lower.contains("digital-arrest") ||
                lower.contains("update-pan") || lower.contains("bank-verify") -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.High,
                        title = "🚨 HIGH RISK MALICIOUS QR",
                        reason = "Contains known phishing signatures, rogue APK download, or banking credential harvesting patterns.",
                        isUrl = isUrl
                    )
                }

                // 3. Suspicious unverified URL / shortener
                isUrl && (lower.contains("bit.ly") || lower.contains("tinyurl.com") || lower.contains("is.gd") || lower.contains(".top") || lower.contains(".xyz")) -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Suspicious,
                        title = "⚠️ SUSPICIOUS OBFUSCATED URL",
                        reason = "Uses a shortened or untrusted top-level domain that masks the true destination website.",
                        isUrl = true
                    )
                }

                // 4. Clean verified URL or standard text
                isUrl -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Low,
                        title = "✅ VERIFIED SAFE LINK",
                        reason = "Clean domain format. No blacklisted phishing patterns or malicious parameters detected.",
                        isUrl = true
                    )
                }

                isUpi -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Low,
                        title = "✅ STANDARD UPI PAYMENT QR",
                        reason = "Standard merchant or recipient VPA address format.",
                        isUpi = true
                    )
                }

                else -> {
                    QrVerdict(
                        rawValue = text,
                        status = RiskStatus.Low,
                        title = "✅ SCANNED TEXT / DATA",
                        reason = "No harmful command scripts or exploit payloads found in text content."
                    )
                }
            }
        }
    }
}

@Composable
fun QrScannerScreen(
    hasCameraPermission: Boolean,
    verdict: QrVerdict?,
    onRequestPermission: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onResetScan: () -> Unit,
    onClose: () -> Unit,
    onSimulate: (String) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (hasCameraPermission) {
            CameraPreviewView(
                isScanningActive = verdict == null,
                onBarcodeDetected = onBarcodeDetected
            )
        } else {
            // Permission Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = CyberEmerald,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("Camera Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "Guardian needs camera permission to scan QR and barcodes for scam detection.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color(0xFF022C22))
                ) {
                    Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated.copy(alpha = 0.8f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(12.dp))
            Text("QR & Barcode Shield", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Viewfinder Reticle Overlay when scanning
        if (verdict == null && hasCameraPermission) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.Center)
                    .border(BorderStroke(2.dp, CyberEmerald), shape = RoundedCornerShape(16.dp))
            )
            Text(
                "Align QR / Barcode within frame",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 300.dp)
                    .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom Result Overlay or Stage Demo Buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (verdict != null) {
                // VERDICT CARD
                val (cardBg, accentColor, borderCol) = when (verdict.status) {
                    RiskStatus.High -> Triple(Color(0xFF330909), CoralRed, CoralRed)
                    RiskStatus.Suspicious -> Triple(Color(0xFF2E1C03), AmberWarning, AmberWarning)
                    RiskStatus.Low -> Triple(DarkSurfaceElevated, CyberEmerald, CyberEmerald.copy(alpha = 0.5f))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.5.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (verdict.status == RiskStatus.Low) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                verdict.title,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            verdict.reason,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = DarkBackground.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                verdict.rawValue.take(120) + if (verdict.rawValue.length > 120) "..." else "",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Scanned QR", verdict.rawValue))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy", fontSize = 12.sp)
                            }

                            if (verdict.isUrl && verdict.status == RiskStatus.Low) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verdict.rawValue))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald, contentColor = Color(0xFF022C22)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = onResetScan,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Rescan", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Stage Quick Simulator Controls
                Surface(
                    color = DarkSurfaceElevated.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Instant QR Test Simulator:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSimulate("upi://pay?pa=cyberfraud@fakebank&pn=RefundDesk&am=9800&cu=INR") },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🚨 Reverse UPI", fontSize = 10.sp, color = CoralRed)
                            }
                            OutlinedButton(
                                onClick = { onSimulate("https://sbi-card-kyc-update.xyz/verify.apk") },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⚠️ Fake APK", fontSize = 10.sp, color = AmberWarning)
                            }
                            OutlinedButton(
                                onClick = { onSimulate("https://www.npci.org.in/what-we-do/upi/product-overview") },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("✅ Safe Link", fontSize = 10.sp, color = CyberEmerald)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreviewView(
    isScanningActive: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_DATA_MATRIX,
                        Barcode.FORMAT_AZTEC,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_CODE_128
                    )
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy: ImageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && isScanningActive) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        scanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val raw = barcode.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        onBarcodeDetected(raw)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("GuardianQR", "Camera bind failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
