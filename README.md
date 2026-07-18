# Fluyo

**Plataforma de gestión de finanzas personales para estudiantes universitarios en Lima, Perú.**

Fluyo es el prototipo desarrollado como proyecto de tesis para la carrera de Ingeniería Informática de la **Pontificia Universidad Católica del Perú (PUCP)**. Aplica la metodología de **Diseño Centrado en el Usuario (DCU)** para resolver un problema concreto: los estudiantes de 18 a 26 años que usan Yape/Plin a diario abandonan el registro de sus gastos por lo tedioso del ingreso manual.

La solución reduce esa fricción combinando **escaneo OCR de capturas de Yape/Plin**, **entrada por voz** y **registro conversacional por WhatsApp**, sumado a mecánicas de gamificación y metas de ahorro que refuerzan el hábito.

---

## Tabla de contenidos

- [Descripción general](#descripción-general)
- [Características principales](#características-principales)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Requisitos previos](#requisitos-previos)
- [Configuración local](#configuración-local)
- [Compilación y ejecución](#compilación-y-ejecución)
- [Pruebas](#pruebas)
- [Base de datos](#base-de-datos)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Contexto académico](#contexto-académico)
- [Licencia](#licencia)

---

## Descripción general

Fluyo tiene **dos puntos de entrada** que escriben sobre la **misma base de datos PostgreSQL (Supabase)**, garantizando una única fuente de verdad sin importar cómo el usuario registre el gasto:

1. **App nativa Android** (Kotlin + Jetpack Compose) — interfaz principal. Registra gastos por OCR, entrada manual y voz; incluye gamificación, metas de ahorro y recordatorios de comportamiento (*nudges*).
2. **Bot de WhatsApp** (sobre un backend NestJS existente) — interfaz secundaria. El usuario escribe o envía notas de voz para registrar gastos de forma conversacional (por ejemplo, *"Gasté 15 soles en almuerzo"*).

## Características principales

| Módulo | Descripción |
| --- | --- |
| **Registro por OCR** | Escaneo de capturas de Yape/Plin con ML Kit **en el dispositivo** (sin subir datos financieros a servidores externos). Objetivo: ≤ 10 segundos por gasto. |
| **Entrada manual rápida** | Teclado numérico y selección de categoría. Objetivo: ≤ 5 segundos. |
| **Entrada por voz** | Transcripción de voz en español (`RecognizerIntent`) parseada a monto, categoría y descripción. |
| **WhatsApp** | Registro conversacional; las notas de voz se transcriben con Whisper en el backend. |
| **Metas de ahorro** | Creación de metas, depósitos, barra de progreso y animación de confeti al completarse. |
| **Estadísticas** | Gráfico de dona por categoría (Vico) y comparaciones mensuales en tono positivo. |
| **Gamificación** | Insignias (badges), niveles por puntos y recordatorios diarios (máx. 1/día). |
| **Multi-moneda** | Moneda seleccionable por el usuario (PEN por defecto). |

## Arquitectura

La app Android sigue **Clean Architecture + MVVM**, donde la capa de dominio no tiene **ninguna** dependencia de Android.

```
┌─────────────────────────────────────────────┐
│                SUPABASE (Cloud)              │
│   Auth  ·  PostgreSQL  ·  Storage  ·  Edge   │
└──────────────┬────────────────┬──────────────┘
               │                │
        ┌──────┴──────┐   ┌─────┴───────────────┐
        │ App Android │   │  Backend NestJS     │
        │ (Compose)   │   │  (DigitalOcean VPS) │
        │  OCR ML Kit │   │  WhatsApp Cloud API │
        └─────────────┘   │  Whisper (voz)      │
                          └─────────────────────┘
```

**Flujo de datos (app Android):** sesión de Supabase Auth → escaneo OCR en el dispositivo → escritura directa a PostgreSQL vía Supabase Kotlin SDK → recálculo local del presupuesto y actualización de la UI.

**Flujo de datos (WhatsApp):** mensaje entrante → webhook al backend NestJS → (si es voz) transcripción con Whisper → parseo → escritura a la misma base con `source = 'whatsapp'` → respuesta de confirmación al usuario.

**Vínculo de usuarios:** los usuarios de WhatsApp se enlazan a su cuenta de Android por número de teléfono (`users.phone_number`).

## Stack tecnológico

| Componente | Tecnología |
| --- | --- |
| Lenguaje | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3 |
| Arquitectura | Clean Architecture + MVVM, StateFlow |
| Inyección de dependencias | Hilt (Dagger) 2.59.2 + KSP |
| Backend / datos | Supabase Kotlin SDK 3.0.2 (Auth, Postgrest, Storage) |
| Autenticación | Credential Manager + Google ID (One Tap) |
| OCR | Google ML Kit Text Recognition 16.0.1 (en el dispositivo) |
| Gráficos | Vico (dona por categorías) |
| Pruebas | JUnit 4.13.2 + MockK 1.13.13 + coroutines-test 1.9.0; Espresso 3.6.1 |
| Build | Gradle (Kotlin DSL) + AGP 9.2.1, version catalog |
| SDK | minSdk 24 (Android 7.0) · target/compileSdk 36 (Android 15) |
| App ID | `com.qolve.fluyo` |

## Requisitos previos

- **Android Studio** (versión compatible con AGP 9.2.1) o el JDK requerido por Gradle.
- Un **emulador Android** o dispositivo físico (API ≥ 24).
- Un **proyecto de Supabase** con las migraciones aplicadas (ver [Base de datos](#base-de-datos)).
- Credenciales de **Google OAuth** (Web Client ID) para el inicio de sesión.

## Configuración local

Las claves se leen desde `local.properties` (ignorado por git) y se exponen como campos de `BuildConfig`. Sin ellas, la autenticación y el cliente de Supabase no funcionan:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon_key>
GOOGLE_WEB_CLIENT_ID=<oauth_web_client_id>

# Firma de release (opcional, solo para bundleRelease):
RELEASE_KEYSTORE_PATH=...
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

> Las versiones de dependencias están centralizadas en `gradle/libs.versions.toml` (version catalog). Agrega o actualiza librerías ahí, no en línea dentro de `app/build.gradle.kts`.

## Compilación y ejecución

Todos los comandos se ejecutan desde la raíz del repositorio con el wrapper de Gradle (`./gradlew`). El único módulo es `:app`.

```bash
# Compilar
./gradlew :app:assembleDebug          # APK debug → app/build/outputs/apk/debug/
./gradlew :app:bundleRelease          # AAB release (requiere RELEASE_KEYSTORE_* en local.properties)
./gradlew clean                       # limpiar outputs

# Instalar y ejecutar en dispositivo/emulador conectado
./gradlew :app:installDebug
adb shell am start -n com.qolve.fluyo/.MainActivity

# Lint / análisis estático
./gradlew :app:lintDebug              # → app/build/reports/lint-results-debug.html
```

## Pruebas

El proyecto incluye **pruebas unitarias** sobre la lógica pura de Kotlin y un **smoke test** que valida el arranque de la app.

### Pruebas unitarias (JVM)

Usan **JUnit 4 + MockK + coroutines-test** (`runTest`). Cubren la lógica sin dependencias de Android: parseo de voz (`VoiceParserTest`), formato de moneda (`MoneyTest`), matemática de metas (`GoalTest`) y delegación de casos de uso (`CreateGoalUseCaseTest`).

```bash
# Ejecutar todas las pruebas unitarias
./gradlew :app:testDebugUnitTest

# Ejecutar una sola clase o método
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.data.voice.VoiceParserTest"
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.domain.model.GoalTest.progress is clamped to 1 when over-funded"
```

Resultados:
- **Reporte HTML:** `app/build/reports/tests/testDebugUnitTest/index.html`
- **Resultados XML:** `app/build/test-results/testDebugUnitTest/*.xml`

### Smoke test (arranque en emulador)

`scripts/smoke-test.sh` instala el APK debug en un emulador/dispositivo en ejecución, lanza `MainActivity` y **falla** si la actividad no llega al primer plano o si aparece un crash en logcat. Cada corrida guarda automáticamente una captura del emulador en `build/smoke-test/`.

```bash
./gradlew :app:assembleDebug     # 1) compilar el APK
./scripts/smoke-test.sh          # 2) con un emulador ya corriendo
```

Salida esperada al finalizar:

```
    ✓ Fluyo is in the foreground
    ✓ No fatal exceptions in logcat
==> SMOKE TEST PASSED ✅
```

## Base de datos

El esquema se versiona como migraciones SQL ordenadas en `supabase/migrations/`, que son la **fuente de verdad** del esquema en producción:

- `0001_initial_schema.sql` — tablas base (`users`, `categories`, `expenses`, `goals`, `badges`, `goal_deposits`), vistas y triggers.
- `0002_rls_policies.sql` — políticas de Row Level Security (cada usuario accede solo a sus datos).
- `0003_security_hardening.sql` — endurecimiento de seguridad.
- `0004_category_ondelete_setnull.sql` — la FK `expenses.category_id` pasa a `ON DELETE SET NULL`, de modo que una categoría puede eliminarse sin fallar por gastos que la referencian.

**RLS:** los usuarios solo acceden a sus propios datos (`auth.uid() = user_id`). El backend NestJS usa la `service_role` key, que omite RLS para escribir en nombre de usuarios de WhatsApp.

## Estructura del proyecto

```
app/src/main/java/com/qolve/fluyo/
├── di/            # Módulos de Hilt (AppModule, SupabaseModule, RepositoryModule)
├── domain/        # KOTLIN PURO — sin dependencias de Android
│   ├── model/     # Entidades (Expense, Goal, Badge, Category, User…)
│   ├── repository/# Interfaces de repositorio
│   └── usecase/   # Casos de uso (RegisterExpense, CreateGoal, DepositToGoal…)
├── data/          # Implementaciones Supabase, DTOs, mappers, OCR, voz, badges
├── notifications/ # Canales, nudges (WorkManager) y notificaciones de badges
└── presentation/  # UI Compose: theme, navigation, screens, components
```

## Contexto académico

Proyecto de tesis para la carrera de **Ingeniería Informática** de la **Pontificia Universidad Católica del Perú (PUCP)**, diseñado con metodología de **Diseño Centrado en el Usuario (DCU)**.

**Restricciones del prototipo:**

- Toda la interfaz en **español (Latinoamérica)**, mediante `strings.xml`.
- **OCR solo en el dispositivo** (ML Kit); ningún dato financiero se envía a APIs externas.
- Cumple con la **Ley N.° 29733** de protección de datos personales (Perú).
- Sin publicidad ni funciones premium — es un prototipo de tesis.

## Licencia

Copyright (C) 2026 Jeremy Aldama

Fluyo es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la
**Licencia Pública General de GNU (GNU GPL)** publicada por la Free Software Foundation,
ya sea la versión 3 de la Licencia o (a tu elección) cualquier versión posterior.

Se distribuye con la esperanza de que sea útil, pero **SIN NINGUNA GARANTÍA**; ni siquiera
la garantía implícita de COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR. Consulta
la GNU GPL para más detalles.

El texto completo está en el archivo [`LICENSE`](LICENSE) o en
<https://www.gnu.org/licenses/gpl-3.0.html>.
