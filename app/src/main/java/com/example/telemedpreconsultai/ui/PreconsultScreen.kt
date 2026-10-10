package com.example.telemedpreconsultai.ui

import com.example.telemedpreconsultai.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telemedpreconsultai.data.DoctorDeliverable
import com.example.telemedpreconsultai.data.FollowUpQuestion
import com.example.telemedpreconsultai.data.GemmaLocalEngine
import com.example.telemedpreconsultai.data.PatientDeliverable
import com.example.telemedpreconsultai.theme.TelemedPreconsultAITheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TelemedPreconsultAITheme {
                PreConsultaNativeApp()
            }
        }
    }
}

@Composable
fun PreConsultaNativeApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var selectedCaseId by remember { mutableStateOf<String?>(null) }
    var transcript by remember { mutableStateOf("") }
    var temperatureC by remember { mutableStateOf("") }
    var spo2Percent by remember { mutableStateOf("") }
    var heartRateBpm by remember { mutableStateOf("") }
    var bloodPressure by remember { mutableStateOf("") }

    var isLoadingFollowUps by remember { mutableStateOf(false) }
    var followUpQuestions by remember { mutableStateOf<List<FollowUpQuestion>>(emptyList()) }
    val followUpAnswers = remember { mutableStateMapOf<Int, String>() }

    var isLoadingSummary by remember { mutableStateOf(false) }
    var patientDeliverable by remember { mutableStateOf<PatientDeliverable?>(null) }
    var doctorDeliverable by remember { mutableStateOf<DoctorDeliverable?>(null) }

    // Step 3 Native Bottom Navigation Tabs: 0 = Mi Pase, 1 = Preparación, 2 = Ficha Médica
    var resultBottomTab by remember { mutableStateOf(0) }
    var clinicalFramework by remember { mutableStateOf("SOAP") }
    val checkedPrep = remember { mutableStateMapOf<Int, Boolean>() }
    var copiedNote by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.forLanguageTag("es-GT")
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            val spoken = matches[0]
            transcript = if (transcript.isBlank()) spoken else "$transcript $spoken"
        }
    }

    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-GT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe tus síntomas...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            if (transcript.isBlank()) {
                val demo = GemmaLocalEngine.demoCases[2]
                selectedCaseId = demo.id
                transcript = demo.transcript
                temperatureC = "38.8"
                spo2Percent = "96"
            }
        }
    }

    val buildEnrichedTranscript = {
        val vitalsParts = mutableListOf<String>()
        if (temperatureC.isNotBlank()) vitalsParts.add("Temperatura ${temperatureC}C")
        if (spo2Percent.isNotBlank()) vitalsParts.add("Oxigenación SpO2 ${spo2Percent}%")
        if (heartRateBpm.isNotBlank()) vitalsParts.add("Pulso ${heartRateBpm} lpm")
        if (bloodPressure.isNotBlank()) vitalsParts.add("Presión arterial $bloodPressure")
        if (vitalsParts.isEmpty()) transcript.trim()
        else "${transcript.trim()} (Signos vitales en casa: ${vitalsParts.joinToString(", ")})"
    }

    Scaffold(
        containerColor = Color(0xFFF1F5F9),
        topBar = {
            NativeTopAppBar(
                step = step,
                followUpCount = followUpQuestions.size,
                onBack = {
                    if (step > 1) step -= 1
                },
                onReset = {
                    step = 1
                    resultBottomTab = 0
                }
            )
        },
        bottomBar = {
            if (step == 1) {
                NativeBottomActionSheet(
                    primaryLabel = if (isLoadingFollowUps) "Analizando síntomas..." else "Continuar",
                    enabled = transcript.isNotBlank(),
                    isLoading = isLoadingFollowUps,
                    helperText = null,
                    onClick = {
                        isLoadingFollowUps = true
                        scope.launch {
                            val fullText = buildEnrichedTranscript()
                            val qs = GemmaLocalEngine.generateFollowUpQuestions(fullText)
                            followUpQuestions = qs
                            followUpAnswers.clear()
                            qs.forEach { q ->
                                q.quickOptions.firstOrNull()?.let { firstOpt ->
                                    followUpAnswers[q.id] = firstOpt
                                }
                            }
                            isLoadingFollowUps = false
                            step = 2
                        }
                    }
                )
            } else if (step == 2) {
                val answeredCount = followUpQuestions.count { !followUpAnswers[it.id].isNullOrBlank() }
                NativeBottomActionSheet(
                    primaryLabel = if (isLoadingSummary) "Generando pase y ficha médica..." else "Ver mi resumen clínico",
                    enabled = followUpQuestions.isNotEmpty() && answeredCount == followUpQuestions.size,
                    isLoading = isLoadingSummary,
                    helperText = null,
                    onClick = {
                        isLoadingSummary = true
                        scope.launch {
                            val fullText = buildEnrichedTranscript()
                            val (pat, doc) = GemmaLocalEngine.generateDeliverables(
                                fullText,
                                followUpQuestions,
                                followUpAnswers.toMap()
                            )
                            patientDeliverable = pat
                            doctorDeliverable = doc
                            checkedPrep.clear()
                            resultBottomTab = 0
                            isLoadingSummary = false
                            step = 3
                        }
                    }
                )
            } else if (step == 3) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    NavigationBarItem(
                        selected = resultBottomTab == 0,
                        onClick = { resultBottomTab = 0 },
                        icon = { VectorClipboardIcon(color = if (resultBottomTab == 0) Color(0xFF0284C7) else Color(0xFF64748B)) },
                        label = {
                            Text(
                                "Mi Pase",
                                fontSize = 11.sp,
                                fontWeight = if (resultBottomTab == 0) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0284C7),
                            selectedTextColor = Color(0xFF0284C7),
                            indicatorColor = Color(0xFFE0F2FE),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                    NavigationBarItem(
                        selected = resultBottomTab == 1,
                        onClick = { resultBottomTab = 1 },
                        icon = { VectorCheckCircleIcon(color = if (resultBottomTab == 1) Color(0xFF0284C7) else Color(0xFF64748B)) },
                        label = {
                            Text(
                                "Preparación",
                                fontSize = 11.sp,
                                fontWeight = if (resultBottomTab == 1) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0284C7),
                            selectedTextColor = Color(0xFF0284C7),
                            indicatorColor = Color(0xFFE0F2FE),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                    NavigationBarItem(
                        selected = resultBottomTab == 2,
                        onClick = { resultBottomTab = 2 },
                        icon = { VectorMedicalCrossIcon(color = if (resultBottomTab == 2) Color(0xFF0284C7) else Color(0xFF64748B)) },
                        label = {
                            Text(
                                "Ficha Médica",
                                fontSize = 11.sp,
                                fontWeight = if (resultBottomTab == 2) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0284C7),
                            selectedTextColor = Color(0xFF0284C7),
                            indicatorColor = Color(0xFFE0F2FE),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { currentStep ->
            when (currentStep) {
                1 -> Step1NativeSymptomScreen(
                    transcript = transcript,
                    onTranscriptChange = { transcript = it },
                    selectedCaseId = selectedCaseId,
                    onSelectCase = { demo ->
                        selectedCaseId = demo.id
                        transcript = demo.transcript
                        if (demo.id == "fever") {
                            temperatureC = "38.8"
                            spo2Percent = "96"
                            heartRateBpm = "92"
                            bloodPressure = "118/76"
                        } else if (demo.id == "chest") {
                            temperatureC = "36.6"
                            spo2Percent = "93"
                            heartRateBpm = "108"
                            bloodPressure = "148/94"
                        } else {
                            temperatureC = "36.8"
                            spo2Percent = "98"
                            heartRateBpm = "76"
                            bloodPressure = "120/78"
                        }
                    },
                    onStartVoice = startVoiceInput,
                    temperatureC = temperatureC,
                    onTempChange = { temperatureC = it },
                    spo2Percent = spo2Percent,
                    onSpo2Change = { spo2Percent = it },
                    heartRateBpm = heartRateBpm,
                    onHrChange = { heartRateBpm = it },
                    bloodPressure = bloodPressure,
                    onBpChange = { bloodPressure = it }
                )

                2 -> Step2NativeQuestionsScreen(
                    questions = followUpQuestions,
                    answers = followUpAnswers,
                    onSelectAnswer = { id, value -> followUpAnswers[id] = value },
                    onSpeakQuestion = { qText ->
                        tts?.speak(qText, TextToSpeech.QUEUE_FLUSH, null, "q_tts")
                    }
                )

                3 -> {
                    val pat = patientDeliverable
                    val doc = doctorDeliverable
                    if (pat != null && doc != null) {
                        Step3NativeResultsScreen(
                            pat = pat,
                            doc = doc,
                            rawSymptomTranscript = transcript,
                            temperatureC = temperatureC,
                            spo2Percent = spo2Percent,
                            heartRateBpm = heartRateBpm,
                            bloodPressure = bloodPressure,
                            followUpQuestions = followUpQuestions,
                            followUpAnswers = followUpAnswers,
                            activeBottomTab = resultBottomTab,
                            clinicalFramework = clinicalFramework,
                            onChangeFramework = { clinicalFramework = it },
                            checkedPrep = checkedPrep,
                            onTogglePrep = { idx -> checkedPrep[idx] = !(checkedPrep[idx] ?: false) },
                            copiedNote = copiedNote,
                            onCopyNote = {
                                val noteText = when (clinicalFramework) {
                                    "SOAP" -> "SOAP\nS: ${doc.soap.subjective}\nO: ${doc.soap.objective}\nA: ${doc.soap.assessment}\nP: ${doc.soap.plan}"
                                    "OPQRST" -> "OPQRST\nAparición: ${doc.opqrst.onset}\nProvocación: ${doc.opqrst.provocation}\nCalidad: ${doc.opqrst.quality}\nIrradiación: ${doc.opqrst.radiation}\nSeveridad: ${doc.opqrst.severity}\nTiempo: ${doc.opqrst.time}"
                                    else -> "ISBAR\nI: ${doc.isbar.identification}\nS: ${doc.isbar.situation}\nB: ${doc.isbar.background}\nA: ${doc.isbar.assessment}\nR: ${doc.isbar.recommendation}"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ficha Médica", noteText))
                                copiedNote = true
                                scope.launch {
                                    delay(2000)
                                    copiedNote = false
                                }
                            },
                            onSharePass = {
                                val shareText = buildString {
                                    appendLine("Pase de Pre-Consulta")
                                    appendLine(pat.urgencyNotice)
                                    appendLine()
                                    appendLine(pat.friendlySummary)
                                }
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir pase"))
                            },
                            onSpeakSummary = {
                                tts?.speak(
                                    "${pat.urgencyNotice}. ${pat.friendlySummary}",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "summary_tts"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeTopAppBar(
    step: Int,
    followUpCount: Int,
    onBack: () -> Unit,
    onReset: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (step > 1) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onBack() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                VectorBackArrowIcon(color = Color(0xFF0F172A))
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                VectorMedicalCrossIcon(color = Color(0xFF0284C7))
                            }
                        }
                    }

                    Column {
                        Text(
                            text = when (step) {
                                1 -> "PreConsulta"
                                2 -> "Ayúdame con más información"
                                else -> "Resumen de Pre-Consulta"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = when (step) {
                                1 -> "Triaje y preparación médica"
                                2 -> "$followUpCount ${if (followUpCount == 1) "pregunta breve" else "preguntas breves"}"
                                else -> "Pase del paciente y ficha clínica"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (step == 3) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onReset() }
                    ) {
                        Text(
                            text = "Nueva",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF0F9FF)
                    ) {
                        Text(
                            text = "$step / 3",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { step / 3f },
                color = Color(0xFF0284C7),
                trackColor = Color(0xFFE2E8F0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

@Composable
private fun NativeBottomActionSheet(
    primaryLabel: String,
    enabled: Boolean,
    isLoading: Boolean,
    helperText: String?,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!helperText.isNullOrBlank()) {
                Text(
                    text = helperText,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Button(
                onClick = { if (!isLoading) onClick() },
                enabled = enabled || isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoading) Color(0xFF0369A1) else Color(0xFF0284C7),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE2E8F0),
                    disabledContentColor = Color(0xFF94A3B8)
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(primaryLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                } else {
                    Text(primaryLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Step1NativeSymptomScreen(
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    selectedCaseId: String?,
    onSelectCase: (com.example.telemedpreconsultai.data.DemoPatientCase) -> Unit,
    onStartVoice: () -> Unit,
    temperatureC: String,
    onTempChange: (String) -> Unit,
    spo2Percent: String,
    onSpo2Change: (String) -> Unit,
    heartRateBpm: String,
    onHrChange: (String) -> Unit,
    bloodPressure: String,
    onBpChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Native Mobile Section Greeting
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "¿Cómo te sientes hoy?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Dicta por voz o selecciona un ejemplo clínico rápido.",
                fontSize = 13.sp,
                color = Color(0xFF475569)
            )
        }

        // Native Horizontal Scrollable Pill Chips for Clinical Examples
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "EJEMPLOS CLÍNICOS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GemmaLocalEngine.demoCases.forEach { demo ->
                    val isSelected = selectedCaseId == demo.id
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) Color(0xFF0284C7) else Color.White,
                        shadowElevation = if (isSelected) 2.dp else 0.dp,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF0284C7) else Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectCase(demo) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color.White
                                        else when (demo.id) {
                                            "chest" -> Color(0xFFEF4444)
                                            "fever" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF0284C7)
                                        }
                                    )
                            )
                            Text(
                                text = demo.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        // Native Symptom Input Card with High-Contrast Transcript Box & Tactile Voice Pill
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "RELATO DE SÍNTOMAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "Gemma 4 Local",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (transcript.isNotBlank()) {
                        Text(
                            text = "Limpiar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.clickable { onTranscriptChange("") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(102.dp)
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        if (transcript.isEmpty()) {
                            Text(
                                text = "Toca \"Hablar\" o escribe desde cuándo iniciaron tus síntomas, qué tan intensos son y si has tomado algún medicamento...",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        BasicTextField(
                            value = transcript,
                            onValueChange = onTranscriptChange,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tactile Voice Action Bar with Pill CTA
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onStartVoice() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    VectorMicIcon(color = Color.White)
                                }
                            }
                            Column {
                                Text(
                                    text = "Dictar síntomas por voz",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Transcripción clínica en español",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0369A1)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFF0284C7)
                        ) {
                            Text(
                                text = "Hablar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Clean 2x2 Grid of Vitals Cards (No Nested Box-in-Box, Baseline-Aligned Units + Clinical Status)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "SIGNOS VITALES EN CASA (OPCIONAL)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B)
            )
            val tempAlert = temperatureC.toDoubleOrNull()?.let { it >= 38.0 } == true
            val spo2Alert = spo2Percent.toIntOrNull()?.let { it in 1..94 } == true
            val hrAlert = heartRateBpm.toIntOrNull()?.let { it >= 100 } == true
            val bpAlert = bloodPressure.startsWith("14") || bloodPressure.startsWith("15") || bloodPressure.startsWith("16")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NativeVitalMetricCard(
                    title = "Temperatura",
                    unit = "°C",
                    placeholder = "36.5",
                    value = temperatureC,
                    onValueChange = onTempChange,
                    accentColor = Color(0xFFF59E0B),
                    statusBadge = if (tempAlert) "Fiebre" else null,
                    isAlert = tempAlert,
                    modifier = Modifier.weight(1f)
                )
                NativeVitalMetricCard(
                    title = "Oxigenación",
                    unit = "% SpO2",
                    placeholder = "96",
                    value = spo2Percent,
                    onValueChange = onSpo2Change,
                    accentColor = Color(0xFF0284C7),
                    statusBadge = if (spo2Alert) "Vigilar" else null,
                    isAlert = spo2Alert,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NativeVitalMetricCard(
                    title = "Pulso",
                    unit = "lpm",
                    placeholder = "80",
                    value = heartRateBpm,
                    onValueChange = onHrChange,
                    accentColor = Color(0xFF10B981),
                    statusBadge = if (hrAlert) "Elevado" else null,
                    isAlert = hrAlert,
                    modifier = Modifier.weight(1f)
                )
                NativeVitalMetricCard(
                    title = "Presión",
                    unit = "mmHg",
                    placeholder = "120/80",
                    value = bloodPressure,
                    onValueChange = onBpChange,
                    accentColor = Color(0xFF6366F1),
                    statusBadge = if (bpAlert) "Elevada" else null,
                    isAlert = bpAlert,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NativeVitalMetricCard(
    title: String,
    unit: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color,
    statusBadge: String?,
    isAlert: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isAlert) Color(0xFFFFFBEB) else Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (isAlert) Color(0xFFFCD34D) else Color(0xFFE2E8F0)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isAlert) Color(0xFFD97706) else accentColor)
                    )
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                }
                if (statusBadge != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = statusBadge,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun Step2NativeQuestionsScreen(
    questions: List<FollowUpQuestion>,
    answers: Map<Int, String>,
    onSelectAnswer: (Int, String) -> Unit,
    onSpeakQuestion: (String) -> Unit
) {
    // Track which question IDs have their custom free-text input expanded
    val expandedCustomIds = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        questions.forEachIndexed { idx, q ->
            val selected = answers[q.id] ?: ""
            val isCustomValue = selected.isNotBlank() && !q.quickOptions.contains(selected)
            val showCustomInput = expandedCustomIds[q.id] == true || isCustomValue

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF0F9FF)
                        ) {
                            Text(
                                text = "PREGUNTA ${idx + 1} DE ${questions.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onSpeakQuestion(q.question) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                VectorSpeakerIcon(color = Color(0xFF0284C7))
                            }
                        }
                    }

                    Text(
                        text = q.question,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    // Native Full-Width Radio Option Cards (Clean, No Duplicate TextField!)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        q.quickOptions.forEach { opt ->
                            val isSelected = selected == opt && !showCustomInput
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFF0F9FF) else Color(0xFFF8FAFC),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedCustomIds[q.id] = false
                                        onSelectAnswer(q.id, opt)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    NativeRadioCircle(selected = isSelected)
                                    Text(
                                        text = opt,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF334155),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Compact Toggle Option for Custom Free-Text Response
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (showCustomInput) Color(0xFFF0F9FF) else Color.White,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (showCustomInput) Color(0xFF0284C7) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val nextState = !showCustomInput
                                    expandedCustomIds[q.id] = nextState
                                    if (nextState && q.quickOptions.contains(selected)) {
                                        onSelectAnswer(q.id, "")
                                    } else if (!nextState && selected.isBlank()) {
                                        q.quickOptions.firstOrNull()?.let { onSelectAnswer(q.id, it) }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NativeRadioCircle(selected = showCustomInput)
                                Text(
                                    text = "Escribir otra respuesta...",
                                    fontSize = 13.sp,
                                    fontWeight = if (showCustomInput) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (showCustomInput) Color(0xFF0284C7) else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    if (showCustomInput) {
                        OutlinedTextField(
                            value = if (q.quickOptions.contains(selected)) "" else selected,
                            onValueChange = { onSelectAnswer(q.id, it) },
                            placeholder = {
                                Text("Describe tu respuesta con tus palabras...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeRadioCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFF0284C7) else Color.White)
            .drawBehind {
                if (!selected) {
                    drawCircle(
                        color = Color(0xFFCBD5E1),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun NativeCheckboxSquare(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Color(0xFF10B981) else Color.White)
            .drawBehind {
                if (!checked) {
                    drawRoundRect(
                        color = Color(0xFFCBD5E1),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.76f)
                    lineTo(size.width * 0.84f, size.height * 0.24f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
private fun Step3NativeResultsScreen(
    pat: PatientDeliverable,
    doc: DoctorDeliverable,
    rawSymptomTranscript: String,
    temperatureC: String,
    spo2Percent: String,
    heartRateBpm: String,
    bloodPressure: String,
    followUpQuestions: List<FollowUpQuestion>,
    followUpAnswers: Map<Int, String>,
    activeBottomTab: Int,
    clinicalFramework: String,
    onChangeFramework: (String) -> Unit,
    checkedPrep: Map<Int, Boolean>,
    onTogglePrep: (Int) -> Unit,
    copiedNote: Boolean,
    onCopyNote: () -> Unit,
    onSharePass: () -> Unit,
    onSpeakSummary: () -> Unit
) {
    val isUrgent = doc.triageLevel == "ROJO"
    val isRoutine = doc.triageLevel == "VERDE"
    val bgColor = when {
        isUrgent -> Color(0xFFFEF2F2)
        isRoutine -> Color(0xFFECFDF5)
        else -> Color(0xFFFFFBEB)
    }
    val borderColor = when {
        isUrgent -> Color(0xFFEF4444)
        isRoutine -> Color(0xFF10B981)
        else -> Color(0xFFF59E0B)
    }
    val textColor = when {
        isUrgent -> Color(0xFF991B1B)
        isRoutine -> Color(0xFF065F46)
        else -> Color(0xFF92400E)
    }
    val badgeText = when {
        isUrgent -> "PRIORIDAD CLÍNICA · ATENCIÓN URGENTE"
        isRoutine -> "PRIORIDAD CLÍNICA · CONSULTA RUTINARIA"
        else -> "PRIORIDAD CLÍNICA · CONSULTA PRIORITARIA HOY"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // On Tab 0 ("Mi Pase"): Full Hero Clinical Callout Banner
        // On Tabs 1 & 2 ("Preparación", "Ficha Médica"): Compact 1-Line Priority Strip so content takes center stage
        if (pat.urgencyNotice.isNotBlank()) {
            if (activeBottomTab == 0) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = bgColor,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .drawBehind {
                            drawRect(
                                color = borderColor,
                                topLeft = Offset.Zero,
                                size = Size(width = 7.dp.toPx(), height = size.height)
                            )
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = borderColor
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = pat.urgencyNotice,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = borderColor
                        ) {
                            Text(
                                text = "TRIAJE ${doc.triageLevel}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = pat.urgencyNotice,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // TAB 0: MI PASE (STRUCTURED CLINICAL PASS FOR THE PATIENT)
        if (activeBottomTab == 0) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tu pase de pre-consulta",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Resumen estructurado para mostrar a tu médico",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0F9FF),
                            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onSpeakSummary() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                VectorSpeakerIcon(color = Color(0xFF0284C7))
                            }
                        }
                    }

                    // 1. Vitals Strip (if recorded)
                    if (temperatureC.isNotBlank() || spo2Percent.isNotBlank() || heartRateBpm.isNotBlank() || bloodPressure.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (temperatureC.isNotBlank()) {
                                PassVitalChip("Temp", "$temperatureC °C", Modifier.weight(1f))
                            }
                            if (spo2Percent.isNotBlank()) {
                                PassVitalChip("SpO2", "$spo2Percent%", Modifier.weight(1f))
                            }
                            if (heartRateBpm.isNotBlank()) {
                                PassVitalChip("Pulso", "$heartRateBpm lpm", Modifier.weight(1f))
                            }
                            if (bloodPressure.isNotBlank()) {
                                PassVitalChip("Presión", bloodPressure, Modifier.weight(1f))
                            }
                        }
                    }

                    // 2. Main Symptom Narrative
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MOTIVO PRINCIPAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rawSymptomTranscript.ifBlank { pat.friendlySummary },
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 3. Structured Follow-Up Answers
                    if (followUpQuestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "RESPUESTAS DE SEGUIMIENTO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            followUpQuestions.forEach { q ->
                                val ans = followUpAnswers[q.id]
                                if (!ans.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = q.question,
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                text = ans,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onSharePass,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Compartir mi pase por WhatsApp / Correo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // TAB 1: PREPARACIÓN Y PREGUNTAS PARA EL MÉDICO
        if (activeBottomTab == 1) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lista de preparación antes de entrar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        val doneCount = pat.prepChecklist.indices.count { checkedPrep[it] == true }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFF0FDF4)
                        ) {
                            Text(
                                text = "$doneCount / ${pat.prepChecklist.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    pat.prepChecklist.forEachIndexed { idx, item ->
                        val isDone = checkedPrep[idx] == true
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDone) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isDone) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onTogglePrep(idx) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NativeCheckboxSquare(checked = isDone)
                                Text(
                                    text = item,
                                    fontSize = 13.sp,
                                    color = if (isDone) Color(0xFF64748B) else Color(0xFF1E293B),
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Preguntas sugeridas para tu médico",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    pat.questionsToAskDoctor.forEachIndexed { idx, q ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE0F2FE),
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }
                                Text(
                                    text = q,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // TAB 2: FICHA MÉDICA (SOAP / ISBAR / OPQRST)
        if (activeBottomTab == 2) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ficha Clínica",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF1F5F9))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            listOf("SOAP", "ISBAR", "OPQRST").forEach { fw ->
                                val active = clinicalFramework == fw
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) Color.White else Color.Transparent,
                                    shadowElevation = if (active) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onChangeFramework(fw) }
                                ) {
                                    Text(
                                        text = fw,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color(0xFF0284C7) else Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (doc.redFlags.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFF1F2),
                            border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "SIGNOS DE ALERTA DETECTADOS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9F1239)
                                )
                                doc.redFlags.forEach { rf ->
                                    Text(
                                        text = "• $rf",
                                        fontSize = 12.sp,
                                        color = Color(0xFF881337)
                                    )
                                }
                            }
                        }
                    }

                    when (clinicalFramework) {
                        "SOAP" -> {
                            NativeClinicalBlock("S · SUBJETIVO", doc.soap.subjective)
                            NativeClinicalBlock("O · OBJETIVO", doc.soap.objective)
                            NativeClinicalBlock("A · EVALUACIÓN", doc.soap.assessment)
                            NativeClinicalBlock("P · PLAN", doc.soap.plan)
                        }
                        "OPQRST" -> {
                            NativeClinicalBlock("O · APARICIÓN (ONSET)", doc.opqrst.onset)
                            NativeClinicalBlock("P · PROVOCACIÓN / ALIVIO", doc.opqrst.provocation)
                            NativeClinicalBlock("Q · CALIDAD DEL SÍNTOMA", doc.opqrst.quality)
                            NativeClinicalBlock("R · IRRADIACIÓN / REGIÓN", doc.opqrst.radiation)
                            NativeClinicalBlock("S · SEVERIDAD (EVA)", doc.opqrst.severity)
                            NativeClinicalBlock("T · TIEMPO Y EVOLUCIÓN", doc.opqrst.time)
                        }
                        else -> {
                            NativeClinicalBlock("I · IDENTIFICACIÓN", doc.isbar.identification)
                            NativeClinicalBlock("S · SITUACIÓN", doc.isbar.situation)
                            NativeClinicalBlock("B · ANTECEDENTES", doc.isbar.background)
                            NativeClinicalBlock("A · EVALUACIÓN", doc.isbar.assessment)
                            NativeClinicalBlock("R · RECOMENDACIÓN", doc.isbar.recommendation)
                        }
                    }

                    Button(
                        onClick = onCopyNote,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (copiedNote) "Nota copiada al portapapeles" else "Copiar nota $clinicalFramework",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PassVitalChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF0F9FF),
        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0369A1)
            )
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun NativeClinicalBlock(label: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0284C7)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFF334155),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// Crisp Native Vector Icons drawn with Compose Canvas
@Composable
private fun VectorBackArrowIcon(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.65f, this@Canvas.size.height * 0.2f)
            lineTo(this@Canvas.size.width * 0.25f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.65f, this@Canvas.size.height * 0.8f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun VectorMicIcon(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.34f, h * 0.12f),
            size = Size(w * 0.32f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.28f),
            size = Size(w * 0.56f, h * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun VectorSpeakerIcon(color: Color, size: Dp = 16.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.38f)
            lineTo(w * 0.35f, h * 0.38f)
            lineTo(w * 0.58f, h * 0.18f)
            lineTo(w * 0.58f, h * 0.82f)
            lineTo(w * 0.35f, h * 0.62f)
            lineTo(w * 0.15f, h * 0.62f)
            close()
        }
        drawPath(path, color = color)
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.48f, h * 0.26f),
            size = Size(w * 0.36f, h * 0.48f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun VectorMedicalCrossIcon(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.38f, h * 0.16f),
            size = Size(w * 0.24f, h * 0.68f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.38f),
            size = Size(w * 0.68f, h * 0.24f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

@Composable
private fun VectorClipboardIcon(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.7.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.16f),
            size = Size(w * 0.6f, h * 0.72f),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = stroke)
        )
        drawLine(color, Offset(w * 0.34f, h * 0.42f), Offset(w * 0.66f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.60f), Offset(w * 0.56f, h * 0.60f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun VectorCheckCircleIcon(color: Color, size: Dp = 18.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.7.dp.toPx()
        drawCircle(
            color = color,
            radius = w * 0.38f,
            style = Stroke(width = stroke)
        )
        val path = Path().apply {
            moveTo(w * 0.34f, h * 0.52f)
            lineTo(w * 0.46f, h * 0.64f)
            lineTo(w * 0.68f, h * 0.38f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
