# Code Review & Fix Plan

Deep review of the `dashboard` backend (Quarkus, modules `dashboard-security` and `dashboard-user`),
performed on the code base as of commit `14ed2f1` (Quarkus 3.4.3). Findings were produced by four
parallel reviews (security, correctness, build/deploy, API docs) and the critical ones were
re-verified against the source.

Paths are relative to `dashboard-user/src/main/java/com/usth/edu/vn/` unless stated otherwise.

**Status (v2.0):** all Critical/High/Medium items in sections 1–4 have been applied on this branch,
plus most Low items. Still open: automated tests (B8 — none exist yet; Dev Services MySQL needs Docker),
Flyway migrations (S11), refresh tokens / revocation (S10), rate limiting (S9), and a data migration for
pre-2.0 rows that store absolute file paths (they are refused with 403 on download). Existing databases
also need `ALTER TABLE user_management.inferences MODIFY result TEXT;`.

---

## 0. Version upgrade **[done]**

| Item | Before | After |
|---|---|---|
| Quarkus platform | 3.4.3 (EOL) | 3.40.1.1 (latest stable 3.x; 4.0 is still beta) |
| Java release | 17 | 21 (current LTS) |
| Lombok | 1.18.30 (classpath processor) | 1.18.48 via `annotationProcessorPaths` |
| REST stack | `quarkus-resteasy-reactive[-jackson]` | `quarkus-rest[-jackson]` |
| JPAStreamer | `quarkus-jpastreamer` 3.0.3 (incompatible with Hibernate 7) | removed, replaced by Panache |
| Maven wrapper | 3.9.3 | 3.9.11 |

See the upgrade commit for the full list of property renames.

---

## 1. Security

### Critical

| # | Finding | Where | Fix |
|---|---|---|---|
| S1 | **Arbitrary file read → JWT private key theft → forged admin tokens.** `PUT /resources/update` stores a client-supplied `filepath`; `GET /files/get-resource` is `@PermitAll` and streams whatever path is stored. | `repository/ResourceRepository.java:124-137`, `services/FileServices.java:76-90`, `resource/FileResource.java:76-87` | Never accept `filepath` from clients (update DTO with only `type`). Store server-generated relative names; on read `root.resolve(rel).normalize()` and reject if `!startsWith(root)`. Require auth + ownership. |
| S2 | **Arbitrary recursive delete.** `DELETE /resources/delete/{id}` calls `deleteDir(new File(filepath))`; combined with S1, `filepath=".."` wipes everything writable. | `resource/Resource.java:100-108`, `services/FileServices.java` (`deleteDir`) | Same as S1; delete only the single stored file after the root check; ownership check. |
| S3 | **Unauthenticated path-traversal write.** `type` query param and multipart filename are concatenated into paths with `mkdirs()`; `/models/create`, `/resources/create`, `/files/upload-*` are `@PermitAll` and trust `user_id`. | `resource/ModelResource.java` (`createModel`), `resource/Resource.java` (`createResource`), `services/FileServices.java:43-74,115-123` | `@RolesAllowed`, owner from JWT, whitelist `type` (enum / `@Pattern`), store as `UUID + allowed extension`, normalize+`startsWith(root)`, `Files.copy(..., CREATE_NEW)`. Remove `MultipartFormDataInput` endpoints. |

### High

| # | Finding | Where | Fix |
|---|---|---|---|
| S4 | **IDOR + privilege escalation.** `PUT /users/update?id=` copies `roles` from body → any user can become `admin`; any user can update/delete any other user. | `resource/UserResource.java`, `repository/UserRepository.java` (`updateUser`, `deleteUser`) | `UserUpdateRequest` without `roles`; resolve "self" from `JsonWebToken`; other ids only for `admin`; separate admin-only role endpoint. |
| S5 | **Password hashes returned** by every `/users*` read and by `createUser`/`updateUser` (entity echoed). | `model/dto/UserDto.java`, `repository/UserRepository.java` projections | Remove `password` from `UserDto` and queries; return DTOs, never entities. |
| S6 | **Missing authn/ownership on writes**: model delete, avatar upload/delete, model/resource download, ratings & inferences for arbitrary `user_id`, model/resource update. | `ModelResource`, `FileResource`, `RatingResource`, `InferenceResource`, `Resource` | Drop `user_id` params, derive caller from `SecurityIdentity`; `@Authenticated`/`@RolesAllowed` on all mutations; owner-or-admin check; `quarkus.security.jaxrs.deny-unannotated-endpoints=true`. |
| S7 | **One RSA pair signs *and* encrypts; signing private key is deployed to `dashboard-user`.** | `keygen.sh`, both `application.properties`, `docker-compose.yml` | Two key pairs: security gets `sign-private` + `enc-public`; user gets `sign-public` + `enc-private`; mount read-only. |

### Medium / Low

| # | Finding | Fix |
|---|---|---|
| S8 | `InferenceService`: JSON body built with `String.format` from raw upload (JSON injection); `type`/filename unencoded in URL path; no timeouts; inside `@Transactional`. | Quarkus REST Client (`@RegisterRestClient`), typed request record, timeouts, call outside the transaction. |
| S9 | Password policy never enforced (setter hashes before `@ValidPassword` runs — see C1); passwords in query string on `update-password`. | Validate raw password on a request DTO with `@Valid`; move to POST body; rate-limit `/jwt` and password endpoints. |
| S10 | JWT: no audience, 24 h lifetime, no revocation. | `.audience(...)`, `mp.jwt.verify.audiences`, 15 min + refresh token. |
| S11 | DB as `root` with empty password; `generation=update` and SQL logging in prod. | `%prod` env-based credentials, least-privilege user, `%dev`-only update/log, Flyway. |
| S12 | Unbounded `pageSize`, unpaged list endpoints, `findByUsername` streams all users, uploads buffered in memory. | `@Min/@Max` on paging, `Page.of`, `find("username", u).firstResultOptional()`, stream uploads. |
| S13 | CORS `*` with `Authorization`; absolute server paths leaked in DTOs; Swagger always on; debug `TestResource` shipped. | Explicit origins in `%prod`; return ids not paths; Swagger dev-only; remove `TestResource`. |

Verified OK: no HQL/SQL injection (all JPQL parameterised), `addUser` forces `roles=user`, bcrypt compatible with `quarkus-security-jpa`, no secrets in git history.

---

## 2. Correctness

### High

| # | Finding | Where | Fix |
|---|---|---|---|
| C1 | `@ValidPassword` validates the **bcrypt hash** (setter hashes during JSON deserialization) → any password accepted. | `model/Users.java` (`setPassword`), `validation/PasswordValidator.java` | Validate on a request DTO; hash in the service. |
| C2 | **Rating first/last name swapped** in every response: `RatingDto` declares `lastname, firstname`, queries pass `firstname, lastname`. | `model/dto/RatingDto.java:20-24`, `repository/RatingRepository.java:40-41,64-65,90-91,116-117` | Reorder DTO fields. |
| C3 | **Resource `lastname` contains firstname** (`ud.firstname` selected twice). | `repository/ResourceRepository.java:44-45,66-67,88-89` | Second column → `ud.lastname`. |
| C4 | `findModelById` uses `AVG`/`COUNT` without `GROUP BY` → MySQL 8 `ONLY_FULL_GROUP_BY` error 1140 → `GET /models/{id}` and `/files/get-model` return 500. | `repository/ModelRepository.java:54-83` | `GROUP BY m.id, ...`; map no-result to 404. |
| C5 | "Top 10 most used" is arbitrary: `ORDER BY` only in derived table, outer query has `LIMIT 10` without `ORDER BY`; `o[4].toString()` NPEs on null description. | `repository/ModelRepository.java:183-237` | Outer `ORDER BY inference_count DESC`; null-safe mapping (or JPQL constructor query). |
| C6 | Deleting a model that has inferences fails on FK at flush (cascade only `PERSIST`) **after** the file was already deleted from disk; same for users. | `model/Models.java`, `resource/ModelResource.java` (`deleteModel`), `resource/UserResource.java` | Decide cascade semantics; delete files only after commit (`@Observes(during = AFTER_SUCCESS)`). |
| C7 | Python calls: no connect/read timeout, connection never disconnected, held inside a DB transaction; `GET model-train-eval-MPAP` writes data. | `services/InferenceService.java`, `resource/InferenceResource.java` | Timeouts / REST client; persist in a separate short transaction; make it `POST`. |

### Medium

| # | Finding | Fix |
|---|---|---|
| C8 | `EmailValidator`/`PasswordValidator` keep `Matcher` in instance fields (not thread-safe) and NPE on null. | `static final Pattern`, return `true` for null. |
| C9 | All `CustomException`s map to 400 (not-found should be 404, conflicts 409); `NoResultException` → 500 for unknown ids on `/inferences/{id}`, `/ratings/{id}`, `/resources/{id}`, deletes. | Typed exceptions (`NotFoundException`, `ConflictException`); mapper for `NoResultException`; check `deleteById` result. |
| C10 | Paging: missing `pageNo` → negative offset → 500; no `ORDER BY` → unstable pages. | `@DefaultValue`, `@Min(1)`, `ORDER BY id`. |
| C11 | `FileServices`: `deleteDir(long,String)`/`getAvatar` NPE when folder missing; `writeFile` doesn't create parents, overwrites silently, leaks stream; `getFile` compares strings with `==`; file written before DB persist (orphan files). | Null-safe listing, `Files.createDirectories`, try-with-resources, `.equals`, cleanup on rollback. |
| C12 | Uploads > 10 MB rejected (`max-body-size` default 10M; only `max-form-attribute-size` raised). | `quarkus.http.limits.max-body-size=100M`. |
| C13 | No unique constraint on `username` or `(user_id, model_id)` ratings → races create duplicates and break login; `stars` unbounded. | `@Column(unique = true)`, `@Table(uniqueConstraints=...)`, `@Min(1) @Max(5)`. |
| C14 | `findByUsername` full scan + N+1; model aggregates join Ratings × Inferences (R×I rows). | `firstResultOptional()`; correlated subqueries for AVG/COUNT. |
| C15 | `new ObjectMapper()` with `FAIL_ON_UNKNOWN_PROPERTIES=true` → any new field from the ML service = 500. | Inject Quarkus `ObjectMapper`. |

### Low / cleanups

- `@Data` on bidirectional entities → `toString()` recursion (`StackOverflowError`). Use `@Getter/@Setter` + id-based equals.
- Update endpoints return the request body with `202`, not the saved entity.
- Password regex `[@#$%^&*()-+=]` contains an accidental range `)-+`.
- Inconsistent routes (`/ratings/model_id` vs `/inferences/by-model`), 404-on-empty for users vs `200 []` for models.
- Dead code: `UserDetailsRepository`, `ModelRepository.getModelById`, `UserRepository.getUserById`, stub endpoints `/inferences/create` and `/inferences/model-train-eval`, unused constants.

---

## 3. Build, configuration & deployment

| # | Finding | Fix |
|---|---|---|
| B1 | `quickstart.sh` `cd`s into non-existent `authentication-jwt/` and `user-management/`, has no `set -e`, ends up running `docker compose` outside the repo, never builds images. | Rewrite (see below). |
| B2 | No `src/main/docker/Dockerfile.jvm` in either module → `quarkus-container-image-docker` cannot build. | Add Dockerfiles or switch to `quarkus-container-image-jib`. |
| B3 | Compose image names (`dashboard/user-management`, `dashboard/jwt`) don't match generated `${user}/dashboard-*:1.0-SNAPSHOT`. | `quarkus.container-image.group=dashboard`; update compose. |
| B4 | Compose maps security service `8082:8082`, app listens on 8080. | `8080:8080`. |
| B5 | No MySQL service; datasource points at `localhost` (the container itself). | Add `mysql:8.4` with healthcheck; `QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://mysql:3306/user_management`. |
| B6 | Compose only sets one JWT key per service; decrypt/encrypt keys fall back to `../jwt/...` (wrong in a container). | Set all four key locations (`file:/jwt/...`), or the `jwt.key-dir` pattern. |
| B7 | README says schema `user-management`; code uses `user_management`. | Fix README. |
| B8 | No health checks, no tests (deps declared, zero `src/test`). | `quarkus-smallrye-health`; at least one `@QuarkusTest` per module (Dev Services MySQL). |
| B9 | Parent pom forces all deps on every module; duplicate `container-image-docker`; unused `quarkus-agroal`, `quarkus-arc`, `quarkus-elytron-security-jdbc`. | Move deps to children; remove unused. |
| B10 | `mvnw` not executable in git; `keygen.sh` has no shebang and `rm -rf jwt` on every run (invalidates all tokens). | `git update-index --chmod=+x mvnw`; generate keys only if missing. |
| B11 | `python-service.host` default `0.0.0.0` and relative `system.folder` unusable in containers. | Env overrides + volume. |

A ready-to-use `docker-compose.yml` (MySQL + both services with healthchecks) and a rewritten
`quickstart.sh` were drafted during the review and can be applied once B2 (Dockerfiles) and B8
(health extension) are in place.

---

## 4. Documentation

- `API.md` documents **13 of 49** endpoints; Model, Rating, Inference, Resource, File sections are empty.
- Wrong path `/uses/updatePassword` → `/users/update-password`; malformed JSON in `PUT /users/update`; wrong `createTime` format.
- `README.md`: empty `[API doc]()` link, wrong schema name, broken "Recommended" Docker path, no mention of the Python ML service, `system.folder`, `mvnw`.
- `CHANGELOG.md`: claims JPAStreamer was removed (it wasn't until this upgrade); no entries after 19 Jan 2024; "Hibernation" typo.

A complete, code-accurate replacement `API.md` has been drafted.

---

## 5. Suggested order of work

1. **S1–S3, S7** (full compromise chain) → then **S4–S6**.
2. **C2–C5** (wrong data / 500s on core endpoints, all one-line-ish fixes).
3. **C1/S9, S5** (password handling, DTO hygiene).
4. **B1–B8** (make `quickstart.sh` + compose actually run, add health + smoke tests).
5. Docs (`API.md`, README, CHANGELOG).
6. Remaining Medium/Low items.

## References

- Quarkus security guides: <https://quarkus.io/guides/security-overview>, JWT: <https://quarkus.io/guides/security-jwt>, JWT build: <https://quarkus.io/guides/security-jwt-build>
- Quarkus update tool & migration guides: <https://quarkus.io/guides/update-quarkus>, <https://github.com/quarkusio/quarkus/wiki/Migration-Guides>
- Quarkus REST client: <https://quarkus.io/guides/rest-client>
- Hibernate ORM with Panache: <https://quarkus.io/guides/hibernate-orm-panache>
- OWASP Path Traversal: <https://owasp.org/www-community/attacks/Path_Traversal>; OWASP API Security Top 10 (API1 BOLA / IDOR): <https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/>
- Nested JWT (RFC 7519 §5.2): <https://datatracker.ietf.org/doc/html/rfc7519>; JWE (RFC 7516): <https://datatracker.ietf.org/doc/html/rfc7516>
- Key separation guidance: NIST SP 800-57 Part 1 Rev. 5, §5.2 ("a key should be used for only one purpose"): <https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final>
