alter table public.profiles
    add column if not exists role text not null default 'user'
        check (role in ('user', 'admin')),
    add column if not exists is_active boolean not null default true;

create or replace function public.prevent_profile_admin_field_changes()
returns trigger
language plpgsql
as $$
begin
    if coalesce(auth.role(), '') <> 'service_role'
       and current_user not in ('postgres', 'supabase_admin')
       and (new.role is distinct from old.role or new.is_active is distinct from old.is_active) then
        raise exception 'Role and account status can only be changed by trusted admin services';
    end if;
    return new;
end;
$$;

drop trigger if exists protect_profile_admin_fields on public.profiles;
create trigger protect_profile_admin_fields
    before update on public.profiles
    for each row execute function public.prevent_profile_admin_field_changes();

create table if not exists public.admin_debug_logs (
    id bigint generated always as identity primary key,
    actor_id uuid not null references auth.users(id) on delete cascade,
    event text not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

alter table public.admin_debug_logs enable row level security;
revoke all on public.admin_debug_logs from public, anon, authenticated;
grant all on public.admin_debug_logs to service_role;
