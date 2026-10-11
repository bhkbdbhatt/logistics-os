# platform:entitlement

Licensing + feature-entitlement seam for the sellable **editions** (`editions/`). Framework-agnostic;
applications inject an `EntitlementGate` and check features through it so the soft → hard enforcement
switch happens in one place.

## API

- `Entitlement` — record: `edition`, `version`, `tenant`, `expiresAt`, `features`, `mode`
  (`EVALUATION` | `LICENSED` | `INVALID`). `isEnabled(feature)` is permissive for unknown features
  **only** in `EVALUATION` mode; `isExpired(now)`.
- `EntitlementLoader` — builds an `Entitlement` from `ENTITLEMENT_LICENSE` (blank → evaluation; a
  path if it does not start with `{`; otherwise inline JSON).
- `EntitlementGate` — the injected check surface: `isEnabled(feature)`, `entitlement()`.
- `EntitlementException` — unreadable/malformed license.

## Soft vs hard

- **Soft (current):** no license → `evaluation` (permissive) with a reported `mode`. Contract governs.
- **Hard (later):** verify the `signature` (Ed25519/JWT), bind to `tenant`, enforce expiry/limits and
  gate unknown features. The seam means callers don't change.

See [docs/product/licensing.md](../../docs/product/licensing.md) and
[docs/product/editions.md](../../docs/product/editions.md).

## Example

```java
EntitlementGate gate = EntitlementGate.fromEnvironment();
if (!gate.isEnabled("l5_orchestration")) {
    return ResponseEntity.status(403).build();
}
```
