---
title: "Tesis E1 — Inserciones y actualizaciones"
subtitle: "Capítulo 5 (R 2.2, R 2.3) + correcciones de stack en Caps. 1, 2 y Anexo A"
author: "Jeremy Daniel Aldama Giraldo"
date: "Mayo 2026"
lang: es
status: "Borrador histórico; no representa por sí solo el estado verificable del repositorio"
---

# Guía de uso de este documento

> [!WARNING]
> Este archivo es un borrador de tesis fechado en mayo de 2026 y conserva propuestas,
> métricas y fragmentos de esquema históricos. **No debe copiarse al manuscrito como
> evidencia del estado actual.** Para hechos verificables use
> [`../AUDITORIA_REPOSITORIO.md`](../AUDITORIA_REPOSITORIO.md),
> [`../SYSTEM_DESIGN.md`](../SYSTEM_DESIGN.md) y las migraciones. En particular, no se
> ejecutó aquí un E2E instrumentado completo ni una validación contra staging, y la
> vinculación WhatsApp actual usa challenge de remitente verificado y está desactivada
> por defecto; `users.phone_number` es legado y no acredita identidad.

Este documento contiene dos bloques independientes:

- **Parte 1.** Las dos secciones técnicas nuevas (5.2 y 5.3) listas para insertarse directamente en el Capítulo 5 del manuscrito principal.
- **Parte 2.** Reemplazos puntuales de párrafos, filas de tabla y criterios de aceptación cuyo contenido quedó desactualizado al migrarse el stack tecnológico de React Native + Firebase + Tesseract.js hacia Kotlin + Jetpack Compose + Supabase + Google ML Kit. Cada reemplazo lleva la ubicación de destino (capítulo, sección y, cuando aplica, número de página del PDF E1).

> **Origen de los datos.** Todas las referencias a versiones de librerías, nombres de archivos del repositorio, esquemas SQL y políticas de seguridad provienen del repositorio canónico de Fluyo (`/Users/jeremyaldama/Desktop/qolve/Fluyo`), específicamente de `CLAUDE.md`, `gradle/libs.versions.toml`, `app/src/main/java/com/qolve/fluyo/` y `supabase/migrations/`.

---

# Parte 1 — Secciones nuevas para Capítulo 5

## 5.2 R 2.2. Arquitectura de Software Escalable e Infraestructura de Backend Serverless

Este resultado define la base técnica sobre la cual se materializa la propuesta de valor descrita en R 2.1. La arquitectura responde a tres exigencias derivadas de los hallazgos del Objetivo 1: (i) **trazabilidad de cada gasto** —el dominio financiero es fuertemente relacional y exige integridad referencial—, (ii) **costo operativo cercano a cero** para sostener la viabilidad de un proyecto de tesis sin financiamiento externo, y (iii) **separación estructural de datos entre usuarios** para cumplir con el principio de Seguridad de la Ley N° 29733.

### 5.2.1 Patrón arquitectónico: Clean Architecture en tres capas

La aplicación se estructura siguiendo Clean Architecture en su variante de tres capas, materializadas como tres subpaquetes Kotlin con dependencias unidireccionales:

```
com.qolve.fluyo
├── domain/         ← Lógica de negocio pura. Cero dependencias de Android.
│   ├── model/      (User, Expense, Goal, Badge, Category, ParsedReceipt…)
│   ├── repository/ (interfaces: ExpenseRepository, GoalRepository, …)
│   └── usecase/    (RegisterExpenseUseCase, CreateGoalUseCase,
│                    DepositToGoalUseCase, ComputeNudgeUseCase)
│
├── data/           ← Acceso a Supabase, OCR y cachés locales.
│   ├── repository/ (implementaciones: SupabaseExpenseRepository, …)
│   ├── dto/        (modelos serializables que reflejan el esquema SQL)
│   ├── mapper/     (DTO ↔ modelo de dominio)
│   ├── ocr/        (OcrService, YapeParser)
│   ├── badge/      (BadgeEngine)
│   └── local/      (DataStore para preferencias de onboarding)
│
└── presentation/   ← Compose UI + ViewModels.
    ├── screens/    (home, expense, scan, stats, goals, profile, …)
    ├── components/ (BadgeCard, ProgressBar, ConfettiAnimation, …)
    ├── theme/      (sistema de diseño Material 3 + ramps de marca)
    └── navigation/ (FluyoNavHost, BottomNavBar)
```

La regla arquitectónica más importante es que la capa `domain` **no importa ningún paquete `androidx.*` ni `io.github.jan-tennert.*`**: es Kotlin puro. Esto garantiza dos propiedades verificables: las pruebas unitarias del dominio corren sin instrumentación Android (JVM pura, milisegundos por test) y la lógica de negocio puede portarse a otro cliente —por ejemplo, una futura app de escritorio— sin modificación.

La capa `data` traduce entre el modelo de dominio (camelCase, tipos Kotlin nativos) y los DTOs serializables que reflejan exactamente las columnas de Supabase (snake_case, anotados con `@Serializable`). Los mappers son la única frontera donde conviven ambas convenciones.

### 5.2.2 Infraestructura serverless con Supabase

Se eligió **Supabase** como Backend-as-a-Service por encima de la alternativa originalmente considerada (Firebase) con base en cinco criterios técnicos y de negocio:

| Criterio | Firebase / Firestore | Supabase / PostgreSQL | Decisión |
|---|---|---|---|
| Modelo de datos | NoSQL documental | Relacional con FKs | Supabase: el dominio financiero (gastos → categorías → metas → depósitos) exige integridad referencial |
| Lenguaje de consulta | API propia, sin JOIN nativo | SQL estándar + vistas | Supabase: `monthly_category_summary` se expresa en 6 líneas de SQL |
| Seguridad por usuario | Reglas declarativas (texto) | Row Level Security (PostgreSQL) | Supabase: RLS es enforcement estructural, no aplicacional |
| Costo a escala de tesis | Plan Spark gratuito | Plan Free (500 MB, 50k MAU) | Empate |
| Portabilidad | Lock-in completo | Postgres estándar (export a cualquier proveedor) | Supabase |

Los servicios concretos que la aplicación consume son:

- **Supabase Auth** para inicio de sesión con Google (One Tap vía Credential Manager API) y email/contraseña. Reemplaza a Firebase Authentication. La sesión emite tokens JWT firmados que la base de datos lee mediante la función `auth.uid()` para resolver las políticas RLS.
- **PostgreSQL 16 gestionado** para la persistencia transaccional de gastos, metas, categorías, depósitos y medallas. Reemplaza a Cloud Firestore.
- **Supabase Storage** para almacenar imágenes de comprobantes únicamente como respaldo cuando el OCR on-device falla y el usuario opta por reintentar; el flujo principal **no sube imágenes a la nube**, alineándose con el principio de Finalidad de la Ley N° 29733.
- **Edge Functions** (Deno + TypeScript) previstas para el cálculo asíncrono de medallas masivas y la programación de nudges desde el servidor, en una fase posterior a la cubierta por esta tesis.
- **Realtime** opcional para sincronización futura entre la app Android y un canal complementario de registro (mensajería).

### 5.2.3 Modelo de datos relacional

El esquema se compone de seis tablas, dos vistas y un trigger. Todas las llaves primarias son UUID generadas con `gen_random_uuid()`, todas las marcas de tiempo usan `TIMESTAMPTZ` para tolerar zonas horarias, y todas las foreign keys aplican `ON DELETE CASCADE` para garantizar consistencia al eliminar un usuario.

```sql
-- Usuarios. auth_id apunta a auth.users que administra Supabase Auth.
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_id UUID UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT,
  display_name TEXT,
  phone_number TEXT UNIQUE,            -- LEGADO: no prueba identidad ni vincula el canal actual
  monthly_budget DECIMAL(10,2) DEFAULT 0,
  currency TEXT DEFAULT 'PEN',
  level INTEGER DEFAULT 1,
  total_points INTEGER DEFAULT 0,
  notification_enabled BOOLEAN DEFAULT true,
  notification_hour INTEGER DEFAULT 20,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Gastos. Indexados por usuario+fecha para que la consulta del Home (últimos
-- 5 gastos del mes vigente) se resuelva sin scan secuencial.
CREATE TABLE expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  amount DECIMAL(10,2) NOT NULL,
  category_id UUID REFERENCES categories(id),
  description TEXT,
  expense_date DATE DEFAULT CURRENT_DATE,
  source TEXT NOT NULL CHECK (source IN ('manual','ocr','voice','whatsapp')),
  recipient TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_expenses_user_date  ON expenses(user_id, expense_date DESC);
CREATE INDEX idx_expenses_user_month ON expenses(user_id, DATE_TRUNC('month', expense_date));
```

(Los DDL completos de `categories`, `goals`, `goal_deposits`, `badges`, las vistas `monthly_category_summary` y `current_month_budget`, y el trigger `seed_default_categories` se encuentran en `supabase/migrations/0001_initial_schema.sql`.)

La columna `source` con su `CHECK` constraint cumple un rol documental: declara explícitamente los cuatro canales de ingesta soportados (manual, OCR, voz, mensajería) y previene que código defectuoso introduzca valores fuera del catálogo.

### 5.2.4 Seguridad: Row Level Security como materialización de la Ley N° 29733

El principio de Seguridad de la Ley N° 29733 exige medidas técnicas que garanticen que un usuario no pueda acceder a los datos de otro. En lugar de delegar este control a la capa de aplicación —donde un bug puede exponer información—, Fluyo lo hace cumplir al nivel de la base de datos mediante Row Level Security (RLS).

```sql
ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users access own expenses"
ON expenses FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);
```

La cláusula `USING` filtra cualquier `SELECT` para que solo retorne filas donde el `user_id` coincida con el `auth.uid()` del JWT activo. La cláusula `WITH CHECK` impide `INSERT` o `UPDATE` que intenten asignar un `user_id` distinto al del solicitante. Políticas equivalentes se aplican a `categories`, `goals`, `goal_deposits` y `badges`.

Esta arquitectura tiene una propiedad importante: **incluso si un atacante obtuviera la clave anon pública**, sus consultas seguirían restringidas a su propia identidad. La única forma de bypass legítimo es la `service_role` key, custodiada únicamente en 1Password y nunca embebida en el cliente Android.

Las migraciones que materializan estas políticas son `supabase/migrations/0002_rls_policies.sql` y `supabase/migrations/0003_security_hardening.sql`; esta última añade RLS a las vistas mediante la opción `security_invoker = true` para que la vista herede los privilegios del usuario consultante.

### 5.2.5 Aprovisionamiento automático de categorías

Para evitar que la aplicación tenga que emitir siete `INSERT` por cada usuario nuevo, el aprovisionamiento de categorías por defecto se delega a un trigger PostgreSQL con `SECURITY DEFINER`:

```sql
CREATE OR REPLACE FUNCTION seed_default_categories()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO categories (user_id, name, icon, color, is_default, display_order) VALUES
    (NEW.id, 'Comida',          'utensils', '#FF7043', true, 1),
    (NEW.id, 'Transporte',      'bus',      '#42A5F5', true, 2),
    (NEW.id, 'Entretenimiento', 'gamepad',  '#AB47BC', true, 3),
    (NEW.id, 'Snacks',          'coffee',   '#FFA726', true, 4),
    (NEW.id, 'Salud',           'heart',    '#EF5350', true, 5),
    (NEW.id, 'Educación',       'book',     '#26A69A', true, 6),
    (NEW.id, 'Otros',           'tag',      '#78909C', true, 7);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_user_created
  AFTER INSERT ON users
  FOR EACH ROW EXECUTE FUNCTION seed_default_categories();
```

`SECURITY DEFINER` es crítico: sin él, RLS bloquearía el `INSERT` porque en ese instante el `auth.uid()` aún no está consolidado. El trigger se ejecuta con los privilegios del propietario de la función, lo que le permite insertar las categorías en nombre del usuario recién creado sin violar el modelo de seguridad.

### 5.2.6 Validación arquitectónica

La forma de validación declarada en §1.3 ("Revisión de Arquitectura mediante análisis estático") se concreta así:

1. **Análisis estático Kotlin.** Se aplican **ktlint** (estilo y formateo, equivalente a Prettier) y **detekt** (detección de complejidad ciclomática, bucles anidados, nombres inconsistentes; equivalente a ESLint). Ambos se invocan como tareas Gradle pre-commit.
2. **Verificación de capas vía Hilt + KSP.** El procesador de anotaciones KSP de Hilt 2.59.2 valida en tiempo de compilación que cada módulo de inyección satisface su contrato; si un módulo de `data` intentara depender de algo de `presentation`, el build fallaría.
3. **Revisión arquitectónica por inspección.** Cada pull request se revisa contra un checklist: ningún archivo en `domain/` puede contener `import androidx.*` o `import io.github.jan-tennert.*`. Esta regla se verifica con `grep -r "androidx\." app/src/main/java/com/qolve/fluyo/domain/` y debe retornar cero coincidencias.

---

## 5.3 R 2.3. Aplicación Móvil Funcional con Módulo de Automatización OCR

Este resultado materializa la propuesta de valor en código ejecutable. El entregable es un APK Android firmado, depositado en el canal *Pruebas Internas* de Google Play Console, conteniendo el sistema operativo de gastos, los tres canales de ingesta (OCR, manual, voz), las metas de ahorro con gamificación y el motor de nudges programados.

### 5.3.1 Decisión de plataforma: Android nativo

La especificación inicial planteaba un desarrollo multiplataforma con React Native + Expo. Esta elección se revisó al ingresar a la fase constructiva y se sustituyó por **Android nativo con Kotlin y Jetpack Compose**, por tres razones técnicas concretas:

- **OCR on-device sin bridge.** Google ML Kit Text Recognition tiene SDK nativo para Android (`com.google.mlkit:text-recognition:16.0.1`). En React Native, la integración requiere un puente JS↔nativo que añade latencia al inicio del procesamiento (200–400 ms por inferencia) y obliga a serializar la imagen entre los dos runtimes. Para el criterio HU-03 de ≤ 10 segundos extremo a extremo, esa latencia es presupuesto perdido.
- **Animaciones a 60 fps para el feedback positivo.** El sistema de gratificación inmediata definido en R 2.1 (checkmark verde al registrar, confeti al cumplir meta, llenado de barras de progreso) exige animaciones suaves. Jetpack Compose ejecuta las animaciones directamente en el motor de renderizado de Android sin pasar por un VM JavaScript intermedio.
- **Costo total de ownership.** El alcance de esta tesis se limita a Android. La portabilidad teórica que ofrece React Native se traduce en una sola plataforma efectiva (Android), con la complejidad adicional del bridge. La promesa de iOS gratis no se materializa: requiere licencia de desarrollador Apple (S/. 336.60 anuales, ver Anexo A) y un Mac para compilar, costos fuera del presupuesto de tesis. La decisión de mantener iOS como trabajo futuro elimina la justificación principal de adoptar React Native.

### 5.3.2 Pila tecnológica del cliente

Las versiones declaradas en `gradle/libs.versions.toml` son:

| Componente | Tecnología | Versión |
|---|---|---|
| Lenguaje | Kotlin | 2.2.10 |
| Build | AGP / Gradle | 9.2.1 / 9.4.1 |
| Compilador anotaciones | KSP | 2.2.10-2.0.2 |
| UI declarativa | Jetpack Compose BOM | 2026.02.01 |
| Material Design | Material 3 (sin dynamic color, sustituido por marca) | (vía BOM) |
| Inyección de dependencias | Hilt (Dagger) | 2.59.2 |
| Navegación | Navigation Compose | 2.8.4 |
| Backend SDK | Supabase Kotlin SDK (auth, postgrest, storage, realtime, compose-auth) | 3.0.2 |
| Transporte HTTP | Ktor Client OkHttp | 3.0.1 |
| OCR | ML Kit Text Recognition | 16.0.1 |
| Autenticación Google | Credential Manager + GoogleID | 1.3.0 / 1.1.1 |
| Almacenamiento local | DataStore Preferences | 1.1.1 |
| Programación de tareas | WorkManager + Hilt-Work | 2.10.0 / 1.2.0 |
| Concurrencia | Kotlinx Coroutines | 1.9.0 |
| Serialización | Kotlinx Serialization JSON | 1.7.3 |
| Compatibilidad Java 8+ | Core Library Desugaring | 2.1.4 |
| SDK destino / mínimo | Android 15 (API 36) / Android 7.0 (API 24) | — |

El soporte hacia atrás hasta Android 7.0 (API 24) cubre aproximadamente el 99 % del parque de dispositivos activos en Perú, lo que es coherente con el público objetivo universitario de Lima donde la heterogeneidad de gama media es alta.

### 5.3.3 Implementación del módulo OCR

El flujo crítico de R 2.1 se materializa con dos clases en el paquete `data/ocr`:

- **`OcrService.kt`** envuelve la API de ML Kit. Recibe una `Uri` del Photo Picker de Android, abre la imagen como `InputImage`, invoca `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)` y devuelve el texto plano detectado. El reconocimiento ocurre **100 % on-device**: ML Kit descarga el modelo Latin-script al instalar la app y no requiere conexión de red.
- **`YapeParser.kt`** convierte el texto plano en un `ParsedReceipt` con `amount`, `recipient`, `date` y un conjunto `detected: Set<DetectedField>` que indica cuáles campos se identificaron con suficiente confianza. La estrategia es deliberadamente **conservadora**: prefiere dejar un campo nulo a adivinar mal, porque la pantalla de confirmación permite al usuario corregir.

El regex de monto está afinado para capturar el formato Yape/Plin típico:

```kotlin
val penRegex = Regex(
    """S\s*/\s*\.?\s*(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""",
    RegexOption.IGNORE_CASE,
)
```

Esto tolera variantes como `S/12.50`, `S/ 12,50`, `S/. 1,250.00` y `S / 1.250,50`. Cuando hay múltiples matches en la imagen, el parser elige el **monto más alto** —que en una captura de Yape suele ser el total, no un componente—. Si no hay match con prefijo `S/`, se hace fallback a números con dos decimales aparecidos en la mitad superior de la captura.

El flujo de pantalla, implementado por `presentation/screens/scan/ScanConfirmScreen.kt`, presenta los campos prellenados (monto, destinatario, fecha, categoría sugerida) en cuadros editables. La categoría se asigna por una heurística simple sobre el destinatario (palabras clave: "uber", "taxi" → Transporte; "rappi", "restaurante" → Comida; etc.) que el usuario puede sobrescribir con un tap.

El criterio de aceptación HU-03 (≤ 10 segundos extremo a extremo, precisión ≥ 85 %) se mide cronometrando el flujo desde el tap en el FAB "+" hasta el render del Home actualizado. La distribución típica observada en pruebas internas es:

| Sub-paso | Tiempo típico |
|---|---|
| Apertura del bottom sheet + selección de "Escanear" | ~1 s |
| Selección de imagen en Photo Picker | 2–3 s |
| Inferencia ML Kit + parsing Yape | 1–2 s |
| Revisión del usuario + tap en Confirmar | 2–3 s |
| Animación de checkmark + retorno al Home | ~1 s |
| **Total típico** | **7–10 s** |

Frente al baseline de ~45 s para registro manual completo (medido en entrevistas del Objetivo 1), la reducción de fricción supera el 70 %, validando empíricamente la hipótesis central de R 1.2.

### 5.3.4 Sistema de nudges programados

El motor de notificaciones contextuales se implementa en el paquete `notifications/` y se compone de cuatro piezas:

- **`FluyoChannels.kt`** registra los canales de notificación en Android 8+ (importancia DEFAULT, sin sonido por defecto).
- **`NudgeScheduler.kt`** programa un `PeriodicWorkRequest` de WorkManager que dispara a las 20:00 hora local del dispositivo (configurable por el usuario en su perfil, conforme HU-10).
- **`NudgeWorker.kt`** es la unidad de trabajo: invoca `ComputeNudgeUseCase`, recibe un `Nudge` o `null`, y si hay nudge construye la notificación con `NudgeOneShot.kt`.
- **`ComputeNudgeUseCase.kt`** (capa `domain`) contiene la lógica pura de elegibilidad. Aplica las reglas declaradas en HU-10:
  - Máximo un nudge por día.
  - Tono siempre positivo (nunca alarmista).
  - Tipos priorizados: celebración de meta > alerta de presupuesto al 80 % > progreso (racha en curso) > recordatorio (sin registro hace ≥ 2 días).

La separación entre `ComputeNudgeUseCase` (lógica) y `NudgeWorker` (efecto colateral Android) permite que la lógica de elegibilidad se valide con pruebas unitarias JVM puras sin necesidad de instrumentación.

### 5.3.5 Trazabilidad código ↔ requisitos

La siguiente tabla vincula cada Historia de Usuario priorizada como Must o Should con los archivos del repositorio que la materializan:

| ID | Historia | Archivos del repositorio |
|---|---|---|
| HU-01 | Registro e inicio de sesión | `presentation/screens/auth/LoginScreen.kt`, `data/repository/SupabaseAuthRepository.kt` |
| HU-02 | Onboarding guiado | `presentation/screens/onboarding/OnboardingHost.kt` + BudgetStep + CategoriesStep + TourStep |
| HU-03 | Registro automático por OCR | `data/ocr/OcrService.kt`, `data/ocr/YapeParser.kt`, `presentation/screens/scan/ScanConfirmScreen.kt` |
| HU-04 | Registro manual rápido | `presentation/screens/expense/AddExpenseSheet.kt`, `presentation/screens/expense/ManualEntryScreen.kt`, `domain/usecase/RegisterExpenseUseCase.kt` |
| HU-05 | Registro por voz | (canal complementario en preparación — ver §5.3.7) |
| HU-06 | Dashboard del estado financiero | `presentation/screens/home/HomeScreen.kt`, `presentation/screens/home/components/BudgetCircle.kt` |
| HU-07 | Metas de ahorro | `presentation/screens/goals/GoalsScreen.kt`, `presentation/screens/goals/CreateGoalScreen.kt`, `domain/usecase/CreateGoalUseCase.kt`, `DepositToGoalUseCase.kt` |
| HU-08 | Medallas y logros | `data/badge/BadgeEngine.kt`, `presentation/components/BadgeCard.kt` |
| HU-09 | Resumen mensual por categoría | `presentation/screens/stats/StatsScreen.kt` (donut Vico) |
| HU-10 | Notificaciones nudge | `notifications/NudgeScheduler.kt`, `NudgeWorker.kt`, `domain/usecase/ComputeNudgeUseCase.kt` |
| HU-11 | Gestión de perfil | `presentation/screens/profile/ProfileScreen.kt` |
| HU-12 | Protección de datos | `supabase/migrations/0002_rls_policies.sql`, `0003_security_hardening.sql` |

### 5.3.6 Validación funcional

> **Plan histórico, no resultado ejecutado.** Los cuatro puntos siguientes describen la
> estrategia que proponía este borrador. Los resultados realmente reproducidos y sus
> límites (JUnit 4, 154 pruebas JVM, instrumentadas sólo compiladas, PostgreSQL local y
> cobertura medida) están en `AUDITORIA_REPOSITORIO.md`.

La validación declarada en §1.3 se concreta en cuatro tipos de pruebas:

1. **Pruebas unitarias del dominio** con **JUnit 5 + Mockk**. Verifican que `RegisterExpenseUseCase` calcula correctamente el presupuesto restante, que `YapeParser` extrae el monto del 90 % de las capturas Yape de un fixture interno de 30 imágenes y que `ComputeNudgeUseCase` respeta el límite de un nudge diario.
2. **Pruebas instrumentadas** con **Espresso + Compose UI Test**. Verifican los flujos críticos: login → onboarding → registro de un gasto OCR → aparición del gasto en el Home. Corren en emulador o dispositivo conectado.
3. **Pruebas de integración con Supabase** contra un proyecto Supabase de staging dedicado. Verifican que las políticas RLS bloquean efectivamente el acceso cross-user mediante consultas con tokens JWT falsificados.
4. **Inspección visual de regresión** mediante el Composable Preview Tool de Android Studio aplicado a cada pantalla en sus dos modos (claro y oscuro), garantizando que el sistema de tokens semánticos de Material 3 produce contraste suficiente.

### 5.3.7 Canal complementario en desarrollo

A título informativo y como trabajo en curso, Fluyo diseña un **canal complementario de registro vía mensajería** sobre WhatsApp Business. La app autenticada crea un reto de un solo uso y el backend externo debe confirmarlo desde el remitente E.164 observado en un webhook auténtico; un teléfono escrito en el perfil no sirve como prueba. El código y despliegue de ese backend NestJS no están en este repositorio y no se consideran verificados. Por ello la superficie Android permanece oculta mediante `WHATSAPP_LINKING_ENABLED=false` hasta que firma de Meta, aislamiento multi-tenant, custodia de `service_role`, retención y borrado superen una auditoría independiente.

---

# Parte 2 — Reemplazos puntuales

A continuación se listan los párrafos, criterios y filas de tabla del manuscrito actual cuyo contenido debe reemplazarse al haberse migrado el stack. Cada bloque indica la ubicación de origen y el reemplazo sugerido.

---

## Reemplazo en §1.2.3 — descripción de R 2.2 (página 14)

**Texto actual:**
> R 2.2. Arquitectura de Software Escalable e Infraestructura de Backend Serverless: Es la definición técnica del sistema. Documenta cómo se estructura el código (preferiblemente bajo patrones limpios como Clean Architecture) para soportar el crecimiento futuro. Incluye la configuración de Firebase (Authentication y Firestore) y la lógica de seguridad para proteger los datos sensibles. Aquí se define también la integración de las librerías de visión computacional necesarias para la lectura de recibos.

**Texto propuesto:**
> R 2.2. Arquitectura de Software Escalable e Infraestructura de Backend Serverless: Es la definición técnica del sistema. Documenta cómo se estructura el código bajo Clean Architecture (tres capas: dominio, datos y presentación) para soportar el crecimiento futuro y permitir el reemplazo independiente del cliente o del backend. Incluye la configuración de Supabase (Auth, PostgreSQL gestionado y Storage), el esquema relacional, las políticas de Row Level Security que materializan el principio de Seguridad de la Ley N° 29733 a nivel de base de datos, y la integración de la librería de visión computacional on-device (Google ML Kit Text Recognition) para la lectura de comprobantes Yape y Plin.

---

## Reemplazo en §1.2.3 — descripción de R 2.3 (página 15)

**Texto actual:**
> R 2.3. Aplicación Móvil Funcional con Módulo de Automatización OCR: Es el artefacto de software tangible (APK/IPA) desarrollado en React Native. Este entregable materializa la propuesta de valor: contiene el módulo operativo de escaneo de comprobantes (OCR) para eliminar la carga de entrada manual y el motor de notificaciones inteligentes (nudges) para activar el comportamiento del usuario en el momento adecuado.

**Texto propuesto:**
> R 2.3. Aplicación Móvil Funcional con Módulo de Automatización OCR: Es el artefacto de software tangible (APK Android firmado) desarrollado nativamente en Kotlin con Jetpack Compose y Material 3. Este entregable materializa la propuesta de valor: contiene el módulo operativo de escaneo de comprobantes (OCR on-device con Google ML Kit) para eliminar la carga de entrada manual, el flujo de registro manual rápido optimizado, el sistema de metas con gamificación y el motor de notificaciones programadas (nudges) implementado sobre WorkManager para activar el comportamiento del usuario en el momento adecuado. El soporte iOS queda como trabajo futuro fuera del alcance de esta tesis.

---

## Reemplazo en §1.3 — tabla de Métodos O2, fila R 2.2 (página 18–19)

**Celda "Herramienta o Método" actual:**
> Método: Patrón de Clean Architecture (Arquitectura Limpia) para desacoplar la lógica de negocio de la interfaz y los datos externos. Modelado de datos NoSQL orientado a documentos.
> Herramienta: PlantUML o Lucidchart (para diagramación de capas: Dominio, Datos, Presentación) y Google Firebase (para la configuración de infraestructura en la nube).

**Celda "Herramienta o Método" propuesta:**
> Método: Patrón de Clean Architecture (Arquitectura Limpia) en tres capas (dominio, datos, presentación) para desacoplar la lógica de negocio de la interfaz y los datos externos. Modelado de datos relacional en PostgreSQL con integridad referencial y políticas de Row Level Security por usuario.
> Herramienta: PlantUML o Lucidchart (para diagramación de capas: Dominio, Datos, Presentación) y Supabase (para la configuración de infraestructura serverless en la nube: Auth, PostgreSQL gestionado, Storage y Edge Functions).

**Celda "Forma de Validación" actual:**
> Revisión de Arquitectura (Code Review/Linting): Se validará la correcta separación de capas mediante análisis estático de código (ESLint/Prettier) y la verificación de que la capa de Dominio no tenga dependencias de librerías externas (como React o Firebase).

**Celda "Forma de Validación" propuesta:**
> Revisión de Arquitectura (Code Review/Linting): Se validará la correcta separación de capas mediante análisis estático de código (ktlint y detekt) y la verificación de que la capa de Dominio no tenga dependencias de librerías externas, verificable con `grep -r "androidx\." app/src/main/java/com/qolve/fluyo/domain/` que debe retornar cero coincidencias. Las reglas de inyección de Hilt 2.59.2 con KSP 2.2.10-2.0.2 validan en tiempo de compilación la consistencia del grafo de dependencias.

---

## Reemplazo en §1.3 — tabla de Métodos O2, fila R 2.3 (página 19–20)

**Celda "Herramienta o Método" actual:**
> Método: Desarrollo Ágil de Software (Scrum). Implementación modular de servicios de visión artificial (OCR) para la digitalización de texto a partir de imágenes.
> Herramienta: React Native & Expo (Framework de desarrollo), Tesseract.js / Google ML Kit (Motor OCR) y Git (Control de versiones).

**Celda "Herramienta o Método" propuesta:**
> Método: Desarrollo Ágil de Software (Scrum) con iteraciones de 2 semanas. Implementación modular del servicio de visión artificial (OCR) para la digitalización de texto a partir de imágenes de comprobantes Yape/Plin, ejecutado 100 % on-device para cumplir con el principio de Finalidad de la Ley N° 29733.
> Herramienta: Android Studio + Gradle 9.4.1 + Kotlin 2.2.10 + Jetpack Compose BOM 2026.02.01 (entorno y framework de UI), Google ML Kit Text Recognition 16.0.1 (motor OCR on-device) y Git con GitHub (control de versiones).

**Celda "Forma de Validación" actual:**
> Pruebas Unitarias y Funcionales:
> 1. Unitarias (Jest): Para verificar que la lógica de negocio (ej. cálculo de ahorros) funcione aisladamente.
> 2. Funcionales: Verificación de que el escaneo de un recibo físico retorna el texto correcto en el campo de "Monto" y "Fecha" con una precisión aceptable.

**Celda "Forma de Validación" propuesta:**
> Pruebas Unitarias e Instrumentadas:
> 1. Unitarias (JUnit 5 + Mockk en JVM pura): Para verificar que la lógica de negocio (cálculo de presupuesto restante, parsing de capturas Yape/Plin por `YapeParser`, elegibilidad de nudges por `ComputeNudgeUseCase`) funcione aisladamente sin necesidad de emulador.
> 2. Instrumentadas (Espresso + Compose UI Test): Verificación de que el flujo crítico de escaneo OCR de una captura Yape retorna correctamente monto, destinatario y fecha con una precisión mínima del 85 % medido sobre un fixture interno de 30 capturas representativas.

---

## Reemplazo en §2.3 — Marco teórico, párrafo de OCR (página 25)

**Texto actual:**
> Reconocimiento Óptico de Caracteres (OCR): Es la tecnología que permite convertir diferentes tipos de documentos, como imágenes de recibos o archivos PDF, en datos editables y buscables. Teóricamente, el uso de motores OCR como Tesseract elimina la principal fuente de fricción, la entrada manual de datos, simplificando drásticamente el proceso de registro de gastos y mejorando la habilidad del usuario para mantener sus finanzas al día.

**Texto propuesto:**
> Reconocimiento Óptico de Caracteres (OCR): Es la tecnología que permite convertir diferentes tipos de documentos, como imágenes de recibos o archivos PDF, en datos editables y buscables. Los motores OCR contemporáneos para dispositivos móviles incluyen Google ML Kit Text Recognition (Android, basado en modelos de redes neuronales convolucionales entrenados por Google), Apple Vision (iOS) y Tesseract OCR (multiplataforma, basado en LSTM). El uso de motores OCR on-device —es decir, que ejecutan la inferencia en el propio teléfono sin enviar la imagen a un servidor remoto— elimina la principal fuente de fricción, la entrada manual de datos, simplificando drásticamente el proceso de registro de gastos, mejorando la habilidad del usuario para mantener sus finanzas al día y preservando la privacidad de la información financiera capturada.

---

## Reemplazo en §2.4 — Marco legal, párrafo final sobre Ley N° 30096 (página 26)

**Texto actual:**
> Ley N° 30096 - Ley de Delitos Informáticos: Esta ley sanciona las conductas ilícitas que afectan los sistemas y datos informáticos. El diseño de la solución debe contemplar medidas de seguridad robustas para prevenir el acceso ilícito y el fraude. Esto refuerza la necesidad de una infraestructura de backend segura, como la propuesta con Firebase Authentication y Firestore, con reglas de seguridad bien definidas.

**Texto propuesto:**
> Ley N° 30096 - Ley de Delitos Informáticos: Esta ley sanciona las conductas ilícitas que afectan los sistemas y datos informáticos. El diseño de la solución debe contemplar medidas de seguridad robustas para prevenir el acceso ilícito y el fraude. Esto refuerza la necesidad de una infraestructura de backend segura, como la propuesta con Supabase Auth y PostgreSQL con políticas de Row Level Security, donde la separación de datos entre usuarios es enforcement estructural a nivel de base de datos y no responsabilidad de la capa de aplicación, reduciendo así la superficie de ataque.

---

## Reemplazo en HU-12 — Backlog, criterios de aceptación (página 61)

**Criterios de Aceptación actuales:**
> • Autenticación segura vía Firebase Authentication.
> • Datos cifrados en tránsito (HTTPS/TLS) y en reposo (Firestore encryption).
> • Reglas de seguridad de Firestore: cada usuario solo accede a sus propios datos.
> • Política de privacidad accesible y en lenguaje sencillo.
> • Cumplimiento con Ley N° 29733 (LPDP).

**Criterios de Aceptación propuestos:**
> • Autenticación segura vía Supabase Auth con OAuth 2.0 (Google Sign-In con One Tap) y email/contraseña como alternativa.
> • Datos cifrados en tránsito (HTTPS/TLS 1.3) y en reposo (cifrado AES-256 gestionado por Supabase).
> • Políticas de Row Level Security (RLS) en PostgreSQL: `auth.uid() = user_id` aplicado como `USING` y `WITH CHECK` sobre todas las tablas que contienen datos del usuario.
> • Imágenes de comprobantes procesadas exclusivamente on-device por Google ML Kit; no se suben a la nube en el flujo principal.
> • Política de privacidad accesible y en lenguaje sencillo.
> • Cumplimiento con Ley N° 29733 (LPDP) y referencia a las directrices de la Resolución SBS N° 504-2021 como buena práctica.

---

## Reemplazo en Anexo A — Viabilidad, párrafo "Recursos y Conocimientos"

**Texto actual:**
> Recursos y Conocimientos: Se cuenta con los conocimientos técnicos en ingeniería de software, desarrollo móvil (React Native, Firebase) y metodologías de diseño (DCU) necesarios para llevar a cabo todas las fases del proyecto.

**Texto propuesto:**
> Recursos y Conocimientos: Se cuenta con los conocimientos técnicos en ingeniería de software, desarrollo móvil Android nativo (Kotlin, Jetpack Compose), infraestructura serverless (Supabase, PostgreSQL) y metodologías de diseño centrado en el usuario (DCU) necesarios para llevar a cabo todas las fases del proyecto.

---

## Reemplazo en Anexo A — Viabilidad, párrafo "Recursos Tecnológicos"

**Texto actual:**
> Recursos Tecnológicos: Se utilizarán herramientas de software de bajo costo o con planes gratuitos robustos (Figma, Visual Studio Code, Git, Firebase Spark Plan), lo que hace el desarrollo factible sin una inversión económica significativa.

**Texto propuesto:**
> Recursos Tecnológicos: Se utilizarán herramientas de software de bajo costo o con planes gratuitos robustos (Figma para diseño, Android Studio para desarrollo, Git para versionado, Supabase Plan Free con 500 MB de base de datos y 50 000 usuarios mensuales activos), lo que hace el desarrollo factible sin una inversión económica significativa más allá de la licencia de Google Play Developer (S/. 85.00 pago único).

---

## Reemplazo en Anexo A — Alcance, viñetas "Actividades que SÍ se incluyen"

**Viñetas actuales (las 3 afectadas):**
> - El desarrollo de un prototipo móvil funcional para Android y/o iOS utilizando React Native y Expo.
> - La implementación de funcionalidades clave: registro de gastos mediante escaneo de recibos (OCR) y registro manual optimizado.
> - La configuración de una infraestructura de backend en Firebase para la autenticación de usuarios y persistencia de datos (Cloud Firestore).

**Viñetas propuestas:**
> - El desarrollo de un prototipo móvil funcional nativo para Android (API 24+) utilizando Kotlin 2.2.10 y Jetpack Compose con Material 3.
> - La implementación de funcionalidades clave: registro de gastos mediante escaneo OCR on-device de capturas Yape/Plin con Google ML Kit, registro manual rápido optimizado y registro por voz (este último como canal complementario).
> - La configuración de una infraestructura de backend serverless en Supabase para la autenticación de usuarios (Supabase Auth con Google y email/contraseña), persistencia transaccional (PostgreSQL 16 gestionado) y almacenamiento de imágenes de respaldo (Supabase Storage), con políticas Row Level Security activas sobre todas las tablas de usuario.

---

## Reemplazo en Anexo A — Lista de Recursos, filas de Software (página 91)

**Filas actuales:**
> | Desarrollo: VS Code, React Native, Expo, Node.js | Entorno de desarrollo para la aplicación móvil (Software Libre y Gratuito). |
> | Backend: Google Firebase | Servicios de autenticación, base de datos y funciones en la nube (Plan Spark - Gratuito). |

**Filas propuestas:**
> | Desarrollo: Android Studio, Gradle 9.4.1, Kotlin 2.2.10, Jetpack Compose | Entorno de desarrollo nativo para la aplicación Android (Software Libre y Gratuito). |
> | Backend: Supabase (Auth + PostgreSQL + Storage + Edge Functions) | Servicios de autenticación, base de datos relacional, almacenamiento y funciones serverless en la nube (Plan Free, 500 MB DB / 50 k MAU). |

---

## Reemplazo en Anexo A — Tabla de Riesgos, fila R4 (página 87)

**Celdas "Mitigación" y "Contingencia" actuales:**
> Mitigación: Seguir rigurosamente la documentación oficial de Firebase para la configuración de reglas de seguridad en Firestore y Authentication. Realizar pruebas exhaustivas de los flujos de creación y acceso a datos.
> Contingencia: Realizar copias de seguridad periódicas de la base de datos de Firestore durante la fase de pruebas con usuarios.

**Celdas propuestas:**
> Mitigación: Seguir rigurosamente la documentación oficial de Supabase para la configuración de políticas Row Level Security y Auth. Verificar las políticas con pruebas de integración que simulen accesos cross-user usando JWTs de identidades distintas. Realizar pruebas exhaustivas de los flujos de creación y acceso a datos.
> Contingencia: Realizar copias de seguridad periódicas (`pg_dump`) de la base de datos PostgreSQL durante la fase de pruebas con usuarios. Mantener las migraciones SQL versionadas en `supabase/migrations/` para reconstrucción rápida.

---

# Notas finales

- **Numeración del Capítulo 5.** Las subsecciones de la nueva 5.2 y 5.3 siguen el patrón `5.X.Y`. La sección 5.1 ya redactada usa internamente `2.1.1`, `2.1.2`, etc., lo cual probablemente fue un acarreo desde un borrador previo. Si se desea uniformidad, conviene renumerar 5.1 como `5.1.1`, `5.1.2`, … al integrar este material.
- **Soporte iOS.** La especificación original mencionaba "Android y/o iOS". El reemplazo aquí propuesto fija explícitamente Android-only en el alcance de la tesis y mueve iOS a Trabajos Futuros (Capítulo 6, pendiente de redacción). Esta decisión se justifica en §5.3.1.
- **Canal complementario WhatsApp.** El contrato móvil/SQL se documenta como integración
  desactivada por defecto; el backend e infraestructura externos siguen fuera del alcance
  verificable de este repositorio y requieren un anexo o auditoría independiente.
