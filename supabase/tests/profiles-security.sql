-- Disposable local/staging database ONLY, after the migration. All test rows roll back.
-- No real account is created: these are synthetic PostgreSQL identity/session fixtures.
BEGIN;
CREATE FUNCTION pg_temp.assert(ok boolean, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN IF ok IS DISTINCT FROM true THEN RAISE EXCEPTION 'FAIL: %',label; END IF;
RAISE NOTICE 'PASS: %',label; END $$;
INSERT INTO auth.users(id,email_confirmed_at,is_anonymous) VALUES
 ('10000000-0000-0000-0000-000000000001',now(),false),
 ('10000000-0000-0000-0000-000000000002',now(),false),
 ('10000000-0000-0000-0000-000000000003',NULL,false),
 ('10000000-0000-0000-0000-000000000004',now(),false);
INSERT INTO auth.sessions(id,user_id,not_after) VALUES
 ('20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',NULL),
 ('20000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000002',NULL),
 ('20000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000003',NULL),
 ('20000000-0000-0000-0000-000000000004','10000000-0000-0000-0000-000000000004',now()-interval '1 minute');
SELECT pg_temp.assert(NOT has_schema_privilege('authenticated','app','USAGE'),'client cannot use private schema');
SELECT pg_temp.assert(NOT has_table_privilege('authenticated','app.profiles','SELECT'),'client cannot read Auth mapping');
SELECT pg_temp.assert(NOT has_table_privilege('anon','app.profiles','SELECT'),'anonymous cannot read profiles');
SELECT pg_temp.assert(NOT has_table_privilege('authenticated','app.profiles','UPDATE'),'cross-user/direct profile update denied');
SELECT pg_temp.assert(NOT has_table_privilege('authenticated','app.player_stats','UPDATE'),'statistics tampering denied');
SELECT pg_temp.assert(NOT has_function_privilege('authenticated','profile_api.execute(uuid,uuid,text,jsonb)','EXECUTE'),'client cannot impersonate server RPC');
SELECT pg_temp.assert(NOT has_function_privilege('anon','profile_api.snapshot(uuid)','EXECUTE'),'public snapshot helper denied');
SELECT pg_temp.assert(NOT has_table_privilege('bg_profile_backend','app.profiles','SELECT'),'backend has function-only access');
SELECT pg_temp.assert(NOT has_table_privilege('bg_profile_owner','app.player_stats','UPDATE'),'profile function owner cannot edit statistics');
SELECT pg_temp.assert((SELECT bool_and(relrowsecurity AND relforcerowsecurity) FROM pg_class
 WHERE oid IN ('app.profiles'::regclass,'app.player_stats'::regclass,'app.player_activity'::regclass,
 'app.profile_commands'::regclass,'app.profile_limits'::regclass)),'RLS enabled and forced on every table');

SET LOCAL ROLE bg_profile_backend;
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000002','GET')->>'code' = 'AUTH_REQUIRED','cross-owner session denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000003',
 '20000000-0000-0000-0000-000000000003','GET')->>'code' = 'AUTH_REQUIRED','unconfirmed user denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000004',
 '20000000-0000-0000-0000-000000000004','GET')->>'code' = 'AUTH_REQUIRED','expired session denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','GET')->>'code' = 'PROFILE_NOT_FOUND','missing profile is explicit');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"Admin","displayName":"Player","avatarId":"crown"}')->>'code' = 'USERNAME_RESERVED','reserved name denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"xy","displayName":"Player","avatarId":"crown"}')->>'code' = 'USERNAME_INVALID','short username denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"valid","displayName":"Bad\nName","avatarId":"crown"}')->>'code' = 'DISPLAY_NAME_INVALID','control character denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"valid","displayName":"Player","avatarId":"https://evil.invalid/x"}')->>'code' = 'AVATAR_INVALID','external avatar URL denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"valid","displayName":"Player","avatarId":"crown","gamesWon":999}')->>'code' = 'INVALID_REQUEST','statistics payload injection denied');

CREATE TEMP TABLE profile_reply AS SELECT profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','POST',
 '{"username":" Test_Player ","displayName":" გიორგი ","avatarId":"knight"}') AS value;
SELECT pg_temp.assert((SELECT value->>'username' = 'test_player' AND value->>'displayName' = 'გიორგი'
 FROM profile_reply),'canonical Georgian onboarding');
SELECT pg_temp.assert((SELECT (value->>'gamesPlayed')::int = 0 AND (value->>'gamesWon')::int = 0
 AND (value->>'gamesLost')::int = 0 AND value->'rating' = 'null'::jsonb FROM profile_reply),'server initial statistics zero');
SELECT pg_temp.assert((SELECT value->>'id' <> '10000000-0000-0000-0000-000000000001'
 AND NOT(value ?| ARRAY['auth_user_id','email','session_id','access_token','user_metadata']) FROM profile_reply),'public projection separates identity');
SELECT pg_temp.assert((SELECT profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','POST',
 '{"username":"another","displayName":"Other","avatarId":"moon"}')->>'id' = value->>'id' FROM profile_reply),'creation retry returns existing profile');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000002',
 '20000000-0000-0000-0000-000000000002','POST',
 '{"username":"TEST_PLAYER","displayName":"Other","avatarId":"sun"}')->>'code' = 'USERNAME_UNAVAILABLE','case-insensitive uniqueness conflict');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','PATCH',
 '{"username":"test_player","displayName":"Changed","avatarId":"star","expectedVersion":99,"commandId":"30000000-0000-0000-0000-000000000001"}')->>'code' = 'VERSION_CONFLICT','stale edit denied');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','PATCH',
 '{"username":"test_player","displayName":"Changed","avatarId":"star","expectedVersion":0,"commandId":"30000000-0000-0000-0000-000000000001"}')->>'profileVersion' = '1','versioned edit succeeds');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','PATCH',
 '{"username":"test_player","displayName":"Changed","avatarId":"star","expectedVersion":0,"commandId":"30000000-0000-0000-0000-000000000001"}')->>'profileVersion' = '1','same-command retry does not increment version');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','PATCH',
 '{"username":"test_player","displayName":"Different","avatarId":"star","expectedVersion":0,"commandId":"30000000-0000-0000-0000-000000000001"}')->>'code' = 'VERSION_CONFLICT','same-command changed payload rejected');
SELECT pg_temp.assert(profile_api.execute('10000000-0000-0000-0000-000000000001',
 '20000000-0000-0000-0000-000000000001','PATCH',
 '{"username":"new_username","displayName":"Changed","avatarId":"star","expectedVersion":1,"commandId":"30000000-0000-0000-0000-000000000002"}')->>'code' = 'RATE_LIMITED','username daily throttle');
RESET ROLE;
SELECT pg_temp.assert((SELECT count(*) = 1 FROM app.profiles),'no duplicate profiles');
SELECT pg_temp.assert((SELECT count(*) = 1 FROM app.player_stats),'atomic statistics creation');
SELECT pg_temp.assert((SELECT count(*) = 1 FROM app.player_activity WHERE mode='IDLE'),'atomic idle activity creation');
DO $$ BEGIN
  BEGIN INSERT INTO app.profiles(auth_user_id,username,username_key,display_name)
    VALUES('10000000-0000-0000-0000-000000000002','test_player','test_player','Other');
    RAISE EXCEPTION 'uniqueness missing';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'PASS: direct duplicate blocked by database constraint'; END;
END $$;
DO $$ BEGIN
  SET LOCAL ROLE authenticated;
  BEGIN
    PERFORM profile_api.execute('10000000-0000-0000-0000-000000000001',
      '20000000-0000-0000-0000-000000000001','GET');
    RAISE EXCEPTION 'client function execution unexpectedly allowed';
  EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: actual client RPC execution denied'; END;
  BEGIN
    UPDATE app.profiles SET display_name='Attack';
    RAISE EXCEPTION 'client write unexpectedly allowed';
  EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: actual cross-user database write denied'; END;
  BEGIN
    UPDATE app.player_stats SET games_won=999;
    RAISE EXCEPTION 'stats write unexpectedly allowed';
  EXCEPTION WHEN insufficient_privilege THEN RAISE NOTICE 'PASS: actual statistics write denied'; END;
  RESET ROLE;
END $$;
ROLLBACK;
