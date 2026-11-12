import { test } from "node:test";
import assert from "node:assert/strict";
import { validate, avatarIds, selectPublishableKey } from "./validation.ts";
const base = { username: "player_1", displayName: "გიორგი", avatarId: "crown" };
for (const [label, patch, expected] of [
  ["valid Georgian", {}, undefined],
  ["ASCII normalization", { username: " Player_1 " }, undefined],
  ["NFC", { displayName: " e\u0301 " }, undefined],
  ["40 emoji codepoints", { displayName: "🙂".repeat(40) }, undefined],
  ["41 emoji", { displayName: "🙂".repeat(41) }, "DISPLAY_NAME_INVALID"],
  ["empty name", { displayName: "" }, "DISPLAY_NAME_INVALID"],
  ["spaces name", { displayName: "   " }, "DISPLAY_NAME_INVALID"],
  ["short username", { username: "ab" }, "USERNAME_INVALID"],
  ["long username", { username: "a".repeat(21) }, "USERNAME_INVALID"],
  ["Georgian username", { username: "აბგ" }, "USERNAME_INVALID"],
  ["confusable", { username: "аbc" }, "USERNAME_INVALID"],
  ["fullwidth", { username: "ＡＢＣ" }, "USERNAME_INVALID"],
  ["reserved", { username: "AdMiN" }, "USERNAME_RESERVED"],
  ["newline", { displayName: "A\nB" }, "DISPLAY_NAME_INVALID"],
  ["null", { displayName: "A\u0000B" }, "DISPLAY_NAME_INVALID"],
  ["C1 control", { displayName: "A\u0085B" }, "DISPLAY_NAME_INVALID"],
  ["bidi", { displayName: "A\u202EB" }, "DISPLAY_NAME_INVALID"],
  ["hidden format", { displayName: "A\u200BB" }, "DISPLAY_NAME_INVALID"],
  ["lone surrogate", { displayName: "\uD800" }, "DISPLAY_NAME_INVALID"],
  ["external URL", { avatarId: "https://evil.invalid" }, "AVATAR_INVALID"],
  ["stats injection", { gamesWon: 900 }, "INVALID_REQUEST"],
  ["actor injection", { authUserId: "attacker" }, "INVALID_REQUEST"],
] as const) {
  test(label, () => assert.equal(validate({ ...base, ...patch }, "POST").code, expected));
}
test("canonical values", () => assert.deepEqual(
  validate({ ...base, username: " Player_1 ", displayName: " e\u0301 " }, "POST").value,
  { ...base, username: "player_1", displayName: "é" }));
test("eight original stable IDs", () => assert.equal(new Set(avatarIds).size, 8));
test("update requires command and version", () => assert.equal(validate(base, "PATCH").code, "INVALID_REQUEST"));
test("valid versioned update", () => assert.ok(validate({ ...base, expectedVersion: 0,
  commandId: "30000000-0000-0000-0000-000000000001" }, "PATCH").value));
test("unsafe integer rejected", () => assert.equal(validate({ ...base, expectedVersion: 2 ** 54,
  commandId: "30000000-0000-0000-0000-000000000001" }, "PATCH").code, "INVALID_REQUEST"));
test("hosted publishable key dictionary", () => assert.equal(
  selectPublishableKey('{"default":"sb_publishable_fixture_not_real"}'), "sb_publishable_fixture_not_real"));
test("local CLI singular key", () => assert.equal(
  selectPublishableKey(undefined, "sb_publishable_fixture_not_real"), "sb_publishable_fixture_not_real"));
test("privileged or legacy key rejected", () => {
  assert.equal(selectPublishableKey('{"default":"sb_secret_fixture_not_real"}'), undefined);
  assert.equal(selectPublishableKey(undefined, "eyJ.fake.jwt"), undefined);
});
test("malformed hosted keys fail closed", () => {
  for (const input of ["bad", "null", "[]"])
    assert.equal(selectPublishableKey(input, "sb_publishable_fixture_not_real"), undefined);
});
