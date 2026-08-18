export const MAX_CONNECT_BODY_BYTES = 12 * 1_024;
export const MAX_AUTHORIZATION_CODE_LENGTH = 4_096;
export const MAX_ENCRYPTED_STATE_LENGTH = 4_096;

export type GmailConnectRequest =
  | { kind: "init"; redirectUri: string | undefined }
  | { kind: "complete"; authorizationCode: string; state: string };

export type PublicOAuthCallbackOutcome =
  | { kind: "error"; redirectUri: string; code: "access_denied" | "invalid_state" | "state_expired" | "server_error" }
  | { kind: "complete"; redirectUri: string; authorizationCode: string; state: string };

export class GmailConnectContractError extends Error {
  constructor(
    public readonly code: "invalid_request" | "state_user_mismatch",
    public readonly status: 400 | 403,
  ) {
    super(code);
    this.name = "GmailConnectContractError";
  }
}

function invalidRequest(): never {
  throw new GmailConnectContractError("invalid_request", 400);
}

function hasOnlyKeys(record: Record<string, unknown>, allowed: readonly string[]): boolean {
  const allowedKeys = new Set(allowed);
  return Object.keys(record).every((key) => allowedKeys.has(key));
}

function isRedirectUriInput(value: unknown): value is string {
  return typeof value === "string" && value.length >= 1 && value.length <= 512 && !/[\r\n\0]/.test(value);
}

export function isValidAuthorizationCode(value: unknown): value is string {
  // OAuth authorization codes are opaque. Accept RFC 5234 VSCHAR rather than
  // coupling the app to Google's currently observed code alphabet.
  return typeof value === "string" && value.length >= 1 &&
    value.length <= MAX_AUTHORIZATION_CODE_LENGTH &&
    /^[\x21-\x7E]+$/.test(value);
}

export function isSealedOAuthState(value: unknown): value is string {
  return typeof value === "string" && value.length <= MAX_ENCRYPTED_STATE_LENGTH &&
    /^v1\.[A-Za-z0-9_-]{16}\.[A-Za-z0-9_-]+$/.test(value);
}

/** Parse the exact POST contract and fail closed on mixed/unknown fields. */
export function parseGmailConnectRequest(value: unknown): GmailConnectRequest {
  if (!value || typeof value !== "object" || Array.isArray(value)) return invalidRequest();
  const record = value as Record<string, unknown>;
  const action = record.action;

  if (action === undefined) {
    // Backward-compatible init used by the currently published app.
    if (!hasOnlyKeys(record, ["redirect_uri"])) return invalidRequest();
    if (record.redirect_uri !== undefined && !isRedirectUriInput(record.redirect_uri)) return invalidRequest();
    return { kind: "init", redirectUri: record.redirect_uri as string | undefined };
  }

  if (action === "init") {
    if (!hasOnlyKeys(record, ["action", "redirect_uri"]) || !isRedirectUriInput(record.redirect_uri)) {
      return invalidRequest();
    }
    return { kind: "init", redirectUri: record.redirect_uri };
  }

  if (action === "complete") {
    if (!hasOnlyKeys(record, ["action", "authorization_code", "state"])) return invalidRequest();
    if (!isValidAuthorizationCode(record.authorization_code) || !isSealedOAuthState(record.state)) {
      return invalidRequest();
    }
    return {
      kind: "complete",
      authorizationCode: record.authorization_code,
      state: record.state,
    };
  }

  return invalidRequest();
}

/**
 * The callback code may be consumed only inside this ownership gate. Keeping
 * the action in the closure makes the login-CSRF ordering explicit and testable.
 */
export async function runForOAuthStateOwner<T>(
  stateUserId: string,
  authenticatedUserId: string,
  action: () => Promise<T>,
): Promise<T> {
  if (stateUserId !== authenticatedUserId) {
    throw new GmailConnectContractError("state_user_mismatch", 403);
  }
  return await action();
}

/**
 * Resolve the public provider callback with only one injected capability:
 * opening state. It cannot exchange tokens, start a watch, or access the DB.
 */
export async function resolvePublicOAuthCallback(
  searchParams: URLSearchParams,
  defaultRedirectUri: string,
  openState: (state: string) => Promise<{ redirectUri: string }>,
): Promise<PublicOAuthCallbackOutcome> {
  const encodedStates = searchParams.getAll("state");
  const encodedState = encodedStates.length === 1 ? encodedStates[0] : null;
  if (!isSealedOAuthState(encodedState)) {
    return { kind: "error", redirectUri: defaultRedirectUri, code: "invalid_state" };
  }

  let state: { redirectUri: string };
  try {
    state = await openState(encodedState);
  } catch (error) {
    return {
      kind: "error",
      redirectUri: defaultRedirectUri,
      code: error instanceof Error && error.message === "expired_state" ? "state_expired" : "invalid_state",
    };
  }

  const providerErrors = searchParams.getAll("error");
  const authorizationCodes = searchParams.getAll("code");
  if (providerErrors.length > 0) {
    if (providerErrors.length !== 1 || authorizationCodes.length > 0) {
      return { kind: "error", redirectUri: state.redirectUri, code: "server_error" };
    }
    return {
      kind: "error",
      redirectUri: state.redirectUri,
      code: providerErrors[0] === "access_denied" ? "access_denied" : "server_error",
    };
  }

  const authorizationCode = authorizationCodes.length === 1 ? authorizationCodes[0] : null;
  if (!isValidAuthorizationCode(authorizationCode)) {
    return { kind: "error", redirectUri: state.redirectUri, code: "server_error" };
  }
  return {
    kind: "complete",
    redirectUri: state.redirectUri,
    authorizationCode,
    state: encodedState,
  };
}

/** Build the only success shape emitted by the public Google callback. */
export function buildOAuthCompletionRedirect(base: string, authorizationCode: string, state: string): string {
  if (!isValidAuthorizationCode(authorizationCode) || !isSealedOAuthState(state)) return invalidRequest();
  const url = new URL(base);
  url.search = "";
  url.hash = "";
  url.searchParams.set("status", "complete");
  url.searchParams.set("code", authorizationCode);
  url.searchParams.set("state", state);
  return url.toString();
}
