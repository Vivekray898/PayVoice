-- =============================================================================
-- 0007_payment_health_security.sql
-- =============================================================================
-- Fix a cross-tenant leak in the payment_health view.
--
-- Two defects:
--   1. The view ran with its OWNER's privileges, so it bypassed RLS on
--      payment_events and returned cross-owner aggregates.
--   2. `anon` and `authenticated` held full table grants (SELECT/INSERT/
--      UPDATE/DELETE/TRUNCATE/REFERENCES/TRIGGER) on the view, so even an
--      unauthenticated caller with the publishable key could read it.
--
-- Fix:
--   * Recreate the view with `security_invoker = on` — RLS on payment_events
--     is now evaluated against the caller's auth.uid().
--   * Add `owner_uid = auth.uid()` to the WHERE clause (belt-and-suspenders
--     in case the RLS policy on payment_events is ever loosened).
--   * Revoke every grant from `anon` and `public`; grant only SELECT to
--     `authenticated`.
--
-- Requires PostgreSQL 15+. Verified on 17.6.
-- =============================================================================

-- 1. Drop the old view.
drop view if exists public.payment_health;

-- 2. Recreate with security_invoker + explicit caller scoping.
create view public.payment_health
with (security_invoker = on)
as
select
    date_trunc('hour', created_at) as hour,
    count(*) as events,
    avg(extract(epoch from (delivered_at - created_at)) * 1000)::int as avg_delivery_ms,
    sum(case when delivered_to::text like '%true%'  then 1 else 0 end) as delivered,
    sum(case when delivered_to::text like '%false%' then 1 else 0 end) as failed
from public.payment_events
where created_at > now() - interval '7 days'
  and owner_uid = auth.uid()
group by 1
order by 1 desc;

-- 3. Strip all grants from anon and public.
revoke all on public.payment_health from anon, public;

-- 4. Also strip the over-granted write privileges from authenticated — a
--    read-only analytics view should not grant INSERT/UPDATE/DELETE/TRUNCATE.
revoke all on public.payment_health from authenticated;

-- 5. Grant only what is actually needed.
grant select on public.payment_health to authenticated;

-- 6. Confirm the final state (this output is part of the verification).
select grantee, privilege_type
from information_schema.role_table_grants
where table_name = 'payment_health'
order by grantee, privilege_type;