# Telemed Pre-Consult AI — Cliente Android Nativo (`telemed-preconsult-android`)

Cliente móvil nativo en **Kotlin + Jetpack Compose (100% Light Mode)** para captura rápida de síntomas por voz, 3 preguntas dinámicas de seguimiento y generación del **Pase de Pre-Consulta del Paciente** y la **Ficha Clínica (SOAP / ISBAR)**, diseñado para operar tanto conectado como en modo offline con **Gemma 4 E2B**.

- **Aplicación Web Compañera (Deploy en Producción):** [https://telemed-preconsult-ai.vercel.app](https://telemed-preconsult-ai.vercel.app)
- **Repositorio Web Compañero:** [`ykro/telemed-preconsult-ai`](https://github.com/ykro/telemed-preconsult-ai)
- **Especificación Técnica:** [Ver SPEC.md](./SPEC.md)
- **Guía de Onboarding e Instalación Android:** [Ver docs/ONBOARDING.md](./docs/ONBOARDING.md)
- **Índice General de Proyectos:** [`ykro/hackathon-prototipos-index`](https://github.com/ykro/hackathon-prototipos-index)

---

## Arquitectura del Cliente Android (3 Diagramas)

### 1. Arquitectura Híbrida Offline / Cloud (Gemma 4 E2B + Gemini 3.8 Flash)
```mermaid
flowchart LR
    A["Paciente en App Android"] --> B["SpeechRecognizer Nativo (es-GT)"]
    B --> C["Redacción PII Local (DPI / Teléfono)"]
    C --> D{"¿Hay conexión?"}
    D -->|Conectado| E["API Cloud (Gemma 4 + Gemini 3.8 Flash)"]
    D -->|Offline / Brigada Rural| F["Motor Local Gemma 4 E2B On-Device"]
    E --> G["3 Preguntas Rápidas + Resumen Paciente & SOAP/ISBAR"]
    F --> G
```

### 2. Flujo de Interacción en 3 Pasos (Minimalista y sin Fricción)
```mermaid
sequenceDiagram
    participant P as Paciente
    participant UI as Jetpack Compose UI (Light Mode)
    participant TTS as Android TextToSpeech
    participant E as GemmaLocalEngine

    P->>UI: 1. Toca para hablar (describe síntomas)
    UI->>E: generateFollowUpQuestions(transcript)
    E-->>UI: Devuelve 3 preguntas puntuales (OPQRST)
    UI->>TTS: Lee pregunta opcionalmente en español
    P->>UI: 2. Responde las 3 preguntas con 1 toque
    UI->>E: generateDeliverables(transcript, answers)
    E-->>UI: 3. Muestra Pase del Paciente y Ficha SOAP/ISBAR
```

### 3. Estructura de Capas del Proyecto Android
```mermaid
flowchart TD
    A["MainActivity.kt / PreConsultaScreen (Compose UI)"] --> B["Estado Reactivo (Step 1 -> Step 2 -> Step 3)"]
    B --> C["GemmaLocalEngine.kt (Repositorio Clínico)"]
    C --> D["Redactor Local de PII"]
    C --> E["Cliente REST hacia telemed-preconsult-ai.vercel.app"]
    C --> F["Fallback Determinista / Gemma 4 E2B Local"]
```

---

## Estructura de Documentación
- **[SPEC.md](./SPEC.md):** Alcance técnico del cliente Android y esquema de datos.
- **[docs/ONBOARDING.md](./docs/ONBOARDING.md):** Instrucciones para abrir en Android Studio, compilar el APK con `./gradlew assembleDebug` y ejecutar en emulador o dispositivo físico.
