-- =============================================================================
-- 0010_revoke_anon_sequence_usage.sql
-- =============================================================================
-- Revoke USAGE on public sequences from `anon` and `public` only.
--
-- Do NOT revoke from `authenticated` or `service_role` — PostgREST inserts
-- use the identity column, which internally calls nextval(), which requires
-- USAGE on the sequence for the inserting role. Revoking from `authenticated`
-- breaks device registration.
--
-- Supabase's default schema setup grants sequences to anon; the earlier
-- migrations (0008) covered tables but not sequences.
--
-- Idempotent: safe to re-run.
-- =============================================================================

-- Revoke from anon and public on every sequence in public.
do $$
declare
    r record;
begin
    for r in
        select sequence_name
        from information_schema.sequences
        where sequence_schema = 'public'
    loop
        execute format('revoke all on sequence public.%I from anon, public', r.sequence_name);
    end loop;
end $$;

-- Fallback: if information_schema.sequences didn't list identity sequences,
-- revoke directly from the known sequence.
revoke all on sequence public.devices_id_seq from anon;
revoke all on sequence public.devices_id_seq from public;

-- Prevent recurrence on future sequences.
alter default privileges in schema public revoke all on sequences from anon;
alter default privileges in schema public revoke all on sequences from public;

-- Verify the final state. Expect: anon absent, authenticated + service_role present.
select
    grantee,
    object_name,
    privilege_type
from information_schema.role_usage_grants
where object_schema = 'public'
order by object_name, grantee;