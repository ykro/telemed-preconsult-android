# PreConsulta — Cliente Android Nativo (`telemed-preconsult-android`)

Aplicación móvil nativa en **Kotlin y Jetpack Compose (Material 3)** para preparación de teleconsultas médicas: captura síntomas por voz, registra signos vitales domiciliarios en tarjetas táctiles, realiza de **1 a 3 preguntas breves ("Ayúdame con más información")** mediante un stepper sin scroll y entrega tanto el **Pase del Paciente** como la **Ficha Clínica (`SOAP` / `ISBAR` / `OPQRST`)** en navegación inferior nativa.

- **Aplicación Web Compañera:** [https://telemed-preconsult-ai.vercel.app](https://telemed-preconsult-ai.vercel.app) ([`ykro/telemed-preconsult-ai`](https://github.com/ykro/telemed-preconsult-ai))
- **Índice de Prototipos:** [`ykro/ilab-aibuilders-prototypes`](https://github.com/ykro/ilab-aibuilders-prototypes)

---

## Flujo Móvil en 3 Pantallas

1. **1. Cuéntanos cómo te sientes:** Dictado por voz (`RecognizerIntent`), casos de prueba rápida y cuadrícula 2x2 de signos vitales con indicadores semánticos (`Normal`, `Fiebre`, `Vigilar`, `Elevado`).
2. **2. Ayúdame con más información:** Stepper interactivo de **1 pregunta a la vez** (`Pregunta 1`, `Pregunta 2`, `Pregunta 3`) y barra de respuestas contestadas (`P1`, `P2`, `P3`) que cabe en pantalla sin hacer scroll.
3. **3. Resumen con `NavigationBar` Nativa:** Dividido en tres pestañas inferiores (`Mi Pase`, `Preparación` y `Ficha Médica` con selector `SOAP` / `ISBAR` / `OPQRST`).

---

## Uso de Gemma 4 E2B (Android Nativo vs. Web)

- **Rol en la App Móvil:** Ejecuta anonimización local de PII, evaluación de brechas semiológicas **OPQRST** (1 a 3 preguntas), triaje de prioridad (`ROJO`, `AMARILLO`, `VERDE`) y generación de notas `SOAP` / `ISBAR` completamente **offline** en zonas sin cobertura, sincronizando con **Gemini 3.8 Flash** cuando hay conectividad.
- **¿Por qué en Android nativo sí y en Web no?** El modelo cuantizado **Gemma 4 E2B** (~1.3 GB en formato `.task`) se guarda de forma persistente en el almacenamiento de Android y corre sobre la GPU/NPU del teléfono vía **LiteRT / MediaPipe**, mientras que en un navegador web móvil descargar 1.3 GB por sesión es inviable en redes celulares y excede la RAM de WebAssembly/WebGPU (ver detalle en [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)).

---

## Documentación y Artículos

- **Arquitectura Técnica y Diagramas:** [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- **Temas para Artículos Técnicos y de Producto (6 temas):** [`docs/ARTICLES.md`](./docs/ARTICLES.md)
- **Especificación Funcional:** [`SPEC.md`](./SPEC.md)
- **Guía de Instalación y Emulador:** [`docs/ONBOARDING.md`](./docs/ONBOARDING.md)

---

## Compilación y Ejecución

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.telemedpreconsultai/.MainActivity
```
