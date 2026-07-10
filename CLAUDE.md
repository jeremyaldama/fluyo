# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Fluyo** is a personal finance management platform with two entry points:

1. **Native Android app** (Kotlin + Jetpack Compose) — Primary interface for tracking expenses via OCR scanning, manual entry, and voice. Includes gamification, savings goals, and behavioral nudges.
2. **WhatsApp bot** (via existing NestJS backend) — Secondary interface where users can text or send voice messages to register expenses conversationally (e.g., "Gasté 15 soles en almuerzo").

Both interfaces write to the **same Supabase PostgreSQL database**, ensuring a single source of truth regardless of how the user registers an expense.

This is a thesis project for the Pontificia Universidad Católica del Perú (PUCP), Computer Science Engineering degree. The app is designed using User-Centered Design (UCD) methodology.

**Target users:** University students aged 18-26 in Lima, Peru, who use Yape/Plin daily and struggle to track expenses due to the tedium of manual registration.

---

## Build, Test & Run Commands

All commands run from the repo root via the Gradle wrapper (`./gradlew`). The single module is `:app`.

```bash
# Build
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/
./gradlew :app:bundleRelease          # release AAB (needs RELEASE_KEYSTORE_* in local.properties)
./gradlew clean                       # wipe build outputs

# Install / run on a connected device or emulator
./gradlew :app:installDebug
adb shell am start -n com.qolve.fluyo/.MainActivity

# Lint & static analysis
./gradlew :app:lintDebug              # Android Lint → app/build/reports/lint-results-debug.html

# Tests
./gradlew :app:testDebugUnitTest      # JVM unit tests (src/test) — JUnit + Mockk + coroutines-test
./gradlew :app:connectedDebugAndroidTest   # instrumented/Compose tests (src/androidTest, needs device)

# Run a single unit test class or method
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.ExampleUnitTest"
./gradlew :app:testDebugUnitTest --tests "com.qolve.fluyo.SomeClass.someMethod"
```

> Unit tests use **JUnit 4 + MockK + coroutines-test** (`runTest`). `src/test` covers the pure-Kotlin logic: `VoiceParserTest`, `MoneyTest`, `GoalTest` (domain math), `CreateGoalUseCaseTest` (fake-repo delegation), and `StartRouteReducerTest` (auth→route mapping). `src/androidTest` still holds only the generated `ExampleInstrumentedTest`; Compose UI tests (Phase 7) are not written yet. A `scripts/smoke-test.sh` installs the debug APK on a running emulator, launches `MainActivity`, screenshots it, and fails on a missing foreground activity or a logcat crash.

## Local Configuration

`app/build.gradle.kts` reads secrets from `local.properties` (gitignored) and exposes them as `BuildConfig` fields. Without these, auth and the Supabase client won't work:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_ANON_KEY=<anon_key>
GOOGLE_WEB_CLIENT_ID=<oauth_web_client_id>   # for Credential Manager / Google One Tap
# Release signing (optional; only needed for bundleRelease):
RELEASE_KEYSTORE_PATH=...
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog) — add/upgrade libraries there, not inline in `app/build.gradle.kts`.

## Database / Supabase

- Schema lives as ordered SQL migrations in `supabase/migrations/` (`0001_initial_schema.sql`, `0002_rls_policies.sql`, `0003_security_hardening.sql`, `0004_category_ondelete_setnull.sql` — makes `expenses.category_id` FK `ON DELETE SET NULL`) — these, not the snippets in this doc, are the source of truth for the live schema.
- The Supabase MCP server is configured in `.mcp.json` (project ref `fxbrxfsyxmzadyonhaoj`); use the `mcp__supabase__*` tools to inspect tables, apply migrations, and check advisors before/after schema changes.

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        SUPABASE (Cloud)                         │
│  ┌──────────┐  ┌──────────┐  ┌─────────┐  ┌────────────────┐  │
│  │ Supabase │  │PostgreSQL│  │ Storage │  │ Edge Functions │  │
│  │   Auth   │  │ Database │  │ (Images)│  │(Badge calc,    │  │
│  │          │  │          │  │         │  │ nudge schedule)│  │
│  └────┬─────┘  └────┬─────┘  └────┬────┘  └────────────────┘  │
│       │              │             │                            │
└───────┼──────────────┼─────────────┼────────────────────────────┘
        │              │             │
        │         ┌────┴─────┐       │
        │         │          │       │
   ┌────┴────┐    │          │  ┌────┴────┐
   │ Android │    │          │  │ Android │
   │   App   │────┘          │  │   App   │
   │(Compose)│               │  │ (OCR)   │
   └─────────┘               │  └─────────┘
                              │
                    ┌─────────┴──────────┐
                    │  NestJS Backend     │
                    │  (DigitalOcean VPS) │
                    │                    │
                    │  ┌──────────────┐  │
                    │  │ WhatsApp     │  │
                    │  │ Cloud API    │  │
                    │  └──────────────┘  │
                    │                    │
                    │  ┌──────────────┐  │
                    │  │ Voice-to-Text│  │
                    │  │ (Whisper API)│  │
                    │  └──────────────┘  │
                    └────────────────────┘
```

### How Data Flows

**Android app user registers expense:**

1. User opens app → Supabase Auth session
2. Scans Yape receipt (ML Kit OCR on-device) → extracts amount, recipient, date
3. App writes expense directly to Supabase PostgreSQL via Supabase Kotlin SDK
4. Budget recalculates locally, UI updates

**WhatsApp user registers expense:**

1. User sends WhatsApp message: "Gasté 20 soles en taxi" or sends voice note
2. WhatsApp Cloud API delivers webhook to NestJS backend (DigitalOcean)
3. If voice: NestJS sends audio to Whisper API → gets transcription
4. NestJS parses text (extract amount, category, description)
5. NestJS writes expense to same Supabase PostgreSQL (via connection string)
6. NestJS replies via WhatsApp: "✅ Registré S/ 20.00 en Transporte"

**User linking:** WhatsApp users are linked to their Android account via phone number. When a user signs up on Android, they optionally provide their phone number. The NestJS backend matches incoming WhatsApp messages by phone number to find the corresponding user_id in Supabase.

---

## Tech Stack

### Android App

| Component    | Technology                       | Notes                                                          |
| ------------ | -------------------------------- | -------------------------------------------------------------- |
| Language     | Kotlin 2.2.10                    | All code in Kotlin                                             |
| Min SDK      | 24 (Android 7.0)                 | Target SDK / compileSdk: 36 (Android 15)                       |
| UI           | Jetpack Compose (BOM 2026.02.01) | Material 3, declarative, dynamic color disabled for brand      |
| Architecture | Clean Architecture + MVVM        | Domain layer has ZERO Android dependencies                     |
| Navigation   | Jetpack Navigation Compose       | Bottom nav with 4 tabs                                         |
| State        | ViewModel + StateFlow            | Unidirectional data flow                                       |
| DI           | Hilt (Dagger) + KSP              | Constructor injection                                          |
| Backend      | Supabase Kotlin SDK (supabase-kt)| Auth, Postgrest, Storage; Ktor OkHttp transport                |
| Auth         | Credential Manager + Google ID   | Modern One Tap flow; supabase-kt Compose Auth integration      |
| OCR          | Google ML Kit Text Recognition   | On-device, no server upload                                    |
| Charts       | Vico                             | Donut chart for categories (chosen for Compose-native API)     |
| Testing      | JUnit 4 + Espresso + Mockk       | Domain unit tests (src/test); Compose/UI tests pending         |
| Build        | Gradle 9.4.1 (Kotlin DSL) + AGP 9.2.1 | Version catalog (`gradle/libs.versions.toml`)             |
| App ID       | `com.qolve.fluyo`                | Namespace + applicationId                                       |

### WhatsApp Backend (existing — `whatsapp-bot-be`)

**This is a mature, in-production, multi-tenant WhatsApp Business API platform.** It currently serves Tecnigas (auto-repair), MIT/PUCP (university), Qolve Consulting (leads), and PrestaIA (lending). Fluyo will be added as a **new tenant + plugin** (`src/plugins/fluyo/`), it does NOT replace existing tenants.

| Component         | Technology                          | Notes                                                                       |
| ----------------- | ----------------------------------- | --------------------------------------------------------------------------- |
| Runtime           | Node.js + NestJS 11.1.14            | Deployed on DigitalOcean VPS, containerized (Docker + nginx TLS)            |
| Database (current)| Self-hosted PostgreSQL 16 + TypeORM | Owns: `tenants`, `contacts`, `conversations`, `messages`, `agents`, `users` |
| Database (Fluyo)  | Supabase PostgreSQL (separate)      | Fluyo plugin writes to Supabase via `@supabase/supabase-js` (service role)  |
| Cache / sessions  | Redis (ioredis)                     | Webhook config cache, conversation state                                    |
| Media             | AWS S3 (`@aws-sdk/client-s3`)       | Receipt images, voice notes                                                 |
| WhatsApp          | WhatsApp Cloud API                  | Webhook (`/webhook` GET verify + POST receive); multi-app via `phone_number_id` |
| AI orchestration  | OpenAI function calling (gpt-4o-mini)| Tools dispatched via `PluginRegistry` per tenant `business_type`           |
| Voice (Fluyo)     | OpenAI Whisper API                  | **Not yet integrated** — Phase 4 adds this for voice-note expense entries  |
| Text parsing      | OpenAI function-calling tools       | Each plugin exposes its own tools (e.g. `register_expense`)                |
| Auth (API)        | JWT + API key (Bearer)              | `@TenantId` decorator, `TenantContextInterceptor`                          |

**Architecture (plugin-based):**

```
src/plugins/{auto-repair, university, qolve-consulting, prestaia, fluyo}/
  └─ implements `BusinessPlugin` interface (plugin.interface.ts)
src/ai-orchestration/
  └─ ToolDispatchService routes OpenAI function calls → correct plugin
src/whatsapp-core/webhook/
  └─ WebhookConfigLoaderMiddleware resolves tenant by phone_number_id
src/common/
  └─ Guards (ApiKey, JWT, Roles), TenantContextInterceptor
```

### Shared Infrastructure (Supabase)

| Service                  | Usage                                                         |
| ------------------------ | ------------------------------------------------------------- |
| Supabase Auth            | Android app authentication (Google, Email/Password)           |
| PostgreSQL               | Single database for all data (expenses, goals, badges, users) |
| Supabase Storage         | Receipt images (temporary, for OCR fallback)                  |
| Supabase Edge Functions  | Badge calculation, nudge scheduling                           |
| Supabase Realtime        | Optional: live sync between Android and WhatsApp entries      |
| Row Level Security (RLS) | Each user can only access their own data                      |

---

## Database Schema (Supabase PostgreSQL)

### Row Level Security (RLS)

```sql
-- Users can only access own data
CREATE POLICY "Users access own expenses"
ON expenses FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- NestJS uses service_role key which bypasses RLS
-- This allows writing on behalf of WhatsApp users
```

### Tables

```sql
-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_id UUID UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  email TEXT,
  display_name TEXT,
  phone_number TEXT UNIQUE,          -- Links WhatsApp to Android account
  monthly_budget DECIMAL(10,2) DEFAULT 0,
  currency TEXT DEFAULT 'PEN',
  level INTEGER DEFAULT 1,
  total_points INTEGER DEFAULT 0,
  notification_enabled BOOLEAN DEFAULT true,
  notification_hour INTEGER DEFAULT 20,
  notification_types TEXT[] DEFAULT ARRAY['progress', 'reminder', 'budget', 'goal'],
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- CATEGORIES
-- ============================================================
CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  icon TEXT NOT NULL,
  color TEXT NOT NULL,
  is_default BOOLEAN DEFAULT false,
  display_order INTEGER DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- EXPENSES
-- ============================================================
CREATE TABLE expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  amount DECIMAL(10,2) NOT NULL,
  category_id UUID REFERENCES categories(id),
  description TEXT,
  expense_date DATE DEFAULT CURRENT_DATE,
  source TEXT NOT NULL CHECK (source IN ('manual', 'ocr', 'voice', 'whatsapp')),
  recipient TEXT,
  image_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_expenses_user_date ON expenses(user_id, expense_date DESC);
CREATE INDEX idx_expenses_user_month ON expenses(user_id, DATE_TRUNC('month', expense_date));

-- ============================================================
-- GOALS
-- ============================================================
CREATE TABLE goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  target_amount DECIMAL(10,2) NOT NULL,
  current_amount DECIMAL(10,2) DEFAULT 0,
  deadline DATE,
  status TEXT DEFAULT 'active' CHECK (status IN ('active', 'completed')),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  completed_at TIMESTAMPTZ
);

-- ============================================================
-- BADGES
-- ============================================================
CREATE TABLE badges (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  badge_type TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT,
  criteria TEXT,
  unlocked_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(user_id, badge_type)
);

-- ============================================================
-- GOAL DEPOSITS
-- ============================================================
CREATE TABLE goal_deposits (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  goal_id UUID REFERENCES goals(id) ON DELETE CASCADE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  amount DECIMAL(10,2) NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- VIEWS
-- ============================================================
CREATE VIEW monthly_category_summary AS
SELECT
  user_id,
  DATE_TRUNC('month', expense_date) AS month,
  category_id,
  c.name AS category_name,
  c.color AS category_color,
  SUM(amount) AS total,
  COUNT(*) AS transaction_count
FROM expenses e
JOIN categories c ON e.category_id = c.id
GROUP BY user_id, DATE_TRUNC('month', expense_date), category_id, c.name, c.color;

CREATE VIEW current_month_budget AS
SELECT
  u.id AS user_id,
  u.monthly_budget,
  COALESCE(SUM(e.amount), 0) AS total_spent,
  u.monthly_budget - COALESCE(SUM(e.amount), 0) AS remaining
FROM users u
LEFT JOIN expenses e ON e.user_id = u.id
  AND DATE_TRUNC('month', e.expense_date) = DATE_TRUNC('month', CURRENT_DATE)
GROUP BY u.id, u.monthly_budget;

-- ============================================================
-- AUTO-SEED DEFAULT CATEGORIES ON USER CREATION
-- ============================================================
CREATE OR REPLACE FUNCTION seed_default_categories()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO categories (user_id, name, icon, color, is_default, display_order) VALUES
    (NEW.id, 'Comida', 'utensils', '#FF7043', true, 1),
    (NEW.id, 'Transporte', 'bus', '#42A5F5', true, 2),
    (NEW.id, 'Entretenimiento', 'gamepad', '#AB47BC', true, 3),
    (NEW.id, 'Snacks', 'coffee', '#FFA726', true, 4),
    (NEW.id, 'Salud', 'heart', '#EF5350', true, 5),
    (NEW.id, 'Educación', 'book', '#26A69A', true, 6),
    (NEW.id, 'Otros', 'tag', '#78909C', true, 7);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER on_user_created
  AFTER INSERT ON users
  FOR EACH ROW EXECUTE FUNCTION seed_default_categories();
```

---

## Android Project Structure

```
app/src/main/java/com/qolve/fluyo/
├── di/
│   ├── AppModule.kt
│   ├── SupabaseModule.kt
│   └── RepositoryModule.kt
├── domain/                          # PURE KOTLIN — NO Android imports
│   ├── model/
│   │   ├── User.kt
│   │   ├── Expense.kt
│   │   ├── Goal.kt
│   │   ├── GoalDeposit.kt
│   │   ├── Badge.kt
│   │   ├── Category.kt
│   │   ├── ExpenseSource.kt         # enum: MANUAL, OCR, VOICE, WHATSAPP
│   │   └── MonthlyBreakdown.kt
│   ├── repository/
│   │   ├── ExpenseRepository.kt
│   │   ├── GoalRepository.kt
│   │   ├── BadgeRepository.kt
│   │   ├── CategoryRepository.kt
│   │   └── AuthRepository.kt
│   └── usecase/
│       ├── RegisterExpenseUseCase.kt
│       ├── GetExpensesUseCase.kt
│       ├── GetMonthlyBreakdownUseCase.kt
│       ├── CalculateRemainingBudgetUseCase.kt
│       ├── CreateGoalUseCase.kt
│       ├── DepositToGoalUseCase.kt
│       ├── CheckBadgeUnlockUseCase.kt
│       └── GetUserLevelUseCase.kt
├── data/
│   ├── repository/
│   │   ├── SupabaseExpenseRepository.kt
│   │   ├── SupabaseGoalRepository.kt
│   │   ├── SupabaseBadgeRepository.kt
│   │   ├── SupabaseCategoryRepository.kt
│   │   └── SupabaseAuthRepository.kt
│   ├── dto/
│   │   ├── ExpenseDto.kt
│   │   ├── GoalDto.kt
│   │   ├── BadgeDto.kt
│   │   └── CategoryDto.kt
│   ├── mapper/
│   │   ├── ExpenseMapper.kt
│   │   ├── GoalMapper.kt
│   │   └── BadgeMapper.kt
│   └── ocr/
│       └── OcrService.kt
└── presentation/
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   ├── Theme.kt
    │   └── Shape.kt
    ├── navigation/
    │   ├── FluyoNavHost.kt
    │   ├── Screen.kt
    │   └── BottomNavBar.kt
    ├── screens/
    │   ├── onboarding/
    │   ├── auth/
    │   ├── home/
    │   │   └── components/
    │   ├── expense/
    │   ├── stats/
    │   ├── goals/
    │   └── profile/
    └── components/
        ├── FluyoButton.kt
        ├── CategoryIcon.kt
        ├── ProgressBar.kt
        ├── BadgeCard.kt
        ├── ConfettiAnimation.kt
        └── CheckmarkAnimation.kt
```

> The tree above is the target layout; the actual code has drifted. Notable real additions not shown: `notifications/` (`FluyoChannels`, `NudgeScheduler`, `NudgeWorker`, `NudgeOneShot` — WorkManager-driven nudges), `data/local/` (`NudgePrefs`, `OnboardingPrefs` — DataStore), `data/badge/BadgeEngine.kt`, `presentation/icons/`, `presentation/events/` + `screens/scan/` (OCR via system Share sheet — see `MainActivity` launchMode and `SharedImageEvents`), and `presentation/util/`. The `domain/usecase/` package currently holds only `RegisterExpenseUseCase`, `CreateGoalUseCase`, `DepositToGoalUseCase`, and `ComputeNudgeUseCase`; the rest of the listed use cases are not yet implemented. Verify against the filesystem before relying on a path.

---

## Features & User Flows

### Flow 1: OCR Expense Registration (CRITICAL)

1. FAB "+" → bottom sheet: "Escanear captura" (primary), "Manual", "Voz"
2. "Escanear captura" → image picker (gallery/camera)
3. Select Yape/Plin screenshot → ML Kit processes on-device
4. OcrConfirmScreen: pre-filled amount, recipient, date, category (editable)
5. "Confirmar" → checkmark animation → Home updates

- **Target: ≤ 10 seconds**
- Parse Yape format: `S/\s*\.?\s*(\d+[.,]\d{2})` for amount
- If OCR fails → fallback to manual with partial data

### Flow 2: Quick Manual Entry

1. FAB "+" → "Manual" → numeric keyboard auto-focused
2. Enter amount → tap category icon → "Guardar"

- **Target: ≤ 5 seconds**

### Flow 3: WhatsApp Registration

1. User texts: "Gasté 20 en taxi" or sends voice note
2. NestJS: voice → Whisper → text → parse → write to Supabase (source='whatsapp')
3. Bot replies: "✅ Registré S/ 20.00 en Transporte"
4. If ambiguous: "¿En qué categoría? 1) Comida 2) Transporte 3) Otros"
5. Commands: "resumen" → monthly summary, "meta" → goal progress

### Flow 4: Savings Goals

Create → deposit → progress bar animates → confetti on completion

### Flow 5: Auth + Onboarding

Google Sign-In → 3-step onboarding (budget, categories, tour) → optional phone link for WhatsApp

### Flow 6: Stats

Donut chart by category → monthly comparison (positive tone) → week/month filter

---

## Gamification

### Badges

| Type          | Name              | Condition               | Points |
| ------------- | ----------------- | ----------------------- | ------ |
| first_expense | Primer Registro   | 1 expense               | 1      |
| streak_7      | Racha Semanal     | 7 days tracking         | 5      |
| streak_30     | Racha Mensual     | 30 days                 | 20     |
| first_goal    | Primera Meta      | 1 goal completed        | 10     |
| saver_month   | Ahorrista del Mes | Under budget full month | 15     |

### Levels

1=Novato(0pts) → 2=Aprendiz(20) → 3=Organizado(50) → 4=Experto(100) → 5=Maestro Financiero(200)

### Nudges (max 1/day, 8 PM default)

- Progress: "¡Llevas 5 días registrando! 🎉"
- Reminder: "Hace 2 días que no registras 😊" (if no expense in 2 days)
- Budget: "80% del presupuesto usado 💪"
- Goal: "¡70% de tu meta! 🎧"

---

## WhatsApp Bot (existing NestJS on DigitalOcean) — Fluyo integration

The backend keeps its primary self-hosted PostgreSQL for **all existing tenants** (Tecnigas/auto-repair, MIT/PUCP/university, Qolve/consulting, PrestaIA/lending). Only the **Fluyo plugin** additionally writes to Supabase using `@supabase/supabase-js` with the service-role key.

### Fluyo plugin env (added to backend `.env` in Phase 4)

```env
FLUYO_SUPABASE_URL=https://<project>.supabase.co
FLUYO_SUPABASE_SERVICE_ROLE_KEY=<service_role_key>   # bypasses RLS
OPENAI_API_KEY=<existing — also used for Whisper transcription>
```

WhatsApp credentials live on each `tenant` row already (encrypted via `ENCRYPTION_KEY`). A new Fluyo tenant is provisioned via the existing `POST /tenants` admin endpoint.

### Fluyo plugin layout (Phase 4, target)

```
src/plugins/fluyo/
├── fluyo.plugin.ts                 # implements BusinessPlugin (tools: register_expense, get_summary, get_goal)
├── fluyo.module.ts
├── services/
│   ├── supabase.service.ts         # @supabase/supabase-js client, service role
│   ├── expense.service.ts          # writeExpense(userId, amount, category, source, description)
│   ├── expense-parser.service.ts   # natural language → {amount, category}; used as OpenAI tool
│   ├── voice-transcription.service.ts  # WhatsApp audio media → Whisper text
│   └── user-link.service.ts        # phone_number → users.id in Supabase
└── tools/
    └── register-expense.tool.ts    # OpenAI function-calling schema + handler
```

User linking: when a Fluyo WhatsApp message arrives, `tenant_resolver` identifies the Fluyo tenant; the plugin then looks up `users.phone_number` in Supabase. If unmatched, the bot replies with onboarding instructions (download the Android app, register, link phone).

---

## Build Order

### Phase 1: Foundation

1. Android project setup (Gradle, Hilt, Supabase SDK, theme)
2. Supabase setup (tables, RLS, triggers)
3. Auth (Supabase Auth + Google Sign-In)
4. Onboarding (3 screens) + bottom nav

### Phase 2: Core Tracking

5. Home dashboard (budget circle, recent expenses, FAB)
6. Manual entry + Supabase CRUD
7. Categories (auto-seeded defaults + custom)

### Phase 3: OCR

8. ML Kit integration + Yape/Plin parser
9. OCR confirm screen + gallery/camera picker

### Phase 4: WhatsApp (Fluyo plugin in existing backend)

10. Add `src/plugins/fluyo/` plugin in `whatsapp-bot-be`; install `@supabase/supabase-js`; wire `FluyoSupabaseService` with service-role key.
11. `register_expense` OpenAI tool (parses "Gasté X en Y"); add `VoiceTranscriptionService` calling OpenAI Whisper for audio media.
12. Phone number linking — match incoming `contact.phone_number` against Supabase `users.phone_number`; reply with onboarding link if unmatched.

### Phase 5: Goals & Gamification

13. Goals (CRUD, deposits, progress)
14. Stats (donut chart, comparisons)
15. Badges + levels

### Phase 6: Nudges & Polish

16. Push notifications + scheduling
17. Animations (checkmark, confetti)
18. Profile & settings

### Phase 7: Testing

19. Unit tests (domain) + UI tests (flows)
20. Usability testing with 5-10 users

---

## Constraints

- All UI text in Spanish (Latin American), use strings.xml
- Currency: user-selectable (default PEN). Symbol/formatting via `presentation/util/Money.kt` (`money()` / `currencySymbol()` read `LocalCurrencySymbol`, seeded from `User.currency`). Display: "S/ 15.50"
- OCR on-device only (ML Kit). No financial data to external APIs.
- Compliant with Ley N° 29733 (Peruvian data protection)
- No ads, no premium. Thesis prototype.
- Min Android 7.0 (API 24); target Android 15 (API 36)
- Fluyo backend plugin uses Supabase service_role key (bypasses RLS). Existing backend tenants stay on the self-hosted Postgres.

## Code Conventions

- Compose: PascalCase. ViewModels: StateFlow<UiState>
- Use cases: operator fun invoke(). All async: Coroutines
- Error handling: Result<T>. No hardcoded strings.
- DTOs: @Serializable, snake_case (match DB). Domain models: camelCase.
- Mappers bridge DTOs ↔ domain models.
