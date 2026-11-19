package com.example.data.remote

import com.example.BuildConfig

object SecretsConfig {
    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val mapsApiKey: String
        get() = BuildConfig.MAPS_API_KEY.trim()

    val shareBaseUrl: String
        get() = BuildConfig.SHARE_BASE_URL.trim().ifBlank { "https://muzeycha.uz" }

    val telegramBotUsername: String
        get() = BuildConfig.TELEGRAM_BOT_USERNAME.trim().trimStart('@').ifBlank { "muzeycha_uz_bot" }

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.isNotBlank() &&
            !supabaseUrl.startsWith("YOUR_") &&
            supabaseUrl.startsWith("http") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.startsWith("YOUR_")

    val isMapsConfigured: Boolean
        get() = mapsApiKey.isNotBlank() && !mapsApiKey.startsWith("YOUR_")

    const val SUPABASE_SETUP_SQL = """
-- 1) Muzeylar jadvali
create table if not exists public.museums (
  id uuid primary key,
  slug text unique not null,
  owner_user_id text default '',
  title text not null,
  dedication text default '',
  room_style text not null default 'CLASSIC_WHITE',
  bride_name text default '',
  groom_name text default '',
  wedding_date text default '',
  latitude double precision,
  longitude double precision,
  location_name text default '',
  edit_token text not null,
  created_at bigint not null
);
alter table public.museums add column if not exists owner_user_id text default '';

-- 2) Muzey rasmlari jadvali
create table if not exists public.museum_photos (
  id uuid primary key,
  museum_id uuid references public.museums(id) on delete cascade not null,
  sort_order int not null,
  image_url text not null,
  width int not null,
  height int not null,
  title text default '',
  caption text default '',
  latitude double precision,
  longitude double precision,
  location_name text default ''
);

-- 3) Obunalar jadvali
create table if not exists public.subscriptions (
  id uuid primary key,
  user_id text unique not null,
  plan text not null,
  starts_at bigint not null,
  expires_at bigint not null,
  status text not null,
  telegram_chat_id bigint,
  reminder_3d_sent boolean not null default false
);
alter table public.subscriptions add column if not exists telegram_chat_id bigint;
alter table public.subscriptions add column if not exists reminder_3d_sent boolean not null default false;

-- 4) To'y mehmonlari tabriklari jadvali
create table if not exists public.wedding_wishes (
  id uuid primary key,
  museum_id uuid references public.museums(id) on delete cascade not null,
  guest_name text not null,
  message text not null,
  created_at bigint not null
);

-- 5) RLS siyosatlari
alter table public.museums enable row level security;
alter table public.museum_photos enable row level security;
alter table public.subscriptions enable row level security;
alter table public.wedding_wishes enable row level security;

create policy "Public read museums" on public.museums for select using (true);
create policy "Public insert museums" on public.museums for insert with check (true);
create policy "Update own museum" on public.museums for update using (true);
create policy "Delete own museum" on public.museums for delete using (true);

create policy "Public read photos" on public.museum_photos for select using (true);
create policy "Public insert photos" on public.museum_photos for insert with check (true);
create policy "Delete museum photos" on public.museum_photos for delete using (true);

create policy "Read subscriptions" on public.subscriptions for select using (true);
create policy "Upsert subscriptions" on public.subscriptions for insert with check (true);
create policy "Update subscriptions" on public.subscriptions for update using (true);

create policy "Read wishes" on public.wedding_wishes for select using (true);
create policy "Insert wishes" on public.wedding_wishes for insert with check (true);

-- 6) Storage: 'museum-photos' nomli PUBLIC bucket yarating
"""
}
