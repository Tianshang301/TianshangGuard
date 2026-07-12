package com.tianshang.guard.ui.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.tianshang.guard.core.quish.QrDecision
import com.tianshang.guard.core.quish.QrType
import com.tianshang.guard.ui.theme.DeepNavy
import com.tianshang.guard.ui.theme.GuardGreen
import com.tianshang.guard.ui.theme.GuardOrange
import com.tianshang.guard.ui.theme.GuardRed
import com.tianshang.guard.ui.theme.OnSurfaceDark
import com.tianshang.guard.ui.theme.OnSurfaceVariantDark
import com.tianshang.guard.ui.theme.ShieldBlue
import com.tianshang.guard.ui.theme.SurfaceDark
import com.tianshang.guard.ui.theme.SurfaceVariantDark
import com.tianshang.guard.ui.theme.TianshangGuardTheme
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors

class QrPreviewActivity : ComponentActivity() {

    private val viewModel: QrPreviewViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TianshangGuardTheme {
                val state by viewModel.state.collectAsState()
                val context = LocalContext.current
                val cameraPermissionGranted = remember {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        viewModel.resetScan()
                    } else {
                        finish()
                    }
                }

                LaunchedEffect(Unit) {
                    if (!cameraPermissionGranted) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (!cameraPermissionGranted) {
                    CameraPermissionRequest()
                    return@TianshangGuardTheme
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepNavy)
                ) {
                    if (state.scanning) {
                        CameraPreview(
                            onQrDetected = { viewModel.onQrDetected(it) }
                        )
                        ScanOverlay()
                    } else {
                        QrResultPanel(
                            state = state,
                            onRescan = { viewModel.resetScan() },
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun CameraPermissionRequest() {
    Box(
        modifier = Modifier.fillMaxSize().background(DeepNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "需要相机权限",
                style = MaterialTheme.typography.headlineSmall,
                color = OnSurfaceDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "扫描二维码需要相机权限",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantDark,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CameraPreview(onQrDetected: (String) -> Unit) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER

                val cameraExecutor = Executors.newSingleThreadExecutor()
                val lifecycleOwner = ctx as androidx.lifecycle.LifecycleOwner

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(this.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                    imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        QrImageAnalyzer(onQrDetected)
                    )
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("QrPreview", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

@Composable
fun ScanOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Text(
            text = "将二维码置于框内",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
fun QrResultPanel(
    state: QrScanState,
    onRescan: () -> Unit,
    onDismiss: () -> Unit
) {
    val decision = state.decision
    val detected = state.detected

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepNavy)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val icon = when (decision) {
                is QrDecision.BlockWithPreview -> "\u26D4"
                is QrDecision.WarnWithPreview -> "\u26A0\uFE0F"
                else -> "\u2705"
            }
            val title = when (decision) {
                is QrDecision.BlockWithPreview -> "危险二维码"
                is QrDecision.WarnWithPreview -> "可疑二维码"
                else -> "安全二维码"
            }
            val color = when (decision) {
                is QrDecision.BlockWithPreview -> GuardRed
                is QrDecision.WarnWithPreview -> GuardOrange
                else -> GuardGreen
            }

            Text(
                text = icon,
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (detected != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "类型: ${detected.type.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = OnSurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detected.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceDark,
                            maxLines = 4
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceVariantDark
                    )
                ) {
                    Text("关闭")
                }
                Button(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldBlue
                    )
                ) {
                    Text("重新扫描")
                }
            }

            if (detected?.type == QrType.URL && decision !is QrDecision.BlockWithPreview) {
                val ctx = LocalContext.current
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detected.content))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GuardGreen
                    )
                ) {
                    Text("打开链接")
                }
            }
        }
    }
}
