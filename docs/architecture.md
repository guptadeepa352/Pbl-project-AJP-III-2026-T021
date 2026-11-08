# System architecture and security model

## Scope

Secure Cloud Storage is a browser client, Spring Boot REST API, MySQL
relational database, and external AWS S3 object store. It demonstrates
browser-side file encryption and authenticated, owner-scoped file management.
The architecture has zero-knowledge as a design goal, but the current project
has not been independently verified as a zero-knowledge system.

## Components

```text
┌─────────────────────────────────────────────────────────────────────┐
│ Browser: frontend HTML/CSS/JavaScript                               │
│ - registration, login, account dashboard                            │
│ - sessionStorage bearer token                                        │
│ - Web Crypto file encryption/decryption                              │
│ - sends ciphertext and non-secret file metadata                     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │ HTTPS in deployment
                                │ JSON + Authorization: Bearer <JWT>
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Spring Boot API                                                      │
│ Controllers → validation/security → services → repositories/S3      │
│ - BCrypt password verification                                       │
│ - JWT authentication filter                                          │
│ - owner-scoped file lookup and lifecycle                             │
│ - does not decrypt file content                                      │
└────────────────┬──────────────────────────────────┬─────────────────┘
                 │ JPA                              │ AWS SDK
                 ▼                                  ▼
┌──────────────────────────────┐      ┌───────────────────────────────┐
│ MySQL                        │      │ Private AWS S3 bucket         │
│ users                        │      │ encrypted file bytes          │
│ file_metadata                │      │ opaque generated object keys  │
└──────────────────────────────┘      └───────────────────────────────┘
```

### Browser frontend

The static frontend uses HTML, CSS, and vanilla JavaScript. `api.js` handles
JSON requests and attaches the current bearer token. Tokens are stored in
tab-scoped `sessionStorage` and removed at logout or when an authenticated
request returns `401`. The file manager uses the Web Crypto API for local
encryption and decryption. The dashboard displays the email returned by
`GET /api/auth/me`; the greeting uses the email's local part because the
current profile endpoint does not return the user's registered display name.

### Spring Boot backend

The Java 21 application uses Spring Web, Spring Security, Spring Data JPA,
Jakarta Validation, JJWT, and the AWS SDK for Java 2.x. Controllers expose
authentication, health, and file endpoints. Services apply account
normalization, file validation, ownership checks, ciphertext storage, and
metadata persistence. JPA repositories use MySQL in regular development and
H2 for tests.

### Data stores

MySQL stores user accounts and non-secret file metadata, including a private
S3 object key. S3 stores encrypted file bytes. The object key is randomly
generated and is not returned to the browser.

## Encryption workflow

The browser implementation is `frontend/js/encryption.js`:

1. The user selects a file and enters a separate file-encryption secret (at
   least 12 characters). The secret is not the account login password.
2. The browser reads the file bytes and obtains a fresh 16-byte salt and
   12-byte IV with `crypto.getRandomValues`.
3. The secret's UTF-8 bytes are imported as PBKDF2 password material. PBKDF2
   with HMAC-SHA-256 and 600,000 iterations derives a non-extractable,
   256-bit AES-GCM key for encryption and decryption.
4. AES-256-GCM encrypts the file with a 128-bit authentication tag. The tag is
   appended to the ciphertext by Web Crypto.
5. The frontend sends ciphertext encoded as unpadded base64url, the plaintext
   file size, filename, MIME type, algorithm/KDF parameters, IV, and salt to
   `POST /api/files/upload`. It does not send the secret, a derived key, or
   plaintext bytes.
6. The API validates the declared metadata and ciphertext length
   (`plaintext size + 16-byte GCM tag`), writes the ciphertext to S3, and
   saves metadata to MySQL.
7. On download, the API returns ciphertext and metadata for an owned file. The
   user re-enters the secret in the browser. PBKDF2 derives the key again and
   AES-GCM verifies the tag as part of decryption. Incorrect secrets, modified
   ciphertext, or an invalid tag cause decryption to fail locally.

The API checks encoding, length, file-size limits, and supported algorithm
metadata, but it does not authenticate/decrypt the payload. It cannot verify
that a custom or malicious client actually encrypted the uploaded bytes.

## Authentication and authorization workflow

1. Registration validates the name, email, and strong-password requirements.
   The backend trims the name, normalizes the email to lowercase, checks
   uniqueness, hashes the password with BCrypt, and persists the user.
2. Login normalizes the supplied email and verifies the password using the
   configured BCrypt encoder. Incorrect password and unknown email receive
   the same generic response.
3. On success, the API signs a JWT with the configured Base64 key and
   expiration. The browser retains it in `sessionStorage`.
4. Authenticated API requests use `Authorization: Bearer <JWT>`. The JWT filter
   verifies signature/expiration, resolves the account, and sets the
   authenticated principal.
5. For each file operation, the backend derives the account identity from the
   principal. File queries filter by both file ID and owner ID. Another user's
   file is reported as not found (`404`) to avoid disclosing its existence.
6. Logout clears the tab-scoped token and in-memory form state. Expired/invalid
   tokens are rejected; authenticated browser requests that receive `401`
   clear the token and redirect to login.

The signing key is provided through `JWT_SECRET`; token lifetime is
`JWT_EXPIRATION_MS` (900,000 ms / 15 minutes by default). Changing the signing
key invalidates tokens signed with its prior value.

## Database design

The schema reference is [`../database/schema.sql`](../database/schema.sql).
JPA entities are in `backend/src/main/java/com/mca/securecloudstorage/entity/`.

### `users`

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | `BIGINT` primary key | Generated account identifier |
| `name` | `VARCHAR(100)` | Registered display name |
| `email` | `VARCHAR(254)` unique, not null | Lowercase normalized account email |
| `password_hash` | `VARCHAR(60)` | BCrypt hash; never returned in API responses |
| `created_at` | `TIMESTAMP(6)` | Creation time |
| `updated_at` | `TIMESTAMP(6)` | Most recent entity update time |

Unique constraint: `uk_users_email`.

### `file_metadata`

| Column | Type | Meaning |
| --- | --- | --- |
| `id` | `BIGINT` primary key | Generated file metadata identifier |
| `owner_id` | `BIGINT` foreign key | References `users.id`; identifies owner |
| `original_filename` | `VARCHAR(255)` | Client-provided filename |
| `s3_object_key` | `VARCHAR(1024)` | Internal generated S3 key; never returned to clients |
| `file_size` | `BIGINT` | Plaintext file size in bytes |
| `mime_type` | `VARCHAR(127)` | Client-provided MIME metadata |
| `encryption_algorithm` | `VARCHAR(64)` | Supported value is `AES-256-GCM` |
| `key_derivation_function` | `VARCHAR(64)` | Supported value is `PBKDF2-SHA-256` |
| `key_derivation_iterations` | `INT` | Supported value is `600000` |
| `iv`, `salt` | `VARCHAR(128)` each | Base64url non-secret encryption parameters |
| `created_at` | `TIMESTAMP(6)` | Upload metadata creation time |

The owner relationship uses foreign key `fk_file_metadata_owner`. Index
`idx_file_metadata_owner_created` supports owner-scoped, newest-first file
listing. The secret and derived encryption key are not stored in MySQL.

## AWS S3 architecture

`AwsConfig` creates an AWS SDK `S3Client` using the configured region and SDK
default credential provider chain. `S3Service`:

- Requires a bucket when an object operation is requested.
- Generates a random 32-byte key and encodes it as
  `objects/<64 lowercase hexadecimal characters>`.
- Uploads `application/octet-stream` bytes and requests S3-managed
  server-side encryption (`AES256`).
- Does not set an ACL or expose the object key through API responses.
- Downloads/deletes only keys matching the generated-key format.

`FileService` performs the authenticated owner lookup before using an internal
key. Upload writes to S3 before saving metadata and attempts S3 cleanup if
metadata persistence fails. Delete removes S3 content before deleting the
database record. These are separate systems and do not participate in a
distributed transaction; failures can require retry or manual reconciliation.

Automated tests replace `S3Client` with Mockito. They do not require live AWS
credentials or issue requests to AWS.

## Zero-knowledge architecture: status and caveats

The goal is that only a client holding the file secret can recover plaintext.
The normal frontend flow keeps the secret and derived key in the browser,
uploads ciphertext, and performs decryption locally. The server has no
decryption endpoint or key material.

This does not establish a complete zero-knowledge guarantee. The service sees
account identifiers, filenames, sizes, MIME types, timestamps, IVs, and salts.
The service also controls the API and may serve frontend code; no signed-client
or reproducible-build verification is in place. The browser runtime/device may
be compromised, and a low-entropy secret can be guessed offline against
available ciphertext. There is no key recovery or independent audit. Do not
claim the project provides audited zero-knowledge storage until these trust
boundaries are addressed and reviewed.

## Further reading

- [API documentation](api-documentation.md)
- [Development and operations guide](development-and-operations.md)
- [Project README](../README.md)
