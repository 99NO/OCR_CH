package com.example.ocr_ch

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

@Composable
fun OcrScreen(
    modifier: Modifier = Modifier,
    vm: OcrViewModel = viewModel()
) {
    val context = LocalContext.current

    val ocrResult by vm.ocrResult.collectAsState()
    val displayBitmap by vm.displayBitmap.collectAsState()
    val isProcessing by vm.isProcessing.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val translatedText by vm.translatedText.collectAsState()
    val isTranslating by vm.isTranslating.collectAsState()
    val translationError by vm.translationError.collectAsState()
    val isSpeaking by vm.isSpeaking.collectAsState()
    val ttsReady by vm.ttsReady.collectAsState()
    val ttsError by vm.ttsError.collectAsState()

    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { vm.processUri(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.processUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Chinese OCR",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (hasCameraPermission) {
                        val uri = createCameraImageUri(context)
                        cameraImageUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("카메라 촬영") }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) { Text("갤러리에서 선택") }
        }

        HorizontalDivider()

        // 이미지 미리보기
        displayBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "선택된 이미지",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentScale = ContentScale.Fit
            )
        }

        // OCR 처리 중
        if (isProcessing) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("OCR 처리 중...")
                }
            }
        }

        errorMessage?.let { err ->
            Text(text = err, color = MaterialTheme.colorScheme.error)
        }

        // ═══ OCR 결과 섹션 ═══
        ocrResult?.let { result ->
            HorizontalDivider()

            // ── 중국어 OCR 원문 ──────────────────────────
            SectionTitle("중국어 OCR 결과")

            if (result.wasRotated) {
                Text(
                    text = "※ 이미지가 180° 자동 회전됨 (아래 텍스트는 회전 후 결과)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF388E3C)
                )
            }

            if (result.text.isNotEmpty()) {
                Text(
                    text = result.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                )

                // TTS 버튼
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSpeaking) {
                        Button(onClick = { vm.stopSpeaking() }) {
                            Text("⏹  읽기 정지")
                        }
                    } else {
                        Button(
                            onClick = { vm.speakChinese(result.text) },
                            enabled = ttsReady
                        ) {
                            Text("🔊  중국어 읽기")
                        }
                    }
                    if (!ttsReady && !isSpeaking) {
                        Text(
                            text = "TTS 준비 중...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ttsError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text(
                    text = "텍스트를 찾을 수 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── 한국어 번역 ──────────────────────────────
            SectionTitle("한국어 번역")

            when {
                isTranslating -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = "번역 중... (첫 실행 시 모델 다운로드 필요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                translatedText != null -> {
                    Text(
                        text = translatedText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF388E3C), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    )
                }
                translationError != null -> {
                    Text(
                        text = translationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()

            // ── 문서 방향 ────────────────────────────────
            SectionTitle("Document Orientation")

            val (orientationLabel, orientationColor) = when (result.orientation) {
                DocumentOrientation.UPRIGHT ->
                    "UPRIGHT  (정상)" to Color(0xFF388E3C)
                DocumentOrientation.UPSIDE_DOWN ->
                    "UPSIDE_DOWN  (거꾸로 감지됨 / 180°)" to Color(0xFFD32F2F)
                DocumentOrientation.SIDEWAYS ->
                    "SIDEWAYS  (옆으로 회전됨)" to Color(0xFFF57C00)
                DocumentOrientation.UNKNOWN ->
                    "UNKNOWN  (판별 불가)" to Color.Gray
            }

            Text(
                text = orientationLabel,
                color = orientationColor,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            if (result.orientation == DocumentOrientation.UNKNOWN &&
                result.validLineCount < DocumentOrientationDetector.MIN_VALID_LINES
            ) {
                Text(
                    text = "방향 판단에 텍스트가 부족합니다. (유효 라인 ${result.validLineCount}개)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ── Angle 디버그 정보 ────────────────────────
            SectionTitle("Angle Debug Information")

            if (result.wasRotated) {
                Text(
                    text = "※ 아래 각도는 원본(1차 OCR) 기준",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.lineResults.isEmpty()) {
                Text("라인 정보 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                result.lineResults.forEachIndexed { i, line ->
                    val tag = when {
                        kotlin.math.abs(line.angle) <= DocumentOrientationDetector.UPRIGHT_THRESHOLD ->
                            "↑UP"
                        kotlin.math.abs(line.angle) >= DocumentOrientationDetector.UPSIDE_DOWN_THRESHOLD ->
                            "↓DN"
                        else -> "→SW"
                    }
                    Text(
                        text = "Line ${i + 1}: ${"%.1f".format(line.angle)}° [$tag]  \"${line.text.take(20)}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider()

            // ── 통계 ─────────────────────────────────────
            SectionTitle("통계")

            Text("Valid Lines    : ${result.validLineCount}")
            Text("Upright (↑)   : ${result.uprightCount}")
            Text("Upside Down (↓): ${result.upsideDownCount}")
            Text("Sideways (→)  : ${result.sidewaysCount}")

            if (result.validLineCount > 0) {
                Text("Upright Ratio   : ${"%.1f".format(result.uprightRatio * 100)}%")
                Text("Upside Down Ratio: ${"%.1f".format(result.upsideDownRatio * 100)}%")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun createCameraImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera_photos").also { it.mkdirs() }
    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
