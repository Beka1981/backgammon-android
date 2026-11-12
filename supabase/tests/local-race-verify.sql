-- DISPOSABLE local cluster only. Run after pgbench -c 8 -t 1 -f local-race.pgbench.
DO $$ BEGIN
 IF (SELECT count(*) FROM app.profiles) <> 1 OR
    (SELECT count(*) FROM app.profiles WHERE username_key='race_player') <> 1 THEN
    RAISE EXCEPTION 'race must have exactly one winner/profile';
 END IF;
 RAISE NOTICE 'PASS: eight concurrent creation attempts produce one username winner/profile';
 IF (SELECT count(*) FROM app.player_stats) <> 1 OR (SELECT count(*) FROM app.player_activity) <> 1 THEN
    RAISE EXCEPTION 'partial or duplicate onboarding';
 END IF;
 RAISE NOTICE 'PASS: one statistics row and one IDLE row after concurrent retries';
 IF (SELECT count(*) FROM app.profile_limits WHERE bucket='write' AND count=4) <> 2 THEN
    RAISE EXCEPTION 'not all eight concurrent calls ran';
 END IF;
 RAISE NOTICE 'PASS: all eight calls completed across two identities';
END $$;
