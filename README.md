# i-kt

Tiny upload endpoint behind [i.flxrs.com](https://i.flxrs.com), compatible with ShareX, Chatterino and DankChat custom uploaders.

`POST /upload` takes a multipart request with basic auth, stores the first file under a short generated name and responds with its public URL as plain text. Serving the stored files is left to the reverse proxy.

`GET /metrics` serves Prometheus metrics (requests, JVM, `upload_size_bytes`) and should not be exposed publicly.

## Configuration

| Variable           | Default                  |
|--------------------|--------------------------|
| `UPLOAD_PASSWORD`  | required                 |
| `UPLOAD_USER`      | `flxrs`                  |
| `UPLOAD_URL`       | `http://localhost:8080/` |
| `UPLOAD_DIRECTORY` | `uploads` (`/data` in the image) |
| `PORT`             | `8080`                   |

## Image

`ghcr.io/flex3r/i-kt`, built on every push to `master`.
