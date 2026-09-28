-- =============================================================================
-- PayVoice — migration 0002: devices registry + realtime publication.
-- =============================================================================
-- Adds:
--   devices                FCM token registry per auth user (spec §12/§15).
--   supabase_realtime      publication entries for employee/device state.
-- Removes (legacy, replaced by a Dashboard Database Webhook → fcm-gateway):
--   payment_events_fanout  trigger + trigger_fcm_gateway() that shipped a
--                          service-role JWT via app.settings.service_jwt.
-- =============================================================================

-- =============================================================================
-- devices — FCM token registry (spec §12)
-- =============================================================================
create table if not exists public.devices (
    id                bigint generated always as identity primary key,
    user_id           uuid        not null references auth.users (id) on delete cascade,
    fcm_token         text        not null default '',
    device_name       text        not null default 'Device',
    platform          text        not null default 'android',
    is_active         boolean     not null default true,
    last_seen_at      bigint      not null default 0,
    token_refreshed_at bigint,
    created_at        timestamptz not null default now()
);

-- One live device row per user (upsert target; rotate token in place).
create unique index if not exists uq_devices_user on public.devices (user_id);

create index if not exists idx_devices_token on public.devices (fcm_token) where is_active;

alter table public.devices enable row level security;

-- A user sees and maintains ONLY its own device row. The fcm-gateway uses
-- the secret key (service role, BYPASSRLS) to read active tokens and to
-- deactivate rows whose tokens FCM reports as unregistered.
create policy "user maintains own device"
    on public.devices for all
    to authenticated
    using (user_id = auth.uid())
    with check (user_id = auth.uid());

-- =============================================================================
-- Realtime publication (state sync ONLY — never payment delivery)
-- =============================================================================
alter publication supabase_realtime add table public.employees;
alter publication supabase_realtime add table public.devices;

-- =============================================================================
-- Retire the legacy-JWT fan-out trigger.
-- =============================================================================
-- The payment fan-out is invoked by a Supabase **Database Webhook**
-- (Dashboard → Database → Webhooks → payment_events INSERT) which delivers
-- the row payload to the fcm-gateway edge function and authenticates with a
-- secret key server-side. No credential is stored in Postgres settings, and
-- no `app.settings.service_jwt` exists anywhere in this schema.
drop trigger if exists payment_events_fanout on public.payment_events;
drop function if exists public.trigger_fcm_gateway();
