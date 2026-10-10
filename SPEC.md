# Especificación Técnica: PreConsulta (Android Nativo)

## 1. Objetivo
Proveer una experiencia móvil nativa en Android (Kotlin + Jetpack Compose) minimalista, luminosa y sin distracciones para que un paciente registre sus síntomas por voz en menos de 90 segundos, responda de **1 a 3 preguntas breves de seguimiento ("Ayúdame con más información")** según qué datos falten en su relato y obtenga su **Pase de Pre-Consulta** encabezado por un banner de prioridad clínica de alta visibilidad junto con la **Ficha Clínica (SOAP / ISBAR)** para su médico.

## 2. Principios de Diseño UI/UX
- **Minimalismo Clínico (Light Mode Puro):** Fondo `#F8FAFC`, tarjetas blancas `#FFFFFF`, acento primario `#0284C7`, cero modo oscuro y cero emojis.
- **Prioridad Clínica de Alta Visibilidad:** En la parte superior del Paso 3 (`3. Resumen`), sobre las pestañas de Paciente y Médico, destaca el bloque de prioridad clínica con borde izquierdo de `6dp`, insignia en mayúsculas (`NIVEL DE PRIORIDAD CLÍNICA · CONSULTA PRIORITARIA HOY` / `ATENCIÓN URGENTE INMEDIATA` / `CONSULTA RUTINARIA`) y tipografía de `17sp` en seminegrita.
- **Casos de Ejemplo Discretos:** Tres píldoras pequeñas debajo del cuadro de texto (`Migraña`, `Dolor en el pecho`, `Fiebre y tos`) para pruebas rápidas.

## 3. Componentes Principales
- `MainActivity.kt`: Implementa `PreConsultaScreen` con navegación limpia en 3 pasos (`1. Síntomas`, `2. Preguntas`, `3. Resumen`), integración con `RecognizerIntent` (voz a texto nativa de Android), `TextToSpeech` (lectura de preguntas) e `Intent.ACTION_SEND` (compartir resumen).
- `data/GemmaLocalEngine.kt`: Ejecuta la protección local de datos personales (DPI y teléfono), conecta con el backend productivo en `https://telemed-preconsult-ai.vercel.app/api/telemed` y provee inferencia local de respaldo para entornos sin conectividad.
