-- TASK09: review locally, inspect remote schema/policies, obtain approval BEFORE applying.
-- No Auth signup trigger, Auth deletion, Storage changes, or client table grants.
BEGIN;
CREATE SCHEMA app; -- Fail closed on an existing app schema: reconcile after remote inspection.
CREATE SCHEMA profile_api;
REVOKE ALL ON SCHEMA app, profile_api FROM PUBLIC, anon, authenticated;
CREATE ROLE bg_profile_owner NOLOGIN NOINHERIT NOBYPASSRLS;
CREATE ROLE bg_profile_backend NOLOGIN NOINHERIT NOBYPASSRLS;
GRANT USAGE ON SCHEMA app, profile_api, auth TO bg_profile_owner;
GRANT USAGE ON SCHEMA profile_api TO bg_profile_backend;
GRANT SELECT(id, email_confirmed_at, banned_until, is_anonymous) ON auth.users TO bg_profile_owner;
GRANT SELECT(id, user_id, not_after) ON auth.sessions TO bg_profile_owner;

CREATE TABLE app.profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id uuid UNIQUE REFERENCES auth.users(id) ON DELETE SET NULL,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','DELETING','DELETED')),
    username text,
    username_key text UNIQUE,
    display_name text NOT NULL,
    avatar_kind text NOT NULL DEFAULT 'BUILTIN' CHECK(avatar_kind = 'BUILTIN'),
    built_in_avatar_id text NOT NULL DEFAULT 'crown'
        CHECK(built_in_avatar_id IN ('crown','knight','sun','moon','mountain','lotus','shield','star')),
    version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    username_changed_at timestamptz NOT NULL DEFAULT now(),
    CHECK (
        (status = 'ACTIVE' AND auth_user_id IS NOT NULL AND username IS NOT NULL AND username_key IS NOT NULL
         AND username = username_key AND username_key COLLATE "C" ~ '^[a-z0-9_]{3,20}$'
         AND username_key NOT IN ('admin','administrator','support','system','moderator','backgammon',
             'official','supabase','postgres','api','root','null','deleted'))
        OR (status IN ('DELETING','DELETED') AND username IS NULL AND username_key IS NULL
            AND display_name = 'Deleted player' AND built_in_avatar_id = 'crown')
    ),
    CHECK(char_length(display_name) BETWEEN 1 AND 40 AND octet_length(display_name) <= 160
        AND display_name = normalize(btrim(display_name), NFC)
        AND display_name !~ U&'[\0001-\001F\007F-\009F\200B-\200F\2028-\202E\2060-\206F\FEFF]')
);
CREATE TABLE app.player_stats (
    profile_id uuid PRIMARY KEY REFERENCES app.profiles(id),
    games_played bigint NOT NULL DEFAULT 0 CHECK(games_played >= 0),
    games_won bigint NOT NULL DEFAULT 0 CHECK(games_won >= 0),
    games_lost bigint NOT NULL DEFAULT 0 CHECK(games_lost >= 0),
    rating integer CHECK(rating IS NULL), -- future explicit migration, no rating authority yet
    version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
    CHECK(games_won <= games_played AND games_lost = games_played - games_won)
);
-- Persistent occupancy lock foundation. TASK10 expands this IDLE-only constraint and targets.
CREATE TABLE app.player_activity (
    profile_id uuid PRIMARY KEY REFERENCES app.profiles(id),
    mode text NOT NULL DEFAULT 'IDLE' CHECK(mode = 'IDLE'),
    version bigint NOT NULL DEFAULT 0 CHECK(version >= 0),
    last_active_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE app.profile_commands (
    profile_id uuid NOT NULL REFERENCES app.profiles(id),
    command_id uuid NOT NULL,
    payload jsonb NOT NULL CHECK(octet_length(payload::text) <= 1024),
    accepted_version bigint NOT NULL CHECK(accepted_version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(profile_id, command_id)
);
CREATE INDEX profile_commands_cleanup ON app.profile_commands(created_at);
CREATE TABLE app.profile_limits (
    auth_user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    bucket text NOT NULL CHECK(bucket IN ('read','write')),
    window_start timestamptz NOT NULL,
    count integer NOT NULL CHECK(count BETWEEN 1 AND 100),
    PRIMARY KEY(auth_user_id,bucket)
);
REVOKE ALL ON app.profiles, app.player_stats, app.player_activity, app.profile_commands, app.profile_limits
    FROM PUBLIC, anon, authenticated, bg_profile_backend;
ALTER TABLE app.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE app.player_stats ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.player_stats FORCE ROW LEVEL SECURITY;
ALTER TABLE app.player_activity ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.player_activity FORCE ROW LEVEL SECURITY;
ALTER TABLE app.profile_commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.profile_commands FORCE ROW LEVEL SECURITY;
ALTER TABLE app.profile_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.profile_limits FORCE ROW LEVEL SECURITY;
-- Only the narrowly granted definer role can read/write through the functions below.
CREATE POLICY profile_owner_access ON app.profiles TO bg_profile_owner USING(true) WITH CHECK(true);
CREATE POLICY stats_owner_access ON app.player_stats TO bg_profile_owner USING(true) WITH CHECK(true);
CREATE POLICY activity_owner_access ON app.player_activity TO bg_profile_owner USING(true) WITH CHECK(true);
CREATE POLICY commands_owner_access ON app.profile_commands TO bg_profile_owner USING(true) WITH CHECK(true);
CREATE POLICY limits_owner_access ON app.profile_limits TO bg_profile_owner USING(true) WITH CHECK(true);
GRANT SELECT, INSERT, UPDATE ON app.profiles TO bg_profile_owner;
GRANT SELECT, INSERT ON app.player_stats, app.player_activity TO bg_profile_owner;
GRANT SELECT, INSERT ON app.profile_commands TO bg_profile_owner;
GRANT SELECT, INSERT, UPDATE ON app.profile_limits TO bg_profile_owner;

CREATE FUNCTION profile_api.snapshot(p_id uuid) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
 SELECT jsonb_build_object(
    'id', p.id, 'username', p.username, 'displayName', p.display_name, 'avatarId', p.built_in_avatar_id,
    'profileVersion', p.version, 'statsVersion', s.version, 'gamesPlayed', s.games_played,
    'gamesWon', s.games_won, 'gamesLost', s.games_lost, 'rating', s.rating,
    'createdAt', to_char(p.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'))
 FROM app.profiles p JOIN app.player_stats s ON s.profile_id = p.id
 WHERE p.id = p_id AND p.status = 'ACTIVE'
$$;
ALTER FUNCTION profile_api.snapshot(uuid) OWNER TO bg_profile_owner;
REVOKE ALL ON FUNCTION profile_api.snapshot(uuid) FROM PUBLIC, anon, authenticated, bg_profile_backend;

CREATE FUNCTION profile_api.execute(p_actor uuid, p_session uuid, p_operation text, p_payload jsonb DEFAULT '{}'::jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    p app.profiles%ROWTYPE;
    v_username text; v_name text; v_avatar text;
    v_count integer; v_expected bigint; v_command uuid; v_receipt app.profile_commands%ROWTYPE;
    v_bucket text := CASE WHEN p_operation = 'GET' THEN 'read' ELSE 'write' END;
BEGIN
    -- p_actor/session are supplied ONLY by the verified Edge endpoint, never Android.
    IF NOT EXISTS(SELECT 1 FROM auth.users u JOIN auth.sessions s ON s.user_id = u.id
        WHERE u.id = p_actor AND s.id = p_session AND u.email_confirmed_at IS NOT NULL
        AND NOT coalesce(u.is_anonymous,false) AND (u.banned_until IS NULL OR u.banned_until < now())
        AND (s.not_after IS NULL OR s.not_after > now())) THEN
        RETURN jsonb_build_object('code','AUTH_REQUIRED');
    END IF;
    IF p_operation NOT IN ('GET','POST','PATCH') OR p_payload IS NULL OR jsonb_typeof(p_payload) <> 'object'
        OR octet_length(p_payload::text) > 1024 THEN RETURN jsonb_build_object('code','INVALID_REQUEST'); END IF;
    -- Same Auth-subject guard must be acquired by future deletion onboarding reconciliation.
    PERFORM pg_advisory_xact_lock(hashtextextended(p_actor::text, 91428));
    INSERT INTO app.profile_limits(auth_user_id,bucket,window_start,count)
        VALUES(p_actor,v_bucket,date_trunc('minute',now()),1)
        ON CONFLICT(auth_user_id,bucket) DO UPDATE SET
            count = CASE WHEN app.profile_limits.window_start = EXCLUDED.window_start
                         THEN least(app.profile_limits.count + 1,100) ELSE 1 END,
            window_start = EXCLUDED.window_start RETURNING count INTO v_count;
    IF v_count > (CASE WHEN v_bucket = 'read' THEN 60 ELSE 10 END)
        THEN RETURN jsonb_build_object('code','RATE_LIMITED'); END IF;
    SELECT * INTO p FROM app.profiles WHERE auth_user_id = p_actor FOR UPDATE;
    IF FOUND AND p.status <> 'ACTIVE' THEN RETURN jsonb_build_object('code','AUTH_REQUIRED'); END IF;
    IF p_operation = 'GET' THEN
        IF p.id IS NULL THEN RETURN jsonb_build_object('code','PROFILE_NOT_FOUND'); END IF;
        RETURN profile_api.snapshot(p.id);
    END IF;
    IF p_operation = 'POST' AND p.id IS NOT NULL THEN RETURN profile_api.snapshot(p.id); END IF;
    IF p_operation = 'PATCH' AND p.id IS NULL THEN RETURN jsonb_build_object('code','PROFILE_NOT_FOUND'); END IF;
    IF EXISTS(SELECT 1 FROM jsonb_object_keys(p_payload) k
        WHERE k NOT IN ('username','displayName','avatarId','expectedVersion','commandId'))
        OR jsonb_typeof(p_payload->'username') IS DISTINCT FROM 'string'
        OR jsonb_typeof(p_payload->'displayName') IS DISTINCT FROM 'string'
        OR jsonb_typeof(p_payload->'avatarId') IS DISTINCT FROM 'string'
        THEN RETURN jsonb_build_object('code','INVALID_REQUEST'); END IF;
    v_username := translate(btrim(p_payload->>'username'),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz');
    v_name := normalize(btrim(p_payload->>'displayName'), NFC);
    v_avatar := p_payload->>'avatarId';
    IF v_username COLLATE "C" !~ '^[a-z0-9_]{3,20}$' THEN RETURN jsonb_build_object('code','USERNAME_INVALID'); END IF;
    IF v_username IN ('admin','administrator','support','system','moderator','backgammon',
        'official','supabase','postgres','api','root','null','deleted')
        THEN RETURN jsonb_build_object('code','USERNAME_RESERVED'); END IF;
    IF char_length(v_name) NOT BETWEEN 1 AND 40 OR octet_length(v_name) > 160
        OR v_name ~ U&'[\0001-\001F\007F-\009F\200B-\200F\2028-\202E\2060-\206F\FEFF]'
        THEN RETURN jsonb_build_object('code','DISPLAY_NAME_INVALID'); END IF;
    IF v_avatar NOT IN ('crown','knight','sun','moon','mountain','lotus','shield','star')
        THEN RETURN jsonb_build_object('code','AVATAR_INVALID'); END IF;
    IF p_operation = 'POST' THEN
        BEGIN
            INSERT INTO app.profiles(auth_user_id,username,username_key,display_name,built_in_avatar_id)
                VALUES(p_actor,v_username,v_username,v_name,v_avatar) RETURNING * INTO p;
            INSERT INTO app.player_stats(profile_id) VALUES(p.id);
            INSERT INTO app.player_activity(profile_id) VALUES(p.id);
        EXCEPTION WHEN unique_violation THEN RETURN jsonb_build_object('code','USERNAME_UNAVAILABLE');
        END;
    ELSE
        BEGIN
            v_expected := (p_payload->>'expectedVersion')::bigint;
            v_command := (p_payload->>'commandId')::uuid;
        EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
            RETURN jsonb_build_object('code','INVALID_REQUEST');
        END;
        IF v_expected IS NULL OR v_expected < 0 OR v_command IS NULL
            THEN RETURN jsonb_build_object('code','INVALID_REQUEST'); END IF;
        SELECT * INTO v_receipt FROM app.profile_commands WHERE profile_id = p.id AND command_id = v_command;
        IF FOUND THEN
            IF v_receipt.payload <> p_payload THEN RETURN jsonb_build_object('code','VERSION_CONFLICT'); END IF;
            RETURN profile_api.snapshot(p.id);
        END IF;
        IF p.version <> v_expected THEN RETURN jsonb_build_object('code','VERSION_CONFLICT'); END IF;
        IF p.username_key <> v_username AND p.username_changed_at > now() - interval '1 day'
            THEN RETURN jsonb_build_object('code','RATE_LIMITED'); END IF;
        BEGIN
            UPDATE app.profiles SET username = v_username, username_key = v_username, display_name = v_name,
                built_in_avatar_id = v_avatar, version = version + 1, updated_at = now(),
                username_changed_at = CASE WHEN username_key <> v_username THEN now() ELSE username_changed_at END
                WHERE id = p.id RETURNING * INTO p;
            INSERT INTO app.profile_commands(profile_id,command_id,payload,accepted_version)
                VALUES(p.id,v_command,p_payload,p.version);
        EXCEPTION WHEN unique_violation THEN RETURN jsonb_build_object('code','USERNAME_UNAVAILABLE');
        END;
    END IF;
    RETURN profile_api.snapshot(p.id);
END $$;
ALTER FUNCTION profile_api.execute(uuid,uuid,text,jsonb) OWNER TO bg_profile_owner;
REVOKE ALL ON FUNCTION profile_api.execute(uuid,uuid,text,jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION profile_api.execute(uuid,uuid,text,jsonb) TO bg_profile_backend;
-- No grants to service_role or client roles. Deployment provisions a restricted LOGIN separately,
-- grants bg_profile_backend to it, and uses SET LOCAL ROLE inside the Edge transaction.
COMMIT;
