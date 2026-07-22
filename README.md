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
2. **Bot de WhatsApp** (sobre un backend NestJS externo) — interfaz secundaria. El usuario escribe o envía notas de voz para registrar gastos de forma conversacional (por ejemplo, *"Gasté 15 soles en almuerzo"*). El código y despliegue de ese backend no forman parte de este repositorio.

## Características principales

| Módulo | Descripción |
| --- | --- |
| **Registro por OCR** | Escaneo de capturas de Yape/Plin con ML Kit **en el dispositivo** (sin subir datos financieros a servidores externos). Objetivo: ≤ 10 segundos por gasto. |
| **Entrada manual rápida** | Teclado numérico y selección de categoría. Objetivo: ≤ 5 segundos. |
| **Entrada por voz** | Transcripción de voz en español (`RecognizerIntent`) parseada a monto, categoría y descripción. |
| **WhatsApp** | Registro conversacional; las notas de voz se transcriben con Whisper en el backend. |
| **Metas de ahorro** | Creación de metas, depósitos, barra de progreso y animación de confeti al completarse. |
| **Estadísticas** | Gráfico de dona propio con Compose `Canvas` y comparaciones mensuales en tono positivo. |
| **Gamificación** | Insignias (badges), niveles por puntos y recordatorios diarios (máx. 1/día). |
| **Preferencia de moneda** | Símbolo/formato seleccionable por el usuario (PEN por defecto). No convierte importes ni guarda una moneda por gasto. |

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

**Vínculo de usuarios:** la migración `0006` incorpora un reto de un solo uso. La app obtiene el reto y el backend externo debe confirmarlo usando el número E.164 observado en un webhook auténtico; solo entonces se crea `whatsapp_links`. Escribir `users.phone_number` no constituye una vinculación verificada.

> Alcance del repositorio: aquí se versionan la app Android y las migraciones de Supabase. El backend NestJS, su infraestructura y sus pruebas deben revisarse en su repositorio/versionado propio antes de considerar verificada la integración de WhatsApp.

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
| Gráficos | Componentes propios con Compose `Canvas` |
| Pruebas | JUnit 4.13.2 + MockK 1.13.13 + coroutines-test 1.9.0; Espresso 3.6.1 |
| Build | Gradle (Kotlin DSL) + AGP 9.2.1, version catalog |
| SDK | minSdk 24 (Android 7.0) · targetSdk 36 · compileSdk 36.1 (Android 16 QPR2) |
| App ID | `com.qolve.fluyo` |

## Requisitos previos

- **JDK 21** y **Android SDK Platform 36.1** (Android 16 QPR2), o una versión de Android Studio compatible con AGP 9.2.1.
- Un **emulador Android** o dispositivo físico (API ≥ 24).
- Un **proyecto de Supabase** con las migraciones aplicadas (ver [Base de datos](#base-de-datos)).
- Credenciales de **Google OAuth** (Web Client ID) para el inicio de sesión.

## Configuración local

Las claves se leen desde `local.properties` (ignorado por git) y se exponen como campos de `BuildConfig`. Sin ellas, la autenticación y el cliente de Supabase no funcionan:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon_key>
GOOGLE_WEB_CLIENT_ID=<oauth_web_client_id>

# WhatsApp permanece oculto salvo activación explícita tras desplegar/verificar el backend:
WHATSAPP_LINKING_ENABLED=false
# Obligatorio en formato E.164 (solo dígitos) cuando se activa:
WHATSAPP_BOT_NUMBER=

# Destinos públicos obligatorios en una distribución release:
TERMS_URL=https://<host>/terminos
PRIVACY_URL=https://<host>/privacidad
ACCOUNT_DELETION_URL=https://<host>/eliminar-cuenta

# Firma de distribución (obligatoria para bundleRelease/assembleRelease):
RELEASE_KEYSTORE_PATH=...
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Los ejemplos de URL son marcadores: configura destinos HTTPS reales y publicados, sin inventarlos dentro de la app. En debug los tres campos de `BuildConfig` permanecen vacíos. Las cuatro credenciales de firma también pueden proporcionarse como propiedades Gradle (`-P...`) o variables de entorno con los mismos nombres. Las tareas de distribución de `release` fallan si falta una URL, si no es HTTPS, si falta algún valor de firma o si el keystore no existe; nunca generan silenciosamente una distribución incompleta/sin firma.

> Las versiones de dependencias están centralizadas en `gradle/libs.versions.toml` (version catalog). Agrega o actualiza librerías ahí, no en línea dentro de `app/build.gradle.kts`.

## Compilación y ejecución

Todos los comandos se ejecutan desde la raíz del repositorio con el wrapper de Gradle (`./gradlew`). El único módulo es `:app`.

```bash
# Compilar
./gradlew :app:assembleDebug          # APK debug → app/build/outputs/apk/debug/
./gradlew :app:bundleRelease          # AAB firmado; acepta RELEASE_KEYSTORE_* local/Gradle/entorno
./gradlew :app:bundleReleaseUnsigned  # AAB sin firma, solo para inspección local; no publicar
./gradlew clean                       # limpiar outputs

# Instalar y ejecutar en dispositivo/emulador conectado
./gradlew :app:installDebug
adb shell am start -n com.qolve.fluyo/.MainActivity

# Lint / análisis estático
./gradlew :app:lintDebug              # → app/build/reports/lint-results-debug.html
```

Los artefactos `.apk`/`.aab` son outputs generados y están ignorados por Git. Deben publicarse mediante Play Console, CI o una release, no almacenarse en el repositorio.

## Pruebas

El proyecto incluye **pruebas unitarias** sobre la lógica pura de Kotlin y un **smoke test** que valida el arranque de la app.

GitHub Actions ejecuta en cada `push` a `main` y pull request las pruebas JVM, un piso de cobertura de líneas del 18%, Android Lint, instrumentación focalizada en un emulador API 35, APK debug, AAB local minificado, gates negativo/positivo de firma release, contratos SQL/RLS sobre PostgreSQL 17, formato/lint/type-check/tests congelados de la Edge Function con Deno y escaneo de secretos en el historial. Dependabot revisa semanalmente dependencias Gradle, acciones del workflow y el lockfile Deno.

### Pruebas unitarias (JVM)

Usan **JUnit 4 + MockK + coroutines-test** (`runTest`). Cubren la lógica sin dependencias de Android: parseo de voz (`VoiceParserTest`), formato de moneda (`MoneyTest`), matemática de metas (`GoalTest`) y delegación de casos de uso (`CreateGoalUseCaseTest`).

```bash
# Ejecutar todas las pruebas unitarias
./gradlew :app:testDebugUnitTest

# Generar cobertura XML y verificar el piso de no-regresión
./gradlew :app:koverXmlReportDebug :app:koverVerifyDebug

# Compilar pruebas instrumentadas sin requerir emulador
./gradlew :app:compileDebugAndroidTestKotlin

# Ejecutarlas (requiere dispositivo/emulador)
./gradlew :app:connectedDebugAndroidTest

# Ejecutar una sola clase o método
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.data.voice.VoiceParserTest"
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.domain.model.GoalTest.progress is clamped to 1 when over-funded"
```

Resultados:
- **Reporte HTML:** `app/build/reports/tests/testDebugUnitTest/index.html`
- **Resultados XML:** `app/build/test-results/testDebugUnitTest/*.xml`
- **Cobertura XML:** `app/build/reports/kover/reportDebug.xml`

### Smoke test (arranque en emulador)

`scripts/smoke-test.sh` instala el APK debug en un emulador/dispositivo en ejecución, lanza `MainActivity` y **falla** si la actividad no llega al primer plano o si aparece un crash en logcat. Cada corrida intenta guardar evidencia visual en `build/smoke-test/`; Android puede producir una imagen vacía porque `FLAG_SECURE` protege los datos financieros y el preview de Recents.

El script localiza `adb` mediante `PATH`, `ADB`, `ANDROID_SDK_ROOT` o `ANDROID_HOME`, exige seleccionar un dispositivo de forma inequívoca y aplica límites de tiempo al dispositivo, arranque e invocaciones. Usa `ANDROID_SERIAL` cuando haya más de uno conectado.

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
- `0005_budget_extras.sql` — ingresos extraordinarios, vista mensual de presupuesto y funciones auxiliares asociadas.
- `0006_data_integrity_and_secure_operations.sql` — constraints de integridad, depósitos atómicos e idempotentes, reglas de gamificación, mes consistente con Lima y vinculación verificada de WhatsApp.
- `0007_repository_closure.sql` — altas idempotentes por RPC, perfiles sin carrera, borrado lógico de metas, Storage privado versionado y cierre de semántica/retención.

La fase contractual `supabase/contract-migrations/0008_write_path_contract.sql` está fuera del directorio automático por diseño: se aplica con `scripts/apply-contract-migrations.sh` sólo después de desplegar clientes compatibles con los RPC, retirar escritores directos legacy y reparar datos históricos. En una base nueva puede aplicarse inmediatamente después de `0007`. El harness reproducible exige una base vacía y confirmación explícita, y prueba tanto instalación nueva como actualización `0001..0005 → datos legacy → resto`:

```bash
DATABASE_URL=postgresql://.../fluyo_migration_test \
MIGRATION_TEST_CONFIRM_RESET=fluyo_migration_test \
./scripts/test-migrations.sh
```

**RLS:** `auth.uid()` se enlaza con `users.auth_id`; las políticas restringen cada fila por el `users.id` correspondiente. El backend NestJS externo descrito usaría `service_role`, que omite RLS, por lo que debe operar únicamente tras autenticar el webhook y confirmar el reto desde el remitente real. Ese código no está incluido ni queda validado por este repositorio.

## Estructura del proyecto

```
app/src/main/java/com/qolve/fluyo/
├── di/            # Módulos de Hilt (AppModule, SupabaseModule, RepositoryModule)
├── domain/        # KOTLIN PURO — sin dependencias de Android
│   ├── model/     # Entidades (Expense, Goal, Badge, Category, User…)
│   ├── repository/# Interfaces de repositorio
│   └── usecase/   # Casos de uso (RegisterExpense, CreateGoal, DepositToGoal…)
├── data/          # Implementaciones Supabase, DTOs, mappers, OCR y voz
├── notifications/ # Canales, nudges (WorkManager) y notificaciones de badges
└── presentation/  # UI Compose: theme, navigation, screens, components
```

## Contexto académico

Proyecto de tesis para la carrera de **Ingeniería Informática** de la **Pontificia Universidad Católica del Perú (PUCP)**, diseñado con metodología de **Diseño Centrado en el Usuario (DCU)**.

**Restricciones del prototipo:**

- Toda la interfaz en **español (Latinoamérica)**, mediante `strings.xml`.
- **OCR solo en el dispositivo** (ML Kit); la imagen y el texto OCR bruto no se envían a un proveedor de reconocimiento externo ni se guardan con el gasto.
- Diseñada para minimizar datos y aplicar aislamiento mediante RLS; el cumplimiento de la **Ley N.° 29733** requiere además validar los flujos operativos, políticas, retención y eliminación del despliegue completo.
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
