-- READ-ONLY review script. Do not run any remote SQL without the user's approval.
-- Contains metadata only, no Auth users, emails, tokens, object keys or signed URLs.
BEGIN READ ONLY;
SELECT current_setting('server_version') AS server_version, current_setting('server_encoding') AS encoding;
SELECT schema_name FROM information_schema.schemata
 WHERE schema_name IN ('app','profile_api','public','auth','storage');
SELECT schemaname, tablename, rowsecurity FROM pg_tables
 WHERE schemaname IN ('app','profile_api','public');
SELECT n.nspname, c.relname, c.relrowsecurity, c.relforcerowsecurity
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname IN ('app','profile_api','public') AND c.relkind IN ('r','p');
SELECT schemaname,tablename,policyname,permissive,roles,cmd,qual,with_check
 FROM pg_policies WHERE schemaname IN ('app','public','storage');
SELECT table_schema,table_name,grantee,privilege_type FROM information_schema.table_privileges
 WHERE table_schema IN ('app','profile_api','public') ORDER BY table_schema,table_name,grantee,privilege_type;
SELECT n.nspname,p.proname,pg_get_function_identity_arguments(p.oid),p.prosecdef,p.proacl
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname IN ('app','profile_api');
SELECT rolname,rolsuper,rolbypassrls,rolcanlogin,rolinherit FROM pg_roles
 WHERE rolname IN ('bg_profile_owner','bg_profile_backend','anon','authenticated');
SELECT n.nspname,a.defaclobjtype,a.defaclacl FROM pg_default_acl a
 LEFT JOIN pg_namespace n ON n.oid=a.defaclnamespace;
SELECT table_name,column_name,data_type FROM information_schema.columns
 WHERE table_schema='auth' AND ((table_name='users' AND column_name IN
 ('id','email_confirmed_at','banned_until','is_anonymous')) OR
 (table_name='sessions' AND column_name IN ('id','user_id','not_after')));
SELECT name,public,file_size_limit,allowed_mime_types FROM storage.buckets;
ROLLBACK;
