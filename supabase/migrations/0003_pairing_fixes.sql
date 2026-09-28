-- =============================================================================
-- PayVoice — migration 0003: pairing / owner-visibility fixes.
-- =============================================================================
-- Fixes three concrete defects found in the end-to-end audit:
--
--   1. NO owner SELECT policy on `employees`: the owner could never read the
--      employee registry, so a successful pairing never appeared on the owner
--      device ("Employee does not appear"). Only the employee's own-row
--      read policy existed.
--   2. The employee update policy ("employee updates own row") allowed an
--      employee to rewrite owner_uid (re-pointing their row at another
--      owner). Ownership is set by the security-definer claim_pairing()
--      only — updates must keep it stable.
--   3. claim_pairing() returned a single "invalid-or-expired" error for
--      every failure mode, which the Android UI rendered as "Pair code
--      expired" even when the real cause was a not-yet-established auth
--      session (the direct-APK-install bug signature). The function now
--      distinguishes invalid / used / expired so the client can show an
--      honest message. Server time (now()) remains the ONLY expiry clock —
--      no client timestamp is trusted.
-- =============================================================================

-- =============================================================================
-- 1. Owner reads own employee registry (the missing policy)
-- =============================================================================
create policy "owner reads own employees"
    on public.employees for select
    to authenticated
    using (owner_uid = auth.uid());

-- =============================================================================
-- 2. Employee updates keep ownership stable
-- =============================================================================
-- Drop and recreate the own-row update policy WITH a check that owner_uid
-- cannot be changed by the client. The security-definer claim_pairing() sets
-- it (it bypasses RLS); revocation via the owner policy is unaffected.
drop policy if exists "employee updates own row" on public.employees;

create policy "employee updates own row"
    on public.employees for update
    to authenticated
    using (id = auth.uid())
    with check (id = auth.uid() and owner_uid = coalesce(
        (select e.owner_uid from public.employees e where e.id = auth.uid()),
        auth.uid()
    ));

-- =============================================================================
-- 3. claim_pairing(text, text): granular errors, unchanged security model
-- =============================================================================
create or replace function public.claim_pairing(p_code text, p_device_name text)
returns json
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code       text := upper(btrim(coalesce(p_code, '')));
    v_owner      uuid;
    v_now        bigint := (extract(epoch from now()) * 1000)::bigint;
    v_caller     uuid := auth.uid();
    v_name       text := left(btrim(coalesce(p_device_name, '')), 40);
    v_used       boolean;
    v_expires_at bigint;
begin
    if v_caller is null then
        return json_build_object('ok', false, 'error', 'unauthenticated');
    end if;
    if v_name is null or length(v_name) = 0 then
        v_name := 'Employee Device';
    end if;

    select owner_uid, used, expires_at
      into v_owner, v_used, v_expires_at
    from public.pairing_codes
    where code = v_code;

    if v_owner is null then
        return json_build_object('ok', false, 'error', 'invalid');
    end if;
    if v_used then
        return json_build_object('ok', false, 'error', 'already-used');
    end if;
    if v_expires_at <= v_now then
        return json_build_object('ok', false, 'error', 'expired');
    end if;

    update public.pairing_codes set used = true where code = v_code;

    insert into public.employees (id, owner_uid, name, status, paired_at, last_seen_at)
    values (v_caller, v_owner, v_name, 'ACTIVE', v_now, v_now)
    on conflict (id) do update
        set owner_uid    = excluded.owner_uid,
            name         = excluded.name,
            status       = 'ACTIVE',
            paired_at    = excluded.paired_at,
            last_seen_at = excluded.last_seen_at,
            left_at      = null,
            revoked_at   = null,
            revoked_by   = null;

    return json_build_object('ok', true, 'owner_uid', v_owner);
end;
$$;

revoke all on function public.claim_pairing(text, text) from public;
grant execute on function public.claim_pairing(text, text) to authenticated;
