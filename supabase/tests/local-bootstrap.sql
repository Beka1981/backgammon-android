-- DISPOSABLE local PostgreSQL only. NEVER run against Supabase or an existing database.
-- Creates minimal Auth fixtures; does not test the real GoTrue/JWT service.
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE SCHEMA auth;
CREATE TABLE auth.users(id uuid PRIMARY KEY, email_confirmed_at timestamptz, banned_until timestamptz, is_anonymous boolean);
CREATE TABLE auth.sessions(id uuid PRIMARY KEY, user_id uuid REFERENCES auth.users(id), not_after timestamptz);
