export interface Overview {
  generatedAt?: number;
  shipments?: Array<Record<string, unknown>>;
  alerts?: Array<Record<string, unknown>>;
  health?: Record<string, unknown>;
  sourcesAvailable_readModel?: boolean;
  sourcesAvailable_tower?: boolean;
  sourcesAvailable_mdm?: boolean;
}

export interface Entitlement {
  edition?: string;
  version?: string;
  tenant?: string;
  mode?: string;
  expiresAt?: string;
  features?: string[];
}

export function asString(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) {
    return fallback;
  }
  return String(value);
}
