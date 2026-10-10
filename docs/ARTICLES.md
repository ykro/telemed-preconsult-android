# Temas para Artículos Técnicos y de Producto: PreConsulta Android (`telemed-preconsult-android`)

Selección de 6 temas (3 de producto/arquitectura móvil y 3 de ingeniería en Kotlin y Jetpack Compose) derivados del desarrollo del cliente Android nativo de **PreConsulta**.

---

## Producto y Arquitectura Móvil

### 1. IA en el dispositivo (`Gemma 4 E2B`) frente a inferencia en la nube (`Gemini 3.8 Flash`) en aplicaciones médicas móviles
Análisis arquitectónico sobre persistencia de modelos cuantizados (~1.3 GB), aceleración por hardware en GPU/NPU móvil mediante LiteRT/MediaPipe y las razones técnicas por las que la inferencia local funciona de forma óptima en una app nativa Android pero resulta inviable dentro de un navegador web móvil.

### 2. De formulario web a experiencia móvil nativa en Jetpack Compose (Material 3) para pacientes con síntomas agudos
Decisiones de diseño e ingeniería de interfaz móvil orientadas a reducir la carga cognitiva del paciente: stepper interactivo de una sola pregunta a la vez sin necesidad de hacer scroll, barra de acción en la zona del pulgar y tarjetas de signos vitales con alertas semánticas en tiempo real.

### 3. Arquitectura híbrida offline-first para triaje clínico en brigadas rurales y zonas de baja conectividad
Cómo estructurar un cliente móvil capaz de ejecutar anonimización de PII, interrogatorio OPQRST, detección de banderas rojas y generación de fichas `SOAP` / `ISBAR` completamente fuera de línea y sincronizar cuando vuelve la cobertura.

---

## Ingeniería en Android (Kotlin y Jetpack Compose)

### 4. Ejecución de inferencia local en Android con Kotlin Coroutines (`Dispatchers.IO`) y gestión de memoria para modelos cuantizados `.task`
Cómo aislar el motor `GemmaLocalEngine` del hilo principal (`UI Thread`), evitar bloqueos de fotogramas (`jank`) en Compose y mantener tiempos de respuesta sub-segundo en dispositivos móviles.

### 5. Sistema de iconografía y controles clínicos vectoriales en Jetpack Compose usando primitivas `Canvas` y `DrawScope`
Cómo implementar casillas de verificación (`NativeCheckboxSquare`), selectores de radio (`NativeRadioCircle`) e iconos médicos mediante trazos vectoriales directos en código sin depender de paquetes pesados de recursos ni iconos de terceros.

### 6. Integración reactiva de `RecognizerIntent` (`ActivityResultContracts`) y ciclo de vida de `TextToSpeech` en Jetpack Compose
Manejo seguro de contratos de voz del sistema Android y liberación determinista del motor TTS mediante `DisposableEffect` dentro de una arquitectura de navegación por pasos.
