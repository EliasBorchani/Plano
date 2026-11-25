-- A guest is an app_user with no auth_identity, bound to exactly one event: claiming a guest from that event's
-- link ("C'est moi") can then never open another event. Signing in with Google or Apple turns the guest into an
-- account, or merges it into an existing one.
alter table app_user add column guest_event_id uuid references event (id) on delete cascade;
create index app_user_guest_event_idx on app_user (guest_event_id) where guest_event_id is not null;
