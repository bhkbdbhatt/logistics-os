export interface Session {
  token: string;
  username: string;
  tenantId: string;
  roles: string[];
  expiresAt: number;
}

const STORAGE_KEY = 'logistics-os.session';

function base64UrlDecode(value: string): string {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4));
  return atob(padded + pad);
}

export function decodeJwt(token: string): Record<string, unknown> {
  const parts = token.split('.');
  if (parts.length < 2) {
    return {};
  }
  try {
    return JSON.parse(base64UrlDecode(parts[1])) as Record<string, unknown>;
  } catch {
    return {};
  }
}

function toStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map((item) => String(item));
  }
  if (typeof value === 'string' && value.length > 0) {
    return value.split(/[\s,]+/);
  }
  return [];
}

export function sessionFromToken(token: string): Session {
  const claims = decodeJwt(token);
  const exp = typeof claims.exp === 'number' ? claims.exp * 1000 : Date.now() + 3_600_000;
  return {
    token,
    username: String(claims.preferred_username ?? claims.sub ?? 'user'),
    tenantId: String(claims.tenant_id ?? claims.tenantId ?? ''),
    roles: toStringArray(claims.roles),
    expiresAt: exp,
  };
}

export function loadSession(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const session = JSON.parse(raw) as Session;
    if (session.expiresAt < Date.now()) {
      sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function saveSession(session: Session): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}
