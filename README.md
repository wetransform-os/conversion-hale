# conversion-hale

Docker image for format conversion based on hale-cli using wetransform conversion-service.

## Image variants

- `wetransform/conversion-hale:latest` / `:<version>` — the default image.
- `wetransform/conversion-hale:latest-no-orientdb` / `:<version>-no-orientdb` — same image with
  the OrientDB dependency removed (see [`no-orientdb/Dockerfile`](no-orientdb/Dockerfile)). OrientDB
  is only used by hale as temporary instance storage, which is not needed for the data rewrite
  (e.g. GML) use case. Removing it eliminates CVE-2017-11467 (`orientdb-core`). Use this variant
  where images must be free of high-risk CVEs, e.g. on-premise deployments.
