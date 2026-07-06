package com.ga.airdrop.feature.shipments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ga.airdrop.R
import com.ga.airdrop.core.designsystem.components.GradientButton
import com.ga.airdrop.core.designsystem.theme.AirdropTheme
import com.ga.airdrop.core.designsystem.theme.AirdropType
import com.ga.airdrop.core.designsystem.theme.BrandPalette
import com.ga.airdrop.core.designsystem.theme.Radius
import com.ga.airdrop.core.designsystem.theme.Spacing
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun QuickTrackBarcodeScanner(
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraUnavailable by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("shipments-quick-track-scanner"),
    ) {
        if (hasCameraPermission && !cameraUnavailable) {
            ScannerCameraPreview(
                onUnavailable = { cameraUnavailable = true },
                onCodeScanned = { payload ->
                    val cleaned = payload.trim().uppercase(Locale.US)
                    if (cleaned.isNotEmpty()) onCodeScanned(cleaned)
                },
                modifier = Modifier.fillMaxSize(),
            )
            LiveScannerOverlay(onDismiss = onDismiss)
        } else {
            CameraPermissionState(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun ScannerCameraPreview(
    onUnavailable: () -> Unit,
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                providerFuture.addListener(
                    {
                        runCatching {
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also { preview ->
                                preview.setSurfaceProvider(surfaceProvider)
                            }
                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(
                                        analysisExecutor,
                                        QuickTrackBarcodeAnalyzer { payload ->
                                            if (delivered.compareAndSet(false, true)) {
                                                onCodeScanned(payload)
                                            }
                                        },
                                    )
                                }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analyzer,
                            )
                        }.onFailure {
                            onUnavailable()
                        }
                    },
                    ContextCompat.getMainExecutor(viewContext),
                )
            }
        },
    )
}

@Composable
private fun LiveScannerOverlay(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScannerIconButton(
                label = "Close scanner",
                text = "X",
                modifier = Modifier.testTag("shipments-quick-track-scanner-close"),
                onClick = onDismiss,
            )
            Text(
                text = "Scan tracking barcode",
                style = AirdropType.subtitle1,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(36.dp))
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .height(180.dp)
                .align(Alignment.CenterHorizontally)
                .border(3.dp, BrandPalette.OrangeMain, RoundedCornerShape(20.dp))
                .testTag("shipments-quick-track-scan-window"),
        )
        Text(
            text = "Center the barcode in the frame.",
            style = AirdropType.body2,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
        Spacer(Modifier.weight(1.25f))
    }
}

@Composable
private fun CameraPermissionState(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = AirdropTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .safeDrawingPadding()
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.gray100)
                .border(1.dp, colors.iconShape, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp)
                .testTag("shipments-quick-track-camera-denied"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Camera access is off",
                style = AirdropType.subtitle1,
                color = colors.textDarkTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Enable camera access in Settings to scan tracking barcodes. You can still type the code by hand on the previous screen.",
                style = AirdropType.body2,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
            )
            GradientButton(
                text = "Open Settings",
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .testTag("shipments-quick-track-open-settings"),
            )
            Text(
                text = "Not now",
                style = AirdropType.button,
                color = colors.textDescription,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.xs))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag("shipments-quick-track-camera-not-now"),
            )
        }
    }
}

@Composable
private fun ScannerIconButton(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AirdropType.subtitle2, color = Color.White)
    }
}

@Composable
fun QuickTrackScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrandPalette.OrangeMain)
            .clickable(onClick = onClick)
            .testTag("shipments-quick-track-scan"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_qr_code),
            contentDescription = "Scan tracking barcode",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(24.dp),
        )
    }
}

private class QuickTrackBarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(
                    DecodeHintType.POSSIBLE_FORMATS,
                    listOf(
                        BarcodeFormat.QR_CODE,
                        BarcodeFormat.CODE_128,
                        BarcodeFormat.CODE_39,
                        BarcodeFormat.CODE_93,
                        BarcodeFormat.EAN_13,
                        BarcodeFormat.EAN_8,
                        BarcodeFormat.UPC_A,
                        BarcodeFormat.UPC_E,
                        BarcodeFormat.ITF,
                        BarcodeFormat.PDF_417,
                        BarcodeFormat.DATA_MATRIX,
                        BarcodeFormat.AZTEC,
                    ),
                )
                put(DecodeHintType.TRY_HARDER, true)
            },
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val sourceBytes = image.yPlaneBytes()
            val (bytes, width, height) = sourceBytes.rotateFor(image.width, image.height, image.imageInfo.rotationDegrees)
            val source = PlanarYUVLuminanceSource(
                bytes,
                width,
                height,
                0,
                0,
                width,
                height,
                false,
            )
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            result.text?.takeIf { it.isNotBlank() }?.let(onBarcode)
        } catch (_: NotFoundException) {
            // Normal camera frames often do not contain a readable code.
        } catch (_: RuntimeException) {
            // A malformed frame should not tear down the scanner.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

private data class LuminanceFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

private fun ImageProxy.yPlaneBytes(): ByteArray {
    val plane = planes.first()
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val data = ByteArray(width * height)
    var outputOffset = 0
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        if (pixelStride == 1) {
            buffer.get(data, outputOffset, width)
            outputOffset += width
        } else {
            for (column in 0 until width) {
                data[outputOffset++] = buffer.get()
                if (column < width - 1) {
                    buffer.position(buffer.position() + pixelStride - 1)
                }
            }
        }
    }
    return data
}

private fun ByteArray.rotateFor(width: Int, height: Int, rotationDegrees: Int): LuminanceFrame =
    when (rotationDegrees) {
        90 -> LuminanceFrame(rotate90(width, height), height, width)
        180 -> LuminanceFrame(rotate180(width, height), width, height)
        270 -> LuminanceFrame(rotate270(width, height), height, width)
        else -> LuminanceFrame(this, width, height)
    }

private fun ByteArray.rotate90(width: Int, height: Int): ByteArray {
    val rotated = ByteArray(size)
    var index = 0
    for (x in 0 until width) {
        for (y in height - 1 downTo 0) {
            rotated[index++] = this[y * width + x]
        }
    }
    return rotated
}

private fun ByteArray.rotate180(width: Int, height: Int): ByteArray {
    val rotated = ByteArray(size)
    var index = 0
    for (y in height - 1 downTo 0) {
        for (x in width - 1 downTo 0) {
            rotated[index++] = this[y * width + x]
        }
    }
    return rotated
}

private fun ByteArray.rotate270(width: Int, height: Int): ByteArray {
    val rotated = ByteArray(size)
    var index = 0
    for (x in width - 1 downTo 0) {
        for (y in 0 until height) {
            rotated[index++] = this[y * width + x]
        }
    }
    return rotated
}
