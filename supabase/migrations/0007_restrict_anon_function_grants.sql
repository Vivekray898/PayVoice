-- =============================================================================
-- 0009_restrict_anon_function_grants.sql
-- =============================================================================
-- Fix: `anon` has EXECUTE on claim_pairing, a `security definer` function
-- that upserts into `employees`. An unauthenticated caller with a valid
-- pairing code could insert themselves as an employee of any owner.
--
-- `create or replace function` preserves existing grants, so the grants
-- from earlier migrations accumulated. This migration explicitly revokes
-- EXECUTE from `anon` and `public` on every PayVoice function, then
-- re-grants only what each function needs.
--
-- Idempotent: safe to re-run.
-- =============================================================================

-- 1. Revoke all EXECUTE grants from anon and public on every public function.
--    Using DO blocks so this covers any future functions too.
do $$
declare
    r record;
begin
    for r in
        select p.proname, pg_get_function_identity_arguments(p.oid) as args
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
    loop
        execute format('revoke all on function public.%I(%s) from anon, public',
                       r.proname, r.args);
    end loop;
end $$;

-- 2. Re-grant EXECUTE only to `authenticated` on the RPC functions the app
--    actually calls. `postgres` and `service_role` retain ownership-level
--    access via the owner, so no explicit grant is needed for them.
grant execute on function public.claim_pairing(text, text)  to authenticated;
grant execute on function public.revoke_employee(uuid)      to authenticated;

-- 3. If you added insert_payment_event (migration 0007 path A), grant it too:
--    grant execute on function public.insert_payment_event(text, text, bigint, text, text, text, bigint) to authenticated;

-- 4. If you added my_payment_health (migration 0007 path B), grant it too:
--    grant execute on function public.my_payment_health() to authenticated;

-- 5. Verify the final state — this output is part of the change.
select
    p.proname           as routine_name,
    coalesce(r.rolname, 'PUBLIC') as grantee,
    a.privilege_type
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
cross join lateral aclexplode(p.proacl) a
left join pg_roles r on r.oid = a.grantee
where n.nspname = 'public'
order by p.proname, grantee;