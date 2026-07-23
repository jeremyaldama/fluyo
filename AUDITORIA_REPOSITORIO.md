# Auditoría integral del repositorio Fluyo

**Fecha de revisión:** 21 de julio de 2026  
**Alcance:** aplicación Android, esquema y migraciones Supabase, seguridad, privacidad, consistencia de datos, arquitectura, pruebas, build, release, documentación y experiencia de desarrollo.  
**Modalidad:** revisión estática del repositorio y verificación local de compilación, pruebas y lint.  
**Estado del código al iniciar la auditoría:** rama `main` sincronizada con `origin/main` y árbol de trabajo limpio.

> [!IMPORTANT]
> Este informe no reproduce ninguna credencial. Se encontró una contraseña versionada y se documenta únicamente su ubicación y la respuesta recomendada.

## Estado de remediación — 22 de julio de 2026

La auditoría original se conserva íntegra debajo como línea base. Después de autorizar
la corrección se implementó una remediación transversal en Android, PostgreSQL, Edge
Functions, build, CI, pruebas y documentación. “Resuelto en el repositorio” significa
que el código y sus controles están presentes; no implica que una migración, función,
secreto o servicio externo haya sido desplegado en producción.

| Hallazgo | Estado actualizado | Evidencia de remediación / límite |
|---|---|---|
| SEC-01 | **Parcial; operación externa bloqueante** | Se retiraron el archivo local y el AAB, se ampliaron ignores y se añadió Gitleaks con una regla específica para passwords de `keytool`. La única ocurrencia histórica conocida tiene una excepción por huella exacta en [`.gitleaksignore`](.gitleaksignore), por lo que cualquier aparición nueva sigue bloqueando CI. [`docs/SECURITY_INCIDENT_RESPONSE.md`](docs/SECURITY_INCIDENT_RESPONSE.md) guía la rotación, decisión sobre la upload key y purga coordinada aún pendientes. |
| SEC-02 | **Resuelto en repo** | Configuraciones locales dejaron de versionarse; `.mcp.json.example` es no productivo/read-only y la documentación exige separación de entornos. |
| ID-01 | **Resuelto en app/DB; backend externo pendiente** | La entrada libre de teléfono fue eliminada. La app crea un reto de un solo uso, abre `VINCULAR FLUYO <token>`, sólo lee vínculos verificados y permite desvincular. `0006` guarda hash, E.164 y ACL server-only; además, toda la superficie queda oculta por defecto mediante `WHATSAPP_LINKING_ENABLED=false` hasta desplegar la confirmación autenticada. |
| PRIV-01 | **Resuelto en repo; despliegue externo bloqueante** | `delete-account` verifica JWT, activa primero un tombstone DB idempotente, bloquea nuevas escrituras/uploads, exige que el cleanup externo drene media, elimina Storage y sólo entonces borra Auth. Falla cerrado en cualquier etapa; la app purga sesión, cachés y preferencias. Faltan desplegar migración/función e implementar el cleanup del backend externo bajo ese contrato. |
| FIN-01 | **Resuelto** | `deposit_to_goal` es transaccional, valida ownership, usa request UUID idempotente y devuelve snapshots estables; el contrato `0008` revoca el camino directo legacy. |
| AUTH-01 | **Resuelto** | Coordinador de identidad, cachés registradas, preferencias por `authId`, sesión cifrada y un epoch atómico rechazan respuestas/eventos/notificaciones tardíos A→B. Los URIs compartidos y callbacks se consumen y borran del intent. |
| AUTH-02 | **Resuelto** | Navegación raíz modela `Provisioning` y no expone contenido hasta que `ensureUserRow()` termina para la identidad vigente. |
| AUTH-03 | **Resuelto en app** | Registro distingue confirmación pendiente, existe UX de espera/reenvío y callback exacto con URI saneado. El App Link HTTPS verificado requiere un dominio externo. |
| DATA-01 | **Resuelto para filas nuevas; rollout pendiente** | `0006..0007` añaden rangos, ownership, operaciones idempotentes, tombstones y derivación server-side; `0008` cierra caminos directos. Fecha de gasto queda limitada a `2000-01-01..hoy` y nuevas metas no aceptan deadline pasado, también en entrada manual/OCR. CI prueba fresh y upgrade legacy con reparación, RLS A/B y validación. Los `NOT VALID` aún exigen auditoría/backfill/`VALIDATE CONSTRAINT` sobre datos históricos reales. |
| FIN-02 | **Resuelto** | Dominio, parsers y cálculos usan `MoneyAmount` en centavos (`Long`/`BigDecimal`) con redondeo explícito y overflow controlado. `Double` queda sólo en DTOs de serialización Supabase y geometría visual no financiera. |
| FIN-03 | **Resuelto** | La cuenta conserva una denominación base; la DB impide relabeling después de actividad monetaria y serializa el primer alta concurrente con el cambio de moneda. |
| TIME-01 | **Resuelto** | Vista mensual y lógica financiera usan explícitamente `America/Lima` mediante `FluyoTime`. |
| DATA-02 | **Resuelto** | Historial, racha, metas, categorías, extras e insignias paginan por ventanas de 500 con orden total estable; CSV y estadísticas cargan el rango completo solicitado. |
| STATS-01 | **Resuelto** | Cargas anteriores se cancelan, los errores se exponen y el promedio usa días transcurridos del período actual. |
| APP-01 | **Resuelto** | PATCH nullable usa DSL explícita para enviar `null` y poder limpiar categoría/descripción. |
| APP-02 | **Resuelto** | Eventos usan cola y los eventos sensibles quedan ligados al epoch; se drenan al cambiar de identidad. |
| APP-03 | **Resuelto** | Todas las insignias se reconcilian diariamente con un worker durable independiente de la preferencia de nudges, con catch-up/reintento idempotente; ya no dependen de abrir una pantalla ni de una ventana mensual. |
| APP-04 | **Resuelto** | Exportación neutraliza `=`, `+`, `-`, `@` incluso después de whitespace/controles y aplica quoting CSV después. |
| APP-05 | **Resuelto** | Notificaciones usan un drawable `ic_stat_name` válido y se cancelan al cerrar/cambiar sesión. |
| PRIV-02 | **Resuelto** | Sesión AES-GCM con Android Keystore, migración autocurable desde plaintext y archivos bajo `noBackupFilesDir`; backups cloud/device están deshabilitados. |
| PRIV-03 | **Parcial; publicación/servicios externos pendientes** | Release exige URLs HTTPS con host público no reservado; rechaza localhost y redes privadas, pero deliberadamente no hace DNS/HTTP. UI enlaza términos/privacidad/borrado y voz muestra disclosure. Deben publicarse y probarse las páginas reales, además de auditar retención de Meta/backend/transcripción. |
| PRIV-04 | **Resuelto** | Capturas/exports viven en caché sensible, tienen limpieza al usar/salir/iniciar y no se persiste `image_url` desde Android. `FLAG_SECURE` evita screenshots, grabación y preview de Recents con datos financieros/autenticación. |
| BUILD-01 | **Resuelto** | Se retiró la ruta JDK específica de una máquina; CI configura JDK 21 y SDK explícitos. |
| BUILD-02 | **Resuelto** | Tareas release fallan si faltan firma/backend o si las URLs legales no son HTTPS públicas y no reservadas; `releaseUnsigned` es una variante local explícita y verificable con R8, ofuscación y resource shrinking. La publicación/alcanzabilidad se valida fuera del build. |
| CI-01 | **Resuelto en configuración** | CI cubre JVM, Kover, lint, instrumentación focalizada en emulador API 35, APK/AAB minificado, gates negativo/positivo de release, PostgreSQL/RLS, Deno congelado y Gitleaks. Lint/debug y KSP/release se ejecutan en grafos separados para evitar la carrera entre variantes observada en AGP. El secreto histórico está exceptuado sólo por fingerprint hasta completar SEC-01. |
| TEST-01 | **Mejorado y medido** | **154 pruebas JVM** y **11/11 pruebas instrumentadas** pasan; Kover mide **18.4965%** de líneas y exige piso de **18%**. Se añadieron pruebas de dinero, fechas, sesión/epoch, navegación, CSV, archivos sensibles, paginación, idempotencia incierta, depósitos recuperables, reenvío de email, errores UI, parsers, WhatsApp y contratos DB. Aún faltan journeys Compose E2E y matriz de dispositivos. |
| LINT-01 | **Controlado, deuda visible** | Se eliminaron 97 recursos muertos, PNG redundantes y 15 problemas de pluralización. Lint queda con `warningsAsErrors=true`, cero incidencias nuevas y baseline de 37 avisos exclusivamente de actualización de SDK/dependencias. Dependabot los mantiene visibles; no se presentan como “corregidos”. |
| BUILD-03 | **Resuelto** | El AAB versionado fue retirado y APK/AAB/keystores/outputs se ignoran. |
| DX-01 | **Resuelto** | Smoke script descubre SDK/dispositivo, usa timeouts, valida foreground/FATAL y guarda evidencia. |
| ARCH-01 | **Resuelto** | `BadgeEngine` pasó a dominio con puertos; el dominio ya no importa Android/data/presentation y `DomainBoundaryTest` lo impide. |
| ARCH-02 | **Parcial** | Se separaron gateways, estados de WhatsApp, schedulers y lógica de sesión; Profile/Compose sigue siendo grande y conviene modularizarlo incrementalmente sin mezclarlo con fixes de seguridad. |
| ARCH-03 | **Resuelto en caminos críticos** | Operaciones financieras/identidad tienen idempotencia, cancelación o epoch. Una mutación incierta conserva su request UUID y consulta al backend antes de acuñar otro; depósitos pendientes persisten intención exacta y se reconcilian tras recreación. `suspendRunCatching` preserva cancelación estructurada y errores externos no se vuelcan crudos en UI/logcat. |
| DOC-01 | **Resuelto** | Setup enumera `0001..0007`, gate contractual registrado `0008`, harness fresh/upgrade, validación histórica, Storage, Edge Function y contrato WhatsApp. |
| DOC-02 | **Resuelto** | README, CLAUDE y SYSTEM_DESIGN distinguen código verificable de backend/despliegue externo y ya no publican project refs. |

### Cierre de casos descubiertos durante la remediación

- La idempotencia cliente ya no reemplaza una key después de un timeout ambiguo: primero
  consulta por `request_id`; los reintentos de gasto, meta, extra y depósito conservan la
  intención original incluso tras recrear el ViewModel.
- Exportación CSV y OCR serializan la creación/borrado de archivos sensibles con los cambios
  de sesión y revalidan la identidad dentro de la sección crítica. CSV refresca usuario,
  moneda y categorías de forma autoritativa y falla cerrado si no puede obtenerlas.
- Categorías OCR/manual, extras, racha, historial y recargas mensuales distinguen carga vacía
  de fallo, preservan el último dato válido, permiten reintento y no ocultan filas que el
  backend no consiguió borrar.
- El cifrado de sesión deja un tombstone durable de migración: una sesión AES-GCM corrupta o
  un fallo al eliminar el archivo legacy no puede reactivar silenciosamente plaintext.
- Fechas OCR son editables, pero sólo dentro del rango financiero permitido; valores OCR fuera
  de rango pierden el indicador de “detectado” y nunca llegan al RPC.

### Revalidación post-remediación — 22 de julio de 2026

La primera ejecución real de la suite instrumentada descubrió un defecto que la compilación
de `androidTest` no podía detectar: Kotlin infería `Boolean` como retorno de
`nonContentUriIsRejectedWithoutCreatingAnImport()`, mientras JUnit 4 exige un método `void`.
Se fijó explícitamente el retorno `Unit` en
[`SecureOcrImageImporterInstrumentedTest.kt`](app/src/androidTest/java/com/qolve/fluyo/data/ocr/SecureOcrImageImporterInstrumentedTest.kt).
Después de recompilar y reinstalar las APK, la suite completa pasó dos veces con **11/11**
casos. No fue necesario cambiar código productivo.

La reconstrucción forzada con `--offline` también expuso que el baseline de Lint no es
hermético: sus 37 avisos de versiones contienen el número de la versión más reciente y no
coinciden cuando el caché local tiene metadatos más antiguos. `lintDebug` en el modo normal
usado por CI pasa con cero incidencias nuevas. Se conserva esta limitación como deuda de
reproducibilidad; no se alteró la política de actualizaciones basándose en un caché obsoleto.

### Verificación final reproducida sobre el árbol remediado

| Verificación | Resultado |
|---|---|
| JVM | **154/154** pruebas; 0 fallos, 0 errores, 0 omitidas, 40 suites. |
| Cobertura | **18.4965%** de líneas de producto (1,897 cubiertas / 10,256); `koverVerifyDebug` pasa con piso 18%. Es un control de no-regresión, no cobertura suficiente para producción. |
| Lint | `lintDebug` pasa con cero incidencias nuevas; quedan **37** avisos versionados: 20 `GradleDependency`, 14 `NewerVersionAvailable`, 2 `AndroidGradlePluginVersion` y 1 `OldTargetApi`. |
| Android build | Desde `clean` se regeneraron código/recursos y pasaron `testDebugUnitTest`, Kover, `lintDebug`, `assembleDebug` y `assembleDebugAndroidTest`. Separados pasan `bundleReleaseUnsigned` y `bundleRelease` con JKS efímero, R8, resource shrinking y lint vital. El AAB firmado contiene el certificado efímero esperado. |
| Android instrumentado | Emulador Google APIs x86_64, API 35: **11/11** pruebas, 0 fallos, 0 errores y 0 omitidas. Cubren sesión cifrada, borrado fail-closed, importación OCR segura, parser de voz/ICU y contexto de aplicación. |
| Smoke Android | La APK reconstruida se instala y abre `MainActivity`; conserva el mismo PID, queda en primer plano y no registra `FATAL EXCEPTION` ni ANR. La captura es negra por el `FLAG_SECURE` deliberado. |
| Gate de distribución | Rechaza configuración ausente, `.test`, IPv4 privada y `::1`; acepta hostname público e IPv6 público/self-hosted. No hace DNS/HTTP ni afirma que el destino esté publicado. |
| PostgreSQL 17 | Fresh DB y upgrade `0001..0005 → fixture legacy → 0006..0007 → reparación`: contrato `0008` por SHA-256, deriva, fechas, constraints, Storage/tombstone, ocho badges/RPC, paginación snapshot, retención WhatsApp indexada, categorías, RLS/ACL A↔B, agregados y carrera moneda/primer movimiento: **pasan**. |
| Edge Function | Deno 2.5.7: formato (4 archivos), lint (3), `check --all --frozen` y **12/12** tests: **pasa**. Lockfile v5 congelado. |
| Secretos | Gitleaks v8.30.1 escanea **41 commits** y las 301 fuentes versionadas/no ignoradas actuales sin hallazgos no exceptuados. Existe una sola excepción por fingerprint para SEC-01; la regla custom sigue bloqueando nuevas passwords `keytool`. |
| Estáticos | `git diff --check`, `bash -n` (4 scripts) y parseo de YAML (2), JSON (3), TOML (2) y XML (23): **pasan**; cero conflictos y cero secretos vigentes reconocibles en el árbol. |
| Estado Git | La remediación está en `a59ec6057d335cbbb4fc55b77060cc67833c925a`; `main` está un commit delante de `origin/main`. La revalidación deja sólo **2 archivos modificados sin staging**: esta evidencia y la firma `Unit` del test instrumentado; cero conflictos. |

En esta reproducción local sí se ejecutaron pruebas Android y smoke en un emulador API 35.
No se desplegó Supabase, el backend externo de WhatsApp ni un release con credenciales
reales. Sí se produjo localmente un release firmado con credenciales efímeras; el workflow
repite esa ruta. Esto todavía no equivale a journeys Compose E2E, una matriz de dispositivos,
integraciones remotas ni validar Play/producción. La huella
histórica SEC-01 permanece explícitamente exceptuada hasta que la rotación y purga permitan
retirar [`.gitleaksignore`](.gitleaksignore).

### Acciones que no pueden completarse sólo modificando este repositorio

1. Rotar el secreto expuesto, revisar la upload key, coordinar reescritura de historial
   y exigir nuevos clones; no se hizo force-push ni una operación destructiva sin autorización.
2. Auditar/reparar datos históricos, validar constraints, desplegar `0006..0007`, retirar
   clientes legacy y aplicar el contrato `0008` con su script/registro en cada entorno.
3. Implementar/auditar/desplegar el backend WhatsApp: autenticidad de Meta, challenge,
   custodia de `service_role`, idempotencia, cuotas, tombstone de borrado y retención.
4. Configurar secretos y desplegar/probar `delete-account` contra ese cleanup externo.
5. Publicar términos, privacidad y página web de borrado; configurar los HTTPS reales.
6. Proveer un dominio y `assetlinks.json` para migrar el callback a App Link verificado.
7. Configurar Play App Signing/release SHA y ampliar la instrumentación a UI/E2E y dispositivos físicos.
8. Auditar la configuración alojada de Supabase Auth: redirect allow-list, Site URL,
   confirmación de correo, CAPTCHA/rate limits, política de contraseña y proveedores OAuth.
9. Decidir si badges/gamificación son sólo decorativos; fechas y evidencia controladas por
   el usuario no son una base antifraude válida para otorgar valor económico.

### Deuda residual no bloqueante del código

- La cobertura de 18.4965% sigue siendo baja, especialmente en Compose, Keystore y flujos
  Activity Result. El piso evita retroceder, pero no sustituye ampliar tests.
- `ProfileScreen`/`ProfileViewModel` siguen siendo grandes aunque sus efectos críticos ya
  estén separados y ligados a sesión; conviene dividirlos por capacidad en cambios futuros.
- SDK y varias dependencias tienen versiones posteriores. Se conservaron en el baseline
  porque una actualización mayor exige migración y pruebas reales; Dependabot abre revisiones
  semanales en vez de mezclar ese riesgo con la remediación de seguridad.
- El baseline de avisos de versión de Lint depende de metadatos remotos y puede fallar en un
  build forzado con `--offline` si el caché conoce versiones distintas. Para builds offline
  herméticos conviene sacar esos detectores del baseline y delegar su seguimiento a Dependabot.
- La protección global `FLAG_SECURE` prioriza privacidad e impide screenshots/recording en la
  app; es un trade-off de producto documentado, no un fallo accidental.

---

> [!NOTE]
> Desde este punto se conserva la auditoría original del 21 de julio como evidencia de
> línea base. Sus frases en presente y recomendaciones no sustituyen el estado actualizado,
> las verificaciones ni los límites operativos documentados arriba.

## 1. Resumen ejecutivo

Fluyo es una aplicación Android nativa bien encaminada para un MVP: utiliza Kotlin, Jetpack Compose, Hilt, MVVM y una separación razonable entre presentación, dominio y datos. Supabase actúa como backend de autenticación y persistencia, mientras que OCR, voz, presupuestos, metas, estadísticas, badges y nudges componen el producto principal.

El proyecto compila y las pruebas JVM actuales pasan. Sin embargo, **no debería considerarse listo para producción** en su estado actual. Los principales bloqueadores son:

1. Una contraseña de firma release está versionada en texto claro.
2. La configuración local del agente concede permisos amplios y apunta al Supabase descrito como producción.
3. La eliminación de cuenta puede informar éxito sin eliminar realmente la cuenta.
4. La vinculación de WhatsApp no demuestra la propiedad del teléfono y admite colisiones entre formatos equivalentes.
5. Los depósitos a metas no son atómicos y pueden perder actualizaciones.
6. El aislamiento de sesión es incompleto y puede conservar datos del usuario anterior.
7. Varias reglas financieras y de negocio existen solo en el cliente, no en la base de datos.
8. La multi-moneda relabela importes sin conversión y el dinero se representa mediante `Double`.
9. El build predeterminado no es portable y el release puede generarse sin firma sin fallar.
10. No existe CI, la cobertura es limitada y la guía de Supabase omite migraciones necesarias.

### Evaluación general

| Área | Evaluación | Observación principal |
|---|---|---|
| Arquitectura | Aceptable para MVP | Separación clara por paquetes, pero sin límites modulares reales |
| Seguridad | Requiere atención inmediata | Secreto versionado y permisos locales excesivos |
| Identidad | Riesgo alto | WhatsApp no verifica propiedad del teléfono |
| Privacidad | Incompleta | Borrado, backups y transparencia no sustentan las promesas actuales |
| Integridad financiera | Riesgo alto | Depósitos no atómicos, `Double` y multi-moneda incorrecta |
| Base de datos | Parcialmente endurecida | Buen RLS inicial, pero faltan invariantes y ownership relacional |
| Calidad | Funcional, con deuda | 36 pruebas JVM aprobadas, pero cobertura estrecha y 164 warnings de lint |
| Build/release | No portable | Ruta JDK de macOS y bundle release sin validación obligatoria de firma |
| Operación | Inmadura | Sin CI/CD, separación de ambientes ni backend WhatsApp auditable |
| Documentación | Desactualizada | Contradicciones y migraciones omitidas |

## 2. Entendimiento del repositorio

### 2.1 Arquitectura observada

- Un único módulo Gradle Android, `:app`, declarado en [`settings.gradle.kts`](settings.gradle.kts#L25).
- Kotlin, Jetpack Compose, Hilt y coroutines/Flow.
- Separación convencional por capas:
  - `presentation`: pantallas, navegación y ViewModels.
  - `domain`: modelos, contratos de repositorio y casos de uso.
  - `data`: DTO, mappers, repositorios Supabase y cachés.
- Supabase proporciona Auth, PostgREST y Storage.
- Acceso directo desde Android mediante clave anónima y Row Level Security.
- OCR procesado localmente y confirmación del usuario antes de guardar.
- Entrada de voz mediante `RecognizerIntent` del sistema.
- WorkManager para recordatorios y evaluación parcial de badges.
- Presupuestos, ingresos extra, metas de ahorro, estadísticas y exportación CSV.

El dominio no importa APIs Android, lo que constituye una separación útil. Sin embargo, al existir un solo módulo, los límites dependen de convenciones y no son verificados por Gradle.

### 2.2 Componentes fuera del repositorio

[`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md#L197) describe un backend NestJS para WhatsApp, procesamiento de audio, uso de `service_role` y almacenamiento S3. Ese código no está presente. Por ello no fue posible validar:

- autenticidad y firma de webhooks;
- selección de usuario por teléfono;
- aislamiento multiusuario;
- uso y custodia de `service_role`;
- rate limiting y protección contra abuso;
- borrado y retención de audios;
- idempotencia de mensajes y gastos;
- contratos reales entre backend y esquema Supabase.

## 3. Hallazgos críticos y de severidad alta

### SEC-01 — Contraseña de firma release versionada

**Severidad:** crítica  
**Evidencia:** [`.claude/settings.local.json`](.claude/settings.local.json#L30)

El archivo contiene una contraseña de keystore en texto claro y está versionado en Git. La credencial también forma parte del historial. El repositorio remoto es privado y no se encontró el keystore dentro del árbol u objetos inspeccionados, pero cualquier colaborador o copia histórica puede conocer la contraseña.

**Impacto:** si alguien obtiene el keystore, o si la contraseña fue reutilizada, podría firmar artefactos maliciosos o acceder a otros recursos protegidos con la misma credencial.

**Acciones recomendadas:**

1. Rotar inmediatamente la contraseña y revisar si fue reutilizada.
2. Evaluar la rotación de la clave de subida de Google Play cuando corresponda.
3. Retirar `settings.local.json` del índice y añadirlo a `.gitignore`.
4. Purgar el secreto del historial de forma coordinada después de rotarlo.
5. Invalidar clones o credenciales derivadas que deban considerarse comprometidas.

### SEC-02 — Configuración del agente con privilegios sobre producción

**Severidad:** alta  
**Evidencia:** [`.claude/settings.local.json`](.claude/settings.local.json#L10), [`.mcp.json`](.mcp.json#L5)

La configuración autoriza, entre otras operaciones, migraciones, SQL arbitrario, instalaciones de paquetes, lecturas amplias del directorio personal y todos los MCP del proyecto. El MCP de Supabase apunta directamente al proyecto descrito en la documentación como producción.

**Impacto:** una instrucción maliciosa o un error del agente puede leer información local o modificar la base sin una confirmación adicional. No hay separación visible entre desarrollo, staging y producción.

**Acciones recomendadas:**

- No versionar la configuración local del agente.
- Aplicar permisos mínimos y acceso de solo lectura por defecto.
- Separar proyectos y credenciales de desarrollo, staging y producción.
- Promover migraciones mediante CI, revisión y credenciales específicas del entorno.

### ID-01 — Vinculación de WhatsApp sin prueba de propiedad

**Severidad:** alta  
**Evidencia:** [`OnboardingViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/onboarding/OnboardingViewModel.kt#L59), [`ProfileViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/profile/ProfileViewModel.kt#L246), [`0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql#L15), [`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md#L422)

La aplicación acepta cualquier teléfono y únicamente filtra dígitos y longitud. La base aplica `UNIQUE` sobre el texto crudo, mientras el diseño del backend indica que se buscarán varias representaciones del mismo número.

Dos cuentas pueden registrar valores equivalentes, por ejemplo un número local y el mismo número con código de país. Una persona también podría registrar el teléfono de otra antes de que la víctima lo haga.

**Impacto:** gastos o respuestas de WhatsApp pueden terminar asociados a la cuenta incorrecta.

**Solución recomendada:**

1. Generar en la aplicación autenticada un nonce de un solo uso ligado al usuario.
2. Incluirlo en un mensaje `wa.me`.
3. Verificar el nonce usando el número remitente real del webhook.
4. Canonicalizar el número a E.164 en servidor.
5. Guardar `verified_at` y aplicar unicidad al valor canónico.

### PRIV-01 — La eliminación de cuenta puede no eliminar datos ni identidad

**Severidad:** alta  
**Evidencia:** [`0002_rls_policies.sql`](supabase/migrations/0002_rls_policies.sql#L15), [`SupabaseAuthRepository.kt`](app/src/main/java/com/qolve/fluyo/data/repository/SupabaseAuthRepository.kt#L87), [`AuthRepository.kt`](app/src/main/java/com/qolve/fluyo/domain/repository/AuthRepository.kt#L22), [`strings.xml`](app/src/main/res/values/strings.xml#L21)

Las migraciones crean políticas `SELECT`, `INSERT` y `UPDATE` para `public.users`, pero no `DELETE`. El cliente intenta borrar la fila y luego cierra sesión sin comprobar cuántas filas fueron afectadas. Bajo RLS, la operación puede finalizar sin error y afectar cero filas.

Incluso si se permitiera el `DELETE`, el propio contrato reconoce que `auth.users` permanece. El usuario puede volver a autenticarse y recrear su perfil. Tampoco se eliminan explícitamente recursos del backend WhatsApp, S3 o Storage.

**Solución recomendada:** una Edge Function o endpoint autenticado que verifique el JWT, elimine el usuario mediante la API administrativa de Supabase, deje actuar las cascadas, limpie recursos externos y devuelva un resultado verificable e idempotente.

Referencias externas:

- [Supabase Admin Delete User](https://supabase.com/docs/reference/javascript/auth-admin-deleteuser)
- [Requisitos de eliminación de cuentas de Google Play](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-EN)

### FIN-01 — Depósitos a metas no atómicos

**Severidad:** alta  
**Evidencia:** [`SupabaseGoalRepository.kt`](app/src/main/java/com/qolve/fluyo/data/repository/SupabaseGoalRepository.kt#L91), [`0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql#L69)

El flujo ejecuta cuatro pasos independientes:

1. inserta el depósito;
2. vuelve a leer la meta;
3. calcula el nuevo saldo;
4. actualiza `current_amount`.

Dos depósitos concurrentes pueden leer el mismo saldo y perder una actualización. Un fallo tras insertar el depósito puede dejar el historial y `current_amount` divergentes; un reintento también puede duplicar el depósito.

**Solución recomendada:** implementar una función PostgreSQL/RPC transaccional que valide ownership, inserte el movimiento y ejecute un incremento atómico con `RETURNING`; alternativamente, derivar el saldo de `SUM(goal_deposits.amount)`.

### AUTH-01 — Aislamiento de sesión incompleto

**Severidad:** alta  
**Evidencia:** [`SupabaseAuthRepository.kt`](app/src/main/java/com/qolve/fluyo/data/repository/SupabaseAuthRepository.kt#L40), [`SessionCacheModule.kt`](app/src/main/java/com/qolve/fluyo/di/SessionCacheModule.kt#L34)

Los fallos de refresh o ausencia de autenticación se traducen a `SignedOut`, pero todos los cachés solo se limpian durante un `signOut()` explícito. El estado de gastos, categorías, metas, badges, onboarding, moneda y nudges puede sobrevivir a una pérdida automática de sesión o cambio de usuario.

**Impacto:** el usuario B podría ver temporalmente información del usuario A, heredar configuración o saltarse onboarding.

**Solución recomendada:** coordinador de sesión en el nivel raíz que limpie todo estado en cualquier pérdida o cambio de identidad, clave las preferencias persistidas por `user_id` e invalide solicitudes en curso con una generación de sesión.

### AUTH-02 — Carrera entre autenticación y creación del perfil

**Severidad:** alta  
**Evidencia:** [`StartRouteReducer.kt`](app/src/main/java/com/qolve/fluyo/presentation/navigation/StartRouteReducer.kt#L15), [`FluyoNavHost.kt`](app/src/main/java/com/qolve/fluyo/presentation/navigation/FluyoNavHost.kt#L76), [`LoginViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/auth/LoginViewModel.kt#L36), [`EmailAuthViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/auth/EmailAuthViewModel.kt#L81)

La navegación raíz responde inmediatamente al evento `SignedIn`. La creación de `public.users` ocurre después dentro del ViewModel de la pantalla de autenticación. Al eliminar esa pantalla del back stack, la coroutine de aprovisionamiento puede cancelarse y dejar una sesión válida sin perfil público.

**Solución recomendada:** modelar explícitamente `Authenticated → Provisioning → Ready` en un coordinador raíz, o crear el perfil con un trigger seguro sobre `auth.users`. No navegar hasta completar el aprovisionamiento.

### AUTH-03 — Flujo incompleto al activar confirmación de correo

**Severidad:** alta si se habilita la confirmación en producción  
**Evidencia:** [`SUPABASE_SETUP.md`](docs/SUPABASE_SETUP.md#L52), [`EmailAuthViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/auth/EmailAuthViewModel.kt#L77)

La documentación indica desactivar la confirmación para desarrollo y reactivarla en producción. No obstante, el ViewModel intenta crear inmediatamente `public.users` después del registro. Cuando la confirmación está activa, Supabase no inicia automáticamente una sesión completa. Tampoco existe un flujo visible de espera, OTP o deep link de confirmación.

**Solución recomendada:** agregar estado de verificación pendiente, manejo de deep links/OTP y aprovisionar el perfil solo después de una autenticación confirmada, o utilizar un trigger seguro.

Referencia: [Supabase Kotlin sign-up](https://supabase.com/docs/reference/kotlin/v2/auth-signup).

## 4. Integridad de datos y lógica financiera

### DATA-01 — Ownership relacional y constraints insuficientes

**Severidad:** media-alta  
**Evidencia:** [`0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql#L46), [`0002_rls_policies.sql`](supabase/migrations/0002_rls_policies.sql#L39)

RLS comprueba el `user_id` de la fila principal, pero no garantiza que `expenses.category_id` o `goal_deposits.goal_id` pertenezcan al mismo usuario. Las FK no incluyen `user_id` y varios campos críticos admiten `NULL` o valores de negocio inválidos.

Faltan, entre otras, garantías para:

- montos mayores que cero;
- presupuesto y metas no negativos;
- moneda dentro de un catálogo válido;
- hora de notificación dentro del rango permitido;
- ownership compuesto de categorías y metas;
- límites de longitud y cuota;
- límites de metas activas;
- control server-side de puntos, nivel y badges.

Las reglas críticas deben trasladarse a `NOT NULL`, `CHECK`, FK compuestas, grants de columna, RPC o triggers.

### FIN-02 — Dinero representado con `Double`

**Severidad:** media  
**Evidencia:** [`Expense.kt`](app/src/main/java/com/qolve/fluyo/domain/model/Expense.kt#L6), [`Goal.kt`](app/src/main/java/com/qolve/fluyo/domain/model/Goal.kt#L6), [`MonthlyBreakdown.kt`](app/src/main/java/com/qolve/fluyo/domain/model/MonthlyBreakdown.kt#L3)

Los modelos y cálculos monetarios usan coma flotante binaria. Esto puede introducir errores acumulativos en sumas, comparaciones, depósitos y porcentajes cercanos a límites.

**Solución recomendada:** representar importes mediante centavos como `Long` o un tipo `Money` basado en `BigDecimal`, con escala y redondeo explícitos.

### FIN-03 — Multi-moneda implementada solo como relabeling

**Severidad:** alta por integridad semántica  
**Evidencia:** [`0001_initial_schema.sql`](supabase/migrations/0001_initial_schema.sql#L16), [`ProfileViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/profile/ProfileViewModel.kt#L304), [`Money.kt`](app/src/main/java/com/qolve/fluyo/presentation/util/Money.kt#L21)

La moneda vive globalmente en el perfil, mientras cada gasto carece de moneda y tipo de cambio. Cambiar de PEN a USD solo cambia el símbolo: S/100 históricos pasan a mostrarse como $100 sin conversión. La exportación CSV también etiqueta todo el historial con la moneda actual.

**Alternativas válidas:**

- mantener una moneda base inmutable por cuenta; o
- guardar moneda original, monto original, monto base y tasa aplicada por transacción.

### TIME-01 — Cambio mensual incorrecto para Lima

**Severidad:** media-alta  
**Evidencia:** [`0005_budget_extras.sql`](supabase/migrations/0005_budget_extras.sql#L45)

La propia migración reconoce que `current_date` del servidor puede cambiar de mes aproximadamente a las 19:00 del último día en Lima. La aplicación, en cambio, crea fechas según el dispositivo. Home puede mostrar el mes siguiente mientras el usuario sigue en el mes anterior.

**Solución recomendada:** usar consistentemente `America/Lima`, o consultar por un rango/mes explícito calculado con una única fuente de tiempo.

### DATA-02 — Historial, estadísticas y CSV sin paginación

**Severidad:** media  
**Evidencia:** [`SupabaseExpenseRepository.kt`](app/src/main/java/com/qolve/fluyo/data/repository/SupabaseExpenseRepository.kt#L138), [`AllExpensesViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/expense/AllExpensesViewModel.kt#L24), [`ProfileViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/profile/ProfileViewModel.kt#L321)

Las consultas de rango descargan todos los gastos en una sola respuesta. Con el `max_rows` predeterminado de PostgREST/Supabase, normalmente 1000, el historial, las estadísticas y la exportación pueden truncarse sin aviso.

**Solución recomendada:** paginación estable por fecha e ID, agregados mediante RPC/vistas y exportación por páginas o streaming.

Referencia: [configuración local de Supabase](https://supabase.com/docs/guides/local-development/cli/config).

### STATS-01 — Carreras y promedio semanal inflado

**Severidad:** media  
**Evidencia:** [`StatsViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/stats/StatsViewModel.kt#L93)

Cada cambio de período lanza una nueva carga sin cancelar la anterior. Una respuesta lenta del período previo puede sobrescribir el período seleccionado. Además, el patrón semanal divide entre fechas que tuvieron gastos, no entre todas las ocurrencias del día de la semana en el rango, inflando el promedio.

**Solución recomendada:** `flatMapLatest`, cancelación o identificador de solicitud, y denominadores derivados del calendario completo del período.

## 5. Defectos funcionales adicionales

### APP-01 — Los PATCH no pueden limpiar correctamente campos nullable

**Severidad:** media  
**Evidencia:** [`UserDto.kt`](app/src/main/java/com/qolve/fluyo/data/dto/UserDto.kt#L33), [`ExpenseDto.kt`](app/src/main/java/com/qolve/fluyo/data/dto/ExpenseDto.kt#L32), [`SupabaseModule.kt`](app/src/main/java/com/qolve/fluyo/di/SupabaseModule.kt#L23)

Los DTO de actualización definen propiedades nullable con `null` como valor predeterminado. Kotlin serialization omite por defecto los valores iguales al default. Por ello, intentar borrar el teléfono o vaciar una descripción puede omitir la columna y conservar el valor anterior.

**Solución recomendada:** payload triestado, `JsonObject` con `JsonNull` explícito o métodos de actualización específicos; añadir pruebas sobre el JSON generado.

### APP-02 — Bus de eventos propenso a perder confirmaciones

**Severidad:** media  
**Evidencia:** [`AppEvents.kt`](app/src/main/java/com/qolve/fluyo/presentation/events/AppEvents.kt#L18), [`FluyoNavHost.kt`](app/src/main/java/com/qolve/fluyo/presentation/navigation/FluyoNavHost.kt#L207)

El `SharedFlow` usa `replay = 0`, aunque el comentario afirma que funciona sin collector. El collector vive en `MainShell`, que normalmente no está activo cuando las pantallas Manual/OCR emiten antes de hacer `pop`. El snackbar o evento de badge puede perderse.

**Solución recomendada:** collector permanente en la raíz, resultado de navegación mediante `SavedStateHandle` o un canal con acknowledgment.

### APP-03 — Badges mensuales acoplados a notificaciones

**Severidad:** media  
**Evidencia:** [`NudgeWorker.kt`](app/src/main/java/com/qolve/fluyo/notifications/NudgeWorker.kt#L32), [`RootViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/navigation/RootViewModel.kt#L72), [`BadgeEngine.kt`](app/src/main/java/com/qolve/fluyo/data/badge/BadgeEngine.kt#L104)

Los badges de fin de mes se evalúan desde el worker de nudges. Si el usuario desactiva notificaciones, el worker se cancela. Además, la evaluación exige ejecutarse exactamente el último día; un dispositivo apagado o sin red puede perder el badge definitivamente.

**Solución recomendada:** separar logros de notificaciones y ejecutar una evaluación idempotente con catch-up por cada mes cerrado.

### APP-04 — Inyección de fórmulas en CSV

**Severidad:** media-baja  
**Evidencia:** [`CsvExporter.kt`](app/src/main/java/com/qolve/fluyo/presentation/util/CsvExporter.kt#L31)

El exportador escapa delimitadores y comillas, pero no neutraliza texto que comienza con `=`, `+`, `-` o `@`. Descripciones provenientes del usuario, OCR o WhatsApp pueden interpretarse como fórmulas al abrir el CSV en una hoja de cálculo.

**Solución recomendada:** neutralizar esos prefijos en campos textuales, incluso después de espacios iniciales, o utilizar un formato que conserve tipos.

### APP-05 — Recurso de icono de notificación incorrecto

**Severidad:** baja  
**Evidencia:** [`BadgeNotifier.kt`](app/src/main/java/com/qolve/fluyo/notifications/BadgeNotifier.kt#L55), [`NudgeWorker.kt`](app/src/main/java/com/qolve/fluyo/notifications/NudgeWorker.kt#L56)

El código busca dinámicamente `ic_stat_nudge`, pero el recurso se llama `ic_stat_name`. Siempre termina usando el launcher como fallback.

**Solución recomendada:** unificar el nombre y referenciar directamente `R.drawable.ic_stat_nudge`.

## 6. Privacidad y transparencia

### PRIV-02 — Sesiones no cifradas explícitamente y admitidas en backup

**Severidad:** media  
**Evidencia:** [`SupabaseModule.kt`](app/src/main/java/com/qolve/fluyo/di/SupabaseModule.kt#L27), [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml#L19), [`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml#L8), [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml#L6), [`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md#L550)

Supabase Auth usa el gestor de sesión predeterminado. En Android, la versión inspeccionada termina persistiendo la sesión en preferencias sin una implementación de cifrado propia. El backup está habilitado y las reglas están prácticamente vacías. Esto contradice la documentación interna que afirma usar preferencias cifradas mediante Keystore.

El sandbox de Android y el cifrado de Auto Backup reducen el riesgo, pero una restauración comprometida, dispositivo rooteado o extracción forense puede exponer un refresh token.

**Solución recomendada:** gestor de sesión respaldado por Keystore/Tink, almacenamiento fuera de backup y exclusiones explícitas para cloud y transferencia dispositivo a dispositivo.

Referencia: [Android Auto Backup](https://developer.android.com/identity/data/autobackup?hl=en).

### PRIV-03 — Términos, voz y retención de audios

**Severidad:** media  
**Evidencia:** [`LoginScreen.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/auth/LoginScreen.kt#L208), [`SYSTEM_DESIGN.md`](SYSTEM_DESIGN.md#L585), [`README.md`](README.md#L199)

- El texto de términos y privacidad está subrayado, pero no funciona como enlace.
- No hay documentos de términos o privacidad dentro de la aplicación.
- El reconocimiento de voz se delega a una actividad externa y puede utilizar procesamiento en red según el proveedor del dispositivo.
- La documentación admite que los audios de WhatsApp permanecen en S3 sin un plazo claro de retención.
- El README afirma cumplimiento íntegro de la Ley 29733, pero el repositorio no contiene evidencia suficiente para sustentar esa afirmación.

No se declara aquí una infracción legal definitiva. Sí existe un riesgo de transparencia, cumplimiento y revisión de tienda que debe resolverse con asesoría adecuada, una política accesible, consentimiento informado, retención definida y borrado coordinado.

### PRIV-04 — Residuos locales de recibos

**Severidad:** baja  
**Evidencia:** [`FluyoNavHost.kt`](app/src/main/java/com/qolve/fluyo/presentation/navigation/FluyoNavHost.kt#L243), [`OcrConfirmViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/scan/OcrConfirmViewModel.kt#L171)

Las capturas se guardan en `cacheDir/captures` y no se eliminan explícitamente al terminar o cancelar. También se persiste remotamente como `image_url` un `content://` local que no será útil fuera del dispositivo.

**Solución recomendada:** eliminar las capturas al finalizar el flujo y no guardar remotamente URI locales efímeras.

## 7. Build, release, pruebas y CI

### BUILD-01 — El build predeterminado no es portable

**Severidad:** alta para desarrollo y CI  
**Evidencia:** [`gradle.properties`](gradle.properties#L20), [`gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties#L1)

`gradle.properties` fija `org.gradle.java.home` a una ruta exclusiva de Android Studio en macOS. `./gradlew` falla en Linux, Windows, otros Macs y runners CI aunque exista un JDK 21 válido. El repositorio ya contiene selección portable del JDK para el daemon.

**Solución recomendada:** eliminar la ruta del archivo versionado, mantener cualquier override únicamente en configuración local y documentar JDK 21/JAVA_HOME.

### BUILD-02 — Release falla de forma abierta respecto a la firma

**Severidad:** alta operacional  
**Evidencia:** [`app/build.gradle.kts`](app/build.gradle.kts#L41)

Los comentarios indican que un release debe fallar si faltan credenciales, pero la firma se adjunta condicionalmente. En la verificación, `:app:bundleRelease` terminó con éxito y produjo un AAB sin entradas de firma.

**Impacto:** CI o automatización podría tratar como publicable un artefacto que Google Play rechazará.

**Solución recomendada:** hacer obligatorias todas las propiedades de firma cuando se solicite un bundle de distribución, o crear una variante/tarea separada llamada explícitamente `unsigned`.

### CI-01 — No existen puertas automáticas de calidad

**Severidad:** alta operacional  

No existe `.github/workflows/` ni otra configuración de CI. Tampoco hay Dependabot/Renovate, Kover/JaCoCo, umbral de cobertura o pruebas automatizadas de migraciones/RLS.

Una CI mínima debería ejecutar:

```bash
./gradlew \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleDebugAndroidTest
```

Debería usar JDK 21, Android SDK explícito, caché Gradle y pruebas instrumentadas con emulador o dispositivo administrado cuando sea viable.

### TEST-01 — Cobertura estrecha y sin medición

**Severidad:** media  

Inventario durante la auditoría:

- 127 archivos Kotlin productivos y aproximadamente 14 827 líneas.
- 8 archivos de pruebas JVM y 2 instrumentados.
- 36 pruebas JVM.
- Al menos una prueba placeholder (`2 + 2 = 4`).
- Sin reporte ni umbral de cobertura.

No hay pruebas directas suficientes para repositorios Supabase, ViewModels, migraciones/RLS, concurrencia de depósitos, serialización de PATCH, nudges complejos o flujos Compose principales.

### LINT-01 — 164 advertencias sin política de deuda

**Severidad:** media  

`lintDebug` finalizó con 0 errores y 164 advertencias, principalmente:

- 101 recursos sin usar;
- 20 dependencias Gradle actualizables;
- 18 candidatos a plurales;
- 14 versiones nuevas disponibles;
- advertencias adicionales de APIs, iconos, etiquetas y KTX.

**Solución recomendada:** corregir primero las advertencias funcionales e internacionalización, crear un baseline solo para deuda aceptada y hacer que CI impida nuevas advertencias de categorías seleccionadas.

### BUILD-03 — AAB pesado y obsoleto versionado

**Severidad:** media  
**Evidencia:** [`app/release/app-release.aab`](app/release/app-release.aab), [`app/.gitignore`](app/.gitignore#L1), [`app/build.gradle.kts`](app/build.gradle.kts#L31)

El AAB versionado pesa aproximadamente 35 MB y representa prácticamente todo el pack Git. Su metadata corresponde a una revisión y versión anteriores al código actual.

**Solución recomendada:** retirar binarios de control de versiones, ignorar `*.aab` o `app/release/` y distribuirlos mediante GitHub Releases, CI o el sistema de entrega correspondiente.

### DX-01 — Smoke test frágil

**Severidad:** baja-media  
**Evidencia:** [`scripts/smoke-test.sh`](scripts/smoke-test.sh#L10)

El script antepone una ruta de `adb` propia de macOS, espera dispositivos sin timeout y busca cualquier `FATAL EXCEPTION` del logcat completo, no exclusivamente del proceso de Fluyo.

**Solución recomendada:** resolver `adb` desde `ANDROID_HOME`/PATH, añadir timeouts, validar dispositivo/APK y filtrar logcat por PID.

## 8. Arquitectura y mantenibilidad

### ARCH-01 — Límites de Clean Architecture solo convencionales

**Severidad:** media  

Existe un solo módulo. Además, algunas clases de `data` dependen de conceptos de notificaciones/presentación y la capa de presentación importa implementaciones concretas de datos. Esto forma ciclos conceptuales y dificulta verificar dependencias.

**Solución recomendada:** evolucionar gradualmente hacia módulos `domain`, `data` y `app`/features, o al menos aplicar reglas automáticas de arquitectura.

### ARCH-02 — ViewModels y pantallas con demasiadas responsabilidades

**Severidad:** media  
**Evidencia:** [`ProfileViewModel.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/profile/ProfileViewModel.kt#L75), [`ProfileScreen.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/profile/ProfileScreen.kt), [`StatsScreen.kt`](app/src/main/java/com/qolve/fluyo/presentation/screens/stats/StatsScreen.kt)

`ProfileViewModel` mezcla perfil, presupuesto, ingresos extra, teléfono, moneda, CSV, eliminación y notificaciones. Parte de la lógica de presupuesto está duplicada con Home. `ProfileScreen` y `StatsScreen` son archivos especialmente grandes.

**Solución recomendada:** interactores por feature, un controlador compartido de presupuesto y división de pantallas en componentes enfocados.

### ARCH-03 — Manejo inconsistente de concurrencia y errores

**Severidad:** media  

Varias operaciones convierten fallos en listas vacías o ceros, ignoran `Result` o no cancelan cargas anteriores. `NudgeWorker` responde éxito incluso ante ciertos fallos, evitando reintentos. Esto confunde “sin datos” con “no fue posible consultar”.

**Solución recomendada:** estados explícitos `Loading/Data/Error`, errores tipados, single-flight o `flatMapLatest` y políticas de retry según operación.

## 9. Documentación y reproducibilidad

### DOC-01 — La guía de Supabase deja una instalación incompleta

**Severidad:** alta operacional  
**Evidencia:** [`SUPABASE_SETUP.md`](docs/SUPABASE_SETUP.md#L24), [`README.md`](README.md#L170), [`0005_budget_extras.sql`](supabase/migrations/0005_budget_extras.sql#L1)

La guía manual ordena aplicar únicamente `0001` y `0002`. Omite:

- `0003_security_hardening.sql`;
- `0004_category_ondelete_setnull.sql`;
- `0005_budget_extras.sql`.

Seguirla deja fuera endurecimiento de seguridad y objetos requeridos por ingresos extra/presupuesto.

**Solución recomendada:** usar `supabase db push` o indicar inequívocamente que deben aplicarse todas las migraciones en orden; comprobarlo desde CI.

### DOC-02 — README y SYSTEM_DESIGN contradictorios

**Severidad:** baja-media  

Ejemplos:

- README declara multi-moneda mientras `SYSTEM_DESIGN.md` la marca como no objetivo.
- README menciona Vico, pero no existe esa dependencia.
- El diseño afirma usar sesiones cifradas, pero no hay implementación que lo sustente.
- El inventario de archivos y la lista de limitaciones están desactualizados.
- Las migraciones enumeradas no incluyen todas las existentes.

**Solución recomendada:** eliminar datos volátiles o generarlos desde el código y exigir actualización documental en cada PR que altere arquitectura, schema o funcionalidades.

## 10. Verificación ejecutada

La ruta JDK versionada y la ausencia inicial de Android SDK impedían ejecutar Gradle directamente. Se utilizaron JDK 21, Gradle y Android SDK 36/36.1 temporales en `/tmp`, sin modificar fuentes del proyecto, para ejecutar:

```bash
:app:testDebugUnitTest
:app:assembleDebugAndroidTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:bundleRelease
```

### Resultados

- **Build combinado:** exitoso en aproximadamente 5 minutos y 32 segundos.
- **Pruebas JVM:** 36 aprobadas, 0 fallos, 0 errores, 0 omitidas.
- **Pruebas instrumentadas:** 4 compilaron; no se ejecutaron porque no había emulador o dispositivo.
- **Debug APK:** compiló correctamente.
- **Release APK:** compiló correctamente.
- **Android Lint:** 0 errores y 164 advertencias.
- **Kotlin:** advertencia de API experimental por `resetReplayCache()` en [`SharedImageEvents.kt`](app/src/main/java/com/qolve/fluyo/presentation/events/SharedImageEvents.kt#L33).
- **Smoke script:** sintaxis Bash válida; no se ejecutó contra dispositivo.
- **Release bundle sin credenciales:** compiló y produjo un AAB sin firma.

Estos resultados demuestran que el código compila, pero no validan comportamiento en dispositivo, estado remoto de Supabase ni integración real de WhatsApp.

## 11. Controles positivos encontrados

- Dominio sin imports Android.
- Separación entre DTO, modelos y mappers.
- Repositorios definidos mediante interfaces.
- RLS habilitado en todas las tablas declaradas.
- Vistas corregidas con `security_invoker`.
- Función `SECURITY DEFINER` con `search_path` fijado y ejecución revocada donde corresponde.
- `local.properties` ignorado.
- No se encontraron claves `service_role`, claves privadas o keystores versionados.
- El AAB contiene únicamente una clave Supabase anónima, que por diseño debe depender de RLS.
- `FileProvider` no exportado y limitado a rutas concretas.
- Gradle Wrapper con SHA-256 fijado.
- OCR local y confirmación previa al guardado.
- Uso general de `StateFlow`, collection consciente del ciclo de vida y `SavedStateHandle`.
- Los parsers y parte de la matemática pura tienen pruebas útiles.

## 12. Limitaciones de la auditoría

No fue posible confirmar desde este repositorio:

- el estado real del proyecto Supabase y qué migraciones están aplicadas;
- la configuración viva de RLS, Storage, `max_rows`, email confirmation, CAPTCHA, MFA o políticas de contraseña;
- el backend WhatsApp/NestJS y sus controles de seguridad;
- S3, webhooks, rate limits y retención de audios;
- flujos completos en dispositivo, al no disponer de emulador;
- requisitos legales específicos sin revisar políticas, contratos, operación y jurisdicción con asesoría especializada.

Las carreras descritas se sustentan en la estructura del código, pero una prueba instrumentada o de concurrencia permitiría medir su frecuencia real.

## 13. Plan de remediación recomendado

### P0 — Inmediato

1. Rotar la contraseña expuesta y comprobar reutilización.
2. Retirar y purgar la configuración local versionada.
3. Reducir permisos de agentes y separar desarrollo/staging/producción.
4. Revisar quién tuvo acceso histórico al repositorio.

### P1 — Antes de producción

1. Implementar borrado real mediante backend privilegiado y limpieza de recursos externos.
2. Sustituir la edición libre del teléfono por vinculación verificada con nonce y E.164.
3. Convertir depósitos a metas en una transacción/RPC idempotente.
4. Implementar un coordinador raíz de sesión y aprovisionamiento.
5. Completar el flujo de confirmación de correo.
6. Añadir constraints, FK de ownership y reglas server-side.
7. Corregir multi-moneda y representación monetaria.

### P2 — Calidad y operación

1. Eliminar la ruta JDK versionada.
2. Hacer fallar los bundles de distribución sin firma.
3. Añadir CI con build, pruebas y lint.
4. Incorporar pruebas de repositorios, RLS, concurrencia, ViewModels y serialización.
5. Añadir paginación y agregados server-side.
6. Corregir timezone mensual y carreras de estadísticas.
7. Proteger sesiones, backups y CSV.
8. Separar la evaluación de badges de las notificaciones.

### P3 — Mantenibilidad

1. Dividir ViewModels y pantallas grandes.
2. Reducir dependencias cruzadas entre capas o modularizar.
3. Retirar el AAB de Git.
4. Corregir advertencias de lint y establecer una política de deuda.
5. Actualizar README, SYSTEM_DESIGN y la guía Supabase.
6. Incorporar o enlazar de forma versionada el backend WhatsApp para permitir auditoría integral.

## 14. Criterio final

La base técnica es razonable y el proyecto puede evolucionar sin una reescritura total. La prioridad no debe ser añadir más funcionalidades, sino cerrar primero seguridad, identidad, borrado, transacciones e invariantes de datos. Después de resolver los puntos P0 y P1, añadir CI y pruebas de integración permitirá convertir el MVP en un sistema operable con mucha mayor confianza.
