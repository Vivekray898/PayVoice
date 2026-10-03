-- =============================================================================
-- 0009 — SECURITY DEFINER search_path hardening.
--
-- Why this matters.
--
-- `security definer` functions run with the DEFINER's rights, not the
-- caller's. Anything they name without a schema qualifier is resolved through
-- `search_path`, and Postgres implicitly searches `pg_temp` FIRST unless it
-- is explicitly listed. A caller who can create a temporary table therefore
-- controls which table a bare `employees` / `pairing_codes` reference resolves
-- to inside a definer function — and the function then performs its write
-- with elevated privileges against the attacker's table.
--
-- `set search_path = public` narrows the path but does NOT remove pg_temp,
-- because pg_temp is searched ahead of any path that omits it. The fix is to
-- pin the path to the empty string: with nothing on the path, an unqualified
-- name cannot resolve to a caller-controlled object at all.
--
-- Every reference in both bodies is already schema-qualified (`public.…`), and
-- `auth.uid()` is schema-qualified by construction, so an empty path is
-- sufficient and nothing needs rewriting. `pg_catalog` stays implicitly
-- reachable, which is required for `now()`, `upper()`, `json_build_object()`
-- and friends.
--
-- No behaviour change: same bodies, same grants, same return values.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- claim_pairing(text, text)
-- Body identical to 0003 (the current version). Only `search_path` changes.
-- -----------------------------------------------------------------------------
create or replace function public.claim_pairing(p_code text, p_device_name text)
returns json
language plpgsql
security definer
set search_path = ''
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

-- -----------------------------------------------------------------------------
-- revoke_employee(uuid)
-- Body identical to 0004. Only `search_path` changes.
-- -----------------------------------------------------------------------------
create or replace function public.revoke_employee(p_employee_id uuid)
returns json
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller uuid := auth.uid();
    v_rows   int := 0;
begin
    if v_caller is null then
        return json_build_object('ok', false, 'error', 'unauthenticated');
    end if;

    -- Atomic flip: ownership verified IN the predicate (requesting user must
    -- be the row's owner); only non-REVOKED rows change (idempotent).
    update public.employees
       set status      = 'REVOKED',
           revoked_at  = (extract(epoch from now()) * 1000)::bigint,
           revoked_by  = v_caller,
           fcm_token   = ''
     where id        = p_employee_id
       and owner_uid = v_caller
       and status <> 'REVOKED';

    get diagnostics v_rows = row_count;

    if v_rows = 0 then
        -- Either not the owner, or already revoked. Deliberately vague: no
        -- information is leaked about other owners' rows.
        return json_build_object('ok', false, 'error', 'not-found');
    end if;

    return json_build_object('ok', true, 'rows', v_rows);
end;
$$;

-- `create or replace` preserves existing grants, but stating them keeps this
-- migration self-contained and prevents a future `create or replace` from
-- silently widening access. anon must never be able to call either.
revoke all on function public.claim_pairing(text, text) from public;
grant execute on function public.claim_pairing(text, text) to authenticated;

revoke all on function public.revoke_employee(uuid) from public, anon;
grant execute on function public.revoke_employee(uuid) to authenticated;
