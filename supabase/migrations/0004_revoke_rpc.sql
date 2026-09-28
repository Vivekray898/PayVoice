-- =============================================================================
-- PayVoice — migration 0004: atomic owner-side revoke RPC (spec §10/§11).
-- =============================================================================
-- Why an RPC instead of a client-side UPDATE: the RLS update policy lets an
-- owner flip status on their own rows, but "revoke" must be ONE atomic,
-- server-verified operation:
--
--   * the requesting user is verified against employees.owner_uid via
--     auth.uid() — no client-supplied owner id is ever trusted;
--   * only a non-REVOKED row flips (idempotent re-revoke is a no-op, not an
--     error);
--   * revoked_at/revoked_by are stamped by the SERVER clock/identity, never
--     by the client (mirrors claim_pairing: Postgres time is the only clock);
--   * the fcm_token column is cleared so the dead binding is never dialed
--     again even if a stale `devices` row lingers;
--   * cross-owner revocation is impossible by construction.
--
-- The client keeps a counted RLS-filtered UPDATE fallback so revocation still
-- works before this migration is applied (EmployeeRepository.revoke).
-- =============================================================================

create or replace function public.revoke_employee(p_employee_id uuid)
returns json
language plpgsql
security definer
set search_path = public
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

revoke all on function public.revoke_employee(uuid) from public, anon;
grant execute on function public.revoke_employee(uuid) to authenticated;
