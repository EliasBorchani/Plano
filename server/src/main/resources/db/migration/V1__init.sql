-- Accounts. A user can sign in with several providers (one auth_identity each).
create table app_user (
    id           uuid primary key default gen_random_uuid(),
    display_name text        not null,
    email        text,
    created_at   timestamptz not null default now()
);

create table auth_identity (
    provider   text        not null check (provider in ('google', 'apple')),
    subject    text        not null,
    user_id    uuid        not null references app_user (id) on delete cascade,
    email      text,
    created_at timestamptz not null default now(),
    primary key (provider, subject)
);

-- Opaque session tokens live in an httpOnly cookie; only their SHA-256 is stored.
create table session (
    token_hash text primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);
create index session_user_idx on session (user_id);

create table event (
    id          uuid primary key default gen_random_uuid(),
    title       text        not null,
    type        text        not null,
    description text,
    owner_id    uuid        not null references app_user (id),
    created_at  timestamptz not null default now()
);

-- Visibility is computed from this table only (PRD: never from group membership).
create table event_member (
    event_id  uuid        not null references event (id) on delete cascade,
    user_id   uuid        not null references app_user (id) on delete cascade,
    role      text        not null check (role in ('OWNER', 'PLANNER', 'PARTICIPANT')),
    joined_at timestamptz not null default now(),
    primary key (event_id, user_id)
);
create index event_member_user_idx on event_member (user_id);

create table poll (
    id         uuid primary key default gen_random_uuid(),
    event_id   uuid        not null references event (id) on delete cascade,
    kind       text        not null check (kind in ('DATE')),
    created_at timestamptz not null default now(),
    unique (event_id, kind)
);

create table poll_option (
    id         uuid primary key default gen_random_uuid(),
    poll_id    uuid        not null references poll (id) on delete cascade,
    date       date        not null,
    created_by uuid        references app_user (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (poll_id, date)
);

create table vote (
    option_id  uuid        not null references poll_option (id) on delete cascade,
    user_id    uuid        not null references app_user (id) on delete cascade,
    choice     text        not null check (choice in ('PREFER', 'CANNOT', 'IF_MAJORITY')),
    updated_at timestamptz not null default now(),
    primary key (option_id, user_id)
);
