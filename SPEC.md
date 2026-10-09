# Especificación Técnica: Telemed Pre-Consult AI (Android Nativo)

## 1. Objetivo
Proveer una experiencia móvil nativa en Android (Kotlin + Jetpack Compose) minimalista, luminosa (100% Light Mode) y sin distracciones para que un paciente registre sus síntomas por voz en menos de 90 segundos, responda 3 preguntas rápidas de seguimiento y obtenga su **Pase de Pre-Consulta** junto con la **Ficha Clínica (SOAP / ISBAR)** para su médico.

## 2. Principios de Diseño UI/UX
- **Minimalismo Clínico (Light Mode Puro):** Fondo `#F8FAFC`, tarjetas blancas `#FFFFFF`, acento primario `#0284C7`, cero modo oscuro y cero emojis.
- **Sin Ruido Técnico:** La interfaz muestra únicamente los 3 pasos para el usuario (`1. Síntomas`, `2. Preguntas`, `3. Resumen`), ocultando cualquier detalle de infraestructura o latencia.
- **Casos de Ejemplo Discretos:** Tres píldoras pequeñas debajo del cuadro de texto (`Migraña`, `Dolor en el pecho`, `Fiebre y tos`) para pruebas rápidas.

## 3. Componentes Principales
- `MainActivity.kt`: Implementa `PreConsultaScreen` con navegación limpia en 3 pasos, integración con `RecognizerIntent` (voz a texto nativa de Android), `TextToSpeech` (lectura de preguntas) e `Intent.ACTION_SEND` (compartir resumen por WhatsApp).
- `data/GemmaLocalEngine.kt`: Ejecuta la protección local de datos personales (DPI/teléfono), conecta con el backend productivo en `https://telemed-preconsult-ai.vercel.app/api/telemed` y provee inferencia local de respaldo para entornos sin conectividad.
