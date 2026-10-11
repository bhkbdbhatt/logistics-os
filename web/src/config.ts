export interface OidcConfig {
  issuer: string;
  clientId: string;
  scope: string;
}

export interface RuntimeConfig {
  apiBaseUrl: string;
  mode: 'local' | 'oidc';
  oidc?: OidcConfig;
}

const DEFAULTS: RuntimeConfig = {
  apiBaseUrl: '',
  mode: 'local',
};

export function loadConfig(): RuntimeConfig {
  const injected = (typeof window !== 'undefined' ? window.__LOGISTICS_CONFIG__ : undefined) ?? {};
  return {
    apiBaseUrl: injected.apiBaseUrl ?? DEFAULTS.apiBaseUrl,
    mode: injected.mode ?? DEFAULTS.mode,
    oidc: injected.oidc
      ? { scope: 'openid profile', ...injected.oidc }
      : undefined,
  };
}

export const config = loadConfig();
