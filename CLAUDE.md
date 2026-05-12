# CLAUDE.md — Fluyo Development Context

## Project Overview

**Fluyo** is a personal finance management platform with two entry points:

1. **Native Android app** (Kotlin + Jetpack Compose) — Primary interface for tracking expenses via OCR scanning, manual entry, and voice. Includes gamification, savings goals, and behavioral nudges.
2. **WhatsApp bot** (via existing NestJS backend) — Secondary interface where users can text or send voice messages to register expenses conversationally (e.g., "Gasté 15 soles en almuerzo").

Both interfaces write to the **same Supabase PostgreSQL database**, ensuring a single source of truth regardless of how the user registers an expense.

This is a thesis project for the Pontificia Universidad Católica del Perú (PUCP), Computer Science Engineering degree. The app is designed using User-Centered Design (UCD) methodology.

**Target users:** University students aged 18-26 in Lima, Peru, who use Yape/Plin daily and struggle to track expenses due to the tedium of manual registration.

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

| Component    | Technology                     | Notes                                      |
| ------------ | ------------------------------ | ------------------------------------------ |
| Language     | Kotlin                         | All code in Kotlin                         |
| Min SDK      | 26 (Android 8.0)               | Target SDK: 34 (Android 14)                |
| UI           | Jetpack Compose                | Material 3, declarative                    |
| Architecture | Clean Architecture + MVVM      | Domain layer has ZERO Android dependencies |
| Navigation   | Jetpack Navigation Compose     | Bottom nav with 4 tabs                     |
| State        | ViewModel + StateFlow          | Unidirectional data flow                   |
| DI           | Hilt (Dagger)                  | Constructor injection                      |
| Backend      | Supabase Kotlin SDK            | Auth, Database, Storage                    |
| OCR          | Google ML Kit Text Recognition | On-device, no server upload                |
| Charts       | Vico or MPAndroidChart         | Donut chart for categories                 |
| Testing      | JUnit 5 + Espresso + Mockk     | Domain + UI tests                          |
| Build        | Gradle (Kotlin DSL)            | Version catalog                            |

### WhatsApp Backend (existing)

| Component        | Technology          | Notes                                                  |
| ---------------- | ------------------- | ------------------------------------------------------ |
| Runtime          | Node.js + NestJS    | Already deployed on DigitalOcean VPS                   |
| Database         | Supabase PostgreSQL | Same database as Android app (migrate from current PG) |
| WhatsApp         | WhatsApp Cloud API  | Webhook receiver + message sender                      |
| Voice Processing | OpenAI Whisper API  | Voice notes → text transcription                       |
| Text Parsing     | Custom NLP / regex  | Extract amount, category from natural language         |
| ORM              | Prisma or TypeORM   | Connects to Supabase PG via connection string          |

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
app/src/main/java/com/Fluyo/app/
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

## WhatsApp Bot (NestJS on DigitalOcean)

### NestJS connects to Supabase via:

```env
DATABASE_URL=postgresql://postgres:password@db.your-project.supabase.co:5432/postgres
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
WHATSAPP_TOKEN=meta_access_token
WHATSAPP_PHONE_ID=phone_number_id
OPENAI_API_KEY=for_whisper
```

### NestJS Module Structure

```
src/
├── whatsapp/
│   ├── whatsapp.controller.ts     # Webhook endpoint
│   ├── whatsapp.service.ts        # Process + reply
│   └── whatsapp.guard.ts          # Signature verification
├── expense/
│   ├── expense.service.ts         # Write to Supabase
│   └── expense-parser.service.ts  # Text → {amount, category}
├── voice/
│   └── voice.service.ts           # Audio → Whisper → text
├── user/
│   └── user.service.ts            # Find user by phone
└── supabase/
    └── supabase.service.ts        # DB client (service_role)
```

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

### Phase 4: WhatsApp

10. Migrate NestJS DB to Supabase connection
11. Text parser + voice processing (Whisper)
12. Phone number linking

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
- Currency: PEN. Display: "S/ 15.50"
- OCR on-device only (ML Kit). No financial data to external APIs.
- Compliant with Ley N° 29733 (Peruvian data protection)
- No ads, no premium. Thesis prototype.
- Min Android 8.0 (API 26)
- NestJS uses Supabase service_role key (bypasses RLS)

## Code Conventions

- Compose: PascalCase. ViewModels: StateFlow<UiState>
- Use cases: operator fun invoke(). All async: Coroutines
- Error handling: Result<T>. No hardcoded strings.
- DTOs: @Serializable, snake_case (match DB). Domain models: camelCase.
- Mappers bridge DTOs ↔ domain models.
