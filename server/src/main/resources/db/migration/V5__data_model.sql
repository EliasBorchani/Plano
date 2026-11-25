-- PRD step 5: groups, invitations, member status and RSVP, place poll, deadlines, validation, archiving, reminders.

-- Accounts that chose to be found by name (PRD, Finding people). Off by default.
alter table app_user add column searchable boolean not null default false;

-- Groups. Every member manages the one current invite link (owner, 2026-11-25); a new link replaces the token.
create table user_group (
    id                uuid primary key default gen_random_uuid(),
    name              text        not null,
    created_by        uuid        references app_user (id) on delete set null,
    created_at        timestamptz not null default now(),
    invite_token      text        not null unique,
    invite_max_uses   int         not null check (invite_max_uses > 0),
    invite_uses       int         not null default 0,
    invite_expires_at timestamptz not null
);

create table group_member (
    group_id  uuid        not null references user_group (id) on delete cascade,
    user_id   uuid        not null references app_user (id) on delete cascade,
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);
create index group_member_user_idx on group_member (user_id);

-- The groups an event's invitees were picked from: context only, visibility still comes from event_member.
create table event_group (
    event_id uuid not null references event (id) on delete cascade,
    group_id uuid not null references user_group (id) on delete cascade,
    primary key (event_id, group_id)
);
create index event_group_group_idx on event_group (group_id);

-- Archiving by the owner locks editing.
alter table event add column archived_at timestamptz;

-- Member status (INVITED until the first open) and the RSVP, a separate yes or no on coming.
alter table event_member
    add column status     text not null default 'INVITED' check (status in ('INVITED', 'JOINED', 'LEFT')),
    add column rsvp       text not null default 'NOT_ANSWERED' check (rsvp in ('NOT_ANSWERED', 'COMING', 'NOT_COMING')),
    add column invited_by uuid references app_user (id) on delete set null;
update event_member set status = 'JOINED';

-- Someone invited by email who has no account: a guest of that event (guest_event_id) with an email and a
-- personal link. The token signs them in on any device; it is shown to planners so they can send it themselves
-- until emails go out.
create table event_invitation (
    token      text primary key,
    event_id   uuid        not null references event (id) on delete cascade,
    user_id    uuid        not null references app_user (id) on delete cascade,
    email      text        not null,
    invited_by uuid        references app_user (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (event_id, email)
);

-- Polls: a place poll next to the date poll, a deadline from the first option, and the planner's choice.
alter table poll drop constraint poll_kind_check;
alter table poll
    add constraint poll_kind_check check (kind in ('DATE', 'PLACE')),
    add column started_at          timestamptz,
    add column deadline            timestamptz,
    add column validated_option_id uuid,
    add check ((started_at is null) = (deadline is null));

insert into poll (event_id, kind) select id, 'PLACE' from event;
update poll p set started_at = p.created_at, deadline = p.created_at + interval '7 days'
where exists (select 1 from poll_option o where o.poll_id = p.id);

-- An option is a date (date poll), or a place or a plain link card (place poll).
alter table poll_option
    alter column date drop not null,
    add column place_id   uuid references place (id),
    add column link_url   text,
    add column link_title text,
    add constraint poll_option_one_value check (num_nonnulls(date, place_id, link_url) = 1),
    add constraint poll_option_link_title check ((link_url is null) = (link_title is null)),
    add unique (poll_id, place_id),
    add unique (poll_id, link_url);

alter table poll
    add foreign key (validated_option_id) references poll_option (id) on delete set null;

-- Reminder emails, computed from the poll's start and deadline (PollRules.schedule). Unsent rows are rebuilt when
-- the deadline moves and dropped on validation; a sender job comes with the email provider.
create table poll_reminder (
    poll_id  uuid        not null references poll (id) on delete cascade,
    kind     text        not null check (kind in ('STARTED', 'DEADLINE_SOON', 'LAST_DAY', 'LAST_HOUR')),
    due_at   timestamptz not null,
    sent_at  timestamptz,
    primary key (poll_id, kind)
);
create index poll_reminder_due_idx on poll_reminder (due_at) where sent_at is null;
