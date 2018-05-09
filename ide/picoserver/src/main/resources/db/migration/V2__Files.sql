CREATE TABLE source_files(
       id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
       name TEXT NOT NULL UNIQUE
);

CREATE TABLE source_file_revisions(
       id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
       file UUID NOT NULL REFERENCES source_files,
       content TEXT,
       created_at TIMESTAMP NOT NULL
);
