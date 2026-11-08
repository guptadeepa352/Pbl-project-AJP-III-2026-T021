# Development and operations

This guide covers native MySQL setup, local Spring Boot and frontend startup,
Docker Compose, AWS prerequisites, configuration, and automated testing.
Never put real passwords, signing keys, or AWS credentials into source files,
documentation, command history, or commits.

## Prerequisites

- Java 21.
- MySQL 8.x for a native database, or Docker Engine with Docker Compose.
- A modern browser with Web Crypto support.
- A static frontend server (for example, VS Code Live Server).
- Network access for initial dependency and image downloads.

The backend includes Maven Wrapper scripts (`mvnw` and `mvnw.cmd`).

## Native MySQL setup

Create a development database and a dedicated application account using a
MySQL administrator. Replace the example password locally with a unique,
strong value; never copy a real value into tracked files.

```sql
CREATE DATABASE IF NOT EXISTS secure_cloud_storage
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'secure_cloud_app'@'127.0.0.1' IDENTIFIED BY 'REPLACE_LOCALLY';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX
    ON secure_cloud_storage.* TO 'secure_cloud_app'@'127.0.0.1';
```

During development, Hibernate uses `ddl-auto=update` to create/update tables.
The database must exist before application startup. The SQL reference is
[`../database/schema.sql`](../database/schema.sql). Do not use Hibernate
schema updates as a production migration strategy; adopt reviewed database
migrations before production.

## AWS S3 setup

S3 is an external dependency and is not provisioned by the local Compose file.
To use file operations:

1. Create a private bucket in the desired AWS region.
2. Enable all four S3 Block Public Access settings. Do not grant public access.
3. Provide an IAM role or dedicated principal with only the object permissions
   needed by this application: `s3:PutObject`, `s3:GetObject`, and
   `s3:DeleteObject` for the selected bucket/object prefix. Add any bucket-level
   permissions only if required by your environment.
4. Set `AWS_REGION` and `S3_BUCKET`.
5. Provide credentials through the AWS SDK default provider chain. Prefer an
   IAM role in deployed environments; use a local profile or process
   environment for development. Never commit credentials.

The application creates object keys in the `objects/` prefix, uploads bytes as
`application/octet-stream`, requests S3-managed encryption (`AES256`), and
does not set object ACLs. The browser encryption layer remains separate and
should not be replaced by S3 server-side encryption.

If no bucket is configured, the application can start, but an operation that
uses S3 fails explicitly with a storage configuration error.

## Environment configuration

The root `.env.example` contains variable names only. Docker Compose reads
`.env` from the project root. A standalone Spring process does not read `.env`
automatically; export variables in the process environment before launch.
Keep `.env` untracked.

| Variable | Use |
| --- | --- |
| `DB_URL` | Backend JDBC URL. Default is local MySQL `secure_cloud_storage`. |
| `DB_USERNAME`, `DB_PASSWORD` | Backend MySQL application account. |
| `JWT_SECRET` | Base64-encoded signing key with at least 32 random bytes. |
| `JWT_EXPIRATION_MS` | JWT lifetime; default `900000` ms. |
| `AWS_REGION` | AWS region; default `us-east-1`. |
| `S3_BUCKET` | Private bucket name; required when using S3 operations. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | Optional AWS SDK environment credentials; use a role/profile where possible. |
| `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | MySQL container settings for Docker Compose. |
| `BACKEND_PORT` | Host-side backend port; default `8080`. |

Generate a temporary development JWT secret in PowerShell:

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

This changes only the current PowerShell process. A fresh random key on each
launch invalidates tokens from earlier launches. Provision a persistent secret
securely when token continuity is required.

## Run backend and frontend locally

1. Start MySQL and create the database/application account.
2. In the PowerShell window that will run Spring Boot, set:

   ```powershell
   $env:DB_URL = "jdbc:mysql://127.0.0.1:3306/secure_cloud_storage?serverTimezone=UTC"
   $env:DB_USERNAME = "secure_cloud_app"
   $env:DB_PASSWORD = Read-Host "MySQL application password"
   # Set JWT_SECRET using the generation snippet above.
   # Configure AWS_REGION, S3_BUCKET, and AWS credentials if using S3.
   ```

3. Start the backend:

   ```powershell
   Set-Location "secure-cloud-storage\backend"
   .\mvnw.cmd spring-boot:run
   ```

4. Confirm application liveness at `http://localhost:8080/api/health`.
5. Serve `frontend/` using a local static web server, such as VS Code Live
   Server. Do not open the HTML pages with a `file://` URL.
6. Open the server's `index.html`, register, sign in, and use the dashboard.
   The default API base is `http://localhost:8080`.

The default browser origins are ports 5500 and 5501 on `localhost` and
`127.0.0.1`. If using another frontend origin, explicitly add it with the
backend property `app.cors.allowed-origins`. The frontend API base can be
changed by defining `window.SECURE_CLOUD_API_URL` before `js/api.js` loads.

## Docker Compose

The Compose file starts the MySQL and backend services; it does not serve the
frontend or create an AWS bucket.

1. From the project root, copy `.env.example` to `.env`.
2. Fill the required Compose variables with locally generated values:
   `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`,
   `MYSQL_ROOT_PASSWORD`, and a Base64 `JWT_SECRET`. Configure the AWS
   variables only if AWS file operations are needed.
3. Build/start and inspect:

   ```powershell
   Copy-Item .env.example .env
   # Edit .env locally; do not commit it.
   docker compose up -d --build
   docker compose ps
   docker compose logs -f backend mysql
   ```

The backend is published on `127.0.0.1:${BACKEND_PORT:-8080}`. MySQL is
published on `127.0.0.1:3307` and listens on `3306` inside the Compose network.
The backend connects to host `mysql` on the internal network. The named
`mysql_data` volume preserves database contents.

Useful lifecycle commands:

```powershell
docker compose stop
docker compose restart
docker compose down
docker compose up -d --build
```

`docker compose down` preserves the named volume. `docker compose down
--volumes` deletes that development database data; use it only when that is
intended. The Dockerfile builds the backend using Maven/Java 21 and runs the
packaged service as a non-root user on a Java 21 JRE.

## Testing

From the backend directory:

```powershell
.\mvnw.cmd test
```

The test classes activate the `test` profile. That profile configures H2 with
an in-memory database and schema lifecycle for tests. JUnit 5, Mockito, Spring
Boot Test, and Spring Security Test are used for unit and HTTP integration
tests.

The test S3 client is a Mockito mock. The normal test suite does not call AWS
and does not need AWS credentials. Tests cover authentication, invalid and
expired JWTs, file authorization and ownership, file upload/download/delete,
validation/size limits, and S3 service interactions.

The browser encryption self-test is `frontend/encryption-test.html`. Serve
`frontend/` over HTTP, open that page, and confirm that it reports all
browser-only encryption tests passed. It checks round-trip encryption,
random salt and IV generation, ciphertext and GCM tag tampering, and wrong
secrets without calling the backend or AWS.

## Operational notes

- `GET /api/health` is a liveness response only; it does not establish that
  MySQL or S3 is available.
- S3 object deletion and database metadata deletion cannot be atomic. Monitor
  storage and database errors and reconcile partially completed deletes.
- For deployment, terminate HTTPS in a trusted ingress/reverse proxy, restrict
  CORS to known frontend origins, manage secrets with a secret store, and
  review IAM and database permissions.
- See [API documentation](api-documentation.md) and
  [architecture/security model](architecture.md) for request contracts and
  trust boundaries.
