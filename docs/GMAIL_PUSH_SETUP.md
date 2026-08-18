# Importación automática desde Gmail — guía de despliegue

Fluyo puede registrar un gasto cuando llega a Gmail una notificación de pago
compatible. El recorrido es:

1. Android inicia OAuth con una sesión Supabase autenticada.
2. `gmail-connect` usa PKCE y un `state` cifrado; el JWT nunca viaja en la URL.
3. El callback público devuelve a Android únicamente el authorization code de
   un uso y el `state` cifrado. Android los confirma mediante un POST autenticado
   y Edge exige que esa sesión Fluyo sea la misma que inició el flujo antes de
   intercambiar el código o guardar el grant.
4. Gmail publica cambios del buzón en Google Cloud Pub/Sub.
5. Pub/Sub llama a `gmail-webhook` con un token OIDC verificable.
6. El webhook exige remitente permitido + DMARC alineado, deduplica por mensaje
   e inserta el gasto en una transacción que comprueba que el vínculo siga activo.
7. `gmail-renew` renueva diariamente el `watch`, que Gmail hace expirar.

Esta guía configura infraestructura externa. No coloques credenciales reales en
el repositorio ni en capturas/logs.

## 1. Requisitos

- Proyecto Supabase vinculado con la CLI.
- Proyecto Google Cloud con facturación/permisos suficientes.
- Supabase CLI y Google Cloud CLI, o acceso equivalente a sus consolas.
- Android configurado según `docs/GOOGLE_OAUTH_SETUP.md`.

La configuración versionada usa el esquema privado
`com.qolve.fluyo://gmail-callback`. Para una prueba interna es suficiente; antes
de distribuir públicamente, sustituirlo por un Android App Link HTTPS verificado
en un dominio controlado por Fluyo y publicar su `assetlinks.json`, para que otra
app instalada no pueda reclamar el callback.

Los archivos relevantes son:

- `supabase/migrations/0006_add_email_source.sql`
- `supabase/migrations/0007_harden_email_ingestion.sql`
- `supabase/functions/gmail-connect/`
- `supabase/functions/gmail-webhook/`
- `supabase/functions/gmail-renew/`
- `supabase/functions/.env.example`
- `supabase/config.toml`

## 2. Google Cloud

### 2.1 Habilitar APIs

En **APIs & Services → Library**, habilita:

- Gmail API
- Cloud Pub/Sub API

### 2.2 Crear el cliente OAuth de Gmail

Usa un cliente OAuth de tipo **Web application** dedicado a la importación de
correo. No reutilices el cliente Android ni el cliente de inicio de sesión de
Supabase.

Configura como URI de redirección autorizada, exactamente:

```text
https://<PROJECT_REF>.supabase.co/functions/v1/gmail-connect
```

En la pantalla de consentimiento agrega el scope:

```text
https://www.googleapis.com/auth/gmail.readonly
```

Mientras la app esté en **Testing**, agrega cada cuenta a **Test users**. Google
expira normalmente a los 7 días los refresh tokens de apps externas en Testing
que solicitan scopes de Gmail; para una prueba prolongada habrá que volver a
vincular o publicar/verificar la app.

`gmail.readonly` es un scope restringido. Un lanzamiento público requiere la
verificación OAuth de Google y puede requerir evaluación de seguridad. Esto es
un requisito externo, no un cambio de código.

### 2.3 Crear topic y permisos

1. Crea un topic Pub/Sub, por ejemplo `gmail-receipts`, en el mismo proyecto
   Google Cloud que ejecuta el `watch()`/cliente OAuth de Gmail.
2. En el topic, concede **Pub/Sub Publisher** a:

```text
gmail-api-push@system.gserviceaccount.com
```

El valor de `GOOGLE_PUBSUB_TOPIC` será:

```text
projects/<GCP_PROJECT_ID>/topics/gmail-receipts
```

### 2.4 Crear el push autenticado

1. Crea una service account dedicada, por ejemplo
   `gmail-push@<GCP_PROJECT_ID>.iam.gserviceaccount.com`.
2. Permite al service agent de Pub/Sub generar tokens para esa cuenta
   (`roles/iam.serviceAccountTokenCreator`). Quien crea la suscripción también
   necesita permiso para actuar como ella (`iam.serviceAccounts.actAs`).
3. Crea una suscripción push al topic con:

```text
Endpoint: https://<PROJECT_REF>.supabase.co/functions/v1/gmail-webhook
Authentication: la service account dedicada
Audience: https://<PROJECT_REF>.supabase.co/functions/v1/gmail-webhook
Ack deadline: 120 segundos
Expiration period: Never expire
```

Guarda el nombre completo de la suscripción:

```text
projects/<GCP_PROJECT_ID>/subscriptions/gmail-receipts-push
```

El webhook verifica firma OIDC, issuer, audience, email de la service account,
`email_verified` y nombre exacto de la suscripción. No basta con que el JSON se
parezca a un mensaje Pub/Sub.

## 3. Supabase

### 3.1 Aplicar migraciones

Si el proyecto se administra con la CLI, ejecuta:

```bash
supabase db push
```

Si las migraciones anteriores se aplicaron manualmente en SQL Editor, aplica
también `0006` y `0007` allí o repara primero el historial remoto de migraciones;
no mezcles ambos métodos sin reconciliarlo.

Esto aplica `0006` y `0007`: tabla de grants, Vault, RLS de solo lectura para el
cliente, cursores/renovación, deduplicación y RPCs privadas para `service_role`.

### 3.2 Configurar secretos

Copia `supabase/functions/.env.example` a
`supabase/functions/.env.local` (ignorado por Git) y rellena:

| Variable | Uso |
|---|---|
| `GMAIL_CLIENT_ID` | Cliente OAuth web de Gmail |
| `GMAIL_CLIENT_SECRET` | Secreto del cliente OAuth |
| `GOOGLE_PUBSUB_TOPIC` | Topic completo `projects/.../topics/...` |
| `GMAIL_OAUTH_STATE_SECRET` | Aleatorio, mínimo 32 bytes; cifra el state OAuth |
| `GMAIL_DEDUPE_SECRET` | Aleatorio, mínimo 32 bytes; identidad estable del buzón |
| `GMAIL_CRON_SECRET` | Aleatorio distinto, mínimo 32 bytes |
| `GOOGLE_PUBSUB_PUSH_AUDIENCE` | URL exacta del webhook |
| `GOOGLE_PUBSUB_PUSH_SERVICE_ACCOUNT_EMAIL` | Identidad push exacta |
| `GOOGLE_PUBSUB_SUBSCRIPTION` | Suscripción completa `projects/.../subscriptions/...` |
| `GMAIL_OAUTH_ALLOWED_REDIRECT_URIS` | Opcional; por defecto `com.qolve.fluyo://gmail-callback` |

Genera los tres secretos de aplicación por separado, por ejemplo:

```bash
openssl rand -base64 48
```

No rotes `GMAIL_DEDUPE_SECRET` sin migrar los fingerprints ya almacenados: una
rotación sin backfill cambia las claves de idempotencia y podría reimportar un
mensaje antiguo tras una reconexión.

Supabase inyecta `SUPABASE_URL`, `SUPABASE_ANON_KEY` y
`SUPABASE_SERVICE_ROLE_KEY`; no los agregues al archivo versionado.

Carga el archivo local con:

```bash
supabase secrets set --env-file supabase/functions/.env.local
```

### 3.3 Desplegar funciones

`supabase/config.toml` desactiva la validación JWT del gateway únicamente para
estas tres funciones. Cada handler aplica su autenticación específica.

```bash
supabase functions deploy gmail-connect
supabase functions deploy gmail-webhook
supabase functions deploy gmail-renew
```

- `gmail-connect`: sesión Supabase para iniciar/finalizar por POST y para DELETE;
  el callback GET público solo valida el `state` cifrado y redirige a la app, sin
  intercambiar tokens ni escribir datos.
- `gmail-webhook`: OIDC de Google Pub/Sub.
- `gmail-renew`: bearer `GMAIL_CRON_SECRET` comparado en tiempo constante.

### 3.4 Programar la renovación diaria

Gmail entrega una expiración con cada `watch`; Google recomienda renovarlo al
menos cada 7 días. Fluyo selecciona grants próximos a vencer, así que ejecuta
`gmail-renew` diariamente, por ejemplo a las 03:00 de Lima (08:00 UTC).

En Supabase habilita `pg_cron`, `pg_net` y Vault. Guarda en Vault dos secretos:

- `gmail_renew_url` = `https://<PROJECT_REF>.supabase.co/functions/v1/gmail-renew`
- `gmail_cron_secret` = el mismo valor de `GMAIL_CRON_SECRET`

Luego crea el job desde SQL Editor, leyendo ambos valores desde
`vault.decrypted_secrets` para no escribirlos en el SQL ni en el historial:

```sql
select cron.schedule(
  'renew-gmail-watch-daily',
  '0 8 * * *',
  $$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'gmail_renew_url'),
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || (
        select decrypted_secret
        from vault.decrypted_secrets
        where name = 'gmail_cron_secret'
      )
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
```

Verifica que los nombres sean únicos en Vault y que el job aparezca en
**Integrations → Cron**.

## 4. Verificación antes de entrega

### 4.1 Automatizada

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
cd supabase/functions
deno task verify
```

CI ejecuta compilación, lint y tests Android, además de formato, lint,
type-check y tests Deno.

### 4.2 Smoke test real

Usa una cuenta de prueba, nunca una bandeja personal de producción:

1. En **Perfil → Importar gastos desde Gmail**, pulsa **Vincular**.
2. Confirma que Google muestra solo `gmail.readonly` y regresa a Fluyo.
3. Confirma en la app el estado **Vinculado**. En Dashboard/SQL, verifica que
   `email_grants.watch_expiration > now()` para esa cuenta.
4. Envía/recibe una notificación real soportada con DMARC válido y salida de
   dinero; debe aparecer exactamente un gasto con origen correo.
5. Fuerza el mismo push otra vez; no debe duplicar el gasto.
6. Prueba correo ajeno, `From` falsificado, DMARC fallido y abono recibido; todos
   deben ignorarse.
7. Pulsa **Desvincular**; pushes posteriores no deben insertar gastos.
8. Repite vinculación/desvinculación y revisa logs sin tokens, email completo ni
   cuerpo del mensaje.
9. Como prueba negativa, inicia un vínculo con un usuario Fluyo y abre su URL de
   consentimiento en un dispositivo/sesión donde Fluyo tenga otro usuario. La
   finalización debe responder `state_user_mismatch` y no crear ni modificar un
   grant.

Este smoke test solo puede declararse aprobado después de desplegar y configurar
GCP/Supabase. Las pruebas locales no sustituyen OAuth/Pub/Sub reales.

## 5. Comportamiento y límites deliberados

- Solo se leen mensajes de Inbox notificados por Gmail. Si Gmail expira el
  cursor, el fallback retoma desde cinco minutos antes del último sync conocido;
  si nunca hubo sync, retoma desde cinco minutos antes de crear el grant. La
  ventana de 7 días queda solo como último resguardo para metadata ausente o
  inválida. Nunca avanza el cursor silenciosamente si la paginación quedó
  truncada.
- Solo remitentes permitidos con `Authentication-Results` de `mx.google.com`,
  `dmarc=pass` y dominio alineado pueden llegar al parser.
- El parser actual usa reglas conservadoras para PEN, pagos salientes y
  plantillas conocidas. Un formato nuevo se ignora hasta agregar una fixture y
  su prueba; no se recomienda un LLM sobre correo privado por defecto.
- Outlook/Microsoft Graph no está soportado.
- Fluyo guarda refresh tokens en Supabase Vault. Nunca guarda cuerpos de correo;
  persiste únicamente los campos del gasto y una referencia HMAC de idempotencia.
- **Desvincular** borra la fila y el secreto Vault antes de responder; desde ese
  instante los pushes fallan cerrado y el watch vence por sí solo. El handler no
  usa un token antiguo para llamar `stop/revoke`, ya que podría interferir con
  una revinculación concurrente. Si se desea retirar también el permiso visible
  en Google, hacerlo desde **Cuenta de Google → Seguridad → Conexiones con apps**.

## 6. Diagnóstico

| Síntoma | Revisión |
|---|---|
| Google muestra `redirect_uri_mismatch` | URI exacta del cliente web y URL de `gmail-connect` |
| Callback vuelve con `invalid_state`/`state_expired` | `GMAIL_OAUTH_STATE_SECRET`, reloj y reinicio del flujo |
| Finalización responde `state_user_mismatch` | La sesión Fluyo cambió durante OAuth; reiniciar el vínculo desde la cuenta correcta |
| `account_conflict` | La misma cuenta Gmail ya está vinculada a otro usuario Fluyo |
| El estado muestra atención requerida | `last_error`, expiración del watch y ejecución de `gmail-renew` |
| Pub/Sub recibe 401 | Audience, service account, Token Creator y env de identidad/suscripción |
| Pub/Sub recibe 503 | Logs sanitizados, Vault/token, Gmail API o error temporal de DB |
| Mensaje legítimo se ignora | Inbox, remitente exacto, DMARC alineado, plantilla y monto PEN |
| Error de Vault | Migración `0007`, extensión `supabase_vault` y permisos `service_role` |

Referencias oficiales: [Gmail push notifications](https://developers.google.com/workspace/gmail/api/guides/push),
[`users.watch`](https://developers.google.com/workspace/gmail/api/reference/rest/v1/users/watch),
[OAuth 2.0 de Google](https://developers.google.com/identity/protocols/oauth2),
[push autenticado de Pub/Sub](https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions),
[configuración de Edge Functions](https://supabase.com/docs/guides/functions/function-configuration)
y [funciones programadas](https://supabase.com/docs/guides/functions/schedule-functions).
