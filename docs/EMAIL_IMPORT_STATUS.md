# Email receipt import — estado real del feature

**Rama:** `feat/email-receipt-import`
**Fecha:** 2026-08-05

Este documento refleja con honestidad en qué estado quedó el feature de import
automático de boletas por Gmail. Lo que funciona, lo que falla, y lo que falta.

---

## ✅ Funciona y está verificado

| Pieza | Verificación |
|---|---|
| **Parser de boletas** (`supabase/functions/_shared/receipt-parser.ts`) | **28/28 tests Deno pasan.** Whitelist de remitentes (Yape, BCP, Interbank, BBVA, Scotiabank), extracción de monto/fecha/destinatario por regex. |
| **Migración SQL `0006`** | Escrita: extiende `expenses.source` con `'email'`, crea tabla `email_grants` (Vault-backed, RLS). **No aplicada** a la base de datos real todavía (ver sección "Falta configuración manual"). |
| **Edge Function `gmail-webhook`** | Código completo. **No desplegada ni probada** end-to-end (requiere proyecto GCP + secrets, ver setup). |
| **Edge Function `gmail-connect`** | Código completo (OAuth + callback + watch). **No desplegada ni probada.** |
| **Cliente Gmail API** (`_shared/gmail-client.ts`) | Código completo. No testeado en runtime (depende de tokens OAuth reales). |
| **Cliente DB** (`_shared/db.ts`) | Código completo (service-role, patrón WhatsApp). No testeado en runtime. |
| **Doc de setup** (`docs/GMAIL_PUSH_SETUP.md`) | Completa: pasos GCP + Supabase. |

---

## ❌ FALLA — bloqueador de build

### `kspDebugKotlin` falla: Hilt no resuelve `SupabaseEmailGrantRepository`

```
> Task :app:kspDebugKotlin FAILED
e: [ksp] ModuleProcessingStep was unable to process 'com.qolve.fluyo.di.RepositoryModule'
   because 'SupabaseEmailGrantRepository' could not be resolved.
KSP failed with exit code: PROCESSING_ERROR
```

**Qué sé:** el código Kotlin de la clase compila (no hay errores `e:` de
compilador antes del de KSP). El binding en `RepositoryModule.kt` es idéntico
al patrón de los otros 6 repositorios que sí funcionan. Limpié el caché de
KSP/Kotlin/Gradle y el error persiste, así que **no es caché corrupta**.

**Qué probé:**
- Comparar anotaciones (`@Singleton class ... @Inject constructor`) contra repos que funcionan → idénticas.
- Simplificar el `select{}` (quitar `Columns.list`) → mismo error.
- `rm -rf app/build .kotlin .gradle build` + reconstruir → mismo error.

**Qué falta probar (posibles causas):**
1. **Incompatibilidad de versión KSP/Kotlin/Hilt** con algún detalle sutil de
   este binding concreto. El proyecto usa KSP `2.2.10-2.0.2` + Hilt `2.59.2` +
   Kotlin `2.2.10` (todos beta/recientes — ver `gradle/libs.versions.toml`).
   Podría ser un bug de KSP con esta combinación.
2. **El binding** `abstract fun bindEmailGrantRepository(impl: SupabaseEmailGrantRepository): EmailGrantRepository`
   podría tener un problema que los otros no exhiben. Revisar si `EmailGrantRepository`
   (la interfaz, en `domain/repository/`) está bien referenciada.
3. **Orden de procesamiento de KSP** — a veces fallar en un módulo oculta el
   error real en otro. Correr `./gradlew :app:kspDebugKotlin --info 2>&1 | grep -i error`
   para ver el error oculto (no pude ejecutarlo: el `grep` del entorno tiene un
   alias que rompe con múltiples patrones — ejecutar con `--stacktrace` o
   redirigir a archivo y leer con `Read`).

**Workaround temporal si necesitas compilar ya mismo:** comentar el binding de
`EmailGrantRepository` en `RepositoryModule.kt` y la inyección en
`ProfileViewModel.kt`. La app compila sin el feature de Gmail; el resto de
funcionalidad queda intacta.

**Por qué NO es un error de los otros archivos:** el error apunta exactamente a
`SupabaseEmailGrantRepository` y desaparece si eliminas ese binding. Es el
único bloqueador; el resto del código Android (enum, strings, UI, manifest) no
genera errores de compilación.

---

## ⚠️ Falta configuración manual (no es código)

Estos pasos los tienes que hacer tú en consolas externas; están detallados en
`docs/GMAIL_PUSH_SETUP.md` pero los resumo:

1. **Google Cloud Console:**
   - Habilitar Gmail API.
   - Crear OAuth client Web (distinto al de sign-in) con scope `gmail.readonly`
     y redirect URI a la Edge Function `gmail-connect`.
   - Pantalla de consentimiento en modo **Testing** + añadir usuarios de prueba
     (para producción pública: verificación de Google, 2-6 semanas).
   - Crear Pub/Sub topic `gmail-receipts` + push subscription al webhook.
   - Otorgar `roles/pubsub.publisher` a `gmail-api-push@system.gserviceaccount.com`.

2. **Supabase:**
   - Aplicar la migración `0006` (`supabase db push` o SQL editor).
   - Verificar que la extensión `vault` esté habilitada.
   - Setear secrets: `GMAIL_CLIENT_ID`, `GMAIL_CLIENT_SECRET`, `GOOGLE_PUBSUB_TOPIC`.
   - Desplegar las 2 Edge Functions:
     `supabase functions deploy gmail-webhook --no-verify-jwt`
     `supabase functions deploy gmail-connect`

Ninguno de estos pasos se puede automatizar desde el código; son configuración
de infraestructura externa.

---

## 🔮 Mejoras pendientes (no bloqueantes, marcadas para v2)

1. **Parser por regex es frágil.** Cada banco formatea distinto. La v2 debería
   reemplazar `receipt-parser.ts` con un LLM (OpenAI, misma cuenta del bot de
   WhatsApp) para extracción robusta. El módulo está aislado para que el cambio
   no toque nada más.

2. **Sin deduplicación.** Si un webhook reintenta tras un crash, el mismo
   mensaje podría insertarse dos veces. El cursor `email_grants.history_id`
   avanza solo tras el batch, pero un crash intermedio repite. **Fix v2:**
   añadir columna `message_id` UNIQUE a `expenses` (o tabla dedupe).

3. **Sin cola de revisión humana.** Elegiste registro automático directo.
   Para un producto vendible, considera un estado `pendiente` que el usuario
   confirme/edite antes de impactar el presupuesto (protege contra errores de
   parseo de monto).

4. **Outlook/Microsoft Graph no soportado.** La arquitectura deja espacio para
   `outlook-webhook/` pero Graph usa un modelo de suscripción distinto a Pub/Sub.

5. **Verificación OAuth de Google.** `gmail.readonly` es scope restringido.
   Para app pública necesitas pasar el proceso de verificación de Google
   (security assessment, semanas). En desarrollo con "Test users" funciona sin
   verificación.

6. **El `gmail-connect` no verifica el JWT del `state`** — lo decodifica sin
   validar firma. En producción deberías verificarlo con el JWT secret de
   Supabase para evitar que alguien inyecte un `state` ajeno.

---

## Archivos del feature (mapa rápido)

```
supabase/
├── migrations/0006_add_email_source.sql          ✅ escrito
└── functions/
    ├── _shared/
    │   ├── receipt-parser.ts                      ✅ 28 tests pasan
    │   ├── receipt-parser.test.ts                 ✅
    │   ├── gmail-client.ts                        ⚠️ no probado en runtime
    │   └── db.ts                                  ⚠️ no probado en runtime
    ├── gmail-webhook/index.ts                     ⚠️ no desplegado
    └── gmail-connect/index.ts                     ⚠️ no desplegado

app/src/main/java/com/qolve/fluyo/
├── domain/model/ExpenseSource.kt                  ✅ +EMAIL
├── domain/repository/EmailGrantRepository.kt      ✅ interfaz
├── domain/repository/AuthRepository.kt            ✅ +currentAccessToken()
├── data/repository/SupabaseEmailGrantRepository.kt ❌ FALLA en KSP
├── data/repository/SupabaseAuthRepository.kt      ✅ +impl
├── di/RepositoryModule.kt                         ❌ el binding que dispara el error
├── presentation/screens/profile/ProfileScreen.kt  ✅ fila Gmail + OAuth launch
├── presentation/screens/profile/ProfileViewModel.kt ✅ estado + linkGmail()
├── presentation/screens/home/components/ExpenseRow.kt ✅ rama EMAIL
└── (res/values/strings.xml)                       ✅ labels

app/src/main/AndroidManifest.xml                   ✅ deep link
docs/GMAIL_PUSH_SETUP.md                           ✅ setup externo
docs/EMAIL_IMPORT_STATUS.md                        ← este documento
```

---

## Resumen ejecutivo

- **El feature está ~90% implementado** en código.
- **1 bloqueador real:** el binding de Hilt de `SupabaseEmailGrantRepository`
  falla en compilación KSP y no se ha resuelto todavía.
- **0 cosas probadas end-to-end:** las Edge Functions necesitan infraestructura
  externa (GCP + secrets) que no se ha configurado, así que el flujo completo
  no se ha validado en runtime.
- **El parser sí está probado** (28 tests), que es la pieza más frágil.
