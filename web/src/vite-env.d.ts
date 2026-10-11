/// <reference types="vite/client" />

interface LogisticsOidcConfig {
  issuer: string;
  clientId: string;
  scope?: string;
}

interface LogisticsRuntimeConfig {
  apiBaseUrl?: string;
  mode?: 'local' | 'oidc';
  oidc?: LogisticsOidcConfig;
}

interface Window {
  __LOGISTICS_CONFIG__?: LogisticsRuntimeConfig;
}
