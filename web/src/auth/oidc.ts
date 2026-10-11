import { config } from '../config';

const VERIFIER_KEY = 'logistics-os.pkce.verifier';
const STATE_KEY = 'logistics-os.pkce.state';

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomString(length = 48): string {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

async function challenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64UrlEncode(new Uint8Array(digest));
}

function redirectUri(): string {
  return `${window.location.origin}/`;
}

export async function beginOidcLogin(): Promise<void> {
  if (!config.oidc) {
    throw new Error('oidc configuration is missing');
  }
  const verifier = randomString();
  const state = randomString(16);
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  sessionStorage.setItem(STATE_KEY, state);
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: config.oidc.clientId,
    redirect_uri: redirectUri(),
    scope: config.oidc.scope,
    state,
    code_challenge: await challenge(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${config.oidc.issuer}/protocol/openid-connect/auth?${params.toString()}`);
}

export async function completeOidcLogin(): Promise<string | null> {
  const url = new URL(window.location.href);
  const code = url.searchParams.get('code');
  if (!code || !config.oidc) {
    return null;
  }
  const expectedState = sessionStorage.getItem(STATE_KEY);
  const state = url.searchParams.get('state');
  if (expectedState && state !== expectedState) {
    throw new Error('OIDC state mismatch');
  }
  const verifier = sessionStorage.getItem(VERIFIER_KEY) ?? '';
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: config.oidc.clientId,
    redirect_uri: redirectUri(),
    code,
    code_verifier: verifier,
  });
  const response = await fetch(`${config.oidc.issuer}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  sessionStorage.removeItem(VERIFIER_KEY);
  sessionStorage.removeItem(STATE_KEY);
  url.searchParams.delete('code');
  url.searchParams.delete('state');
  window.history.replaceState({}, document.title, url.pathname + url.search);
  if (!response.ok) {
    throw new Error(`token exchange failed (${response.status})`);
  }
  const payload = (await response.json()) as { access_token?: string };
  if (!payload.access_token) {
    throw new Error('token response is missing access_token');
  }
  return payload.access_token;
}
