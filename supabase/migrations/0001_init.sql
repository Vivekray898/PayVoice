-- =============================================================================
-- PayVoice — initial schema (Supabase), replaces Firebase Auth/Firestore.
-- =============================================================================
-- Model (mirrors the deleted Firestore rules):
--   auth.users            one anonymous user per device (Supabase Auth).
--   pairing_codes         owner-created, short-lived, single-use codes.
--   employees             employee device registry (id == auth.uid()).
--   payment_events        owner-created events; fanned out via Database
--                         Webhook → fcm-gateway (no trigger, no stored creds).
--
-- Wire convention: timestamps are epoch-millis bigint (the Android client
-- parses no date strings). Security model lives entirely in RLS + the
-- security-definer claim_pairing() function; clients hold only the anon key
-- and their own JWT.
-- =============================================================================

-- =============================================================================
-- Tables
-- =============================================================================

-- Owner-created pairing invitations. `code` is a lookup key only — no secret
-- material, no owner id embedded.
create table if not exists public.pairing_codes (
    code        text primary key,
    owner_uid   uuid        not null references auth.users (id) on delete cascade,
    created_at  timestamptz not null default now(),
    expires_at  bigint      not null,
    used        boolean     not null default false
);

-- Employee device registry. Doc id == device uid (auth.uid()).
create table if not exists public.employees (
    id                      uuid        primary key references auth.users (id) on delete cascade,
    owner_uid               uuid        not null references auth.users (id) on delete cascade,
    name                    text        not null default 'Employee Device',
    status                  text        not null default 'ACTIVE'
                            check (status in ('ACTIVE', 'LEFT', 'REVOKED')),
    fcm_token               text        not null default '',
    paired_at               bigint      not null default (extract(epoch from now()) * 1000)::bigint,
    last_seen_at            bigint      not null default 0,
    last_event_delivered_at bigint,
    left_at                 bigint,
    revoked_at              bigint,
    revoked_by              uuid
);

-- Payment events. `id` IS the client's eventId (cross-channel fingerprint),
-- so retries and GPay+SMS double detection collapse into one row.
create table if not exists public.payment_events (
    id                      text        primary key,
    owner_uid               uuid        not null references auth.users (id) on delete cascade,
    type                    text        not null check (type in ('PAYMENT_RECEIVED', 'TEST_ANNOUNCEMENT')),
    amount_minor            bigint,
    currency                text,
    sender_name             text,
    source                  text,
    timestamp_ms            bigint,
    local_tts_requested_at_ms bigint,
    fanout_count            int,
    delivered_to            jsonb,
    delivered_at            timestamptz,
    remote_accepted_at_ms   bigint,
    created_at              timestamptz not null default now(),
    constraint amount_required_for_payment check (
        type = 'TEST_ANNOUNCEMENT'
        or (amount_minor is not null and amount_minor > 0 and currency = 'INR')
    )
);

create index if not exists idx_employees_owner on public.employees (owner_uid, status);
create index if not exists idx_payment_events_owner on public.payment_events (owner_uid, created_at);

-- =============================================================================
-- RLS — enabled on everything, deny by default.
-- =============================================================================
alter table public.pairing_codes  enable row level security;
alter table public.employees      enable row level security;
alter table public.payment_events enable row level security;

-- ---- pairing_codes ----------------------------------------------------------
-- Nobody reads codes through the API (claim happens in claim_pairing()).
-- Owners create and (to regenerate) delete ONLY their own unused codes.
create policy "owners create own pairing codes"
    on public.pairing_codes for insert
    to authenticated
    with check (owner_uid = auth.uid());

create policy "owners delete own unused pairing codes"
    on public.pairing_codes for delete
    to authenticated
    using (owner_uid = auth.uid() and used = false);

-- ---- employees --------------------------------------------------------------
-- A device sees ONLY its own row (no listing of other employees, ever).
create policy "employee reads own row"
    on public.employees for select
    to authenticated
    using (id = auth.uid());

-- Pairing claim + heartbeat: only for yourself; owner_uid must be present.
-- (status transitions LEFT/REVOKED are further constrained below.)
create policy "employee writes own row"
    on public.employees for insert
    to authenticated
    with check (id = auth.uid() and owner_uid is not null);

create policy "employee updates own row"
    on public.employees for update
    to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

-- Owner-side revoke: the owner may flip status on rows carrying their own
-- owner_uid. Implemented as an update via the same table; the filter makes
-- cross-owner revocation impossible.
create policy "owner revokes own employees"
    on public.employees for update
    to authenticated
    using (owner_uid = auth.uid())
    with check (owner_uid = auth.uid() and status in ('REVOKED'));

-- ---- payment_events ---------------------------------------------------------
-- Created by the owner they belong to (id = client fingerprint), read back
-- only by the same owner (delivery diagnostics). Employees never read this
-- table — they receive events exclusively through FCM push.
create policy "owners insert own events"
    on public.payment_events for insert
    to authenticated
    with check (
        owner_uid = auth.uid()
        and id like 'evt_%'
        and length(id) between 8 and 64
    );

create policy "owners read own events"
    on public.payment_events for select
    to authenticated
    using (owner_uid = auth.uid());

create policy "owners update own events"
    on public.payment_events for update
    to authenticated
    using (owner_uid = auth.uid())
    with check (owner_uid = auth.uid());

-- =============================================================================
-- claim_pairing(text, text) — atomic, security definer
-- =============================================================================
-- One statement does what the old client-side batch did: validate expiry +
-- single-use, mark used, upsert the employee row. Returns
-- {"ok": bool, "owner_uid": uuid | null} as JSON. The employee's own row is
-- upserted (re-pairing with a new owner just overwrites it).
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
begin
    if v_caller is null then
        return json_build_object('ok', false, 'error', 'unauthenticated');
    end if;
    if v_name is null or length(v_name) = 0 then
        v_name := 'Employee Device';
    end if;

    select owner_uid into v_owner
    from public.pairing_codes
    where code = v_code
      and used = false
      and expires_at > v_now;

    if v_owner is null then
        return json_build_object('ok', false, 'error', 'invalid-or-expired');
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

-- =============================================================================
-- Payment fan-out (Deliberately NOT here)
-- =============================================================================
-- There is NO database trigger and NO credential in Postgres settings.
-- The payment_events INSERT is delivered to the fcm-gateway edge function by
-- a Supabase **Database Webhook** (Dashboard → Database → Webhooks), which
-- authenticates with a secret key server-side. See supabase/README.md step 4
-- and supabase/functions/fcm-gateway (auth: 'secret').
