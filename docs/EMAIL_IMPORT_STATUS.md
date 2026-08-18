# Importación de gastos por Gmail — estado real

**Rama:** `feat/email-receipt-import`<br>
**Fecha:** 2026-08-17

## Resultado

La implementación está cerrada y verificable en código: Android compila, el
flujo OAuth ya no expone el JWT y vincula el callback a la sesión Fluyo que lo
inició, los callbacks funcionan en arranque frío y caliente, las funciones Edge
tienen type-check/lint/tests, y la base protege tokens, deduplicación y
sincronización mediante RPCs privadas.

Todavía no debe afirmarse que está validada en producción. Falta aplicar la
migración, cargar secretos, desplegar las funciones y ejecutar el smoke test con
una cuenta Gmail de prueba y Pub/Sub reales. Esos pasos requieren acceso a los
proyectos externos y están en `docs/GMAIL_PUSH_SETUP.md`.

## Verificado localmente

| Área | Estado |
|---|---|
| Android unit tests | **PASS — 58 tests, 0 fallos** |
| Android compilación debug | **PASS — APK generado** |
| Android lint | **PASS** tras integrar el feature |
| Edge format/lint/type-check/tests | **PASS — 85 tests, 0 fallos** mediante `deno task verify` |
| SQL `0001`–`0007` | **PASS** en PostgreSQL de Supabase 17 |
| Vault y RPCs | **PASS**: create/update/decrypt/delete, permisos y RLS |
| Diff | `git diff --check` limpio en la verificación Android |

La causa del antiguo error KSP era un import faltante de
`SupabaseEmailGrantRepository` en `RepositoryModule.kt`; está corregida. No era
una incompatibilidad de Kotlin/Hilt.

## Qué quedó implementado

### Android

- Inicio OAuth autenticado con POST a `gmail-connect`; ningún token va en query.
- Deep link `com.qolve.fluyo://gmail-callback` con un authorization code de un
  solo uso y `state` cifrado; nunca contiene JWT, correo, access token ni refresh
  token.
- Finalización inmediata mediante un segundo POST autenticado. La app no
  reintenta automáticamente el authorization code y siempre reconcilia el
  resultado leyendo metadata pública del grant.
- Callback en app abierta o cerrada, navegación a Perfil y refresh sin carreras.
- Estados Loading, Disconnected, Authorizing, Linked, NeedsAttention, Failed y
  Disconnecting.
- Relink y unlink con confirmación.
- SELECT limitado a `email`, `watch_expiration` y `last_error`.
- Validadores y resolvers puros cubiertos por tests.

### OAuth, Gmail y Pub/Sub

- OAuth Authorization Code + PKCE.
- `state` opaco cifrado con AES-GCM y TTL de 10 minutos.
- El callback público no intercambia tokens ni escribe en Vault/base de datos:
  devuelve el código a Android y la finalización exige que la sesión Supabase
  autenticada sea exactamente el usuario sellado en `state`.
- Identidad del buzón obtenida con `users.getProfile`, no desde un `id_token`
  ausente.
- Refresh token en Supabase Vault; nunca se entrega al cliente Android.
- `watch()` con expiración persistida y renovación diaria en `gmail-renew`.
- Webhook autenticado con OIDC de Google: issuer, audience, service account,
  `email_verified` y suscripción exacta.
- Historial paginado, manejo de cursor expirado y fallback que no avanza el
  cursor si queda trabajo truncado.
- Errores/reintentos sanitizados; logs sin cuerpo de correo ni tokens.

### Ingesta segura

- Remitente con límites exactos de dirección/dominio.
- `Authentication-Results` de `mx.google.com` con `dmarc=pass` y dominio
  `header.from` alineado; falla cerrado ante ausencia, spoofing o DMARC fallido.
- Solo transacciones de salida; abonos/transferencias recibidas se ignoran.
- MIME multipart, texto/HTML, fechas en Lima y límites de tamaño/profundidad.
- Idempotencia estable por buzón y Gmail message ID usando HMAC, sin revelar el
  email en la referencia.
- Inserción atómica que bloquea el grant: un webhook no puede crear un gasto
  después de que desvincular/revincular haya ganado la carrera.
- Categoría `Otros` resuelta dentro de la misma operación de base de datos.

### Base de datos

`0007_harden_email_ingestion.sql` agrega y prueba:

- metadata de watch/sync/error;
- fingerprint HMAC de buzón y `expenses.source_reference`;
- índice único de idempotencia;
- RLS y privilegios de columnas de solo lectura para Android;
- RPCs `SECURITY DEFINER` ejecutables solo por `service_role`;
- cursor monotónico y renovación de watch sin saltarse eventos;
- limpieza del secreto Vault en unlink/cascade;
- recuperación y desvinculación aun si un secreto Vault está ausente.

## Requisitos externos antes de decir “funciona end-to-end”

1. Aplicar `supabase db push` al proyecto objetivo.
2. Configurar el cliente OAuth web y consentimiento de Gmail.
3. Crear topic y push subscription autenticada en Pub/Sub.
4. Cargar todos los secretos listados en
   `supabase/functions/.env.example`.
5. Desplegar `gmail-connect`, `gmail-webhook` y `gmail-renew`.
6. Programar `gmail-renew` diariamente.
7. Ejecutar completo el smoke test de `docs/GMAIL_PUSH_SETUP.md`.

Mientras OAuth esté en modo **Testing**, solo funcionan test users y Google
puede expirar a los 7 días los refresh tokens con scopes Gmail. Para usuarios
públicos, `gmail.readonly` requiere completar la verificación externa de Google.

## Alcance y límites conocidos

- El parser es deliberadamente conservador. Un banco o formato nuevo debe
  entrar con correo anonimizado como fixture y tests; si no coincide, se ignora.
- El historial inicial no importa toda la bandeja. El fallback de recuperación
  retoma desde el último sync o, si todavía no hubo uno, desde la creación del
  grant; se niega a perder silenciosamente resultados truncados.
- Outlook/Microsoft Graph no está soportado.
- El retorno Android actual usa un esquema privado `com.qolve.fluyo://`. Es
  adecuado para la prueba interna, pero Android no puede reservarlo de forma
  criptográfica frente a otra app instalada. Antes de una distribución pública,
  migrar el callback a un Android App Link HTTPS verificado y publicar
  `assetlinks.json` en un dominio controlado por Fluyo.
- **Desvincular** elimina de forma atómica el grant y refresh token de Fluyo, por
  lo que deja de leer/importar inmediatamente; el `watch` restante expira solo.
  No llama a `stop/revoke` con un token leído antes del borrado, porque esa
  compensación puede apagar un relink concurrente más nuevo. Hasta implementar
  un protocolo durable de desconexión, el usuario puede retirar además el
  permiso visible desde la seguridad de su cuenta Google.
- El `state` tiene cifrado, TTL, nonce y PKCE. El authorization code de Google
  es de un solo uso y solo puede consumirse con la sesión Fluyo que inició el
  flujo; persistir/consumir además el nonce sería defensa adicional ante varios
  consentimientos simultáneos, no un bloqueo del flujo actual.
- No se deben rotar secretos de deduplicación sin una migración/backfill.

## Criterio de entrega

El código queda listo para revisión/push cuando vuelvan a pasar en una misma
revisión final:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
(cd supabase/functions && deno task verify)
git diff --check
```

La entrega operativa termina únicamente cuando el smoke test externo registra
un gasto real una sola vez, ignora los casos negativos y deja de importar tras
desvincular.
