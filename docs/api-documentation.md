# REST API reference

Base URL in local development: `http://localhost:8080`.

Unless otherwise stated, request and response bodies use JSON. Protected routes
require a signed, unexpired JWT in the `Authorization` header:

```http
Authorization: Bearer <access-token>
```

Missing, invalid, or expired credentials return `401 Unauthorized`. An
authenticated request for a file that does not exist or belongs to another
user returns `404 Not Found`.

## Health

### `GET /api/health`

Authentication: not required.

Returns `200 OK`:

```json
{
  "status": "UP"
}
```

This is an application liveness endpoint. It does not check the database or
AWS connectivity.

## Authentication

### `POST /api/auth/register`

Authentication: not required. Content type: `application/json`.

Request:

```json
{
  "name": "Example User",
  "email": "user@example.test",
  "password": "StrongPassword1!"
}
```

Validation:

- `name`: required, non-blank, no more than 100 characters.
- `email`: required, valid email address, no more than 254 characters. The
  service trims and normalizes it to lowercase.
- `password`: required, at least 12 characters, containing uppercase,
  lowercase, number, and symbol; UTF-8 size is limited to 72 bytes for BCrypt.

Success: `201 Created`.

```json
{
  "id": 1,
  "name": "Example User",
  "email": "user@example.test",
  "createdAt": "2026-10-08T10:00:00Z"
}
```

The password and BCrypt hash are never returned. Registration does not issue a
JWT. An existing email returns `409 Conflict`; invalid data returns
`400 Bad Request`.

### `POST /api/auth/login`

Authentication: not required. Content type: `application/json`.

Request:

```json
{
  "email": "user@example.test",
  "password": "StrongPassword1!"
}
```

Email must be valid and at most 254 characters. Password is required and
limited to 1024 characters at the request boundary.

Success: `200 OK`.

```json
{
  "accessToken": "<signed JWT>",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

Unknown email and incorrect password return the same generic
`401 Unauthorized` response. Invalid request fields return `400 Bad Request`.

### `GET /api/auth/me`

Authentication: required.

Success: `200 OK`.

```json
{
  "email": "user@example.test"
}
```

The current endpoint returns the authenticated email, not the registered
display name.

## Files

All file routes require authentication. Ownership is determined from the
authenticated principal, not from client-supplied owner fields.

### `POST /api/files/upload`

Content type: `application/json`. The browser encrypts the file before this
request. This endpoint accepts ciphertext bytes as unpadded base64url; it does
not accept a multipart plaintext file.

Request:

```json
{
  "originalFilename": "report.pdf",
  "mimeType": "application/pdf",
  "fileSize": 1234,
  "encryptionAlgorithm": "AES-256-GCM",
  "keyDerivationFunction": "PBKDF2-SHA-256",
  "keyDerivationIterations": 600000,
  "iv": "<base64url 12-byte IV>",
  "salt": "<base64url 16-byte salt>",
  "ciphertext": "<base64url ciphertext including 16-byte GCM tag>"
}
```

Constraints:

- Plaintext file size is 0 through 10 MiB.
- Filename is non-blank, at most 255 characters, and cannot contain path
  separators/control characters or be `.` / `..`.
- MIME type is syntactically checked but not restricted to an allow-list or
  verified against file contents.
- Algorithm, KDF, and iteration count must exactly match the supported
  parameters shown above.
- IV and salt must be valid base64url metadata decoding to 12 and 16 bytes.
- Ciphertext must be valid base64url and decode to
  `fileSize + 16` bytes.

Success: `201 Created` with file metadata (same fields as the metadata
response below, without `ciphertext`). S3 object keys, owner details, and
encryption secrets are not returned.

Invalid metadata or ciphertext returns `400 Bad Request`. S3 storage errors
return `502 Bad Gateway`. If metadata persistence fails after S3 upload, the
service attempts to delete the newly uploaded object.

### `GET /api/files`

Lists only the authenticated user's metadata, newest first.

Success: `200 OK`.

```json
[
  {
    "id": 1,
    "originalFilename": "report.pdf",
    "fileSize": 1234,
    "mimeType": "application/pdf",
    "encryptionAlgorithm": "AES-256-GCM",
    "keyDerivationFunction": "PBKDF2-SHA-256",
    "keyDerivationIterations": 600000,
    "iv": "<base64url IV>",
    "salt": "<base64url salt>",
    "createdAt": "2026-10-08T10:00:00Z"
  }
]
```

### `GET /api/files/{id}`

Returns one file metadata response for an ID owned by the authenticated
account. An unknown ID or another user's ID returns `404 Not Found`.

### `GET /api/files/{id}/download`

Returns the metadata required by the browser, along with unpadded base64url
ciphertext.

Success: `200 OK`.

```json
{
  "id": 1,
  "originalFilename": "report.pdf",
  "fileSize": 1234,
  "mimeType": "application/pdf",
  "encryptionAlgorithm": "AES-256-GCM",
  "keyDerivationFunction": "PBKDF2-SHA-256",
  "keyDerivationIterations": 600000,
  "iv": "<base64url IV>",
  "salt": "<base64url salt>",
  "createdAt": "2026-10-08T10:00:00Z",
  "ciphertext": "<base64url ciphertext>"
}
```

The backend selects the S3 object key from metadata for the authenticated
owner; callers cannot provide an object key. Unknown or cross-user file IDs
return `404` without an S3 download. The browser derives a key from the
separately supplied file secret, verifies the GCM tag, decrypts, and initiates
a local download. Wrong secrets, corrupt ciphertext, and invalid tags fail in
the browser; the API does not decrypt or validate the GCM tag.

### `DELETE /api/files/{id}`

Deletes an owned S3 object and its metadata.

Success: `204 No Content`, no response body. Unknown IDs and cross-user IDs
return `404`; they do not trigger S3 deletion. S3 failure returns
`502 Bad Gateway` and retains the metadata. If S3 deletion succeeds but
database deletion fails, the API returns `500 Internal Server Error`; this
cross-system operation is not atomic.

## Error response format

Errors are JSON. Validation errors include `fieldErrors`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "fieldErrors": {
    "email": "Email must be a valid email address"
  }
}
```

Not every error contains field errors; for example:

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication is required or the bearer token is invalid",
  "fieldErrors": {}
}
```

Other mapped responses include `409 Conflict` for duplicate email,
`404 Not Found` for unavailable/foreign file metadata, `502 Bad Gateway` for
S3 failures, and `500 Internal Server Error` for file metadata deletion
failure after object deletion.

## JWT configuration

`JWT_SECRET` supplies the Base64-encoded HMAC signing key and must contain at
least 256 bits of randomness. `JWT_EXPIRATION_MS` controls token lifetime and
defaults to `900000` milliseconds. Treat the key as a secret. Changing it
invalidates existing tokens.

## CORS

The default allowed origins are:

- `http://localhost:5500`
- `http://127.0.0.1:5500`
- `http://localhost:5501`
- `http://127.0.0.1:5501`

Allowed methods are `GET`, `POST`, `DELETE`, and `OPTIONS`. Allowed headers are
`Authorization` and `Content-Type`. Configure other explicit origins through
`app.cors.allowed-origins`; do not use an unrestricted wildcard in production.

## Related documents

- [Architecture and security model](architecture.md)
- [Development and operations](development-and-operations.md)
