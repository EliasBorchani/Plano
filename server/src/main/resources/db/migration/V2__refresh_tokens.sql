-- Sessions become a short JWT access token (never stored) plus a rotating refresh token.
drop table session;

-- One row per refresh token. A family is one sign-in on one device: each refresh replaces the token
-- inside its family, and reusing a replaced token revokes the whole family.
create table refresh_token (
    token_hash  text primary key,
    family_id   uuid        not null,
    user_id     uuid        not null references app_user (id) on delete cascade,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    replaced_at timestamptz,
    revoked_at  timestamptz
);
create index refresh_token_family_idx on refresh_token (family_id);
create index refresh_token_user_idx on refresh_token (user_id);
