create table if not exists public.app_config (
    key text primary key,
    value jsonb not null,
    updated_at timestamptz not null default now()
);

alter table public.app_config enable row level security;

revoke all on public.app_config from public, anon, authenticated;
grant select on public.app_config to authenticated;
grant all on public.app_config to service_role;

drop policy if exists "Authenticated users can read app config" on public.app_config;
create policy "Authenticated users can read app config"
    on public.app_config
    for select
    to authenticated
    using (true);
