export const avatarIds = ["crown", "knight", "sun", "moon", "mountain", "lotus", "shield", "star"];
export const reserved = new Set(["admin", "administrator", "support", "system", "moderator",
  "backgammon", "official", "supabase", "postgres", "api", "root", "null", "deleted"]);
export const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const trim = (value: string) => value.replace(/^ +| +$/g, "");
export function validate(body: unknown, method: string): { value?: Record<string, unknown>; code?: string } {
  if (!body || typeof body !== "object" || Array.isArray(body)) return { code: "INVALID_REQUEST" };
  const b = body as Record<string, unknown>;
  const allowed = method === "PATCH"
    ? ["username", "displayName", "avatarId", "expectedVersion", "commandId"]
    : ["username", "displayName", "avatarId"];
  if (Object.keys(b).some(k => !allowed.includes(k)) || Object.keys(b).length !== allowed.length ||
      typeof b.username !== "string" || typeof b.displayName !== "string" || typeof b.avatarId !== "string")
    return { code: "INVALID_REQUEST" };
  const username = trim(b.username).replace(/[A-Z]/g, c => c.toLowerCase());
  const displayName = trim(b.displayName).normalize("NFC");
  if (!/^[a-z0-9_]{3,20}$/.test(username)) return { code: "USERNAME_INVALID" };
  if (reserved.has(username)) return { code: "USERNAME_RESERVED" };
  if ([...displayName].length < 1 || [...displayName].length > 40 ||
      new TextEncoder().encode(displayName).length > 160 ||
      /[\u0000-\u001f\u007f-\u009f\ud800-\udfff\u200b-\u200f\u2028-\u202e\u2060-\u206f\ufeff]/u.test(displayName))
    return { code: "DISPLAY_NAME_INVALID" };
  if (!avatarIds.includes(b.avatarId)) return { code: "AVATAR_INVALID" };
  if (method === "PATCH" && (!Number.isSafeInteger(b.expectedVersion) || Number(b.expectedVersion) < 0 ||
      typeof b.commandId !== "string" || !uuid.test(b.commandId))) return { code: "INVALID_REQUEST" };
  return { value: { username, displayName, avatarId: b.avatarId,
    ...(method === "PATCH" ? { expectedVersion: b.expectedVersion, commandId: b.commandId } : {}) } };
}
/** Hosted platforms inject a dictionary; singular is a local CLI fallback, not a custom reserved secret. */
export function selectPublishableKey(plural?: string, local?: string): string | undefined {
  try {
    const keys = plural === undefined ? {} : JSON.parse(plural);
    if (keys === null || typeof keys !== "object" || Array.isArray(keys)) return undefined;
    return [keys.default, ...Object.values(keys), local].find((value): value is string =>
      typeof value === "string" && /^sb_publishable_[A-Za-z0-9_-]{12,490}$/.test(value));
  } catch { return undefined; }
}
