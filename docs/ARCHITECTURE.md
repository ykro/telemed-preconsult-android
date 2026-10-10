# Arquitectura Técnica: Cliente Android (`telemed-preconsult-android`)

Este documento detalla la arquitectura de la aplicación nativa Android en **Kotlin + Jetpack Compose (Material 3)**, el rol de **Gemma 4 E2B** en el dispositivo, su integración con el backend productivo en Vercel ([`telemed-preconsult-ai.vercel.app`](https://telemed-preconsult-ai.vercel.app)) y el diseño de interfaz nativa móvil.

---

## 1. ¿Para qué se usa Gemma 4 E2B y por qué corre en Android Nativo y no en Web?

### Rol de Gemma 4 E2B en Telemedicina
1. **Privacidad y pre-procesamiento local (*On-Device*):** Permite anonimizar identificadores sensibles (DPI, teléfonos) y estructurar el relato de síntomas directamente en el dispositivo del paciente sin depender de servicios externos.
2. **Interrogatorio OPQRST y triaje offline en zonas sin cobertura:** En brigadas médicas rurales, puestos de salud o comunidades con conectividad móvil inestable, **Gemma 4 E2B** evalúa qué variables semiológicas del protocolo **OPQRST** faltan para formular de **1 a 3 preguntas de seguimiento**, clasifica el nivel de prioridad (`ROJO`, `AMARILLO`, `VERDE`) y construye la nota clínica `SOAP` / `ISBAR` completamente fuera de línea.
3. **Enrutamiento híbrido:** Cuando el teléfono cuenta con conexión estable, sincroniza con el backend en la nube (**Gemini 3.8 Flash**); cuando pierde señal o requiere procesamiento local, **Gemma 4 E2B** garantiza continuidad clínica sin interrupciones.

### ¿Por qué en la App Móvil Nativa sí y en la versión Web no?
- **Tamaño del modelo y almacenamiento persistente:** El modelo cuantizado **Gemma 4 E2B** (~1.3 GB – 1.5 GB en formato `.task` de LiteRT/MediaPipe) se descarga una sola vez en el almacenamiento interno de Android y queda disponible permanentemente. En una aplicación web móvil, descargar más de 1.3 GB desde el navegador antes de iniciar una consulta médica sería inviable en redes celulares y superaría los límites de almacenamiento caché de los navegadores móviles.
- **Acceso directo a GPU y NPU del teléfono:** A través del SDK nativo de Android (**MediaPipe Tasks GenAI / LiteRT**), la aplicación utiliza directamente los aceleradores de hardware (GPU/NPU) del dispositivo con gestión nativa de memoria, evitando los cierres por límite de RAM (*Out-Of-Memory*) que ocurren en WebAssembly/WebGPU dentro de navegadores móviles.

![Enrutamiento híbrido online y offline en Android](./diagrams/edge-cloud-routing.svg)

---

## 2. Capas de Presentación y Experiencia Nativa (Material 3)

La interfaz en Jetpack Compose (`MainActivity.kt` y `ui/PreconsultScreen.kt`) implementa patrones nativos móviles:
- **`Scaffold` con `TopAppBar` nativo e indicador lineal de progreso (`1/3`, `2/3`, `3/3`)** y navegación hacia atrás integrada.
- **Barra de acción inferior fija en zona del pulgar (`NativeBottomActionSheet`):** El botón principal permanece anclado en la parte inferior con contraste clínico activo durante la inferencia local.
- **Tarjetas táctiles 2x2 de altura uniforme para signos vitales** con indicadores clínicos semánticos (`Normal`, `Fiebre`, `Vigilar`, `Elevado`, `Elevada`) y dictado por voz (`RecognizerIntent` + `TextToSpeech` en `es-GT`).
- **Stepper interactivo de 1 pregunta a la vez (`Step 2: Ayúdame con más información`):** Píldoras superiores (`Pregunta 1`, `Pregunta 2`, `Pregunta 3`), tarjeta activa enfocada y resumen inferior compacto (`P1`, `P2`, `P3`) con botón `Editar` para que toda la vista quepa en pantalla sin desplazamiento vertical.
- **Barra de navegación inferior nativa (`NavigationBar` en `Step 3`)** que divide los resultados en tres vistas móviles (`Mi Pase`, `Preparación`, `Ficha Médica`).

![Capas de presentación, estado y motor clínico en Android](./diagrams/android-mvvm-layers.svg)

---

## 3. Estructura de Paquetes

```text
app/src/main/java/com/example/telemedpreconsultai/
├── MainActivity.kt               # Flujo nativo Material 3 en 3 pasos + Stepper + NavigationBar
├── data/
│   ├── GemmaLocalEngine.kt       # Redacción local PII, cliente HTTPS y motor clínico offline (1 a 3 preguntas)
│   └── DataRepository.kt         # Repositorio de datos clínicos
└── ui/
    ├── PreconsultScreen.kt       # Componentes Compose reutilizables y controles vectoriales Canvas
    └── theme/                    # Paleta clínica Light Mode y tipografía Material 3
```
