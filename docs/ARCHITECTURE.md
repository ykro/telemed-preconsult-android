# Arquitectura Técnica: Cliente Android (`telemed-preconsult-android`)

Este documento detalla la arquitectura de la aplicación nativa Android en **Kotlin + Jetpack Compose**, su integración con el backend productivo en Vercel ([`telemed-preconsult-ai.vercel.app`](https://telemed-preconsult-ai.vercel.app)) y su motor clínico local de respaldo para operación sin conexión.

---

## 1. Enrutamiento Híbrido Edge + Cloud

El cliente Android aplica primero una capa local de protección de datos personales (`redactLocalPii`) en el dispositivo antes de consultar el endpoint productivo `/api/telemed`. En caso de operar en zonas rurales o sin conectividad, conmuta automáticamente hacia el motor clínico local en `GemmaLocalEngine.kt`.

![Enrutamiento híbrido online y offline en Android](./diagrams/edge-cloud-routing.svg)

---

## 2. Capas de la Aplicación Android

La interfaz en Jetpack Compose (`PreConsultaScreen` en `MainActivity.kt`) gestiona el flujo en 3 pasos (`1. Síntomas`, `2. Preguntas ("Ayúdame con más información")` con entre 1 y 3 preguntas dinámicas, y `3. Resumen` con banner hero de prioridad clínica y conmutador `SOAP` / `ISBAR`).

![Capas de presentación, estado y motor clínico en Android](./diagrams/android-mvvm-layers.svg)

---

## 3. Estructura de Paquetes

```text
app/src/main/java/com/example/telemedpreconsultai/
├── MainActivity.kt               # PreConsultaScreen en Jetpack Compose (Pasos 1, 2 y 3 + Banner de Prioridad)
├── data/
│   ├── GemmaLocalEngine.kt       # Redacción local PII, cliente HTTPS y motor clínico offline (1 a 3 preguntas)
│   └── DataRepository.kt         # Repositorio base de datos
└── theme/
    ├── Color.kt                  # Paleta clínica Light Mode
    ├── Theme.kt                  # Configuración Material 3
    └── Type.kt                   # Escala tipográfica
```
