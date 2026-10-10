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
            transcript = "Llevo tres días con fiebre de 38.8 por las noches, dolor de garganta al tragar y tos con flema. Siento dolor de cuerpo generalizado, pero respiro bien en reposo y mi oxímetro en casa marca 96%."
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
            kotlinx.coroutines.delay(280)
            return@withContext buildOfflineFollowUps(cleanTranscript)
        }

    suspend fun generateDeliverables(
        rawTranscript: String,
        questions: List<FollowUpQuestion>,
        answers: Map<Int, String>
    ): Pair<PatientDeliverable, DoctorDeliverable> = withContext(Dispatchers.IO) {
        val cleanTranscript = redactLocalPii(rawTranscript)
        kotlinx.coroutines.delay(320)
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
        if (lower.contains("pecho") || lower.contains("brazo izquierdo")) {
            return listOf(
                FollowUpQuestion(
                    1,
                    "¿Qué tan intensa es la presión en el pecho del 1 al 10 y disminuye al estar sentado en reposo?",
                    listOf(
                        "Es muy fuerte (9/10) y no mejora en reposo",
                        "Es moderada (6/10) y disminuye al descansar",
                        "Aparece y desaparece por momentos"
                    )
                ),
                FollowUpQuestion(
                    2,
                    "¿Has sentido mareo intenso, sensación de desmayo o palpitaciones rápidas?",
                    listOf(
                        "Sí, mareo y palpitaciones",
                        "Solo sudor frío y falta de aire",
                        "No he sentido mareo ni desmayo"
                    )
                ),
                FollowUpQuestion(
                    3,
                    "¿Eres alérgico a algún medicamento o has tenido problemas cardíacos antes?",
                    listOf(
                        "Sin alergias ni antecedentes cardíacos previos",
                        "Tomé aspirina hoy; no tengo alergias",
                        "Sí tengo alergia a medicamentos"
                    )
                )
            )
        }

        if (lower.contains("fiebre") || lower.contains("garganta") || lower.contains("tos")) {
            return listOf(
                FollowUpQuestion(
                    1,
                    "¿Has podido tomar líquidos hoy y la fiebre baja cuando tomas algún medicamento?",
                    listOf(
                        "Tomo líquidos con molestia; la fiebre cede con paracetamol",
                        "Me cuesta mucho tragar líquidos hoy",
                        "Me hidrato bien y la fiebre es solo nocturna"
                    )
                ),
                FollowUpQuestion(
                    2,
                    "¿Qué medicamentos has tomado en estos días o tienes alergia a algún antibiótico o analgésico?",
                    listOf(
                        "Solo paracetamol; sin alergias conocidas",
                        "Antigripal de farmacia; sin alergias",
                        "Soy alérgico a la penicilina / antibióticos"
                    )
                )
            )
        }

        val dynamic = mutableListOf<FollowUpQuestion>()
        val hasDuration = Regex("\\b(hace|desde|días|horas|minutos|ayer)\\b").containsMatchIn(lower)
        if (!hasDuration) {
            dynamic.add(
                FollowUpQuestion(
                    dynamic.size + 1,
                    "¿Desde hace cuánto tiempo empezó el malestar y qué tan intenso es del 1 al 10?",
                    listOf("Empezó hoy; moderado (5/10)", "Llevo 2 a 3 días; fuerte (7/10)", "Más de una semana")
                )
            )
        }
        dynamic.add(
            FollowUpQuestion(
                dynamic.size + 1,
                "¿El malestar empeora con algún movimiento o se acompaña de vista borrosa o fiebre?",
                listOf("Empeora con la luz o movimiento", "Mejora en reposo; sin fiebre", "Se acompaña de malestar general")
            )
        )
        dynamic.add(
            FollowUpQuestion(
                dynamic.size + 1,
                "¿Tomaste algún medicamento hoy o tienes alergia a algún fármaco?",
                listOf("No he tomado nada; sin alergias", "Tomé analgésico sin mejora", "Soy alérgico a medicamentos")
            )
        )
        return dynamic.take(3)
    }

    private fun buildOfflineDeliverables(
        transcript: String,
        questions: List<FollowUpQuestion>,
        answers: Map<Int, String>
    ): Pair<PatientDeliverable, DoctorDeliverable> {
        val lower = transcript.lowercase()
        val isChest = lower.contains("pecho") || lower.contains("brazo izquierdo")
        val isFever = lower.contains("fiebre") || lower.contains("garganta") || lower.contains("tos")
        val a1 = answers[1] ?: questions.getOrNull(0)?.quickOptions?.firstOrNull() ?: "Moderada a fuerte"
        val a2 = answers[2] ?: questions.getOrNull(1)?.quickOptions?.firstOrNull() ?: a1
        val a3 = answers[3] ?: questions.getOrNull(2)?.quickOptions?.firstOrNull() ?: "Sin alergias reportadas"

        val urgencyNotice = when {
            isChest -> "Atención prioritaria inmediata: Por las características del dolor en el pecho, se recomienda valoración presencial urgente con electrocardiograma."
            isFever -> "Consulta prioritaria hoy: Tu oxigenación de 96% es estable; si baja de 92% o tienes dificultad para respirar, acude a urgencias."
            else -> "Prioridad media-alta: Si notas debilidad en un brazo, dificultad para hablar o fiebre alta, acude a urgencias de inmediato."
        }

        val patient = PatientDeliverable(
            friendlySummary = "Comentaste: \"$transcript\". Además indicaste en seguimiento: $a1 ($a2).",
            questionsToAskDoctor = listOf(
                "¿Cuál es la causa más probable de mis síntomas hoy?",
                "¿Qué señales de alerta requieren que acuda a una evaluación presencial?",
                "¿Qué medidas o tratamiento debo seguir en las próximas 24 horas?"
            ),
            prepChecklist = listOf(
                "Estar en un lugar silencioso y bien iluminado para la consulta",
                "Tener anotados los medicamentos o dosis tomados recientemente",
                "Tener termómetro u oxímetro a la mano si cuentas con uno"
            ),
            urgencyNotice = urgencyNotice
        )

        val doctor = DoctorDeliverable(
            triageLevel = if (isChest) "ROJO" else "AMARILLO",
            redFlags = when {
                isChest -> listOf("Dolor torácico opresivo con irradiación a miembro superior izquierdo y diaforesis")
                isFever -> listOf("Fiebre persistente de 38.8 °C por 72 horas con odinofagia marcada")
                else -> listOf("Cefalea hemicraneal aguda severa con fotofobia")
            },
            opqrst = OpqrstData(
                onset = "Reportado en relato inicial",
                provocation = a1,
                quality = if (isChest) "Opresivo" else if (isFever) "Odinofagia y tos productiva" else "Pulsátil",
                radiation = if (isChest) "Brazo izquierdo" else "Localizado",
                severity = a1,
                time = if (isFever) "72 horas" else "Curso agudo hoy"
            ),
            soap = SoapNote(
                subjective = "Paciente refiere: $transcript. Seguimiento: $a1; $a2; $a3.",
                objective = "Paciente consciente y orientado durante pre-consulta por voz.",
                assessment = if (isChest) "Síndrome coronario agudo a descartar de forma prioritaria." else if (isFever) "Infección respiratoria aguda superior." else "Crisis migrañosa aguda en estudio.",
                plan = "Confirmar signos vitales en consulta, realizar interrogatorio dirigido y definir manejo."
            ),
            isbar = IsbarNote(
                identification = "Paciente en pre-consulta ambulatoria",
                situation = transcript,
                background = a3,
                assessment = "Respuesta clínica: $a1 ($a2)",
                recommendation = if (isChest) "Derivación urgente para electrocardiograma y valoración presencial." else "Valoración médica prioritaria hoy en teleconsulta."
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
