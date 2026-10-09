package com.example.telemedpreconsultai

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF8FAFC)
                ) {
                    PreConsultaScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreConsultaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var transcript by remember { mutableStateOf("") }
    var showVitals by remember { mutableStateOf(false) }
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
    var activeResultTab by remember { mutableStateOf("PATIENT") }
    var clinicalFramework by remember { mutableStateOf("SOAP") }
    val checkedPrep = remember { mutableStateMapOf<Int, Boolean>() }
    var copiedNote by remember { mutableStateOf(false) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "GT")
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
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Cuéntanos cómo te sientes...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            if (transcript.isBlank()) {
                transcript = GemmaLocalEngine.demoCases[0].transcript
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
        else "${transcript.trim()} (Signos vitales medidos en casa: ${vitalsParts.joinToString(", ")})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Editorial Minimalist Light Header
        Surface(
            color = Color.White,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PreConsulta",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Preparación médica asistida",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepIndicatorPill(1, "Síntomas", step == 1, step > 1)
                    StepIndicatorPill(2, "Detalles", step == 2, step > 2)
                    StepIndicatorPill(3, "Resumen", step == 3, false)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STEP 1: SÍNTOMAS Y SIGNOS VITALES OPCIONALES
            if (step == 1) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Cuéntanos cómo te sientes",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Habla con naturalidad o escribe tus síntomas para preparar tu consulta médica.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // Clean Voice Dictation Button
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF0F9FF),
                            border = BorderStroke(1.5.dp, Color(0xFF0284C7)),
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .clickable { startVoiceInput() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7))
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Dictar",
                                        color = Color(0xFF0369A1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Toca para usar el micrófono en español",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        OutlinedTextField(
                            value = transcript,
                            onValueChange = { transcript = it },
                            placeholder = {
                                Text(
                                    "Ej. Desde hace cuatro horas tengo un dolor de cabeza fuerte del lado derecho y me molesta la luz...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Subtle Wrapped Example Pills (FlowRow prevents clipping on mobile)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Casos de ejemplo rápido:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                GemmaLocalEngine.demoCases.forEach { demo ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.clickable {
                                            transcript = demo.transcript
                                            if (demo.id == "fever") {
                                                temperatureC = "38.8"
                                                spo2Percent = "96"
                                                showVitals = true
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = demo.label,
                                            fontSize = 12.sp,
                                            color = Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Optional Home Vitals Accordion
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showVitals = !showVitals },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Signos vitales medidos en casa (opcional)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = if (showVitals) "Ocultar" else "Agregar",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0284C7)
                                    )
                                }

                                if (showVitals) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CompactVitalField(
                                            label = "Temp (°C)",
                                            placeholder = "38.5",
                                            value = temperatureC,
                                            onValueChange = { temperatureC = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactVitalField(
                                            label = "SpO2 (%)",
                                            placeholder = "96",
                                            value = spo2Percent,
                                            onValueChange = { spo2Percent = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CompactVitalField(
                                            label = "Pulso (lpm)",
                                            placeholder = "84",
                                            value = heartRateBpm,
                                            onValueChange = { heartRateBpm = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactVitalField(
                                            label = "Presión",
                                            placeholder = "120/80",
                                            value = bloodPressure,
                                            onValueChange = { bloodPressure = it },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Button(
                            onClick = {
                                if (transcript.isNotBlank()) {
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
                            },
                            enabled = transcript.isNotBlank() && !isLoadingFollowUps,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isLoadingFollowUps) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text("Continuar", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // STEP 2: AYÚDAME CON MÁS INFORMACIÓN (1 A 3 PREGUNTAS DINÁMICAS)
            if (step == 2) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ayúdame con más información",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF0F9FF),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD))
                            ) {
                                Text(
                                    text = "${followUpQuestions.size} ${if (followUpQuestions.size == 1) "pregunta" else "preguntas"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Responde estas breves preguntas para afinar tu resumen antes de enviarlo al médico.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        followUpQuestions.forEachIndexed { idx, q ->
                            val selected = followUpAnswers[q.id] ?: ""
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${q.question}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = {
                                            tts?.speak(
                                                q.question,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "q_${q.id}"
                                            )
                                        }
                                    ) {
                                        Text("Escuchar", fontSize = 11.sp, color = Color(0xFF0284C7))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    q.quickOptions.forEach { opt ->
                                        val isSelected = selected == opt
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0)
                                            ),
                                            modifier = Modifier.clickable {
                                                followUpAnswers[q.id] = opt
                                            }
                                        ) {
                                            Text(
                                                text = opt,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFF0369A1) else Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = selected,
                                    onValueChange = { followUpAnswers[q.id] = it },
                                    placeholder = {
                                        Text(
                                            "O escribe tu respuesta aquí...",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color(0xFFF8FAFC),
                                        focusedBorderColor = Color(0xFF0284C7),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                if (idx < followUpQuestions.size - 1) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider(color = Color(0xFFF1F5F9))
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { step = 1 }) {
                                Text("Volver", color = Color(0xFF64748B), fontSize = 13.sp)
                            }

                            Button(
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
                                        isLoadingSummary = false
                                        step = 3
                                    }
                                },
                                enabled = !isLoadingSummary &&
                                    followUpQuestions.isNotEmpty() &&
                                    followUpQuestions.all { !followUpAnswers[it.id].isNullOrBlank() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0284C7),
                                    contentColor = Color.White
                                )
                            ) {
                                if (isLoadingSummary) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Text("Generar mi resumen", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // STEP 3: RESUMEN Y BANNER DE PRIORIDAD CLÍNICA
            if (step == 3 && patientDeliverable != null && doctorDeliverable != null) {
                val pat = patientDeliverable!!
                val doc = doctorDeliverable!!

                // High-Prominence Hero Priority Clinical Callout Banner (Above Tabs, Visible on Both Views)
                if (pat.urgencyNotice.isNotBlank()) {
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
                        isUrgent -> "NIVEL DE PRIORIDAD CLÍNICA · ATENCIÓN URGENTE INMEDIATA"
                        isRoutine -> "NIVEL DE PRIORIDAD CLÍNICA · CONSULTA RUTINARIA"
                        else -> "NIVEL DE PRIORIDAD CLÍNICA · CONSULTA PRIORITARIA HOY"
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bgColor,
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .drawBehind {
                                drawRect(
                                    color = borderColor,
                                    topLeft = Offset.Zero,
                                    size = Size(width = 6.dp.toPx(), height = size.height)
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 22.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
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
                                fontSize = 17.sp,
                                lineHeight = 23.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                        }
                    }
                }

                // Segmented Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE2E8F0))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TabButton(
                        title = "Para mí (Paciente)",
                        selected = activeResultTab == "PATIENT",
                        onClick = { activeResultTab = "PATIENT" },
                        modifier = Modifier.weight(1f)
                    )
                    TabButton(
                        title = "Ficha Médica (SOAP)",
                        selected = activeResultTab == "DOCTOR",
                        onClick = { activeResultTab = "DOCTOR" },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeResultTab == "PATIENT") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tu pase de pre-consulta",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                TextButton(
                                    onClick = {
                                        tts?.speak(
                                            "${pat.urgencyNotice}. ${pat.friendlySummary}",
                                            TextToSpeech.QUEUE_FLUSH,
                                            null,
                                            "summary_tts"
                                        )
                                    }
                                ) {
                                    Text("Escuchar", fontSize = 12.sp, color = Color(0xFF0284C7))
                                }
                            }

                            SectionBlock(
                                label = "1. LO QUE REPORTASTE",
                                content = pat.friendlySummary
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "2. PREGUNTAS SUGERIDAS PARA TU MÉDICO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                pat.questionsToAskDoctor.forEachIndexed { i, q ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${i + 1}. $q",
                                            fontSize = 13.sp,
                                            color = Color(0xFF334155),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "3. QUÉ TENER A LA MANO (TOCA PARA MARCAR)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                pat.prepChecklist.forEachIndexed { idx, item ->
                                    val isDone = checkedPrep[idx] == true
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isDone) Color(0xFFF0FDF4) else Color(0xFFF8FAFC),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isDone) Color(0xFFBBF7D0) else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { checkedPrep[idx] = !isDone }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isDone) Color(0xFF10B981) else Color.White,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isDone) Color(0xFF10B981) else Color(0xFFCBD5E1)
                                                ),
                                                modifier = Modifier.size(16.dp)
                                            ) {}
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = item,
                                                fontSize = 13.sp,
                                                color = if (isDone) Color(0xFF64748B) else Color(0xFF334155),
                                                textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = { step = 1 }) {
                                    Text("Nueva consulta", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val shareText = buildString {
                                            appendLine("Pase de Pre-Consulta")
                                            appendLine(pat.urgencyNotice)
                                            appendLine()
                                            appendLine(pat.friendlySummary)
                                            appendLine()
                                            appendLine("Preguntas para el médico:")
                                            pat.questionsToAskDoctor.forEachIndexed { idx, s ->
                                                appendLine("${idx + 1}. $s")
                                            }
                                        }
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(sendIntent, "Compartir resumen")
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                                ) {
                                    Text("Compartir resumen", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ficha clínica estructurada",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { clinicalFramework = "SOAP" }) {
                                        Text(
                                            "SOAP",
                                            fontWeight = if (clinicalFramework == "SOAP") FontWeight.Bold else FontWeight.Normal,
                                            color = if (clinicalFramework == "SOAP") Color(0xFF0284C7) else Color(0xFF64748B)
                                        )
                                    }
                                    TextButton(onClick = { clinicalFramework = "ISBAR" }) {
                                        Text(
                                            "ISBAR",
                                            fontWeight = if (clinicalFramework == "ISBAR") FontWeight.Bold else FontWeight.Normal,
                                            color = if (clinicalFramework == "ISBAR") Color(0xFF0284C7) else Color(0xFF64748B)
                                        )
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
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF9F1239)
                                        )
                                        doc.redFlags.forEach { rf ->
                                            Text(
                                                text = "- $rf",
                                                fontSize = 12.sp,
                                                color = Color(0xFF881337)
                                            )
                                        }
                                    }
                                }
                            }

                            if (clinicalFramework == "SOAP") {
                                SectionBlock("S · SUBJETIVO", doc.soap.subjective)
                                SectionBlock("O · OBJETIVO", doc.soap.objective)
                                SectionBlock("A · EVALUACIÓN", doc.soap.assessment)
                                SectionBlock("P · PLAN", doc.soap.plan)
                            } else {
                                SectionBlock("I · IDENTIFICACIÓN", doc.isbar.identification)
                                SectionBlock("S · SITUACIÓN", doc.isbar.situation)
                                SectionBlock("B · ANTECEDENTES", doc.isbar.background)
                                SectionBlock("A · EVALUACIÓN", doc.isbar.assessment)
                                SectionBlock("R · RECOMENDACIÓN", doc.isbar.recommendation)
                            }

                            if (doc.evidenceQuotes.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Text(
                                    text = "CITAS TEXTUALES DEL PACIENTE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                                doc.evidenceQuotes.forEach { eq ->
                                    Text(
                                        text = "\"${eq.quote}\"",
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(onClick = { step = 1 }) {
                                    Text("Nueva consulta", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val noteText = if (clinicalFramework == "SOAP") {
                                            "SOAP\nS: ${doc.soap.subjective}\nO: ${doc.soap.objective}\nA: ${doc.soap.assessment}\nP: ${doc.soap.plan}"
                                        } else {
                                            "ISBAR\nI: ${doc.isbar.identification}\nS: ${doc.isbar.situation}\nB: ${doc.isbar.background}\nA: ${doc.isbar.assessment}\nR: ${doc.isbar.recommendation}"
                                        }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Nota Clínica", noteText))
                                        copiedNote = true
                                        scope.launch {
                                            delay(2000)
                                            copiedNote = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Text(
                                        text = if (copiedNote) "Nota copiada" else "Copiar nota $clinicalFramework",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactVitalField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF64748B)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, fontSize = 12.sp, color = Color(0xFF94A3B8))
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xFF0284C7),
                unfocusedBorderColor = Color(0xFFE2E8F0)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepIndicatorPill(number: Int, label: String, active: Boolean, completed: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = when {
            active -> Color(0xFFE0F2FE)
            completed -> Color(0xFFF1F5F9)
            else -> Color.Transparent
        }
    ) {
        Text(
            text = "$number. $label",
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                active -> Color(0xFF0369A1)
                completed -> Color(0xFF475569)
                else -> Color(0xFF94A3B8)
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color.White else Color.Transparent,
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF0F172A) else Color(0xFF64748B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionBlock(label: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
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
                color = Color(0xFF334155),
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
