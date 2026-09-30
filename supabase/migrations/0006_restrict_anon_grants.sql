-- =============================================================================
-- 0008_restrict_anon_grants.sql
-- =============================================================================
-- Supabase's default schema grants the full privilege set to `anon` on every
-- new table in `public`. RLS currently blocks `anon` from reading or writing
-- anything, but the grants are a latent hazard: a misconfigured policy or a
-- forgotten `to authenticated` clause would immediately expose the tables.
--
-- Fix: revoke every grant from `anon` on all PayVoice tables. `anon` should
-- have zero direct table grants. RLS remains the caller-scoping layer.
--
-- Also revoke write privileges from `authenticated` on tables where only
-- SELECT is actually needed by the RLS policies — trimming over-granted
-- privileges to the minimum.
--
-- Idempotent: safe to re-run.
-- =============================================================================

-- 1. Strip all grants from `anon` on every PayVoice table.
revoke all on public.devices         from anon;
revoke all on public.employees       from anon;
revoke all on public.pairing_codes   from anon;
revoke all on public.payment_events  from anon;

-- 2. Same for the `public` role (which is the default role for new objects).
--    Anon accesses flow through the `anon` role, but `public` should also be
--    clean for defense in depth.
revoke all on public.devices         from public;
revoke all on public.employees       from public;
revoke all on public.pairing_codes   from public;
revoke all on public.payment_events  from public;

-- 3. Trim `authenticated` grants to what the RLS policies actually use.
--    `authenticated` needs: SELECT/INSERT/UPDATE/DELETE as the RLS policies
--    permit. TRUNCATE, REFERENCES, TRIGGER are never used by PostgREST and
--    are excess attack surface.
revoke truncate, references, trigger on public.devices         from authenticated;
revoke truncate, references, trigger on public.employees       from authenticated;
revoke truncate, references, trigger on public.pairing_codes   from authenticated;
revoke truncate, references, trigger on public.payment_events  from authenticated;

-- 4. Also revoke TRUNCATE from service_role — the service key should not be
--    able to truncate user data by accident. (It still bypasses RLS.)
revoke truncate, trigger on public.devices         from service_role;
revoke truncate, trigger on public.employees       from service_role;
revoke truncate, trigger on public.pairing_codes   from service_role;
revoke truncate, trigger on public.payment_events  from service_role;

-- 5. Prevent recurrence for future objects in `public`.
alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke all on tables from public;
alter default privileges in schema public revoke truncate, references, trigger on tables from authenticated;
alter default privileges in schema public revoke truncate, trigger on tables from service_role;

-- 6. Verify the final state — this output is part of the change.
select grantee, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in ('devices', 'employees', 'pairing_codes', 'payment_events')
order by table_name, grantee, privilege_type;