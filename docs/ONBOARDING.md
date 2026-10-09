# Guía de Onboarding — Cliente Android (`telemed-preconsult-android`)

## 1. Requisitos Previos
- **JDK 17**
- **Android SDK (`compileSdk 36`, `minSdk 24`)** configurado en `~/Library/Android/sdk`
- **Android Studio** o **Android CLI (`android`)**

## 2. Compilar el APK desde Terminal
```bash
cd telemed-preconsult-android
./gradlew assembleDebug
```
El archivo APK compilado se genera en:
`app/build/outputs/apk/debug/app-debug.apk`

## 3. Ejecutar en Emulador o Dispositivo Físico
Con un dispositivo conectado por USB o emulador activo:
```bash
./gradlew installDebug
```
O utilizando `android` CLI:
```bash
android run --apks=app/build/outputs/apk/debug/app-debug.apk
```

## 4. Flujo de Prueba Rápida
1. En la pantalla **1. Síntomas ("Cuéntanos cómo te sientes")**, presiona el botón circular **Voz** para dictar o toca uno de los enlaces pequeños de ejemplo (`Migraña`, `Dolor en el pecho`, `Fiebre y tos`) y presiona **Continuar**.
2. En **2. Preguntas ("Ayúdame con más información")**, responde las preguntas breves generadas dinámicamente (de 1 a 3 preguntas según la información faltante; puedes presionar **Escuchar** para oírlas por voz) y presiona **Ver mi resumen**.
3. En **3. Resumen**, verifica el banner superior de prioridad clínica (`NIVEL DE PRIORIDAD CLÍNICA · CONSULTA PRIORITARIA HOY` / `ATENCIÓN URGENTE INMEDIATA`) y alterna entre **Para mí (Paciente)** y **Ficha Médica (SOAP / ISBAR)**.
