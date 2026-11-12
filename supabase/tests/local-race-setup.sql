-- DISPOSABLE local cluster only, after profiles-security.sql (which rolls back).
-- Never execute fixtures on a real Supabase project.
INSERT INTO auth.users(id,email_confirmed_at,is_anonymous) VALUES
 ('10000000-0000-0000-0000-000000000011',now(),false),
 ('10000000-0000-0000-0000-000000000012',now(),false);
INSERT INTO auth.sessions(id,user_id,not_after) VALUES
 ('20000000-0000-0000-0000-000000000011','10000000-0000-0000-0000-000000000011',NULL),
 ('20000000-0000-0000-0000-000000000012','10000000-0000-0000-0000-000000000012',NULL);
CREATE FUNCTION public.fixture_profile_race(client integer) RETURNS void LANGUAGE plpgsql AS $$
DECLARE actor uuid; sid uuid; result jsonb;
BEGIN
 actor := ('10000000-0000-0000-0000-0000000000' || (11 + client % 2)::text)::uuid;
 sid := ('20000000-0000-0000-0000-0000000000' || (11 + client % 2)::text)::uuid;
 result := profile_api.execute(actor,sid,'POST',
   '{"username":"Race_Player","displayName":"Race fixture","avatarId":"crown"}');
 IF NOT (coalesce(result->>'username'='race_player',false)
   OR coalesce(result->>'code'='USERNAME_UNAVAILABLE',false)) THEN
   RAISE EXCEPTION 'unexpected fixture race result';
 END IF;
END $$;
