# Google OAuth Setup — Fluyo Android + Supabase

Fluyo uses **Google Sign-In via Credential Manager** (the modern One Tap
flow), backed by **Supabase Auth**. Two OAuth clients are required:

1. A **Web** client — Supabase trades the Google ID token for a Supabase session.
2. An **Android** client per signing key (debug + release) — required for
   Credential Manager to recognise the app.

## 1. Google Cloud Console

1. https://console.cloud.google.com/ → create a new project `Fluyo` (or pick
   an existing one).
2. **APIs & Services → OAuth consent screen**:
   - User type: **External**.
   - App name: `Fluyo`. Support email + dev contact email: your address.
   - Authorized domains: `supabase.co`.
   - Scopes: default (`openid`, `email`, `profile`).
   - Test users: add your Google account.

## 2. Create the **Web** OAuth client

**APIs & Services → Credentials → Create credentials → OAuth client ID**:

- Application type: **Web application**.
- Name: `Fluyo — Supabase`.
- **Authorized redirect URIs**:
  `https://<YOUR_PROJECT_REF>.supabase.co/auth/v1/callback`

Copy the **Client ID** (looks like `...apps.googleusercontent.com`) and
**Client secret**.

→ Paste both into Supabase: **Authentication → Providers → Google**, enable it, save.

→ Paste the **Web Client ID** (only) into `local.properties` as
`GOOGLE_WEB_CLIENT_ID`. This is what `ComposeAuth.googleNativeLogin(serverClientId = ...)`
passes to Credential Manager.

## 3. Create the **Android** OAuth client(s)

Credential Manager binds an OAuth client to a `(package_name, sha1)` pair.
You need one per signing key.

### Get the SHA-1 fingerprints

```bash
# debug keystore (Android Studio generates this automatically)
keytool -list -v -keystore ~/.android/debug.keystore \
    -alias androiddebugkey -storepass android -keypass android \
    | grep "SHA1:"

# release keystore (once you create one — required before Play Store upload)
keytool -list -v -keystore <path-to-release.keystore> -alias <release-alias> \
    | grep "SHA1:"

# from a Gradle task instead (debug only)
./gradlew :app:signingReport
```

### Register each fingerprint

In **Credentials → Create credentials → OAuth client ID**:

- Application type: **Android**.
- Name: `Fluyo Android — debug` (or `release`).
- Package name: `com.qolve.fluyo`.
- SHA-1 certificate fingerprint: paste the value from above.

Repeat for the release key when you create it.

No client ID needs to be copied from the Android-type credential — the
binding is implicit through the package name + SHA-1.

## 4. Verify

After running the app:

1. Tap **Continuar con Google**. Credential Manager's bottom sheet should
   appear with your test Google account.
2. Pick the account → app returns signed in.
3. In Supabase dashboard → **Authentication → Users**, a new user with your
   Google email should appear.

### Common errors

- **`No credentials available`** → Credential Manager couldn't match the
  package + SHA-1. Re-check the Android OAuth client; ensure you registered
  the **debug** SHA-1 for debug builds.
- **`Server client ID is null`** → `GOOGLE_WEB_CLIENT_ID` missing from
  `local.properties` or didn't propagate to `BuildConfig`. Run a clean build.
- **`Invalid token: nonce mismatch`** → Stale `serverClientId`. Make sure the
  ID matches the **Web** OAuth client (not Android), and that you pasted the
  same one into Supabase's Google provider.
- **`auth/google/internal-error`** in Supabase logs → Web client's redirect
  URI doesn't match the Supabase callback URL exactly.
