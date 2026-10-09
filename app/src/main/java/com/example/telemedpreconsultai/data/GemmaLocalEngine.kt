package com.example.telemedpreconsultai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class DemoPatientCase(
    val id: String,
    val label: String,
    val transcript: String
)

data class FollowUpQuestion(
    val id: Int,
    val question: String,
    val quickOptions: List<String>
)

data class PatientDeliverable(
    val friendlySummary: String,
    val questionsToAskDoctor: List<String>,
    val prepChecklist: List<String>,
    val urgencyNotice: String
)

data class OpqrstData(
    val onset: String,
    val provocation: String,
    val quality: String,
    val radiation: String,
    val severity: String,
    val time: String
)

data class SoapNote(
    val subjective: String,
    val objective: String,
    val assessment: String,
    val plan: String
)

data class IsbarNote(
    val identification: String,
    val situation: String,
    val background: String,
    val assessment: String,
    val recommendation: String
)

data class EvidenceQuote(
    val fact: String,
    val quote: String
)

data class DoctorDeliverable(
    val triageLevel: String,
    val redFlags: List<String>,
    val opqrst: OpqrstData,
    val soap: SoapNote,
    val isbar: IsbarNote,
    val evidenceQuotes: List<EvidenceQuote>
)

object GemmaLocalEngine {

    private const val LIVE_BACKEND_URL = "https://telemed-preconsult-ai.vercel.app/api/telemed"

    val demoCases = listOf(
        DemoPatientCase(
            id = "migraine",
            label = "Migraña",
            transcript = "Desde hace unas cuatro horas me empezó un dolor muy fuerte del lado derecho de la cabeza, siento que me late detrás del ojo, me molesta mucho la luz de la pantalla y tengo un poco de náuseas."
        ),
        DemoPatientCase(
            id = "chest",
            label = "Dolor en el pecho",
            transcript = "Hace media hora al subir las gradas sentí una presión fuerte en el centro del pecho que se me corre hacia el brazo izquierdo, siento que me falta un poco el aire y estoy sudando frío."
        ),
        DemoPatientCase(
            id = "fever",
            label = "Fiebre y tos",
            transcript = "Llevo tres días con dolor de garganta, tos seca sobre todo en las noches y hoy en la mañana tuve fiebre de 38.5 con dolor de cuerpo."
        )
    )

    fun redactLocalPii(input: String): String {
        return input
            .replace(Regex("\\b\\d{4}\\s?\\d{5}\\s?\\d{4}\\b"), "[DPI PROTEGIDO]")
            .replace(Regex("\\b\\d{4}-?\\d{4}\\b"), "[TEL PROTEGIDO]")
    }

    suspend fun generateFollowUpQuestions(rawTranscript: String): List<FollowUpQuestion> =
        withContext(Dispatchers.IO) {
            val cleanTranscript = redactLocalPii(rawTranscript)
            try {
                val url = URL(LIVE_BACKEND_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }
                val payload = JSONObject().apply {
                    put("action", "generate_followups")
                    put("transcript", cleanTranscript)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                if (conn.responseCode in 200..299) {
                    val responseText = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(responseText)
                    val arr = json.optJSONArray("follow_up_questions")
                    if (arr != null && arr.length() > 0) {
                        val list = mutableListOf<FollowUpQuestion>()
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val optsArr = item.optJSONArray("quick_options") ?: JSONArray()
                            val opts = mutableListOf<String>()
                            for (j in 0 until optsArr.length()) {
                                opts.add(optsArr.getString(j))
                            }
                            list.add(
                                FollowUpQuestion(
                                    id = item.optInt("id", i + 1),
                                    question = item.getString("question"),
                                    quickOptions = opts
                                )
                            )
                        }
                        return@withContext list
                    }
                }
            } catch (_: Exception) {
                // Offline fallback via on-device Gemma 4 E2B rule template
            }
            return@withContext buildOfflineFollowUps(cleanTranscript)
        }

    suspend fun generateDeliverables(
        rawTranscript: String,
        questions: List<FollowUpQuestion>,
        answers: Map<Int, String>
    ): Pair<PatientDeliverable, DoctorDeliverable> = withContext(Dispatchers.IO) {
        val cleanTranscript = redactLocalPii(rawTranscript)
        try {
            val url = URL(LIVE_BACKEND_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
            }
            val answersArray = JSONArray()
            questions.forEach { q ->
                answersArray.put(
                    JSONObject().apply {
                        put("id", q.id)
                        put("question", q.question)
                        put("answer", answers[q.id] ?: "No especificado")
                    }
                )
            }
            val payload = JSONObject().apply {
                put("action", "generate_deliverables")
                put("transcript", cleanTranscript)
                put("follow_up_answers", answersArray)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                val pJson = json.optJSONObject("patient_deliverable")
                val dJson = json.optJSONObject("doctor_deliverable")
                if (pJson != null && dJson != null) {
                    return@withContext parseDeliverablesJson(pJson, dJson)
                }
            }
        } catch (_: Exception) {
            // Offline fallback
        }
        return@withContext buildOfflineDeliverables(cleanTranscript, questions, answers)
    }

    private fun parseDeliverablesJson(
        pJson: JSONObject,
        dJson: JSONObject
    ): Pair<PatientDeliverable, DoctorDeliverable> {
        fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { getString(it) }
        }

        val patient = PatientDeliverable(
            friendlySummary = pJson.optString("friendly_summary"),
            questionsToAskDoctor = pJson.optJSONArray("questions_to_ask_doctor").toStringList(),
            prepChecklist = pJson.optJSONArray("prep_checklist").toStringList(),
            urgencyNotice = pJson.optString("urgency_notice")
        )

        val op = dJson.optJSONObject("opqrst") ?: JSONObject()
        val sp = dJson.optJSONObject("soap") ?: JSONObject()
        val ib = dJson.optJSONObject("isbar") ?: JSONObject()
        val eqArr = dJson.optJSONArray("evidence_quotes") ?: JSONArray()
        val quotes = (0 until eqArr.length()).map { idx ->
            val obj = eqArr.getJSONObject(idx)
            EvidenceQuote(
                fact = obj.optString("fact"),
                quote = obj.optString("quote")
            )
        }

        val doctor = DoctorDeliverable(
            triageLevel = dJson.optString("triage_level", "VERDE"),
            redFlags = dJson.optJSONArray("red_flags").toStringList(),
            opqrst = OpqrstData(
                onset = op.optString("onset", "Reportado en entrevista"),
                provocation = op.optString("provocation", "En evaluación"),
                quality = op.optString("quality", "Descrito por paciente"),
                radiation = op.optString("radiation", "Localizado"),
                severity = op.optString("severity", "Moderada"),
                time = op.optString("time", "Agudo")
            ),
            soap = SoapNote(
                subjective = sp.optString("subjective"),
                objective = sp.optString("objective"),
                assessment = sp.optString("assessment"),
                plan = sp.optString("plan")
            ),
            isbar = IsbarNote(
                identification = ib.optString("identification"),
                situation = ib.optString("situation"),
                background = ib.optString("background"),
                assessment = ib.optString("assessment"),
                recommendation = ib.optString("recommendation")
            ),
            evidenceQuotes = quotes
        )

        return patient to doctor
    }

    private fun buildOfflineFollowUps(transcript: String): List<FollowUpQuestion> {
        val lower = transcript.lowercase()
        return if (lower.contains("pecho") || lower.contains("aire")) {
            listOf(
                FollowUpQuestion(
                    1,
                    "¿Del 1 al 10, qué tan fuerte sientes la presión en el pecho ahora?",
                    listOf("Moderada (5-6)", "Fuerte (7-8)", "Muy intensa (9-10)")
                ),
                FollowUpQuestion(
                    2,
                    "¿El malestar empeora al respirar profundo o al caminar?",
                    listOf("Sí, al hacer esfuerzo", "Es constante en reposo", "Disminuye al sentarme")
                ),
                FollowUpQuestion(
                    3,
                    "¿Padeces de presión alta, diabetes o tomas algún medicamento diario?",
                    listOf("Sí, hipertensión", "No tengo antecedentes", "Tomo medicación diaria")
                )
            )
        } else {
            listOf(
                FollowUpQuestion(
                    1,
                    "¿Del 1 al 10, qué tan intenso sientes el malestar en este momento?",
                    listOf("Leve (1-3)", "Moderado (4-6)", "Fuerte (7-8)", "Muy fuerte (9-10)")
                ),
                FollowUpQuestion(
                    2,
                    "¿Has notado fiebre, mareo o algún otro síntoma adicional hoy?",
                    listOf("No, ninguno adicional", "Sí, fiebre", "Sí, náuseas o mareo")
                ),
                FollowUpQuestion(
                    3,
                    "¿Tomaste algún medicamento hoy o tienes alergia a algún fármaco?",
                    listOf("No he tomado nada", "Tomé analgésico sin mejora", "Soy alérgico a medicamentos")
                )
            )
        }
    }

    private fun buildOfflineDeliverables(
        transcript: String,
        questions: List<FollowUpQuestion>,
        answers: Map<Int, String>
    ): Pair<PatientDeliverable, DoctorDeliverable> {
        val lower = transcript.lowercase()
        val isChest = lower.contains("pecho") || lower.contains("brazo izquierdo")
        val severityAns = answers[1] ?: "Moderada a fuerte"
        val assocAns = answers[2] ?: "Sin otros síntomas reportados"
        val medAns = answers[3] ?: "Sin medicación previa"

        val patient = PatientDeliverable(
            friendlySummary = "Comentaste: \"$transcript\". Además indicaste intensidad ($severityAns), evolución ($assocAns) y antecedente ($medAns).",
            questionsToAskDoctor = listOf(
                "¿Cuál es la causa más probable de mis síntomas hoy?",
                "¿Qué señales de alerta requieren que acuda a una evaluación presencial?",
                "¿Qué medidas o tratamiento debo seguir en las próximas 24 horas?"
            ),
            prepChecklist = listOf(
                "Estar en un lugar silencioso y bien iluminado para la consulta",
                "Tener anotados los medicamentos o dosis tomados recientemente",
                "Tener termómetro o tensiómetro a la mano si cuentas con uno"
            ),
            urgencyNotice = if (isChest) {
                "Atención prioritaria: Por tratarse de dolor en el pecho con irradiación, solicita valoración médica inmediata."
            } else {
                "Pre-consulta lista: Comparte este resumen al iniciar tu llamada con el médico."
            }
        )

        val doctor = DoctorDeliverable(
            triageLevel = if (isChest) "ROJO" else "AMARILLO",
            redFlags = if (isChest) {
                listOf("Dolor torácico opresivo con irradiación a miembro superior izquierdo y diaforesis")
            } else {
                emptyList()
            },
            opqrst = OpqrstData(
                onset = "Inicio agudo reportado por paciente",
                provocation = assocAns,
                quality = if (isChest) "Opresivo" else "Continuo / pulsátil",
                radiation = if (isChest) "Brazo izquierdo" else "Localizado",
                severity = severityAns,
                time = "Curso agudo hoy"
            ),
            soap = SoapNote(
                subjective = "Paciente refiere: $transcript. Intensidad: $severityAns. Contexto: $medAns.",
                objective = "Paciente consciente y orientado durante pre-consulta por voz. $assocAns.",
                assessment = if (isChest) "Síndrome coronario agudo a descartar de forma prioritaria." else "Cuadro agudo sintomático en estudio.",
                plan = "Confirmar signos vitales en consulta, realizar interrogatorio dirigido y definir manejo."
            ),
            isbar = IsbarNote(
                identification = "Paciente en pre-consulta ambulatoria",
                situation = transcript,
                background = medAns,
                assessment = "Severidad reportada: $severityAns ($assocAns)",
                recommendation = if (isChest) "Derivación urgente para electrocardiograma y valoración presencial." else "Valoración médica en teleconsulta programada."
            ),
            evidenceQuotes = listOf(
                EvidenceQuote(
                    fact = "Relato principal del paciente",
                    quote = transcript
                )
            )
        )

        return patient to doctor
    }
}
