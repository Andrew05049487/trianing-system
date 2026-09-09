# Training history video deployment

Apply `sqlserver_migration_training_history_video.sql` to the existing SQL
Server database before deploying the matching backend. Hibernate
`ddl-auto=update` remains enabled for compatibility, but is not a replacement
for this migration.

Render environment settings:

- `MAX_VIDEO_FILE_SIZE`: servlet per-file limit, default `100MB`.
- `MAX_VIDEO_REQUEST_SIZE`: multipart request limit, default `105MB`.
- `MAX_VIDEO_BYTES`: application-level byte limit, default `104857600`.
- `CUSTOM_EXERCISE_IDENTITY_SECRET`: existing identity secret used to authorize
  the patient or a bound therapist when reading a video.

The app uploads metadata first and receives `training_history.id`, then uploads
the binary file to `/api/training-history/{id}/video`. Re-uploading metadata is
idempotent by `(user_id, client_timestamp)`, and re-uploading a video replaces
the one row keyed by `history_id`.

Existing `????` values cannot be recovered by changing `VARCHAR` to
`NVARCHAR`; re-upload the original local app record or recreate that row after
the migration.
