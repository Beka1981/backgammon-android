# Best Practices and Security Alignment Update

## Priority and scope

High priority: the new exported authentication callback entry point and persisted sessions. Scope is Task 08 only. No game rules, geometry, checker input, animations, match storage, RLS or production database changes.

Applied guidance: [Android Intent Security skill](/Users/Admin/.android-studio/skills/android-intent-security/SKILL.md). This is an implementation review, not a penetration-test certification.

## Implementation

- MainActivity handles both onCreate and onNewIntent through the same strict callback path and calls setIntent on warm entry. Ordinary launch Intents retain their action/categories so lifecycle and restoration observers can match them. Only ACTION_VIEW callback Intents are replaced with a clean explicit Activity Intent to remove one-time URI data.
- Only ACTION_VIEW with a bounded URI is processed. The domain parser allowlists scheme, host, port, paths, query cardinality and code characters. No nested Intent, extras, URI grants, arbitrary destination or bearer-token fragments are forwarded.
- Repository callbacks additionally require a locally initiated matching flow and encrypted PKCE verifier. Authorization codes are exchanged server-side by Supabase Auth; no client-provided user ID is trusted.
- Cold/warm callback parsing is identical. Unsupported/expired/malicious links receive a generic error. Duplicate successfully exchanged codes are identified by a stored digest.
- SDK Auth logging is disabled. Password-bearing actions intentionally avoid generated data-class toString. Callback toString redacts the code; configuration toString redacts values.
- Android Keystore AES-GCM encrypts session, verifier, flow and recovery metadata. Auth preferences are excluded from cloud backup and device transfer. Corruption blocks authenticated use; logout clears local identity without touching offline matches.
- Secret/service_role keys and malformed URLs are rejected at build time and runtime. Only valid client-public configuration is embedded. No database policies were created or relaxed.

No IntentSanitizer forwarding pipeline is necessary: incoming intents are never launched, redirected or deserialized as nested intents.

## Verification

AuthValidationTest exercises wrong schemes/hosts/user info, duplicate/extra parameters, token fragments, empty/control/oversized codes and redacted diagnostic strings. AuthViewModelTest uses fake repositories for duplicate submissions, expiration, offline restoration and protected recovery navigation. SupabaseAuthRepositoryTest uses the actual SDK with MockEngine for refresh, project isolation, corruption, PKCE rejection/recovery and local logout. AuthInteractionTest covers forms, account gating, accessibility and real Android Keystore ciphertext/tamper handling in isolated test storage.

Exact final command results and emulator checks are recorded in the [Task 08 report](task08-report.md).

## Remaining release gates

Custom schemes can be claimed by another app. PKCE prevents redemption without the requesting installation's verifier but cannot guarantee link delivery. Verified HTTPS App Links require a genuinely owned domain before production rollout. Dashboard redirect/email/SMTP policies and a dedicated-account end-to-end smoke test must be verified separately.

A lint-suggested intent-filter formatting refactor was rejected by automatic approval review for perceived IPC-surface broadening. The existing strict filter was retained; its style warning is documented rather than bypassed. No approval is needed to retain the safer current filter.

## Unified diff

The exact tracked-file security integration diff is recorded in `security-integration.diff` alongside this report. New parser/storage implementations are listed in the Task 08 inventory and directly reviewed in source.
