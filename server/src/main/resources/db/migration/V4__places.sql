-- A place is stored once, with a copy of its name, address and position: later views never call the geocoder,
-- and OpenStreetMap objects can be edited or deleted (PRD, Storing and hosting).
create table place (
    id         uuid primary key default gen_random_uuid(),
    source     text             not null check (source in ('OSM', 'LINK')),
    -- `N802009153`: Nominatim's node/way/relation letter and the OpenStreetMap id.
    osm_ref    text unique,
    -- LINK places: the link's canonical address, so the same link pasted twice is one place.
    link_url   text unique,
    name       text             not null,
    address    text,
    latitude   double precision not null,
    longitude  double precision not null,
    created_by uuid             references app_user (id) on delete set null,
    created_at timestamptz      not null default now(),
    check ((source = 'OSM') = (osm_ref is not null)),
    check ((source = 'LINK') = (link_url is not null))
);
