// Runtime configuration injected at container start (entrypoint overwrites this file).
// Same-origin by default: the web container (nginx) reverse-proxies /ops, /tower, /auth to bff-ops.
window.__LOGISTICS_CONFIG__ = {
  apiBaseUrl: '',
  mode: 'local',
};
