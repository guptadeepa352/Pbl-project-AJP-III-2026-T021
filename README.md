<<<<<<< HEAD
# Secure Cloud Storage

An MCA final-year project exploring secure cloud file storage through
browser-side encryption, authenticated access, owner-scoped file operations,
and private AWS S3 object storage.

> **Security scope:** files are encrypted in the supported browser workflow
> before upload, and the server stores the bytes it receives without decrypting
> them. This is not a claim of independently verified zero-knowledge security.
> See [Architecture and security model](docs/architecture.md) for the
> limitations and assumptions.

## Contents

- [Project overview](#project-overview)
- [Problem statement](#problem-statement)
- [Objectives](#objectives)
- [Features](#features)
- [Technology stack](#technology-stack)
- [Architecture and zero-knowledge goal](#architecture-and-zero-knowledge-goal)
- [Folder structure](#folder-structure)
- [Installation and prerequisites](#installation-and-prerequisites)
- [MySQL setup](#mysql-setup)
- [AWS S3 setup](#aws-s3-setup)
- [Environment variables](#environment-variables)
- [Run locally](#run-locally)
- [Docker setup](#docker-setup)
- [Testing](#testing)
- [Security considerations and limitations](#security-considerations-and-limitations)
- [Future enhancements](#future-enhancements)
- [Technical documentation](#technical-documentation)

## Project overview

Secure Cloud Storage is a web application with a static HTML/CSS/JavaScript
frontend and a Java Spring Boot REST API. Users register and authenticate,
then upload, list, download, and delete files. The browser encrypts file
contents locally using the Web Crypto API. The API stores the encrypted bytes
in S3 and stores file ownership and non-secret encryption metadata in MySQL.

The backend provides password hashing, signed expiring JWT authentication,
request validation, per-user file authorization, and private object-storage
operations. The frontend keeps the bearer token in tab-scoped
`sessionStorage`; it does not persist the separate file-encryption secret.

## Problem statement

Cloud storage centralizes files with a service provider. If file contents are
stored as ordinary plaintext, access to the storage infrastructure or an
account can expose the files. A project can reduce this exposure by encrypting
content on the user’s device before transmitting it, while still using cloud
services for authentication, metadata, and encrypted-object availability.

This project demonstrates that design and its trade-offs. In particular,
client-side encryption does not by itself eliminate risks from compromised
client code, weak user-chosen secrets, metadata disclosure, or account takeover.

## Objectives

- Provide registration and login with validated input, BCrypt password
  hashing, and expiring JWT bearer tokens.
- Encrypt and decrypt file contents in the browser using authenticated
  encryption; do not send the file-encryption secret or derived key to the API.
- Store ciphertext in S3 and ownership/encryption metadata in a relational
  database.
- Require authentication and enforce owner checks for file listing, retrieval,
  download, and deletion.
- Provide a responsive interface with upload progress states, file actions,
  feedback, and deletion confirmation.
- Make the core automated test suite repeatable without live AWS credentials
  or cloud calls.

## Features

- Account registration with password-strength and duplicate-email validation.
- Login with a signed JWT and generic invalid-credential responses.
- Authenticated account profile and file-management endpoints.
- Browser-side AES-256-GCM encryption and PBKDF2-HMAC-SHA-256 key derivation.
- File upload (up to 10 MiB plaintext), metadata listing, local decryption on
  download, and confirmed deletion.
- Random opaque S3 object keys; internal object keys are not exposed by API
  responses.
- H2-backed Spring tests with a mocked S3 client; browser cryptography
  self-test at `frontend/encryption-test.html`.

## Technology stack

| Area | Technologies |
| --- | --- |
| Frontend | HTML5, CSS, vanilla JavaScript, Web Crypto API |
| Backend | Java 21, Spring Boot 3.5, Spring Web, Spring Security, Spring Data JPA, Jakarta Validation |
| Authentication | BCrypt password encoder, JJWT signed tokens |
| Encryption | AES-256-GCM; PBKDF2-HMAC-SHA-256; 600,000 derivation iterations |
| Database | MySQL 8.x for local/deployed use; H2 for automated tests |
| Object storage | AWS S3 through AWS SDK for Java 2.x |
| Build and tests | Maven Wrapper, JUnit 5, Mockito, Spring Boot Test |
| Optional local deployment | Docker, Docker Compose |

## Architecture and zero-knowledge goal

```text
┌──────────────────────────────┐
│ Browser                      │
│ HTML/CSS/JS + Web Crypto     │
│ encrypts/decrypts file bytes │
└──────────────┬───────────────┘
               │ HTTPS in deployment
               │ JWT + ciphertext + non-secret metadata
               ▼
┌──────────────────────────────┐       ┌──────────────────────────┐
│ Spring Boot REST API         │──────▶│ MySQL                    │
│ auth, validation, ownership  │       │ users, file metadata     │
└──────────────┬───────────────┘       └──────────────────────────┘
               │ encrypted bytes only
               ▼
┌──────────────────────────────┐
│ Private AWS S3 bucket        │
│ opaque object keys           │
└──────────────────────────────┘
```

The intended **zero-knowledge architecture** means that the file-encryption
secret and derived key remain on the client, so the storage service cannot use
them to decrypt stored contents. The current implementation supports important
parts of that model: encryption and decryption occur in the browser, the
secret is not part of API requests, and the backend does not decrypt file data.

However, the project must **not be represented as a verified zero-knowledge
service**:

- The backend and database can see filenames, file sizes, MIME types, upload
  times, ownership, and encryption parameters.
- The API cannot prove that an arbitrary authenticated client uploaded valid
  ciphertext; the browser application encrypts before upload, but a modified
  client could send other bytes.
- A malicious or compromised server could deliver modified frontend code.
  Static frontend integrity and deployment controls are not included here.
- Secret recovery, authenticated-device verification, independent security
  review, and end-to-end deployment guarantees are not implemented.
- TLS is required for deployment but is not configured by this repository's
  local HTTP development setup.

See [Architecture](docs/architecture.md) for the component boundaries and
security properties in more detail.

## Folder structure

```text
secure-cloud-storage/
├── backend/
│   ├── mvnw, mvnw.cmd, pom.xml
│   └── src/
│       ├── main/java/com/mca/securecloudstorage/
│       │   ├── config/       # Spring Security, AWS client configuration
│       │   ├── controller/   # Authentication, file, health REST endpoints
│       │   ├── dto/          # Request/response records and validation
│       │   ├── entity/       # User and FileMetadata JPA entities
│       │   ├── exception/    # API exception mapping
│       │   ├── repository/   # Spring Data repositories
│       │   ├── security/     # JWT services/filter and user lookup
│       │   └── service/      # User, file, and S3 services
│       ├── main/resources/application.properties
│       └── test/             # JUnit, Mockito, Spring integration tests
├── database/
│   └── schema.sql            # MySQL reference schema
├── docs/
│   ├── architecture.md
│   ├── api-documentation.md
│   └── development-and-operations.md
├── frontend/
│   ├── index.html
│   ├── login.html
│   ├── register.html
│   ├── dashboard.html
│   ├── encryption-test.html
│   ├── css/
│   └── js/
├── Dockerfile
├── docker-compose.yml
├── .dockerignore
└── .env.example
```

## Installation and prerequisites

For the standard local setup:

- Java 21.
- MySQL 8.x, or Docker Engine with the Docker Compose plugin.
- A modern browser with Web Crypto support.
- A static frontend server such as VS Code Live Server.
- Internet access for initial Maven dependency and Docker image downloads.

Maven is included through `backend/mvnw` and `backend/mvnw.cmd`.

## MySQL setup

For a native MySQL installation, create a database and a dedicated
application account. Use a unique password stored outside source control.
Hibernate's development setting (`ddl-auto=update`) creates/updates tables
from JPA entities; for explicit reference DDL, see
[`database/schema.sql`](database/schema.sql).

Example commands to execute as a MySQL administrator (replace the password
prompt value locally; do not put a real value in documentation or source):

```sql
CREATE DATABASE IF NOT EXISTS secure_cloud_storage
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'secure_cloud_app'@'127.0.0.1' IDENTIFIED BY 'REPLACE_LOCALLY';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX
    ON secure_cloud_storage.* TO 'secure_cloud_app'@'127.0.0.1';
```

The schema privileges are for local development with Hibernate schema updates.
Use reviewed database migrations and least-privilege runtime grants for
production.

## AWS S3 setup

S3 is external; it is not started by Docker Compose. To enable upload, download,
and deletion:

1. Create a private S3 bucket in the intended AWS region.
2. Enable all S3 Block Public Access settings and do not grant public access.
3. Use an IAM role (preferred where available) or a dedicated IAM principal
   scoped to that bucket. The application needs object `PutObject`,
   `GetObject`, and `DeleteObject` permissions for its generated object prefix.
4. Set `AWS_REGION` and `S3_BUCKET`. Configure credentials through the AWS SDK
   default provider chain (for example, an IAM role, local AWS profile, or
   environment variables). Never put real credentials in source control or
   documentation.

The S3 service generates object keys of the form `objects/<64 lowercase hex
characters>`, sets the content type to `application/octet-stream`, requests
S3-managed server-side encryption (`AES256`), and does not set an ACL. This
server-side encryption is an additional storage layer; it does not replace the
browser's file encryption. A bucket name is required when an S3 operation is
invoked.

## Environment variables

`.env.example` lists names only. Docker Compose reads `.env` from the project
root; a standalone Spring Boot process does **not** automatically load `.env`.
Create local values without checking `.env` into version control.

| Variable | Required | Default / purpose |
| --- | --- | --- |
| `DB_URL` | Yes for native backend | JDBC URL; defaults in application properties to local MySQL `secure_cloud_storage` |
| `DB_USERNAME` | Yes for native backend | MySQL application username |
| `DB_PASSWORD` | Yes for native backend | MySQL application password |
| `JWT_SECRET` | Yes | Base64-encoded signing key with at least 256 bits of randomness |
| `JWT_EXPIRATION_MS` | No | JWT lifetime in milliseconds; defaults to `900000` (15 minutes) |
| `AWS_REGION` | No | S3 region; defaults to `us-east-1` |
| `S3_BUCKET` | Required for S3 actions | Private bucket name; defaults to empty |
| `AWS_ACCESS_KEY_ID` | No | Optional AWS SDK environment credential |
| `AWS_SECRET_ACCESS_KEY` | No | Optional AWS SDK environment credential |
| `AWS_SESSION_TOKEN` | No | Optional session token for temporary AWS credentials |
| `MYSQL_DATABASE` | Docker Compose | Database name passed to the MySQL container |
| `MYSQL_USER` | Docker Compose | MySQL application account |
| `MYSQL_PASSWORD` | Docker Compose | MySQL application password |
| `MYSQL_ROOT_PASSWORD` | Docker Compose | MySQL root password |
| `BACKEND_PORT` | No | Host port for backend; defaults to `8080` |
| `app.cors.allowed-origins` | No | Comma-separated allowed browser origins; see API docs |

Generate a development JWT key in PowerShell without writing the key into a
file or command history:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
}
finally {
    $rng.Dispose()
    [Array]::Clear($bytes, 0, $bytes.Length)
}
$env:JWT_EXPIRATION_MS = "900000"
```

Use a securely provisioned persistent signing key outside development. If the
key changes, previously issued JWTs stop validating.

## Run locally

### 1. Start or provision MySQL

Start a local MySQL service and create the database and application user as
described above, or use the Docker Compose instructions below for MySQL.

### 2. Configure the backend process

Set the database and JWT environment variables in the same PowerShell process
that will run Spring Boot. Use your own local values:

```powershell
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/secure_cloud_storage?serverTimezone=UTC"
$env:DB_USERNAME = "secure_cloud_app"
$env:DB_PASSWORD = Read-Host "MySQL application password"
# Set JWT_SECRET using the key-generation snippet above.
# Set AWS_REGION and S3_BUCKET, and configure AWS credentials if using S3.
```

### 3. Run the backend

```powershell
Set-Location "secure-cloud-storage\backend"
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`; check
`http://localhost:8080/api/health`.

### 4. Serve the frontend

Serve `frontend/` over HTTP using VS Code Live Server or another static
development server. Do not open the pages as `file://` URLs. The default CORS
origins include `http://localhost:5500`, `http://127.0.0.1:5500`,
`http://localhost:5501`, and `http://127.0.0.1:5501`. Adjust the backend
`app.cors.allowed-origins` setting if the frontend uses another origin.

The frontend targets `http://localhost:8080` by default. To change that,
define `window.SECURE_CLOUD_API_URL` before `frontend/js/api.js` is loaded.

## Docker setup

Docker Compose starts MySQL and the backend; the frontend is served separately.
From the project root:

1. Copy `.env.example` to `.env`.
2. Fill in the required MySQL settings and a generated `JWT_SECRET` locally.
   Add S3 settings and credentials only if enabling AWS operations.
3. Build and start:

```powershell
Copy-Item .env.example .env
# Edit .env locally; do not commit it.
docker compose up -d --build
docker compose ps
docker compose logs -f backend mysql
```

The backend is published to `127.0.0.1:${BACKEND_PORT:-8080}` and MySQL to
`127.0.0.1:3307`; MySQL's container port is `3306`. Database data persists in
the named `mysql_data` volume. Stop while preserving data with
`docker compose down`; adding `--volumes` deletes the local database volume.

The backend image is built with Maven and Java 21, runs on a Java 21 JRE image,
and uses a non-root runtime user. AWS remains external and credentials should
be injected using a suitable deployment secret/role mechanism.

## Testing

Run the backend test suite from `backend/`:

```powershell
.\mvnw.cmd test
```

The tests use JUnit 5, Mockito, Spring Boot Test, Spring Security Test, and an
in-memory H2 database under the `test` profile. The S3 client is mocked for
automated tests; the suite does not require real AWS credentials and does not
call AWS.

Coverage includes registration and duplicate registration, login and invalid
credentials, malformed/tampered/expired JWTs, protected endpoints, file
ownership checks, upload/download/delete behavior, validation and file-size
limits, and S3 service interactions.

To run the browser-side encryption self-test, serve the frontend over HTTP and
open `encryption-test.html`. It checks AES-GCM round-trip behavior, random
salt/IV generation, wrong-secret rejection, ciphertext tampering, and
authentication-tag tampering locally.

## Security considerations and limitations

- Use HTTPS in any non-local deployment. The development setup uses HTTP.
- Keep `.env`, database passwords, AWS credentials, and persistent JWT keys
  out of source control and logs.
- Keep the S3 bucket private, enable Block Public Access, and use least
  privilege for AWS permissions.
- The backend enforces owner scoping and does not expose S3 keys in API
  responses. A JWT does not demonstrate that a client used the intended
  encryption flow.
- AES-GCM is authenticated encryption; file decryption requires the original
  file secret. There is no recovery mechanism if that secret is lost.
- Filenames, MIME types, sizes, timestamps, ownership, IVs, and salts are
  metadata and are visible to the API/database. MIME type is client-provided.
- Browser encryption cannot protect against malicious frontend code,
  compromised devices, browser extensions, or weak file secrets. This project
  has not had an independent penetration test or cryptographic review.
- Database/S3 deletion is not a single atomic transaction. The service removes
  the S3 object before deleting metadata; a metadata failure after S3 deletion
  is reported and requires retry/reconciliation.
- The health endpoint reports application liveness, not the health of MySQL or
  S3.

## Future enhancements

- Independent threat modeling, code review, and cryptographic/security audit.
- Stronger deployment hardening: HTTPS termination, secure headers, managed
  secrets, rate limiting, monitoring, and audit events.
- A reviewed database migration workflow instead of Hibernate schema updates.
- S3 lifecycle policies, versioning/retention choices, and operational
  reconciliation for partial failures.
- A carefully designed, privacy-preserving encryption-secret recovery option.
- More complete frontend/API contract tests and automated responsive
  accessibility checks.
- Optional multi-factor authentication and account recovery flows.

## Technical documentation

- [System architecture, encryption, authentication, and security model](docs/architecture.md)
- [REST API reference](docs/api-documentation.md)
- [Database, setup, Docker, AWS, and operations](docs/development-and-operations.md)
=======
# Pbl-project-AJP-III-2026-T021
>>>>>>> 7449b2e0326f194207856d15032918ba3f281631
