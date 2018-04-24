CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE downloaders(
       id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
       label TEXT
);
