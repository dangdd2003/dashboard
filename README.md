# dashboard - Backend

- Backend service - Server for Group Project 2023 - 2024

- for Website service, redirect to [dashboard-UI](https://github.com/ChloroProject-2023/dashboard-UI) repository.

The backend has two Quarkus services:

| Module | Port | Purpose |
|---|---|---|
| `dashboard-security` | `8080` | Checks username/password (HTTP Basic) and issues a signed and encrypted JWT (`POST /jwt`). |
| `dashboard-user` | `8081` | REST API for users, models, ratings, inferences, resources and files. Requires `Authorization: Bearer <jwt>`. |

`dashboard-user` calls a separate **Python ML service** (`PYTHON_SERVICE_URL`, default
`http://localhost:5000`) for model training/evaluation and inference. Only the
`/inferences/model-*-MPAP` endpoints need it; everything else works without it.

# Requirements

- **JDK 21** (`JAVA_HOME` set).
- **Maven 3.9.11**. Use the included wrapper `./mvnw`, which downloads it, so you do not need a local Maven.
- **Docker** with the Compose plugin, for `./quickstart.sh` / `docker compose`.
- **MySQL 8.x** (or MariaDB) on port 3306 with a schema named **`user_management`**. Not needed for
  Docker, because the compose file starts MySQL 8.4.
- **OpenSSL**, for `./keygen.sh` (and `./quickstart.sh`).

# Run in Docker (quickstart)

```shell script
./quickstart.sh
```

`quickstart.sh` does the following:

1. Runs `./keygen.sh`, which creates the JWT key pairs in `./jwt` if they are missing.
2. Creates `.env` from [`.env.example`](.env.example) with random database passwords. An existing
   `.env` is kept, so edit it to change `CORS_ORIGINS`, `PYTHON_SERVICE_URL` or `JWT_LIFESPAN`.
3. Builds both modules and their images, `dashboard/dashboard-user:1.0-SNAPSHOT` and
   `dashboard/dashboard-security:1.0-SNAPSHOT`
   (`./mvnw -B clean package -DskipTests -Dquarkus.container-image.build=true`).
4. Runs `docker compose up -d --wait`, which starts MySQL 8.4 (schema `user_management`, volume
   `mysql-data`) and both services, and waits until they are healthy. Uploaded files go to the volume
   `user-data`. Each service gets only the two key files it needs, mounted read-only.

When the services are ready:

- JWT service: <http://localhost:8080/jwt>
- API: <http://localhost:8081>
- Health: <http://localhost:8080/q/health/ready> and <http://localhost:8081/q/health/ready>
- Logs: `docker compose logs -f`. Stop: `docker compose down` (add `-v` to delete the database and files).

From inside the container, the ML service is reached at `http://host.docker.internal:5000` by
default. Change `PYTHON_SERVICE_URL` in `.env` if it runs somewhere else.

# Manual run (dev mode)

1. Create the schema in your MySQL server:

   ```sql
   CREATE DATABASE IF NOT EXISTS user_management;
   ```

2. Generate the RSA keys into `./jwt`. You need two key pairs, one for signing (`sign-private.pem`,
   `sign-public.pem`) and one for encryption (`enc-private.pem`, `enc-public.pem`). The script keeps
   existing keys. `./keygen.sh --force` regenerates them, and every JWT issued before then stops working.

   ```shell script
   ./keygen.sh
   ```

3. Start `dashboard-user` (port 8081) first. It creates and updates the tables in dev mode, and
   `dashboard-security` only reads the `users` table:

   ```shell script
   ./mvnw -pl dashboard-user quarkus:dev
   ```

4. In a second shell, start `dashboard-security` (port 8080). Both dev-mode processes try to open
   debug port 5005, so give the second one a different port:

   ```shell script
   ./mvnw -pl dashboard-security quarkus:dev -Ddebug=5006
   ```

   In dev mode both services connect to `jdbc:mysql://localhost:3306/user_management` as `root`
   with an empty password (see each module's `application.properties`). The `DB_*` variables below
   only apply to the packaged (prod) build.

5. Get a token and call the API:

   ```shell script
   # register a user (public)
   curl -X POST localhost:8081/users/create -H 'Content-Type: application/json' \
     -d '{"username":"alice","password":"Secret#123","userDetail":{"firstname":"Alice","lastname":"Doe","email":"alice@example.com"}}'

   # get a JWT (HTTP Basic)
   TOKEN=$(curl -s -X POST -u alice:'Secret#123' localhost:8080/jwt)

   # call the API
   curl -H "Authorization: Bearer $TOKEN" localhost:8081/users/profile
   ```

Swagger UI is only available in dev mode at <http://localhost:8080/q/swagger-ui> and
<http://localhost:8081/q/swagger-ui>.

## Configuration (environment variables)

These variables apply to the packaged (prod) build and to Docker. Dev mode (`quarkus:dev`) uses
the `%dev` values from `application.properties`.

| Variable | Used by | Default | Description |
|---|---|---|---|
| `DB_URL` | both | `jdbc:mysql://localhost:3306/user_management` | JDBC URL of the database. |
| `DB_USER` | both | `dashboard` | Database user. |
| `DB_PASSWORD` | both | _(empty)_ | Database password. |
| `DB_SCHEMA_STRATEGY` | both | `validate` | Hibernate schema management (`none`, `validate`, `update`, `create`, `drop-and-create`). Compose sets `update` for `dashboard-user`, which owns the schema. Dev mode uses `update` for `dashboard-user` and `none` for `dashboard-security`. |
| `JWT_KEY_DIR` | both | `../jwt` | Directory with the PEM keys from `keygen.sh` (`/jwt` in Docker). `dashboard-security` reads `sign-private.pem` + `enc-public.pem`; `dashboard-user` reads `sign-public.pem` + `enc-private.pem`. |
| `JWT_LIFESPAN` | dashboard-security | `3600` | Token lifetime in seconds. |
| `SYSTEM_FOLDER` | dashboard-user | `GroupProject` (relative to the working directory) | Folder for uploaded models, resources and avatars (`/data/GroupProject` in Docker). |
| `PYTHON_SERVICE_URL` | dashboard-user | `http://localhost:5000` | Base URL of the Python ML service. Connect timeout 5 s, read timeout 300 s. |
| `CORS_ORIGINS` | both | `http://localhost:3000` (dev: `*`) | Comma-separated list of allowed browser origins. |
| `MYSQL_ROOT_PASSWORD` | compose | _(required)_ | Root password of the MySQL container. |

Copy `.env.example` to `.env` to set these for `docker compose`. `quickstart.sh` does this for you.

## Upgrading from 1.x

- Run `./keygen.sh`. The key file names changed, and tokens issued by 1.x are rejected because they
  have no `aud=dashboard-api` claim.
- Model and resource rows created before 2.0 store absolute file paths. Downloads of those files
  return `403` until the rows are migrated to paths relative to `SYSTEM_FOLDER`.
- See the breaking changes in [CHANGELOG.md](CHANGELOG.md).

# Access API - Endpoint

- Please refer to [API doc](API.md) page.
- Code review findings and the fix plan: [docs/REVIEW.md](docs/REVIEW.md).
- Release notes: [CHANGELOG.md](CHANGELOG.md).

# Contribution

- Author
  - <a href="https://github.com/dangdd2003" target="_blank">Đoàn Đình Đăng</a> (Backend - Service Creator/Developer)
- Contributors
  - <a href="https://github.com/bhhoang" target="_blank">Bùi Huy Hoàng</a> (Querying Database Supporter)
  - <a href="https://github.com/Vivarium69420" target="_blank">Mai Hải Đăng</a> (Frontend Client - API Handler)
  - <a href="https://github.com/thdgg" target="_blank">Trần Hải Đăng</a> (Frontend Service - API Handler & UI/UX Designer)
