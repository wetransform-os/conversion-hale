# conversion-hale

Docker image for format conversion based on hale-cli using wetransform conversion-service.

## Image tags

- `wetransform/conversion-hale:latest` / `:<version>`

The former `-no-orientdb` variant (`latest-no-orientdb` / `<version>-no-orientdb`) has been
discontinued: since hale-cli 7 OrientDB is no longer part of hale, so the default image is free of
it (and of CVE-2017-11467). Use the default image instead; the old tags no longer receive updates.
