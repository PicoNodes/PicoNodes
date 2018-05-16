CREATE VIEW source_files_current AS
SELECT rev.file AS file, rev.id AS revision
FROM (
  SELECT DISTINCT ON (rev.file) rev.id, rev.file, rev.content
  FROM source_file_revisions AS rev
  ORDER BY rev.file ASC, rev.created_at DESC
) AS rev
WHERE rev.content IS NOT NULL;
