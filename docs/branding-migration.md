# NexaMusic branding migration

This branch replaces app-owned NexaMusic identifiers with NexaMusic across the Android application and supporting modules.

- Kotlin package roots and source directories were renamed to `com.nexamusic`.
- App-owned class, resource, directory, and filename identifiers were renamed.
- Room schema history was moved from `com.nexamusic.music.db.InternalDatabase` to `com.nexamusic.music.db.InternalDatabase`; schema versions and definitions were retained.
- The installed application ID remains `com.nexamusic.music` to preserve update compatibility.
- `LICENSE` and `NOTICE` contents remain unchanged to preserve required attribution.
- GitHub workflow definitions were left untouched by the bulk rename because the connected GitHub integration does not have workflow-file write permission.

The cleanup job's verification reported zero remaining NexaMusic paths and zero remaining non-legal text references outside the preserved GitHub workflow files.
