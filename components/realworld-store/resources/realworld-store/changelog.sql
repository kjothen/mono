--liquibase formatted sql

-- The RealWorld schema. Referenced from system config as
-- `realworld-store/changelog.sql`, which is a classpath path: it resolves
-- the same in this workspace, in a generated one, and from inside a jar.

--changeset realworld:1
create table users (
  id            bigserial primary key,
  username      text        not null,
  email         text        not null,
  password_hash text        not null,
  bio           text,
  image         text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now());
create unique index users_username_key on users (username);
create unique index users_email_key on users (email);

--changeset realworld:2
-- No surrogate key: the pair is the identity, and the composite primary key
-- makes following someone twice a no-op via `on conflict do nothing` rather
-- than something the caller has to check for first.
create table follows (
  follower_id bigint      not null references users (id) on delete cascade,
  followed_id bigint      not null references users (id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (follower_id, followed_id));

--changeset realworld:3
create table articles (
  id          bigserial primary key,
  slug        text        not null unique,
  title       text        not null,
  description text        not null,
  body        text        not null,
  author_id   bigint      not null references users (id) on delete cascade,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now());
create index articles_author_id_idx on articles (author_id);
-- Matches the default listing order, which is most-recent-first with id as
-- the tie-break so a page boundary is stable when timestamps collide.
create index articles_created_at_idx on articles (created_at desc, id desc);

--changeset realworld:4
create table tags (
  id   bigserial primary key,
  name text not null unique);
create table article_tags (
  article_id bigint not null references articles (id) on delete cascade,
  tag_id     bigint not null references tags (id) on delete cascade,
  primary key (article_id, tag_id));

--changeset realworld:5
create table favorites (
  user_id    bigint      not null references users (id) on delete cascade,
  article_id bigint      not null references articles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, article_id));

--changeset realworld:6
-- Cascading from articles is what makes deleting an article a single
-- statement: its tags, favorites and comments go with it.
create table comments (
  id         bigserial primary key,
  article_id bigint      not null references articles (id) on delete cascade,
  author_id  bigint      not null references users (id) on delete cascade,
  body       text        not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now());
create index comments_article_id_idx on comments (article_id);
