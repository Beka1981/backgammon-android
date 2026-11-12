// Local implementation only. Deploy solely after schema inspection, tests and explicit approval.
import postgres from "npm:postgres@4.1.1";
import { validate, uuid, selectPublishableKey } from "./validation.ts";

const url = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
const key = selectPublishableKey(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS"), Deno.env.get("SUPABASE_PUBLISHABLE_KEY"));
const database = Deno.env.get("PROFILE_DATABASE_URL");
// Dedicated restricted LOGIN, member of bg_profile_backend. Never the postgres/service_role user.
const sql = (() => {
  if (!database) return null;
  try {
    return postgres(database, { prepare: false, max: 1, ssl: { rejectUnauthorized: true },
      connect_timeout: 10, idle_timeout: 20 });
  } catch { return null; } // Malformed configuration must not log a credential-bearing URL.
})();
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff" },
});
const error = (status: number, code: string) => reply(status, { code });
const statuses: Record<string, number> = {
  AUTH_REQUIRED: 401, PROFILE_NOT_FOUND: 404, INVALID_REQUEST: 400,
  USERNAME_INVALID: 400, USERNAME_RESERVED: 400, DISPLAY_NAME_INVALID: 400, AVATAR_INVALID: 400,
  USERNAME_UNAVAILABLE: 409, VERSION_CONFLICT: 409, RATE_LIMITED: 429,
};
async function boundedJson(req: Request): Promise<unknown> {
  if (!req.body) throw new Error("INVALID_REQUEST");
  const reader = req.body.getReader();
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > 4096) { await reader.cancel(); throw new Error("INVALID_REQUEST"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const c of chunks) { bytes.set(c, offset); offset += c.length; }
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}
Deno.serve(async req => {
  if (!["GET","POST","PATCH"].includes(req.method)) return error(405, "INVALID_REQUEST");
  if (!url || !key || !sql) return error(503, "UNAVAILABLE");
  const authorization = req.headers.get("Authorization") ?? "";
  if (!/^Bearer [A-Za-z0-9_.-]{20,8192}$/.test(authorization)) return error(401, "AUTH_REQUIRED");
  try {
    // Auth server validates the JWT. Never authorize from decoded claims alone.
    const verified = await fetch(url + "/auth/v1/user", {
      headers: { apikey: key, Authorization: authorization }, signal: AbortSignal.timeout(10000),
    });
    if (verified.status === 401 || verified.status === 403) return error(401, "AUTH_REQUIRED");
    if (!verified.ok) return error(503, "UNAVAILABLE");
    const user = await verified.json();
    const token = authorization.slice(7);
    const encoded = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const claims = JSON.parse(atob(encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "=")));
    if (!uuid.test(user.id) || claims.sub !== user.id || !uuid.test(claims.session_id ?? "") ||
        claims.iss !== url + "/auth/v1" || claims.aud !== "authenticated" || claims.role !== "authenticated" ||
        typeof claims.exp !== "number" || claims.exp <= Date.now() / 1000 ||
        !user.email_confirmed_at || user.is_anonymous === true) return error(401, "AUTH_REQUIRED");
    let payload: Record<string, unknown> = {};
    if (req.method !== "GET") {
      if (!req.headers.get("Content-Type")?.startsWith("application/json")) return error(400, "INVALID_REQUEST");
      let body: unknown;
      try { body = await boundedJson(req); } catch { return error(400, "INVALID_REQUEST"); }
      const result = validate(body, req.method);
      if (!result.value) return error(400, result.code!);
      payload = result.value;
    }
    const result = await sql.begin(async tx => {
      await tx`SET LOCAL ROLE bg_profile_backend`;
      await tx`SET LOCAL statement_timeout = '5s'`;
      await tx`SET LOCAL lock_timeout = '2s'`;
      const rows = await tx`SELECT profile_api.execute(${user.id}::uuid, ${claims.session_id}::uuid,
        ${req.method}::text, ${JSON.stringify(payload)}::jsonb) AS result`;
      return rows[0].result;
    });
    if (result.code) return error(statuses[result.code] ?? 503, statuses[result.code] ? result.code : "UNAVAILABLE");
    // The database builds an explicit projection, never SELECT * or an Auth user object.
    return reply(req.method === "POST" ? 201 : 200, result);
  } catch {
    // No request bodies, JWTs, email, SQL connection strings or raw exceptions in logs/responses.
    return error(503, "UNAVAILABLE");
  }
});
