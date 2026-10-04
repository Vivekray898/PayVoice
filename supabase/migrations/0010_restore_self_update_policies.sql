-- =============================================================================
-- PayVoice — migration 0010: restore the self-update RLS policies.
-- =============================================================================
-- Observed against the live project (2026-10-04, emulator client):
--
--   PATCH /rest/v1/employees?id=eq.<uid>   -> 403 42501
--       "new row violates row-level security policy for table employees"
--   PATCH /rest/v1/devices?user_id=eq.<uid> -> 403 42501
--       "new row violates row-level security policy for table devices"
--
-- Both requests are the app writing ITS OWN row as the signed-in employee
-- (leave pairing -> status/left_at, and the last-seen heartbeat). Postgres
-- denies on RLS, not on grants: 0006 left `authenticated` with select/
-- insert/update/delete, and the request carries a valid session. So the live
-- database is missing — or holds a different definition of — the two
-- self-update policies that 0002/0003 define. Everything the employee can do
-- with a pairing is silently rejected there: leaving a business, the
-- last-seen heartbeat the owner sees, and the revoke fallback in
-- EmployeeRepository.revoke() whenever `revoke_employee` is unavailable.
--
-- This migration re-creates the policies from the repository definitions
-- verbatim, so it converges the live database onto the reviewed source of
-- truth whether the policy is missing entirely or has drifted. It is
-- idempotent: re-running it is a no-op.
--
-- The owner_uid invariant from 0003 is preserved deliberately: an employee
-- may change their own row's status/heartbeat fields but can never re-point
-- the row at a different owner. Ownership is set by the security-definer
-- claim_pairing() alone.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- employees: the employee updates their own row, ownership pinned
-- -----------------------------------------------------------------------------
drop policy if exists "employee updates own row" on public.employees;

create policy "employee updates own row"
    on public.employees for update
    to authenticated
    using (id = auth.uid())
    with check (id = auth.uid() and owner_uid = coalesce(
        (select e.owner_uid from public.employees e where e.id = auth.uid()),
        auth.uid()
    ));

-- -----------------------------------------------------------------------------
-- devices: a user maintains only its own device row (insert on first token
-- registration, update on heartbeat/token rotation, delete never used).
-- -----------------------------------------------------------------------------
drop policy if exists "user maintains own device" on public.devices;

create policy "user maintains own device"
    on public.devices for all
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());