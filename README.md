# PreConsulta — Cliente Android Nativo (`telemed-preconsult-android`)

Cliente móvil nativo en **Kotlin + Jetpack Compose (100% Light Mode)** para captura rápida de síntomas por voz, seguimiento dinámico de **1 a 3 preguntas breves ("Ayúdame con más información")** y generación del **Pase de Pre-Consulta del Paciente** (con banner hero de prioridad clínica) y la **Ficha Clínica (SOAP / ISBAR)**, diseñado para operar tanto conectado como en modo offline con **Gemma 4 E2B**.

- **Aplicación Web Compañera (Deploy en Producción):** [https://telemed-preconsult-ai.vercel.app](https://telemed-preconsult-ai.vercel.app)
- **Repositorio Web Compañero:** [`ykro/telemed-preconsult-ai`](https://github.com/ykro/telemed-preconsult-ai)
- **Especificación Técnica:** [Ver SPEC.md](./SPEC.md)
- **Arquitectura del Cliente Android:** [Ver docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
- **Guía de Onboarding e Instalación Android:** [Ver docs/ONBOARDING.md](./docs/ONBOARDING.md)
- **Índice General de Proyectos:** [`ykro/hackathon-prototipos-index`](https://github.com/ykro/hackathon-prototipos-index)

---

## Arquitectura del Cliente Android

### 1. Enrutamiento Híbrido Online y Offline (Edge + Cloud)

![Enrutamiento híbrido online y offline en Android](./docs/diagrams/edge-cloud-routing.svg)

### 2. Capas de Presentación, Estado y Motor Clínico en Android

![Capas de presentación, estado y motor clínico en Android](./docs/diagrams/android-mvvm-layers.svg)

---

## Estructura de Documentación

- **[SPEC.md](./SPEC.md):** Alcance técnico del cliente Android y esquema de datos.
- **[docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md):** Diseño de capas en Jetpack Compose, protección local de PII y enrutamiento resiliente.
- **[docs/ONBOARDING.md](./docs/ONBOARDING.md):** Instrucciones para abrir en Android Studio, compilar el APK con `./gradlew assembleDebug` y ejecutar en emulador o dispositivo físico.
