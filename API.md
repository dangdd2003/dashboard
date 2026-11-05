# API Document

- Document for using service's endpoints.
- Applies to version 2.0 (see [CHANGELOG](CHANGELOG.md)).

## Conventions

- **Access** values used below:
  - _Public_: no token needed (`@PermitAll`).
  - _Authenticated_: any valid JWT with role `admin` or `user`.
  - _Owner or admin_: authenticated, and the caller owns the entity or has role `admin`. Otherwise `403`.
  - _Admin_: role `admin` only.
- **Authentication:** `dashboard-user` expects `Authorization: Bearer <jwt>`, using a JWT from `POST /jwt`.
  Without a valid token, endpoints that are not _Public_ return `401`. Endpoints without a
  security annotation are denied.
- **Caller identity** comes from the JWT (`sub` = username). Mutating endpoints do **not** take a
  `user_id` parameter.
- **Content type:** JSON (`application/json`) unless stated otherwise. Dates are ISO-8601 strings
  (`2026-11-05T08:30:00.000+00:00`).
- **Paging:** `pageNo` starts at `1` (default `1`), `pageSize` defaults to `20`, maximum `100`.
- **Empty lists** return `200 []`.
- **Common status codes:**

  | Code | Meaning |
  |---|---|
  | `200` | OK |
  | `201` | Created |
  | `204` | No content (password change, user delete, avatar delete) |
  | `400` | Validation error. Body: `{"<field>": "<message>"}` or a plain-text message |
  | `401` | Missing or invalid token / credentials |
  | `403` | Authenticated but not allowed (not owner, not admin) |
  | `404` | Entity not found |
  | `409` | Conflict (username exists, model already rated by the caller) |
  | `502` | ML service returned an error or an invalid response |
  | `503` | ML service unreachable |
  | `504` | ML service timed out |

## Table of Endpoints

| Service | Method | Path | Access |
|---|---|---|---|
| security | POST | `/jwt` | HTTP Basic (admin, user) |
| user | GET | `/` | Public |
| user | GET | `/users` | Authenticated |
| user | GET | `/users/count` | Authenticated |
| user | GET | `/users/paging` | Authenticated |
| user | GET | `/users/profile` | Authenticated |
| user | GET | `/users/search` | Authenticated |
| user | GET | `/users/{id}` | Authenticated |
| user | POST | `/users/create` | Public |
| user | PUT | `/users/update` | Self (admin: any `id`) |
| user | PUT | `/users/{id}/roles` | Admin |
| user | PUT | `/users/update-password` | Self (admin: any `id`) |
| user | DELETE | `/users/delete` | Self or admin |
| user | DELETE | `/users/delete/{id}` | Self or admin |
| user | GET | `/models` | Public |
| user | GET | `/models/{id}` | Public |
| user | GET | `/models/paging` | Public |
| user | GET | `/models/search/{keyword}` | Public |
| user | GET | `/models/models-by-user` | Public |
| user | GET | `/models/top10-recently-use` | Public |
| user | GET | `/models/top10-most-use` | Public |
| user | GET | `/models/top10-best-rating` | Public |
| user | GET | `/models/count` | Public |
| user | POST | `/models/create` | Authenticated |
| user | PUT | `/models/update` | Owner or admin |
| user | DELETE | `/models/delete/{id}` | Owner or admin |
| user | GET | `/ratings` | Public |
| user | GET | `/ratings/{id}` | Public |
| user | GET | `/ratings/count` | Public |
| user | GET | `/ratings/model_id` | Public |
| user | GET | `/ratings/user_id` | Public |
| user | POST | `/ratings/create` | Authenticated |
| user | PUT | `/ratings/update` | Authenticated (own rating) |
| user | DELETE | `/ratings/delete/{id}` | Owner or admin |
| user | GET | `/inferences` | Admin |
| user | GET | `/inferences/{id}` | Owner or admin |
| user | GET | `/inferences/by-user` | Owner or admin (default: caller) |
| user | GET | `/inferences/by-model` | Authenticated (own; admin: all) |
| user | GET | `/inferences/count` | Authenticated |
| user | POST | `/inferences/model-train-eval-MPAP` | Authenticated |
| user | POST | `/inferences/model-inference-MPAP` | Authenticated |
| user | DELETE | `/inferences/delete/{id}` | Owner or admin |
| user | GET | `/resources` | Admin |
| user | GET | `/resources/{id}` | Owner or admin |
| user | GET | `/resources/user_id` | Owner or admin (default: caller) |
| user | GET | `/resources/count` | Authenticated |
| user | POST | `/resources/create` | Authenticated |
| user | PUT | `/resources/update` | Owner or admin |
| user | DELETE | `/resources/delete/{id}` | Owner or admin |
| user | POST | `/files/upload-avatar` | Authenticated (own avatar; admin: any `id`) |
| user | DELETE | `/files/delete-avatar` | Authenticated (own avatar; admin: any `id`) |
| user | GET | `/files/get-avatar` | Public |
| user | GET | `/files/get-model` | Authenticated |
| user | GET | `/files/get-resource` | Owner or admin |
| both | GET | `/q/health/ready` | Public |

## Security Service

- Service for signing and encrypting JWT.
- Running default on:
  - Host: `0.0.0.0`
  - Port: `8080`
- HTTP Basic authentication against the `users` table (bcrypt passwords).

### JWT Resource

- **POST:** `/jwt`

  - Role Allowed: _admin, user_ (HTTP Basic: `Authorization: Basic base64(username:password)`)
  - Response Body: `text/plain`, the JWT
  - Token:
    - Signed with RS256 (`sign-private.pem`), then encrypted with RSA-OAEP-256 / A256GCM (`enc-public.pem`), as a nested JWT.
    - Claims: `iss` = `dashboard-user`, `aud` = `dashboard-api`, `sub` = username, `groups` = roles,
      `iat`, `exp` (1 hour), `jti` (unique id).
  - Responses: `200` token, `401` wrong credentials.

  ```shell script
  curl -X POST -u alice:'Secret#123' http://localhost:8080/jwt
  ```

## User Service

- **CRUD** service for users, models managements.
- Running default on:
  - Host: `0.0.0.0`
  - Port: `8081`
- Bearer authentication: `Authorization: Bearer <jwt>`.

### Home Resource

- **GET:** `/`
  - Permit All
  - Response Body: `text/plain` placeholder message

### User Resource

- **_User Response Body (Single)_** (`UserDto`, never contains a password)

  ```json
  {
    "id": 1,
    "username": "alice",
    "role": "user",
    "firstname": "Alice",
    "lastname": "Doe",
    "email": "alice@example.com",
    "createTime": "2026-11-05T08:30:00.000+00:00"
  }
  ```

- **_User Response Body (List)_**: JSON array of the object above, ordered by `id`.

- **GET:** `/users`

  - Role Allowed: _admin, user_
  - Response body: list of users
  - Responses: `200`

- **GET:** `/users/count`

  - Role Allowed: _admin, user_
  - Response body: number of users, e.g. `42`

- **GET:** `/users/paging`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Query: `pageNo`: integer, default `1`, min `1`
    - Query: `pageSize`: integer, default `20`, `1..100`
  - Response body: list of users ordered by `id`
  - Responses: `200` (`[]` past the last page), `400` invalid paging values

- **GET:** `/users/profile`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Query (optional): `username`: string. Defaults to the caller.
  - Response body: single user
  - Responses: `200`, `404`

- **GET:** `/users/search`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Query: `keyword`: string (substring match on username, first name, last name and email; default `""` matches everyone)
    - Query: `pageNo`: integer, default `1`, min `1`
    - Query: `pageSize`: integer, default `20`, `1..100`
  - Response body: list of users ordered by `id`
  - Responses: `200` (`[]` when nothing matches), `400` invalid paging values

- **GET:** `/users/{id}`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Path: `id`: integer
  - Response body: single user
  - Responses: `200`, `404`

- **POST:** `/users/create`

  - Permit All
  - Request Body:

  ```json
  {
    "username": "alice",
    "password": "Secret#123",
    "userDetail": {
      "firstname": "Alice",
      "lastname": "Doe",
      "email": "alice@example.com"
    }
  }
  ```

  - Validation:
    - `username`: required, no whitespace, max 255
    - `password`: 8-255 characters, no whitespace, with at least one uppercase letter, one lowercase letter, one digit and one special character from `@#$%^&*()-+=`
    - `userDetail.firstname`, `userDetail.lastname`: required, max 255
    - `userDetail.email`: required, valid email address
  - New users always get the role `user`. Any `roles` field in the body is ignored.
  - Response Body: single user (`UserDto`)
  - Responses: `201`, `400` validation error, `409` username already exists

- **PUT:** `/users/update`

  - Role Allowed: _admin, user_
  - Updates the caller's own profile. An admin may pass `id` to update another user.
  - Request Parameters:
    - Query (optional): `id`: integer. Non-admins may omit it or pass their own id only.
  - Request Body (all fields optional; `null`/missing fields are left unchanged; `roles`, `username` and `password` cannot be changed here):

  ```json
  {
    "userDetail": {
      "firstname": "New",
      "lastname": "Name",
      "email": "new_email@example.com"
    }
  }
  ```

  - Response Body: updated user
  - Responses: `200`, `400`, `403` non-admin passed another user's `id`, `404`

- **PUT:** `/users/{id}/roles`

  - Role Allowed: _admin_
  - Request Parameters:
    - Path: `id`: integer
  - Request Body:

  ```json
  {
    "role": "admin"
  }
  ```

  - `role`: `admin` or `user`
  - Response Body: updated user
  - Responses: `200`, `400`, `403` caller is not admin, `404`

- **PUT:** `/users/update-password`

  - Role Allowed: _admin, user_
  - Changes the caller's own password. An admin may pass `id` to reset another user's password,
    and then `oldPassword` is not required.
  - Request Parameters:
    - Query (optional): `id`: integer
  - Request Body (passwords are never accepted as query parameters):

  ```json
  {
    "oldPassword": "Secret#123",
    "newPassword": "N3w-Secret!"
  }
  ```

  - `newPassword` follows the same policy as on create.
  - Responses: `204`, `400` wrong old password or weak new password, `403`, `404`

- **DELETE:** `/users/delete`

  - Role Allowed: _admin, user_ (own account, or any account for admin)
  - Request Parameters:
    - Query: `username`: string (required)
  - Deletes the user with their profile, models, ratings, inferences and resources. Their files
    are deleted after the transaction commits.
  - Responses: `204`, `400` missing `username`, `403`, `404`

- **DELETE:** `/users/delete/{id}`

  - Role Allowed: _admin, user_ (own account, or any account for admin)
  - Request Parameters:
    - Path: `id`: integer
  - Responses: `204`, `403`, `404`

### Model Resource

- **_Model Response Body_** (`ModelDto`)

  ```json
  {
    "id": 3,
    "name": "Linear Regression",
    "type": "MPAP",
    "filepath": "1/models/MPAP/0b7f2c1e-....pkl",
    "description": "PCA + linear regression",
    "stars": 4.5,
    "usageCount": 12,
    "user_id": 1,
    "username": "alice",
    "firstname": "Alice",
    "lastname": "Doe",
    "createTime": "2026-11-05T08:30:00.000+00:00"
  }
  ```

  - `filepath`: storage path relative to `SYSTEM_FOLDER`, generated by the server (informational; download with `GET /files/get-model`)
  - `stars`: average rating (`null` when the model has no ratings)
  - `usageCount`: number of inferences that used the model

- **GET:** `/models`

  - Permit All
  - Response body: list of models ordered by `id`

- **GET:** `/models/{id}`

  - Permit All
  - Request Parameters:
    - Path: `id`: integer
  - Response body: single model
  - Responses: `200`, `404`

- **GET:** `/models/paging`

  - Permit All
  - Request Parameters:
    - Query: `pageNo`: integer, default `1`
    - Query: `pageSize`: integer, default `20`, `1..100`
  - Response body: list of models ordered by `id`
  - Responses: `200` (`[]` past the last page), `400` invalid paging values

- **GET:** `/models/search/{keyword}`

  - Permit All
  - Request Parameters:
    - Path: `keyword`: string (substring match on name, type and description)
  - Response body: list of models ordered by `id`

- **GET:** `/models/models-by-user`

  - Permit All
  - Request Parameters:
    - Query: `user_id`: integer (required)
  - Response body: list of models owned by that user
  - Responses: `200`, `400` missing `user_id`

- **GET:** `/models/top10-recently-use`

  - Permit All
  - Response body: the 10 most recently created models (`createTime` descending, then `id` descending)

- **GET:** `/models/top10-most-use`

  - Permit All
  - Response body: the 10 models with the most inferences (`usageCount` descending, then `id`)

- **GET:** `/models/top10-best-rating`

  - Permit All
  - Response body: the 10 models with the highest average `stars` (descending, then `id`; unrated models last)

- **GET:** `/models/count`

  - Permit All
  - Response body: number of models

- **POST:** `/models/create`

  - Role Allowed: _admin, user_ (the caller becomes the owner)
  - Content-Type: `multipart/form-data`
  - Request Parameters (all are **form fields**, not query parameters):
    - Form: `model`: file (required)
    - Form: `name`: string (required, max 255)
    - Form: `type`: string (required) matching `[A-Za-z0-9_-]{1,64}` (e.g. `MPAP`)
    - Form: `description`: string (optional)
  - Response Body: created model, with header `Location: /models/{id}`
  - Responses: `201`, `400` invalid `name`/`type` or missing file

  ```shell script
  curl -X POST -H "Authorization: Bearer $TOKEN" \
    -F model=@linear_regression.pkl -F name='Linear Regression' -F type=MPAP -F description=demo \
    http://localhost:8081/models/create
  ```

- **PUT:** `/models/update`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Query: `id`: integer (required)
  - Request Body (every field optional; `null`/missing fields are left unchanged; the file and owner cannot be changed):

  ```json
  {
    "name": "new name",
    "type": "MPAP",
    "description": "new description"
  }
  ```

  - `name`: not blank, max 255; `type`: `[A-Za-z0-9_-]{1,64}`
  - Response Body: updated model
  - Responses: `200`, `400`, `403`, `404`

- **DELETE:** `/models/delete/{id}`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Deletes the model, its ratings and its inferences. The stored file is deleted after the transaction commits.
  - Response Body: `text/plain`, e.g. `Model 3 is deleted!`
  - Responses: `200`, `403`, `404`

### Rating Resource

- **_Rating Response Body_** (`RatingDto`)

  ```json
  {
    "id": 7,
    "stars": 5,
    "comment": "Works great",
    "user_id": 2,
    "username": "bob",
    "firstname": "Bob",
    "lastname": "Smith",
    "model_id": 3,
    "createTime": "2026-11-05T08:30:00.000+00:00"
  }
  ```

- **GET:** `/ratings`

  - Permit All
  - Response body: list of ratings

- **GET:** `/ratings/{id}`

  - Permit All
  - Request Parameters:
    - Path: `id`: integer
  - Response body: single rating
  - Responses: `200`, `404`

- **GET:** `/ratings/count`

  - Permit All
  - Response body: number of ratings

- **GET:** `/ratings/model_id`

  - Permit All
  - Request Parameters:
    - Query: `model_id`: integer (required)
  - Response body: list of ratings of that model
  - Responses: `200`, `400` missing `model_id`

- **GET:** `/ratings/user_id`

  - Permit All
  - Request Parameters:
    - Query: `user_id`: integer (required)
  - Response body: list of ratings written by that user
  - Responses: `200`, `400` missing `user_id`

- **POST:** `/ratings/create`

  - Role Allowed: _admin, user_ (the rating belongs to the caller)
  - Request Parameters:
    - Query: `model_id`: integer (required)
  - Request Body:

  ```json
  {
    "stars": 5,
    "comment": "Works great"
  }
  ```

  - `stars`: integer `1..5` (required); `comment`: optional
  - Response Body: created rating, with header `Location: /ratings/{id}`
  - Responses: `201`, `400`, `404` model not found, `409` the caller already rated this model

- **PUT:** `/ratings/update`

  - Role Allowed: _admin, user_ (updates the caller's own rating of the model)
  - Request Parameters:
    - Query: `model_id`: integer (required)
  - Request Body: same as create, but every field is optional (`null`/missing fields are left unchanged)
  - Response Body: updated rating
  - Responses: `200`, `400`, `404` the caller has not rated this model

- **DELETE:** `/ratings/delete/{id}`

  - Role Allowed: _admin, user_ (author or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Response Body: `text/plain`, e.g. `Rating 7 is deleted!`
  - Responses: `200`, `403`, `404`

### Inference Resource

All `/inferences` endpoints require a token (_admin, user_). The ML endpoints call the Python
service at `PYTHON_SERVICE_URL` (`/model/{owner_id}/{type}/{model_file_name}/run|inference`).
The connect timeout is 5 s and the read timeout 300 s. These calls do not run inside a database
transaction.

- **_Inference Response Body_** (`InferenceDto`)

  ```json
  {
    "id": 11,
    "result": "{\"message\":\"...\",\"result\":{\"N\":1.2,\"P\":0.4,\"K\":2.1,\"metrics\":{\"mse\":0.01,\"r2\":0.97}}}",
    "user_id": 2,
    "firstname": "Bob",
    "lastname": "Smith",
    "model_id": 3,
    "resource_id": null,
    "createTime": "2026-11-05T08:30:00.000+00:00"
  }
  ```

  - `result`: the raw JSON returned by the ML service, as a string

- **GET:** `/inferences`

  - Role Allowed: _admin_
  - Response body: list of all inferences

- **GET:** `/inferences/{id}`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Response body: single inference
  - Responses: `200`, `403`, `404`

- **GET:** `/inferences/by-user`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Query (optional): `user_id`: integer. Defaults to the caller. Only an admin may pass another user's id.
  - Response body: list of that user's inferences
  - Responses: `200`, `403`

- **GET:** `/inferences/by-model`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Query: `model_id`: integer (required)
  - Response body: list of the caller's inferences of that model (admins get every user's)
  - Responses: `200`, `400` missing `model_id`

- **GET:** `/inferences/count`

  - Role Allowed: _admin, user_
  - Response body: number of inferences

- **POST:** `/inferences/model-train-eval-MPAP`

  - Role Allowed: _admin, user_
  - Runs training + evaluation of a model on the ML service and stores the returned parameter
    file path as a resource of type `ParamsFiles` owned by the caller.
  - Request Parameters:
    - Query: `model_id`: integer (required)
    - Query: `pca_dim`: integer `0..10000`, default `0` (sent to the ML service as `ndim`)
  - Response Body:

  ```json
  {
    "message": "Training and evaluation finished",
    "result": "path/to/params/file"
  }
  ```

  - Responses: `200`, `400` missing `model_id` or invalid `pca_dim`, `404` model not found,
    `409` model has no owner or has an invalid type or file name, `502` ML service error or invalid
    response, `503` ML service unreachable, `504` ML service timeout

- **POST:** `/inferences/model-inference-MPAP`

  - Role Allowed: _admin, user_
  - Runs inference with a model on the ML service and stores the raw result as an inference of the caller.
  - Content-Type: `multipart/form-data`
  - Request Parameters:
    - Query: `model_id`: integer (required)
    - Query: `pca_dim`: integer `0..10000` (optional; left out of the ML call when missing)
    - Form: `test_sample`: UTF-8 text file (max 1 MiB, max 10,000 values) of numbers separated by
      commas and/or whitespace, e.g. `1.9599, 1.9447 1.9289 -3e-2`
  - The service sends `{"input": [1.9599, 1.9447, ...]}` to the ML service.
  - Response Body:

  ```json
  {
    "message": "Inference finished",
    "result": {
      "N": 1.2,
      "P": 0.4,
      "K": 2.1,
      "metrics": { "mse": 0.01, "r2": 0.97 }
    }
  }
  ```

  - Responses: `200`, `400` missing/empty/non-numeric `test_sample` or bad parameters, `404` model
    not found, `409`, `502`, `503`, `504` (same meaning as above)

  ```shell script
  printf '1.9599, 1.9447, 1.9289\n' > sample.txt
  curl -X POST -H "Authorization: Bearer $TOKEN" -F test_sample=@sample.txt \
    'http://localhost:8081/inferences/model-inference-MPAP?model_id=3&pca_dim=3'
  ```

- **DELETE:** `/inferences/delete/{id}`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Response Body: text, e.g. `Inference 11 is deleted!`
  - Responses: `200`, `403`, `404`

### Resource

All `/resources` endpoints require a token (_admin, user_).

- **_Resource Response Body_** (`ResourceDto`)

  ```json
  {
    "id": 5,
    "filepath": "2/resources/dataset/5d1c9a2e-....csv",
    "type": "dataset",
    "user_id": 2,
    "firstname": "Bob",
    "lastname": "Smith",
    "createTime": "2026-11-05T08:30:00.000+00:00"
  }
  ```

  - `filepath`: storage path relative to `SYSTEM_FOLDER`, generated by the server (informational; download with `GET /files/get-resource`)

- **GET:** `/resources`

  - Role Allowed: _admin_
  - Response body: list of all resources

- **GET:** `/resources/{id}`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Response body: single resource
  - Responses: `200`, `403`, `404`

- **GET:** `/resources/user_id`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Query (optional): `user_id`: integer. Defaults to the caller. Only an admin may pass another user's id.
  - Response body: list of that user's resources
  - Responses: `200`, `403`

- **GET:** `/resources/count`

  - Role Allowed: _admin, user_
  - Response body: number of resources

- **POST:** `/resources/create`

  - Role Allowed: _admin, user_ (the caller becomes the owner)
  - Content-Type: `multipart/form-data`
  - Request Parameters:
    - Query: `type`: string (required) matching `[A-Za-z0-9_-]{1,64}`
    - Form: `resource`: file (required)
  - Response Body: created resource
  - Responses: `201`, `400` invalid `type` or missing file

  ```shell script
  curl -X POST -H "Authorization: Bearer $TOKEN" -F resource=@data.csv \
    'http://localhost:8081/resources/create?type=dataset'
  ```

- **PUT:** `/resources/update`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Query: `id`: integer (required)
  - Request Body (only `type` can be changed; the file path is managed by the server):

  ```json
  {
    "type": "dataset"
  }
  ```

  - Response Body: updated resource
  - Responses: `200`, `400`, `403`, `404`

- **DELETE:** `/resources/delete/{id}`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Path: `id`: integer
  - Deletes the resource. The stored file is deleted after the transaction commits.
  - Response Body: `text/plain`
  - Responses: `200`, `403`, `404`

### File Resource

Files are stored under `SYSTEM_FOLDER` with relative names that the server generates. Client
file names and paths are never used to build storage paths, and a stored path that points outside
the storage folder is refused with `403`. Rows created before 2.0 hold absolute paths, so their
downloads return `403` until the rows are migrated. `user_id` query parameters from 1.x are ignored.

- **POST:** `/files/upload-avatar`

  - Role Allowed: _admin, user_ (sets the caller's avatar and replaces the old one)
  - Content-Type: `multipart/form-data`
  - Request Parameters:
    - Query (optional, admin only): `id`: integer (target user). Anyone else must omit it or pass their own id.
    - Form: `image`: PNG or JPEG file, max 5 MiB. The content type and the file's magic bytes are both checked.
  - Response Body:

  ```json
  { "message": "Avatar updated" }
  ```

  - Responses: `200`, `400` not PNG/JPEG or too large, `403` another user's `id`, `404` user not found

- **DELETE:** `/files/delete-avatar`

  - Role Allowed: _admin, user_ (removes the caller's avatar; admin may pass `id`)
  - Request Parameters:
    - Query (optional, admin only): `id`: integer
  - Responses: `204`, `403`, `404`

- **GET:** `/files/get-avatar`

  - Permit All
  - Request Parameters:
    - Query: `id`: integer (user id)
  - Response Body: the image (`image/png` or `image/jpeg`)
  - Responses: `200`, `404` no avatar

- **GET:** `/files/get-model`

  - Role Allowed: _admin, user_
  - Request Parameters:
    - Query: `model_id`: integer
  - Response Body: `application/octet-stream` with `Content-Disposition: attachment; filename="<stored-name>"`
  - Responses: `200`, `403` legacy path outside the storage folder, `404` model or file not found

- **GET:** `/files/get-resource`

  - Role Allowed: _admin, user_ (owner or admin)
  - Request Parameters:
    - Query: `resource_id`: integer
  - Response Body: `application/octet-stream` with `Content-Disposition: attachment; filename="<stored-name>"`
  - Responses: `200`, `403`, `404`

## Health and tooling

- **GET:** `/q/health/ready` (both services): readiness, including the database connection. Public.
- Swagger UI: `/q/swagger-ui`, **dev mode only**. OpenAPI document: `/q/openapi`.
