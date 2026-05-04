# Freelance Marketplace M2 — Test Scenarios

> **425 test cases** covering M1 feature retrofits (S1–S5, F1–F9) and M2 features
> (S1–S5, F10–F12), plus 7 design patterns (DP-1 through DP-7).

---

## Table of Contents

- [S1 — User Service](#s1--user-service)
- [S2 — Job Service](#s2--job-service)
- [S3 — Proposal Service](#s3--proposal-service)
- [S4 — Contract Service](#s4--contract-service)
- [S5 — Wallet Service](#s5--wallet-service)
- [Design Patterns](#design-patterns)

---

# S1 — User Service

## M2 Features — TC01–TC34

## TC001 — Register a new user (happy path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath()`

### What it tests

The S1-F1 register endpoint must persist a brand-new user account to PostgreSQL and respond with HTTP 2xx plus a body containing the newly-assigned numeric id. This maps to the spec requirement that POST /api/auth/register with valid fields (name, email, password, phone) writes a row to the users table and echoes that row's id to the caller. A strict 2xx assertion is intentional: a fresh nonce-based email with a valid payload must succeed unconditionally — any non-2xx is a bug in the controller, the JSR-303 validators, the BCrypt encoder, or the JPA save call. The numeric-id check catches handlers that return only {"message":"ok"} without echoing the persisted row's identifier, leaving clients with no handle to reference the new user.

### Steps

```
1) Build a JSON register body with a nonce()-suffixed email (e.g. tc01_<nonce>@grader.testgen.io), name "TC01 User", a fresh 9-digit phone, and password TestPwd!2026 — the nonce prevents collision with auto-seeded _preseed_* users or earlier runs.
2) POST registerPath() (typically /api/auth/register) with Content-Type: application/json and the body above.
3) Assert 2xx via assert2xx.
4) Parse the response body as JSON via parseNode.
5) Assert the id field is non-null and that its JSON node type is numeric (isNumber()).
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Validation rejects a perfectly-valid payload (@NotBlank/@Email mis-configured on the DTO), or the persistence path throws (BCrypt encoder not wired, schema column missing, transaction not committing) and the exception leaks as 5xx instead of being handled.
- **Body has non-null id field**
  - *Bug it catches:* Controller returns {"message":"ok"} or an empty body instead of echoing the saved row — downstream tests (login, profile read, IDOR checks) all need this id and will fail in confusing ways without it.
- **id is numeric**
  - *Bug it catches:* id is serialised as a string ("id":"42") — breaks Long deserialization on Jackson clients and signals a sloppy DTO with a String-typed id field.

---

## TC002 — Login with valid credentials (happy path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath()`

### What it tests

The S1-F2 login endpoint must verify the supplied credentials against the bcrypt hash that register persisted, and on a match mint a signed JWT and return it under the token field. The 3-segment dot-split check is the lightest possible structural test that the returned value is actually a JWT (header.payload.signature) rather than a placeholder string, an opaque session id, or a hand-rolled username:role token. Strict 2xx is required because the credentials exactly match the just-registered user — any non-2xx is a real bug, not an edge case. This catches the common bcrypt-roundtrip mismatch (register stores in one format, login compares in another), missing token-issuance code, and naive implementations that return an unsigned two-segment value or a UUID.

### Steps

```
1) Build a register body with a unique nonce-based email and known plaintext password TestPwd!2026.
2) POST registerPath() and assert 2xx as a precondition — login cannot succeed if register failed.
3) Build a login body {"email":"<same>","password":"TestPwd!2026"}.
4) POST loginPath() (typically /api/auth/login) with that body.
5) Assert 2xx, parse the response as JSON, assert token is present and non-blank, then split by . and verify exactly 3 segments.
```

### Pass Criteria

- **Login status 200..299**
  - *Bug it catches:* BCrypt hash format mismatch between register (encode) and login (matches) so legitimate credentials are rejected as 401, or the login endpoint is missing entirely (404).
- **Body has non-blank token field**
  - *Bug it catches:* Controller returns 2xx but forgets to include the token ({"message":"login ok"}) — all subsequent authenticated calls will fail with 401.
- **Token splits into exactly 3 dot-separated segments (a.b.c JWT shape)**
  - *Bug it catches:* Token is a plaintext UUID, an opaque session cookie, or a hand-rolled username:role string — meaning no signature, no claims, no expiry, and no actual cryptographic security.

---

## TC003 — Read own user profile with valid JWT (happy path)

**Tags:** `public` `updated_crud`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET crudReadPath("User")`

### What it tests

The JWT minted by login is accepted by the security filter chain, the User CRUD GET-by-id endpoint passes the owner authorization check, and the user's own profile is returned as a JSON object. This is the end-to-end "I just registered, can I see myself" path — the simplest possible owner-read scenario. A strict 2xx is required (no 2xx OR 404 escape) because the test just registered this exact user, so a 404 here means either the controller's GET-by-id path is broken or the JWT chain rejected our own valid token. The JSON-object body check rules out controllers that return 200 OK with an array, a string, or an empty body. Together this catches JWT filter bugs, broken claim extraction (uid missing from the token payload), and CRUD-authorization-chain bugs where the owner check incorrectly compares email vs id.

### Steps

```
1) Register a fresh nonce-based user via POST registerPath() and assert 2xx.
2) Extract the user's numeric id from the registration response JWT via uidFromJwt.
3) POST loginPath() with the same email + password and capture the token from the response.
4) GET crudReadPathFor("User", id) (typically /api/users/<id>) with header Authorization: Bearer <token>.
5) Assert strict 2xx (no 404 escape) and verify the response body parses as a JSON object via j.isObject().
```

### Pass Criteria

- **GET status 200..299 (strict, not the 2xx OR 404 escape)**
  - *Bug it catches:* Owner-read endpoint is missing entirely (404), or the security filter chain rejects a valid token (401) due to a mis-wired JwtAuthenticationFilter or a missing OncePerRequestFilter registration.
- **Response body parses as a JSON object**
  - *Bug it catches:* Controller returns an empty body, a wrapped list envelope, or an array instead of the user resource — clients cannot extract profile fields like name, email, or status.

---

## TC004 — Register with duplicate email returns 4xx (negative path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (called twice)`

### What it tests

The register endpoint must enforce email uniqueness at the persistence layer and translate the conflict into a clean 4xx client error. Per S1-F1 the User.email column carries a unique constraint and the controller is expected to catch the resulting DataIntegrityViolationException (or pre-check via the repository) and return 400/409/422. The 4xx assertion is bracketed strictly: NOT 2xx (which would mean two accounts with the same email exist), NOT 5xx (uncaught DB exception leaking), and NOT 401/403 (misclassifying the error — registration is an unauthenticated public endpoint). This catches a missing @Column(unique=true) on User.email, swallowed exceptions, silent 2xx accepts that create duplicate rows, and ham-fisted error handling that returns 500 on any DB error.

### Steps

```
1) Build a register body with a nonce()-suffixed email + valid password + phone.
2) POST registerPath() and assert 2xx — this establishes that the email is now in use (precondition).
3) Build a second register body with the same email but a different name and phone (uniqueness is on email alone).
4) POST registerPath() again with the duplicate-email body.
5) Read the status and assert it is in 400..499, not 401, not 403, and not 5xx.
```

### Pass Criteria

- **First register status 200..299**
  - *Bug it catches:* Setup precondition broken — the rest of the test is meaningless if the first register failed.
- **Second register status 400..499 (strict client error)**
  - *Bug it catches:* Controller returns 2xx and creates a second row (missing unique constraint or constraint not enforced because Hibernate DDL validation is off), or returns 5xx because DataIntegrityViolationException was not caught.
- **NOT 5xx (no server crash on the duplicate)**
  - *Bug it catches:* DataIntegrityViolationException propagates uncaught — clients see a stack trace dump with no actionable error message.
- **NOT 2xx (uniqueness must be enforced)**
  - *Bug it catches:* Critical data-integrity bug: two users with the same email exist, breaking login (which user does the email belong to?) and all cross-service joins on email.

---

## TC005 — Login with wrong password returns 401 (negative path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath()`

### What it tests

Login authenticates the supplied password against the stored bcrypt hash and rejects mismatches with strictly 401 Unauthorized. Per S1-F2 the spec requires a uniform 401 for any authentication failure, and this test exercises the wrong-password leg. The strictness is deliberate: 404 here would leak whether the account exists (OWASP user-enumeration anti-pattern) and 403 is structurally wrong because login is the act of obtaining permissions, not exercising them. This catches login implementations that skip the password check and silently return a token (catastrophic security bug), NPE on missing passwordEncoder.matches, bcrypt format mismatches that throw 5xx, and 404-vs-401 misclassification.

### Steps

```
1) Register a fresh nonce-based user with password=TestPwd!2026 and assert 2xx — this proves the account exists with the correct hash.
2) Build a login body with the same email but a clearly different password (WrongPwd!2026 — different prefix and length so bcrypt cannot accidentally match).
3) POST loginPath() with the wrong-password body.
4) Read the status code and assert strictly 401, with explicit guards against 2xx, 5xx, 404, and 403.
```

### Pass Criteria

- **Register status 200..299 (precondition)**
  - *Bug it catches:* Setup failure — the rest of the test is meaningless if the account wasn't created.
- **Login status strictly 401**
  - *Bug it catches:* Any code other than 401 is a misclassification of the authentication-failure condition.
- **NOT 2xx**
  - *Bug it catches:* Login skipped the password check and minted a token anyway — critical security bug (anyone with the correct email can impersonate any user without knowing the password).
- **NOT 5xx**
  - *Bug it catches:* BCrypt mismatch threw an unhandled exception (e.g., IllegalArgumentException from a malformed hash) that leaked instead of being caught and translated to 401.
- **NOT 404**
  - *Bug it catches:* OWASP user-enumeration leak — returning 404 vs 401 distinguishes "user exists but wrong password" from "no such user", letting attackers enumerate valid account emails.
- **NOT 403**
  - *Bug it catches:* Controller treats login as a permissioned action and maps any auth failure to 403 generically, instead of correctly returning 401 for unauthenticated conditions.

---

## TC006 — Authentication happy path (valid admin JWT accepted on a non-User CRUD list)

**Tags:** `public` `authentication`  
**Endpoint(s):** `adminToken() (setup), GET crudCollectionPath(firstTopLevelNonUserEntity()) with Bearer token`

### What it tests

The JWT auth pipeline must accept a valid admin token on a controller other than UserController, proving the security filter chain is registered globally (OncePerRequestFilter applied to every dispatcher type) rather than scoped to /api/users/** only. An ADMIN token is used so that role-protected list endpoints cannot mask an auth-filter bug — the test isolates filter wiring from role enforcement. The target entity is resolved dynamically from the manifest so the same code maps across all themes (FreelanceMarketplace → Job, TripPlanning → Destination, Talabat → Address, etc.). This is the A/B partner of TC07: same endpoint, only the Authorization header differs — together they prove the protection is real and not merely decorative.

### Steps

```
1) Call adminToken() to obtain a real admin Bearer JWT.
2) Resolve the first top-level non-User entity from the manifest via firstTopLevelNonUserEntity() (for FreelanceMarketplace: Job).
3) Compute its CRUD list path via crudCollectionPath(entity) (typically /api/jobs).
4) GET that path with header Authorization: Bearer <token>.
5) Assert strict 2xx via assert2xx.
```

### Pass Criteria

- **GET status 200..299 (strict — proves auth filter accepted the admin token on a non-User CRUD)**
  - *Bug it catches:* Auth filter registered only for /api/users/** (e.g., via requestMatchers("/api/users/**")), leaving sibling controllers either wide-open (paired with TC07's 2xx failure) or rejecting valid admin tokens. Also catches admins not receiving the role claim in their JWT, causing role-aware controllers to 403 a real admin.

---

## TC007 — Missing Authorization header on a non-User CRUD list returns 401 (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `GET crudCollectionPath(firstTopLevelNonUserEntity()) with NO Authorization header`

### What it tests

The JWT auth filter must block anonymous requests across all controllers, not just UserController, returning strictly 401. This is the A/B partner of TC06: same endpoint, only the Authorization header is removed — together they prove the protection is global and real, not permitAll. Each non-401 status code masks a distinct bug class: 2xx means the endpoint is wide-open, 404 means the filter passed the request through to a controller that returned empty data (auth never blocked), 403 means the controller is mis-classifying anonymous as "authenticated but unauthorized", and 5xx means the filter chain crashed rather than cleanly rejecting. This catches missing OncePerRequestFilter registration, accidental .permitAll() on the target controller, and controllers exposed outside the security pipeline entirely.

### Steps

```
1) Resolve the first top-level non-User entity from the manifest via firstTopLevelNonUserEntity() (same entity as TC06).
2) Compute the CRUD list path via crudCollectionPath(entity).
3) GET that path with httpGet (the no-auth variant — no Authorization header is sent at all).
4) Read the status code and assert strictly 401.
```

### Pass Criteria

- **GET status strictly 401**
  - *Bug it catches:* Anything other than 401 indicates the auth pipeline is misconfigured for this controller's route.
- **NOT 2xx**
  - *Bug it catches:* Endpoint is wide-open — critical security bug exposing data to anonymous callers.
- **NOT 5xx**
  - *Bug it catches:* Filter chain crashed (e.g., NPE on missing header) instead of cleanly rejecting — the absent-header case is the most common path and must be handled defensively.
- **NOT 404**
  - *Bug it catches:* The endpoint was reachable without auth and returned empty results — auth must block FIRST, before any controller logic runs.
- **NOT 403**
  - *Bug it catches:* No credentials were sent; 403 means "authenticated but lacking permission", which is structurally inappropriate here — the student is conflating unauthenticated with unauthorized.

---

## TC008 — Tampered JWT signature is rejected with 401 (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `adminToken() (setup), GET crudCollectionPath(firstTopLevelNonUserEntity()) with tampered Bearer token`

### What it tests

The auth filter must perform real cryptographic verification of the JWT signature, not merely decode the structure. This is the third and most critical leg of the auth-filter triad — TC06 (valid → 2xx), TC07 (no token → 401), TC08 (forged token → 401) — and together they prove the filter does real signature checking. The catastrophic security bug it targets is when a student extracts claims (uid, role) directly from the base64-decoded payload without validating the signature: an attacker can then forge any token by editing the payload (e.g., flipping "role":"CUSTOMER" to "role":"ADMIN") and re-base64'ing. Strict 401 because a tampered signature is exactly the case auth must reject — 403 would be a misclassification, 404 means the filter passed the bad token through to the controller.

### Steps

```
1) Call adminToken() to get a real, fully-valid admin JWT.
2) Call tamperSignature(token) — keeps the original header and payload intact but replaces the third (signature) segment with a base64-url of "tampered-signature-does-not-verify". The result has 3 dot-separated segments and valid base64, but the signature is mathematically wrong.
3) Resolve firstTopLevelNonUserEntity() and compute crudCollectionPath(entity) (same target as TC06/TC07).
4) GET that path with Authorization: Bearer <tampered-token>.
5) Assert strictly 401.
```

### Pass Criteria

- **GET status strictly 401**
  - *Bug it catches:* Filter is performing real signature verification — any other status means the check is broken or absent.
- **NOT 2xx**
  - *Bug it catches:* Critical security bug — signature not verified. Code like Jwts.parser().parseClaimsJwt(token) (no key check) or direct base64-decode of the payload grants access to anyone who can edit a token's payload segment.
- **NOT 5xx**
  - *Bug it catches:* SignatureException/MalformedJwtException leaking unhandled instead of being caught and translated to 401.
- **NOT 404**
  - *Bug it catches:* Filter passed the request through to the controller — signature mismatch must be caught FIRST in the security chain, not deferred to the data layer.
- **NOT 403**
  - *Bug it catches:* Forged credentials are "not authenticated" (401), not "authenticated but lacking permission" (403). Returning 403 means the student is mis-classifying signature failures as permission errors.

---

## TC009 — Login with non-existent email returns 401 (negative path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST loginPath() (with email never registered)`

### What it tests

Spec §10 S1-F11 mandates that the login endpoint returns 401 Unauthorized for both failure cases — "user not found" and "wrong password" — deliberately avoiding 404 to prevent leaking which email addresses are registered (OWASP user-enumeration anti-pattern). This pairs with TC05 (existing user + wrong password → 401) to confirm that the spec's uniform 401-for-all-auth-failures rule is implemented consistently. Common bugs: login service throws UserNotFoundException for a missing email and maps it to 404 — leaking account presence; or controller maps not-found to 403; or returns 2xx with a null token.

### Steps

```
1) Generate a nonce-suffixed email that has never been registered (e.g. tc09_never_registered_<nonce>@grader.testgen.io).
2) Build a login body {"email":"<nonce-email>","password":"AnythingPwd!2026"}.
3) POST loginPath() with that body.
4) Read the response status code and assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* login service throws UserNotFoundException for a missing email and maps it to 404 — violating the spec's requirement that 401 is returned uniformly for all auth failures (leaking which emails are registered).
- **NOT 2xx**
  - *Bug it catches:* A token was issued for a user that does not exist — suggests login bypassed the user lookup or fell through a default find-any.
- **NOT 5xx**
  - *Bug it catches:* Optional.get() or repository.findByEmail() threw NoSuchElementException/NPE because the controller didn't anticipate the missing-user case and handle it gracefully.
- **NOT 403**
  - *Bug it catches:* Controller mapped "user not found" to a permission failure — structurally wrong; the user simply doesn't exist, no permission decision is involved.

---

## TC010 — Empty Bearer token returns 401 (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `GET crudCollectionPath(firstTopLevelNonUserEntity()) with Authorization: Bearer  (empty)`

### What it tests

The auth filter must reject malformed Authorization headers where the token portion after Bearer  is empty, returning strictly 401. This is a sibling negative test to TC08 (forged signature), TC11 (wrong scheme), and TC12 (garbage token), each targeting a different malformed-header class. Strict 401 — 5xx here means the JWT parser threw NPE or IllegalArgumentException on an empty string. Catches: parseToken("") NPE because String.split("\\.") on an empty string returns a length-1 array, if (token != null) checks that pass empty strings through, and accidental treatment of an empty Bearer as anonymous (the endpoint might still return 401, but for the wrong reason, masking the actual bug).

### Steps

```
1) Resolve firstTopLevelNonUserEntity() and compute crudCollectionPath(entity).
2) Call httpGetWithRawAuth(path, "Bearer ") — the helper sets the header literally as Authorization: Bearer  (trailing space, empty token portion), bypassing HTTP client trims.
3) Read the response status and assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Empty Bearer token accepted (2xx) or parser NPE leaked (5xx).
- **NOT 2xx**
  - *Bug it catches:* Filter treats "Bearer " with empty token as a successful credential, because it sees the prefix and skips the non-empty value check.
- **NOT 5xx**
  - *Bug it catches:* JWT parser threw IllegalArgumentException or NPE on the empty value because the filter didn't check for non-empty before parsing — must short-circuit defensively and return 401.

---

## TC011 — Non-Bearer scheme (Basic) returns 401 (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `GET crudCollectionPath(firstTopLevelNonUserEntity()) with Authorization: Basic dXNlcjpwYXNz`

### What it tests

The auth filter must reject Authorization headers that use any scheme other than Bearer, returning strictly 401. The spec mandates JWT-Bearer as the only accepted auth scheme; Basic auth is the most realistic mistaken alternative (browsers and curl default to it). This catches lenient parsers that strip the leading word and try to decode whatever's left as a JWT — "Basic dXNlcjpwYXNz" would feed dXNlcjpwYXNz into the JWT parser, causing a parse failure (5xx if uncaught). The filter must explicitly check that the scheme word is Bearer before reading the credential value.

### Steps

```
1) Resolve firstTopLevelNonUserEntity() and compute crudCollectionPath(entity).
2) Call httpGetWithRawAuth(path, "Basic dXNlcjpwYXNz") — dXNlcjpwYXNz is the base64 of "user:pass", the canonical Basic-auth example.
3) Read the response status and assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Filter does not validate the scheme and passes non-Bearer credentials to the JWT parser.
- **NOT 2xx**
  - *Bug it catches:* Filter accepted Basic auth on a JWT-protected endpoint — implies an alternate auth path was wired in by accident or the scheme check is missing entirely.
- **NOT 5xx**
  - *Bug it catches:* Filter blindly strips the first word and feeds the rest into the JWT parser (header.substring(7) on a "Basic ..." header), which then throws a MalformedJwtException that propagates uncaught.

---

## TC012 — Garbage non-JWT token returns 401 (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `GET crudCollectionPath(firstTopLevelNonUserEntity()) with Authorization: Bearer not_a_valid_jwt`

### What it tests

The auth filter must reject values that are syntactically invalid as JWTs (no dots, no valid base64 segments) with strictly 401. Together with TC10 (empty), TC11 (wrong scheme), and TC08 (tampered signature), this completes the malformed-input matrix. The strict 5xx prohibition is the key catch: parsers that throw MalformedJwtException or IllegalArgumentException on garbage input must have those exceptions caught at the filter level and translated to 401, not propagated as 500. This catches parsers wired with parseClaimsJws directly in a controller (no exception handling) and "if 3 dot-segments, decode without verifying" anti-patterns that fall apart on inputs without 3 segments.

### Steps

```
1) Resolve firstTopLevelNonUserEntity() and compute crudCollectionPath(entity).
2) Call httpGetWithRawAuth(path, "Bearer not_a_valid_jwt") — a value with no dots and no valid base64 segments, guaranteed to fail every JWT structure check.
3) Read the response status and assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Filter does not handle malformed JWT inputs gracefully.
- **NOT 2xx**
  - *Bug it catches:* Garbage was accepted — implies the filter never tried to parse the token, a dangerous footgun where any random string passes auth.
- **NOT 5xx**
  - *Bug it catches:* MalformedJwtException propagated to the response — the filter must catch all parse exceptions and translate them to 401, not crash the request.

---

## TC013 — Forged role-claim token (payload modified post-signing) is rejected (negative path)

**Tags:** `public` `authentication`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET crudCollectionPath(firstTopLevelNonUserEntity()) with payload-tampered Bearer token`

### What it tests

The JWT signature must cover the payload, so modifying the payload post-signing must invalidate the token even though the signature segment is preserved — this distinguishes this test from TC08 (signature replaced). The test targets the critical privilege-escalation bug where a student extracts claims without signature verification: an attacker can flip "role":"CUSTOMER" to "role":"ADMIN" in the base64 payload and re-encode it, keeping the original signature, which is now mathematically wrong for the new payload. A correctly-implemented filter must reject this with 401 (signature check caught the tamper) or 403 (role re-validated from DB and found mismatch); 2xx means the entire token security model is broken. NOT 5xx because the filter must reject cleanly.

### Steps

```
1) Register a fresh non-admin user and login to capture a real, signed JWT.
2) Split the JWT into 3 segments (header, payload, signature).
3) Base64-decode the payload, modify the role claim (replace "role":"CUSTOMER" with "role":"ADMIN", or inject "role":"ADMIN" if the claim is absent/named differently), re-base64-encode the tampered payload.
4) Reassemble the token as header.tamperedPayload.originalSignature — the signature is now wrong for the new payload.
5) GET crudCollectionPath(firstTopLevelNonUserEntity()) with Authorization: Bearer <forged-token> and assert the response is NOT 2xx and NOT 5xx.
```

### Pass Criteria

- **Status NOT 2xx (forged token must not grant access — 401 from signature check OR 403 from server-side role re-validation)**
  - *Bug it catches:* Critical privilege-escalation bug — the signature is not verified after payload modification. Anyone with a valid non-admin token can self-promote to ADMIN by editing the payload segment and re-base64'ing.
- **NOT 5xx**
  - *Bug it catches:* SignatureException or JSON parse error on the tampered payload propagated uncaught — the filter must reject cleanly regardless of how the tamper manifests.

---

## TC014 — Register with missing required field returns 4xx (negative path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (with body missing email)`

### What it tests

The register endpoint must validate that all required fields are present and return a clean 4xx when any is absent, without crashing or persisting a row with null values. Per S1-F1, email is required; a request body missing it must be rejected by @Valid + @NotBlank before the JPA save call. Strict 4xx (NOT 2xx, NOT 5xx): NOT 2xx means the controller accepted the partial payload and tried to create a user with null email (which will crash as a DB not-null violation, leaking 5xx), NOT 5xx means @Valid was skipped and the NPE/constraint violation propagated. This catches controllers that omit @Valid on the @RequestBody parameter and rely on DB constraints as the only validation.

### Steps

```
1) Build a register body that intentionally omits the email field: {"name":"TC14 User","password":"TestPwd!2026","phone":"+201<nonce>"}.
2) POST registerPath() with this body.
3) Read the response status and assert it is in 400..499 (not 5xx, not 2xx).
```

### Pass Criteria

- **Status 400..499 (strict 4xx)**
  - *Bug it catches:* Controller or validator did not check for the missing field — either 2xx (row persisted with null email, subsequent login queries will fail unpredictably) or 5xx (NPE / DB not-null violation propagated uncaught).
- **NOT 2xx**
  - *Bug it catches:* A user with null email was persisted — login for that account will produce unpredictable behaviour and uniqueness checks on email will break.
- **NOT 5xx**
  - *Bug it catches:* @Valid annotation is missing from the @RequestBody parameter, so missing fields leak as NullPointerException or JPA constraint violation with a 500 stack trace.

---

## TC015 — Register with role=ADMIN in body must NOT result in an ADMIN account (privilege-escalation)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (with role:"ADMIN" in body), JDBC role lookup via fetchUserRole(email)`

### What it tests

A self-registering user must not be able to assign themselves the ADMIN role by injecting "role":"ADMIN" into the register body — this is a mass-assignment / blind JSON-to-entity mapping vulnerability. This catches DTOs with a public role setter or @JsonProperty("role") mapped directly to the entity's role field without overriding to the default CUSTOMER value. The test uses JDBC to read the actual role in the database after a 2xx registration, bypassing any API-layer filtering. Both outcomes are acceptable: 2xx with role NOT ADMIN in the DB (field ignored), or 4xx (extra field rejected). The invariant is: no ADMIN account was created through self-registration.

### Steps

```
1) Build a register body with "role":"ADMIN" plus standard valid fields: {"name":"TC15 User","email":"tc15_<nonce>@grader.testgen.io","password":"TestPwd!2026","phone":"...","role":"ADMIN"}.
2) POST registerPath() with this body and assert NOT 5xx.
3) If the response is 2xx: call fetchUserRole(email) to read the actual role from the DB via JDBC, assert it is NOT "ADMIN" (case-insensitive).
4) If the response is 4xx: the constraint is satisfied — no ADMIN account was created either way.
```

### Pass Criteria

- **Register status NOT 5xx**
  - *Bug it catches:* Controller crashed on the unexpected role field — even rejecting extra fields must be clean (400, not 500).
- **If 2xx: resulting role in DB NOT EQUAL "ADMIN"**
  - *Bug it catches:* DTO exposes a role setter with @JsonProperty and maps directly into the entity — any registrant can self-promote to ADMIN on first call. This is the mass-assignment vulnerability.
- **If 4xx: also acceptable (extra field rejected)**
  - *Bug it catches:* This case has no bug — extra-field rejection is a strict but valid approach.

---

## TC016 — Login with empty password returns 4xx (negative path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (with password:"")`

### What it tests

Login must validate that the password field is non-empty before attempting bcrypt comparison, returning any 4xx. An empty password could short-circuit bcrypt's check in some implementations — encoder.matches("", hash) correctly returns false, but some hand-rolled checks or password-comparison shortcuts may not reach bcrypt at all when the input is empty. Strict NOT 2xx — a token issued for an empty password is a critical security bug. Strict NOT 5xx — controller must validate input cleanly, not NPE on empty string. Any 4xx is acceptable (400 validation, 401 auth failure, 422 semantic error).

### Steps

```
1) Register a fresh user with valid password TestPwd!2026 and assert 2xx — establishes the account exists.
2) Build a login body with the registered email and "password":"" (empty string).
3) POST loginPath() with this body.
4) Read the status and assert NOT 2xx, NOT 5xx, and in range 400..499.
```

### Pass Criteria

- **Status NOT 2xx**
  - *Bug it catches:* Login bypassed bcrypt verification for empty input and issued a token — anyone who knows an email can authenticate without a password.
- **NOT 5xx**
  - *Bug it catches:* Controller NPE'd on empty string (e.g., bcrypt encoder threw on empty input) — must validate defensively.
- **Acceptable: 400 / 401 / 422 (any 4xx)**
  - *Bug it catches:* If none of the 4xx ranges trigger, the implementation is failing the test — it means empty passwords are either accepted (2xx) or crashing (5xx).

---

## TC017 — Cross-user IDOR: User A cannot READ User B's profile (negative path)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() × 2 (setup A + B), POST loginPath() (setup A), GET crudReadPathFor("User", B.id) with A's token`

### What it tests

The User CRUD GET-by-id endpoint must enforce ownership: a CUSTOMER user may only read their own profile, not another customer's. This catches the IDOR (Insecure Direct Object Reference) bug where the controller trusts the {id} path variable without comparing it to the authenticated user's id from the JWT. Both 403 (explicit deny) and 404 (privacy-by-obscurity) are acceptable per the M2 spec; 2xx is the critical failure. The "403 OR 404" leniency is intentional: some implementations use a "find-then-authorize" pattern (finds B, checks ownership, returns 403) while others use a "find-only-own" pattern (no row found for A's ownership scope, returns 404). Both are safe behaviours. NOT 5xx because the auth check must reject cleanly.

### Steps

```
1) Register user A with a nonce-based email and assert 2xx.
2) Register user B with a different nonce-based email and assert 2xx; extract B's id from the registration response via uidFromJwt.
3) Login as A and capture tokenA.
4) GET crudReadPathFor("User", B.id) (i.e. /api/users/<B.id>) with header Authorization: Bearer <tokenA>.
5) Read the status and assert it is NOT 2xx, NOT 5xx, and equals 403 OR 404.
```

### Pass Criteria

- **Status NOT 2xx (must be 403 or 404)**
  - *Bug it catches:* Any authenticated user can read any other user's profile — the controller trusts the path id without checking it matches the authenticated user's id from the JWT.
- **Status 403 (deny) OR 404 (privacy-by-obscurity)**
  - *Bug it catches:* Any other 4xx code (400, 401) signals a misclassification of the cross-user access denial.
- **NOT 5xx**
  - *Bug it catches:* The ownership check threw an unhandled exception (e.g., NPE when extracting uid from the token) instead of cleanly returning 403 or 404.

---

## TC018 — Cross-user IDOR: User A cannot UPDATE User B's profile (negative path)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() × 2 (setup A + B), POST loginPath() (setup A), PUT crudReadPathFor("User", B.id) with A's token + tampered name`

### What it tests

Cross-user write protection: a CUSTOMER user may not modify another customer's profile via PUT. The test uses the full-body mitigation pattern (all original fields plus the changed name) to handle controllers that require all fields on PUT. Critically, a defensive JDBC check verifies that B's name in the database did NOT change even when the HTTP response was 4xx — catching the catastrophic bug where the controller commits the update before doing the auth check and then returns 4xx anyway. Both HTTP and DB-level assertions are required.

### Steps

```
1) Register A and B (capture B.id, B's email, B's phone).
2) Login as A and capture tokenA.
3) PUT /api/users/<B.id> with tokenA and a body containing "name":"TC18 HIJACK" plus B's original email, password, and phone.
4) Assert the response is NOT 2xx (must be 403 or 404) and NOT 5xx.
5) JDBC: SELECT name FROM <users-table> WHERE id = B.id — assert the name is still "TC18 B Original" (unchanged).
```

### Pass Criteria

- **Status NOT 2xx (must be 403 or 404)**
  - *Bug it catches:* Cross-user IDOR write is unprotected — any authenticated user can modify any other user's profile by guessing their id.
- **NOT 5xx**
  - *Bug it catches:* The auth check threw an unhandled exception instead of cleanly returning 403.
- **JDBC: B's name unchanged from "TC18 B Original"**
  - *Bug it catches:* Controller commits the update THEN does the auth check and returns 403 — the HTTP response looks correct but the data was silently mutated (commit-before-check anti-pattern).

---

## TC019 — Cross-user IDOR: User A cannot DELETE User B (negative path)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() × 2 (setup A + B), POST loginPath() (setup A), DELETE crudReadPathFor("User", B.id) with A's token`

### What it tests

Cross-user delete protection: a CUSTOMER user may not delete another customer's account. The JDBC check (COUNT(*) = 1 for B's row) is the more important assertion here because a naive implementation might delete the row then return 403 — the user would be permanently gone with no API-visible evidence. Both HTTP and DB-level assertions are required. The DB check catches the same commit-before-check anti-pattern tested in TC18, applied to DELETE — the most destructive form.

### Steps

```
1) Register A and B (capture B.id).
2) Login as A and capture tokenA.
3) DELETE /api/users/<B.id> with tokenA.
4) Assert the response is NOT 2xx (must be 403 or 404) and NOT 5xx.
5) JDBC: SELECT COUNT(*) FROM <users-table> WHERE id = B.id — assert count = 1 (B still exists).
```

### Pass Criteria

- **Status NOT 2xx (must be 403 or 404)**
  - *Bug it catches:* Cross-user delete is unprotected — any authenticated user can permanently destroy any other user's account by guessing their id.
- **NOT 5xx**
  - *Bug it catches:* The auth check threw instead of cleanly returning 403.
- **JDBC: COUNT(*) = 1 (B still in DB)**
  - *Bug it catches:* Controller deletes the row then does the auth check and returns 403 — the HTTP response looks correct but B is permanently deleted (most destructive form of the commit-before-check anti-pattern).

---

## TC020 — Owner happy path: User A can UPDATE their own profile

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), PUT crudReadPathFor("User", A.id) with A's token + new name`

### What it tests

An owner must be able to update their own profile via PUT and have the change persist to the database. This is the positive counterpart to TC18's negative test — proving the ownership check correctly allows self-modification. JDBC verification (not via GET) is used deliberately: a controller could return 2xx without actually persisting the update if the save() call is missing or the transaction rolled back. The strict 2xx + DB-level assertion combination catches "fake 2xx with no persistence" and "save OK but transaction rolls back" bugs.

### Steps

```
1) Register A and capture A's id and original phone.
2) Login as A and capture tokenA.
3) PUT /api/users/<A.id> with tokenA and a body containing "name":"TC20 Updated" plus the original email, password, and phone.
4) Assert the PUT response is 2xx via assert2xx.
5) JDBC: SELECT name FROM <users-table> WHERE id = A.id — assert the name equals "TC20 Updated".
```

### Pass Criteria

- **PUT status 200..299 (strict)**
  - *Bug it catches:* Owner update was denied with 403 — the ownership check incorrectly rejects the owner themselves, only allowing admins to update users.
- **JDBC: name in DB equals "TC20 Updated"**
  - *Bug it catches:* Controller returned 2xx but the change was not persisted — the save() call is missing, the method is not @Transactional, or the update was to a detached entity that was never merged.

---

## TC021 — Admin override: admin can READ any user (happy path)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() (setup customer), adminToken() (setup), GET crudReadPathFor("User", customer.id) with admin token`

### What it tests

The ADMIN role must bypass cross-user ownership restrictions on the User CRUD GET endpoint, allowing an admin to read any user's profile. This is the RBAC counterpart to TC17's denial test — proving that the ownership check has the correct admin override. A fresh customer (not from pre-seed) is registered so there is no ambiguity about which user is being read. The JSON-object body check confirms the response is a real profile, not an empty body or an accidental permission-error response.

### Steps

```
1) Register a fresh customer and capture their id via uidFromJwt.
2) Call adminToken() to obtain an admin Bearer JWT.
3) GET /api/users/<customer.id> with the admin token.
4) Assert 2xx and parse the response body as a JSON object.
```

### Pass Criteria

- **Status 200..299 (strict)**
  - *Bug it catches:* Admin override is missing from the ownership check — admin is being denied the same way a CUSTOMER is denied in TC17, meaning there is no role-based bypass in the condition.
- **Body parses as a JSON object**
  - *Bug it catches:* Controller returned 200 with an empty body or a permission-error message rather than the actual user resource.

---

## TC022 — Admin override: admin can UPDATE any user (happy path)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() (setup customer), adminToken() (setup), PUT crudReadPathFor("User", customer.id) with admin token + new name`

### What it tests

The ADMIN role must bypass cross-user ownership restrictions on the User CRUD PUT endpoint. JDBC verification (not via GET) confirms persistence — same reasoning as TC20 (a controller could return 2xx without actually saving). This pairs with TC18 (regular user denied) to define the full RBAC matrix for user-profile updates: CUSTOMER→other denied, ADMIN→other allowed. Catches: ownership checks that correctly deny CUSTOMER→B but fail to grant ADMIN→B, and admin PUT paths that return 2xx but don't persist.

### Steps

```
1) Register a fresh customer, capture their id and original phone.
2) Call adminToken() to obtain an admin Bearer JWT.
3) PUT /api/users/<customer.id> with the admin token and a body containing "name":"TC22 Admin-Updated" plus the original email, password, and phone.
4) Assert the PUT response is 2xx.
5) JDBC: SELECT name FROM <users-table> WHERE id = customer.id — assert equals "TC22 Admin-Updated".
```

### Pass Criteria

- **PUT status 200..299 (strict)**
  - *Bug it catches:* Admin override is missing from the ownership check for write operations — admin is denied the update the same way CUSTOMER would be in TC18.
- **JDBC: customer's name equals "TC22 Admin-Updated"**
  - *Bug it catches:* Admin PUT returned 2xx but the change was not persisted — same persistence bug as TC20 but exercised via the admin code path.

---

## TC023 — Admin override: admin can HARD-DELETE any user (happy path, strict)

**Tags:** `public` `authorization`  
**Endpoint(s):** `POST registerPath() (setup customer), adminToken() (setup), DELETE crudReadPathFor("User", customer.id) with admin token, then GET`

### What it tests

The ADMIN role must be able to hard-delete any user via the CRUD DELETE endpoint, with the row physically removed from the database (COUNT(*) = 0) and a subsequent GET returning strictly 404. Strict hard-delete is required per spec direction — soft-delete/deactivation is NOT acceptable for this endpoint. This catches: soft-delete implementations (status=DEACTIVATED instead of row removal), DELETE endpoints that return 2xx but don't call repository.delete(), and GET endpoints that return 200 from a cache or soft-delete flag after physical deletion.

### Steps

```
1) Register a fresh customer and capture their id.
2) Call adminToken() to obtain an admin Bearer JWT.
3) DELETE /api/users/<customer.id> with the admin token and assert 2xx.
4) JDBC: SELECT COUNT(*) FROM <users-table> WHERE id = customer.id — assert count = 0 (row physically removed).
5) GET /api/users/<customer.id> with the admin token and assert strictly 404.
```

### Pass Criteria

- **DELETE status 200..299 (strict)**
  - *Bug it catches:* Admin role is missing from the ownership check for DELETE — admin is denied the same way CUSTOMER would be in TC19.
- **JDBC: COUNT(*) = 0 (row physically removed)**
  - *Bug it catches:* Soft-delete implementation — the row is marked status=DEACTIVATED but still exists in the DB, violating the spec's hard-delete requirement.
- **GET status strictly 404**
  - *Bug it catches:* GET-by-id returns data from an in-memory cache, a soft-delete view, or a detached entity — the row is gone from DB but the API still serves it.

---

## TC024 — S1-F12 owner GET own activity returns 2xx with paginated envelope (happy path)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity with own token`

### What it tests

The S1-F12 activity feed endpoint GET /api/users/{id}/activity must respond to an authenticated owner with 2xx and a paginated envelope matching the spec shape: {content[], page, size, totalElements}. This is the happy-path smoke test that the endpoint is wired and the security config has the right path pattern. Four separate envelope-field checks provide precise failure messages instead of a single schema check. The content array is allowed to be empty for a fresh user who has no logged activities yet — this test does not seed any activity first. Lenient on the specific numeric values of page, size, and totalElements since those are checked by pagination-boundary tests (TC28–TC34).

### Steps

```
1) Register a fresh user A and capture their id via uidFromJwt.
2) Login as A and capture tokenA.
3) GET /api/users/<A.id>/activity with header Authorization: Bearer <tokenA>.
4) Assert 2xx via assert2xx.
5) Parse the body and assert it contains all four fields: content (must be an array), page, size, and totalElements.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Activity endpoint is not wired (404), or the security config's requestMatchers pattern does not cover /api/users/{id}/activity so the filter returns 401.
- **Body has content (array)**
  - *Bug it catches:* Response is a plain list (no envelope) — the controller returned List<ActivityDTO> directly rather than a Page or custom envelope object.
- **Body has page**
  - *Bug it catches:* Envelope is missing the page field — controller returned a custom wrapper that omitted pagination metadata.
- **Body has size**
  - *Bug it catches:* Envelope is missing the size field.
- **Body has totalElements**
  - *Bug it catches:* Envelope is missing totalElements — the most common omission when hand-rolling a custom envelope instead of using Spring's Page response.

---

## TC025 — S1-F12 GET activity for non-existent user ID returns 404 (admin token)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `adminToken() (setup), GET /api/users/<Long.MAX_VALUE>/activity with admin`

### What it tests

Per S1-F12 spec, when the target user does not exist, the activity endpoint must return 404. Admin token is used because a non-admin token would fail the ownership check first with 403, never reaching the user-not-found lookup. Long.MAX_VALUE (9223372036854775807) is used as the id to guarantee no real user has that id. Catches: Optional.get() NPE on the missing user (5xx), controller that returns 200 with an empty content array for non-existent users (silently wrong), and implementations that return 403 because the admin check was evaluated after the existence check.

### Steps

```
1) Call adminToken() to obtain an admin Bearer JWT.
2) GET /api/users/<Long.MAX_VALUE>/activity with the admin token.
3) Read the response status and assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Any non-404 means the endpoint is mishandling the non-existent-user case.
- **NOT 5xx**
  - *Bug it catches:* Optional.get() or userRepository.findById() threw NoSuchElementException/NPE — server must handle it gracefully.
- **NOT 2xx**
  - *Bug it catches:* Controller returned 200 with an empty content array for a user that doesn't exist — spec requires 404, not a successful empty-page response.

---

## TC026 — S1-F12 GET activity for negative user ID returns 4xx (admin token)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `adminToken() (setup), GET /api/users/-1/activity with admin`

### What it tests

A negative id (-1) can never correspond to a real user (auto-generated ids are always positive). The controller must reject it gracefully with a 4xx (400 validation or 404 not-found). Admin token bypasses ownership so the test reaches the validation/not-found path. NOT 5xx — unhandled negative id must not crash the server. NOT 2xx — a negative id matching a user is impossible with standard JPA sequence generation and would signal a corrupted database.

### Steps

```
1) Call adminToken() to obtain an admin Bearer JWT.
2) GET /api/users/-1/activity with the admin token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx (400 validation or 404 not-found)**
  - *Bug it catches:* Controller passed -1 directly to findById(-1L) without a guard and the repository returned empty → NPE (5xx), or passed it to PageRequest which silently accepted it (2xx with empty results).
- **NOT 5xx**
  - *Bug it catches:* Unguarded findById(-1) caused an unexpected DB query error or NPE — controller must validate or handle gracefully.
- **NOT 2xx**
  - *Bug it catches:* Controller returned 200 with empty results for a user that cannot exist — absence of a row should be 404, not a successful empty-page response.

---

## TC027 — S1-F12 GET activity for non-numeric user ID returns 4xx (admin token)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `adminToken() (setup), GET /api/users/abc/activity with admin`

### What it tests

Spring's @PathVariable Long binding must reject the value "abc" with a 4xx (typically 400 Bad Request) before the controller logic runs. This tests the framework-level type safety rather than custom code — if the binding is correct, Spring generates a MethodArgumentTypeMismatchException that must be translated to 400 by a @ControllerAdvice or the default error handler. Admin token ensures any 401 is not from missing auth. NOT 5xx — an unhandled TypeMismatchException propagating as 500 means Spring's default error handling is not configured.

### Steps

```
1) Call adminToken() to obtain an admin Bearer JWT.
2) GET /api/users/abc/activity with the admin token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx (typically 400 Bad Request)**
  - *Bug it catches:* Spring's type-mismatch error is not translated to a client error — implies missing @ControllerAdvice for MethodArgumentTypeMismatchException.
- **NOT 5xx**
  - *Bug it catches:* TypeMismatchException propagated as 500 — means the exception bubbled past the DispatcherServlet without being handled.
- **NOT 2xx**
  - *Bug it catches:* "abc" was coerced into a valid Long by a custom path-variable converter that falls back to 0 or -1 — the binding should reject it outright.

---

## TC028 — S1-F12 size=0 must NOT 5xx (graceful handling)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity?size=0`

### What it tests

PageRequest.of(page, 0) throws IllegalArgumentException ("Page size must not be less than one") in Spring Data JPA. The spec is silent on size=0, but the controller must handle it gracefully (validate/clamp/reject) rather than letting the exception propagate as 500. This is a robustness test — only NOT 5xx is asserted. A 4xx (controller rejects size=0) or 2xx (controller clamps to minimum page size) are both acceptable outcomes. Catches controllers that pass size=0 directly to PageRequest.of() without a guard, which is the most common implementation of this bug.

### Steps

```
1) Register and login a fresh user A, capturing their id and token.
2) GET /api/users/<A.id>/activity?size=0 with the user's own token.
3) Read the response status and assert it is NOT in the 5xx range.
```

### Pass Criteria

- **NOT 5xx**
  - *Bug it catches:* PageRequest.of(0, 0) threw IllegalArgumentException that propagated as 500 — the controller passed size=0 directly to the pageable constructor without validating size >= 1. Any 4xx or 2xx is acceptable; only 5xx is prohibited.

---

## TC029 — S1-F12 size=-1 returns 4xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity?size=-1`

### What it tests

A negative size is semantically invalid and the controller must reject it with a 4xx regardless of how the framework handles it internally. Distinct from TC28 (size=0): some clamping logic might accept 0→1 but still reject -1, or conversely, clamping might turn -1 into 0 which then crashes PageRequest.of. Strict 4xx (NOT 5xx, NOT 2xx): any negative page size is user-provided garbage and must be caught at the controller boundary.

### Steps

```
1) Register and login a fresh user A, capturing their id and token.
2) GET /api/users/<A.id>/activity?size=-1 with the user's own token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx**
  - *Bug it catches:* Controller passed -1 to PageRequest.of(page, -1) which threw IllegalArgumentException (5xx), or clamped -1 to 0 which then crashed PageRequest.of(0, 0) (5xx).
- **NOT 5xx**
  - *Bug it catches:* Negative size propagated to Spring Data JPA's pageable constructor without validation and caused an unhandled exception.
- **NOT 2xx**
  - *Bug it catches:* Controller treated -1 as "no limit" or silently clamped it to a positive value — accepting negative sizes creates undefined behaviour for the client.

---

## TC030 — S1-F12 size=string returns 4xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity?size=abc`

### What it tests

Spring's @RequestParam Integer binding must reject "abc" as a type mismatch with 400 before any controller logic runs. Similar in structure to TC27 (path variable mismatch) but for a query parameter. NOT 5xx: MethodArgumentTypeMismatchException must be translated to 400 by a @ControllerAdvice or the default Spring error handler. NOT 2xx: non-numeric size cannot produce a valid page request.

### Steps

```
1) Register and login a fresh user A, capturing their id and token.
2) GET /api/users/<A.id>/activity?size=abc with the user's own token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx (typically 400 Bad Request)**
  - *Bug it catches:* Spring's query-parameter type binding error is not translated to a 4xx — the MethodArgumentTypeMismatchException bypassed the error handler.
- **NOT 5xx**
  - *Bug it catches:* NumberFormatException from manual Integer.parseInt("abc") propagated uncaught, or MethodArgumentTypeMismatchException leaked as 500.
- **NOT 2xx**
  - *Bug it catches:* "abc" was silently ignored and the endpoint used the default size=10 — the binding should reject non-numeric input, not fall back silently.

---

## TC031 — S1-F12 cross-user activity (regular user A reads User B) returns strictly 403

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() × 2 (setup), POST loginPath() (setup), GET /api/users/{B.id}/activity with A's token`

### What it tests

Per S1-F12 spec, a regular user reading another user's activity must receive strictly 403 (NOT 404). This is stricter than TC17's IDOR test for User CRUD (which accepts 403 OR 404). The spec mandates 403 here because both users exist and the token is valid — the server must recognise the resource exists and deliberately withhold it. Strict 403 means the controller's ownership check must explicitly return 403, not fall through to a 404 lookup. Catches: ownership check that returns 404 by not finding the resource (wrong — should find user then deny), and ownership check that returns 2xx by skipping the check entirely.

### Steps

```
1) Register A and B (capture B.id).
2) Login as A and capture tokenA.
3) GET /api/users/<B.id>/activity with tokenA.
4) Read the status and assert strictly 403.
```

### Pass Criteria

- **Status strictly 403**
  - *Bug it catches:* Any non-403 is a misclassification of the ownership-violation condition for the activity endpoint.
- **NOT 5xx**
  - *Bug it catches:* Ownership check threw instead of cleanly returning 403.
- **NOT 2xx**
  - *Bug it catches:* Regular users can read other users' activity feeds — the ownership check is missing or bypassed.

---

## TC032 — S1-F12 admin reads any user's activity returns 2xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup customer), adminToken() (setup), GET /api/users/{customer.id}/activity with admin`

### What it tests

The ADMIN role must bypass the S1-F12 ownership check and return the activity feed for any user. This is the positive RBAC counterpart to TC31's denial test — proving the ownership guard has the correct admin override. The content array check confirms the response envelope is present even if the user has no activities yet. Catches: admin check missing from the ownership guard (admin gets 403 same as regular user), activity endpoint returning 403 even for admins, and response body missing the content key.

### Steps

```
1) Register a fresh customer and capture their id.
2) Call adminToken() to obtain an admin Bearer JWT.
3) GET /api/users/<customer.id>/activity with the admin token.
4) Assert 2xx and parse the body to verify it contains a content array.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Admin override is missing from the S1-F12 ownership check — admin is denied with 403 the same way a regular user is denied in TC31.
- **Body has content array**
  - *Bug it catches:* Admin received 2xx but the body is empty or missing the content field — the activity query ran but the response envelope was not constructed.

---

## TC033 — S1-F12 page=-1 returns 4xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity?page=-1`

### What it tests

PageRequest.of(-1, size) throws IllegalArgumentException ("Page index must not be less than zero") in Spring Data JPA. The controller must validate that page >= 0 before constructing the PageRequest, returning a 4xx cleanly. Distinct from TC28 (size=0) and TC29 (size=-1) to cover the page parameter's own validation boundary — some implementations validate size but forget to validate page. Strict NOT 5xx because an unhandled IllegalArgumentException propagates as 500.

### Steps

```
1) Register and login a fresh user A, capturing their id and token.
2) GET /api/users/<A.id>/activity?page=-1 with the user's own token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx**
  - *Bug it catches:* Controller passed page=-1 to PageRequest.of(-1, size) without a guard, causing IllegalArgumentException (5xx); or silently treated -1 as "last page" or wrapped around to a positive value (2xx with wrong results).
- **NOT 5xx**
  - *Bug it catches:* Unguarded negative page index propagated to Spring Data JPA's pageable constructor and caused an unhandled exception — controller must validate page >= 0.
- **NOT 2xx**
  - *Bug it catches:* Controller silently accepted page=-1 and returned results — undefined behaviour for the client (which page did they get?).

---

## TC034 — S1-F12 page=string returns 4xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST registerPath() (setup), POST loginPath() (setup), GET /api/users/{ownId}/activity?page=abc`

### What it tests

Spring's @RequestParam Integer binding must reject "abc" as a page parameter with 400, mirroring TC30 for the size parameter. Together TC30 + TC34 confirm that both query parameters are properly typed and that Spring's binding-error translation is configured for this endpoint. NOT 5xx, NOT 2xx. Typically results in 400 Bad Request from Spring's built-in MethodArgumentTypeMismatchException handler — but only if a @ControllerAdvice or DefaultHandlerExceptionResolver is in place.

### Steps

```
1) Register and login a fresh user A, capturing their id and token.
2) GET /api/users/<A.id>/activity?page=abc with the user's own token.
3) Read the response status and assert it is in 400..499 (NOT 5xx, NOT 2xx).
```

### Pass Criteria

- **Status 4xx (typically 400 Bad Request)**
  - *Bug it catches:* Spring's query-parameter type binding error for page is not translated to a 4xx — the MethodArgumentTypeMismatchException bypassed the error handler.
- **NOT 5xx**
  - *Bug it catches:* NumberFormatException from manual Integer.parseInt("abc") propagated uncaught, or MethodArgumentTypeMismatchException leaked as 500.
- **NOT 2xx**
  - *Bug it catches:* "abc" was silently ignored and the endpoint used the default page=0 — the binding should reject non-numeric input, not fall back silently.

---

## M1 Features — TC191–TC220

## TC191 — S1-F1 GET /api/users/search?name=Ahmed returns users with name containing "Ahmed" (partial match)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/search?name=Ahmed`

### What it tests

The S1-F1 user search endpoint must perform a case-insensitive partial name match, returning all users whose name contains the query string. Three users are seeded — two named "Ahmed" and "Ahmed Ali" (both should match), one named "Sara" (must not match). The assertion counts matched results and verifies exactly 2 are returned. This tests that the query uses SQL LIKE/ILIKE or a containsIgnoreCase Spring Data method rather than an exact equality check. Any count other than 2 indicates a broken partial-match implementation or a missing name filter.

### Steps

```
1) Seed users "Ahmed", "Sara", "Ahmed Ali" with unique emails via _FmM1Seed.seedUser.
2) Call adminToken() to get a valid token.
3) GET /api/users/search?name=Ahmed via httpGetAuth.
4) Assert 2xx via assert2xx, parse body, unwrap to list (array or content).
5) Count items whose name contains "Ahmed" and assert count == 2.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Search endpoint not wired (404) or auth guard missing (401).
- **Count of matching users == 2**
  - *Bug it catches:* Exact match used instead of partial — "Ahmed Ali" not returned (count=1); or filter ignored and all 3 returned (count=3).

---

## TC192 — S1-F1 GET /api/users/search?role=FREELANCER returns only FREELANCER users

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/search?role=FREELANCER`

### What it tests

The S1-F1 user search endpoint must support filtering by role, returning only users whose role is exactly FREELANCER. Three users are seeded — two FREELANCERs and one CLIENT. Every item in the result must have role=FREELANCER and the total must be at least 2. This verifies that the role parameter maps to a SQL WHERE clause and that the CLIENT user is excluded from results. A filter that applies LIKE to role instead of equality would pass but is still wrong per spec.

### Steps

```
1) Seed Fr1, Cl1, Fr2 via _FmM1Seed.seedUser.
2) adminToken() → tok.
3) GET /api/users/search?role=FREELANCER via httpGetAuth.
4) Assert 2xx, unwrap list.
5) For every item assert role == "FREELANCER", assert list.size() >= 2.
```

### Pass Criteria

- **Every result has role=FREELANCER**
  - *Bug it catches:* Role filter is ignored — the CLIENT is returned in the results, or the query does a LIKE match that accidentally includes non-FREELANCER roles.
- **list.size() >= 2**
  - *Bug it catches:* Filter too strict — returns 0 results because the query compared role as a differently-cased string (e.g., "freelancer" vs "FREELANCER").

---

## TC193 — S1-F1 search with no-matching name returns empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/search?name=zzzNoMatchXYZ`

### What it tests

When the name query matches no users, the search endpoint must return 2xx with an empty list (not 404). This confirms the "no results" case is handled gracefully — a 404 here would mean the controller confused an empty result set with a missing resource. One user named "Ahmed" is seeded so the DB is non-empty, proving the filter is actually applied.

### Steps

```
1) Seed user "Ahmed".
2) adminToken() → tok.
3) GET /api/users/search?name=zzzNoMatchXYZ.
4) Assert 2xx, unwrap list.
5) Assert list.size() == 0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 when no results found — conflates empty result with missing resource.
- **list.size() == 0**
  - *Bug it catches:* Filter is ignored and all users are returned even when the query should match nothing.

---

## TC194 — S1-F2 PUT /api/users/{uid}/preferences merges JSONB: language preserved, theme updated, currency added

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/preferences`

### What it tests

The S1-F2 preferences update endpoint must perform a JSONB merge (not a replace). An existing preference {"language":"en","theme":"light"} is set on the user; a PUT body {"theme":"dark","currency":"USD"} must update theme to "dark", add currency:"USD", and preserve the untouched language:"en" key. This is the key behavioral guarantee: JSONB merge semantics rather than full overwrite. Catching the full-overwrite bug is critical — it means users lose unrelated preferences on every update.

### Steps

```
1) _FmM1Seed.seedUser → uid.
2) _FmM1Seed.setPrefs(this, uid, "{\"language\":\"en\",\"theme\":\"light\"}") to seed initial prefs.
3) PUT /api/users/{uid}/preferences with body {"theme":"dark","currency":"USD"}.
4) Assert 2xx, parse response, extract preferences node.
5) Assert language=="en", theme=="dark", currency=="USD".
```

### Pass Criteria

- **language == "en" (preserved)**
  - *Bug it catches:* PUT overwrites the entire JSONB field — existing key language is lost because the implementation does UPDATE ... SET preferences = ?::jsonb instead of using the `

---

## TC195 — S1-F2 PUT with existing key overwrites it

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/preferences`

### What it tests

Same-key overwrite: when the PUT body contains a key that already exists in the JSONB, the new value must replace the old one. Initial prefs: {"language":"en"}; PUT body: {"language":"fr"}; expected result: language=="fr". This verifies the merge preserves new-value-wins semantics for duplicate keys. Complements TC194 (which tests new key addition and unrelated key preservation).

### Steps

```
1) Seed user, set initial prefs {"language":"en"}.
2) PUT preferences with body {"language":"fr"}.
3) Assert 2xx, extract prefs node from response.
4) Assert language == "fr".
```

### Pass Criteria

- **language == "fr"**
  - *Bug it catches:* JSONB merge is broken for same-key update — new value is not written because the implementation guards against overwriting existing keys, or the merge uses the wrong operator (-> instead of `

---

## TC196 — S1-F2 PUT preferences for non-existent user returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/999999/preferences`

### What it tests

When the target user id does not exist, the preferences update endpoint must return strictly 404. Admin token ensures ownership is not the denial reason. Uses hard-coded id 999999 which is guaranteed not to exist. Catches NPE from missing Optional.get() guard (5xx) and controllers that return 2xx with an empty body for missing users.

### Steps

```
1) adminToken() → tok.
2) PUT /api/users/999999/preferences with body {"x":"y"}.
3) Assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Optional.get() threw NoSuchElementException (5xx), or controller returned 2xx without checking user existence.

---

## TC197 — S1-F3 GET /api/users/{uid}/contract-summary returns correct totals (5 contracts, 3 COMPLETED, 1 TERMINATED, earnings=700)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/{uid}/contract-summary`

### What it tests

The S1-F3 contract summary endpoint must aggregate a user's contracts: totalContracts counting all statuses, completedContracts counting only COMPLETED, terminatedContracts counting only TERMINATED, and totalEarnings summing only COMPLETED contract amounts. Five contracts are seeded (3 COMPLETED at 150+200+350=700, 1 TERMINATED at 999, 1 ACTIVE at 999) to test that earnings only count COMPLETED and the terminated/active are counted in their respective buckets. Any aggregation that includes TERMINATED or ACTIVE amounts in earnings is a bug.

### Steps

```
1) Seed user + job via _FmM1Seed.seedUser/seedJob.
2) Seed 3 COMPLETED contracts (150, 200, 350), 1 TERMINATED (999), 1 ACTIVE (999) via _FmM1Seed.seedContract.
3) GET /api/users/{uid}/contract-summary, assert 2xx.
4) Assert totalContracts==5, completedContracts==3, terminatedContracts==1, totalEarnings==700±0.5.
```

### Pass Criteria

- **totalContracts == 5**
  - *Bug it catches:* Only COMPLETED contracts are counted in total — ACTIVE and TERMINATED are excluded from the total count.
- **completedContracts == 3**
  - *Bug it catches:* Status filter is broken — TERMINATED or ACTIVE are counted as COMPLETED.
- **terminatedContracts == 1**
  - *Bug it catches:* TERMINATED contracts not tracked in their own bucket.
- **totalEarnings == 700**
  - *Bug it catches:* TERMINATED contract amount (999) is included in earnings — earnings must sum only COMPLETED amounts.

---

## TC198 — S1-F3 contract summary for user with no contracts returns zeros

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/{uid}/contract-summary`

### What it tests

A user with no contracts must receive a summary with all numeric fields at zero (totalContracts=0, totalEarnings=0), not a 404. This validates the zero-result case is handled gracefully. Catches: NPE from aggregation queries that fail on empty result sets (SUM returns null), and controllers that return 404 when no contracts are found.

### Steps

```
1) Seed a fresh user via _FmM1Seed.seedUser (no contracts).
2) GET /api/users/{uid}/contract-summary, assert 2xx.
3) Assert totalContracts == 0, totalEarnings == 0.0.
```

### Pass Criteria

- **totalContracts == 0**
  - *Bug it catches:* Controller returns 404 when no contracts found — should return zeros.
- **totalEarnings == 0.0**
  - *Bug it catches:* SUM() on empty result returns SQL NULL → NPE when mapping to Double; must use COALESCE(SUM(...), 0).

---

## TC199 — S1-F3 contract summary for non-existent user returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/999999/contract-summary`

### What it tests

A request for contract summary for a user id that does not exist must return strictly 404. Complements TC198 (existing user, no contracts → zeros) by confirming the endpoint distinguishes "user exists but has no contracts" from "user does not exist at all".

### Steps

```
1) GET /api/users/999999/contract-summary with admin token.
2) Assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller runs the aggregation query with a non-existent user id and returns 2xx with zeros rather than checking user existence first.

---

## TC200 — S1-F4 PUT /api/users/{uid}/deactivate returns 400 when user has an ACTIVE contract

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/deactivate`

### What it tests

The S1-F4 deactivate endpoint must reject deactivation when the user has at least one ACTIVE contract, returning strictly 400. An ACTIVE contract seeded via _FmM1Seed.seedContract with status ACTIVE blocks deactivation. This prevents users from abandoning in-progress work. The assertion is strict 400 — 2xx would mean the user was deactivated while still having active obligations.

### Steps

```
1) Seed user + job, then seed one ACTIVE contract via _FmM1Seed.seedContract.
2) PUT /api/users/{uid}/deactivate, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Deactivate proceeds without checking for active contracts — user is marked DEACTIVATED while still having an in-progress obligation, leaving the contract in an orphaned state.

---

## TC201 — S1-F4 deactivate succeeds when only COMPLETED contracts; sets status=DEACTIVATED and withdraws SUBMITTED proposals

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/deactivate`

### What it tests

The S1-F4 deactivate endpoint must succeed (2xx) when the user has no ACTIVE contracts. On success: (1) the user's status must be set to DEACTIVATED in the database, and (2) any SUBMITTED proposals by the user must be transitioned to WITHDRAWN. A COMPLETED contract and a SUBMITTED proposal are seeded; post-deactivation, both are verified via JDBC. This tests the cascade effect of deactivation on proposals.

### Steps

```
1) Seed user, seed job, seed COMPLETED contract, seed SUBMITTED proposal via _FmM1Seed.seedProposal.
2) PUT /api/users/{uid}/deactivate, assert 2xx.
3) JDBC: SELECT user status, assert == "DEACTIVATED".
4) JDBC: SELECT proposal status, assert == "WITHDRAWN".
```

### Pass Criteria

- **User status = DEACTIVATED in DB**
  - *Bug it catches:* Deactivate endpoint returns 2xx but doesn't actually update the status column — the user remains ACTIVE despite the response.
- **Proposal status = WITHDRAWN in DB**
  - *Bug it catches:* Deactivation doesn't cascade to cancel the user's in-flight proposals — SUBMITTED proposals remain open, allowing them to be accepted after the user is deactivated.

---

## TC202 — S1-F4 deactivate non-existent user returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/999999/deactivate`

### What it tests

Deactivating a user that does not exist must return strictly 404. Admin token is used to bypass ownership. Catches NPE (5xx) and 2xx no-op responses for missing users.

### Steps

```
1) PUT /api/users/999999/deactivate, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* repository.findById(999999).get() threw NPE (5xx), or controller silently returns 2xx for a non-existent user without throwing.

---

## TC203 — S1-F5 GET /api/users/preferences/search?key=language&value=ar returns users with prefs.language=ar

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/search?key=language&value=ar`

### What it tests

The S1-F5 JSONB preferences search endpoint must find users whose preferences JSONB contains the specified key-value pair. Three users are seeded: Ar1 (language=ar), En1 (language=en), Ar2 (language=ar). Searching for key=language,value=ar must return at least 2 results. This tests the JSONB containment query: preferences ->> 'language' = 'ar' or equivalent. Catches: exact-object match instead of key extraction (matches nothing), or full-text search on the JSONB string (might match "ar" inside other values).

### Steps

```
1) Seed Ar1, En1, Ar2 and set their prefs via _FmM1Seed.setPrefs.
2) GET /api/users/preferences/search?key=language&value=ar, assert 2xx.
3) Unwrap list, assert list.size() >= 2.
```

### Pass Criteria

- **list.size() >= 2**
  - *Bug it catches:* JSONB key extraction is wrong — query uses preferences @> '{"language":"ar"}'::jsonb (containment) or preferences->>'language' = 'ar'; if the column is stored as text and not cast to JSONB, the query returns 0 results.

---

## TC204 — S1-F5 preferences search with unknown value returns empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/search?key=language&value=zh`

### What it tests

When no user has a matching JSONB key-value, the search must return 2xx with an empty list. One user with language=ar is seeded to confirm the DB is non-empty. Searching for language=zh must return zero results, not 404.

### Steps

```
1) Seed user, set prefs {"language":"ar"}.
2) GET /api/users/preferences/search?key=language&value=zh, assert 2xx.
3) Assert list.size() == 0.
```

### Pass Criteria

- **list.size() == 0**
  - *Bug it catches:* Filter ignored — all users returned regardless of preference value. Controller may be doing a non-null check on the key field instead of an equality check on the extracted value.

---

## TC205 — S1-F5 preferences search with blank key returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/search?key=&value=ar`

### What it tests

A blank key parameter must be rejected with strictly 400. An empty key cannot identify any JSONB field, so the controller must validate key is non-blank before executing the query. Catches: JSONB queries with an empty key string that silently return no results (2xx instead of 400), or NPE from passing empty key to the query (5xx).

### Steps

```
1) GET /api/users/preferences/search?key=&value=ar, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Empty key passed to the query without validation — either returns 2xx with empty results (wrong, should be 400) or crashes with 5xx from the DB rejecting an empty key in a JSONB path expression.

---

## TC206 — S1-F6 GET /api/users/reports/top-freelancers ranks freelancer B (3500 total) above freelancer A (1200 total)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/reports/top-freelancers?startDate=2026-03-01&endDate=2026-03-31&limit=10`

### What it tests

The S1-F6 top freelancers report must rank freelancers by total earnings from COMPLETED contracts in the given date range, in descending order. User A has one COMPLETED contract (1200), user B has two COMPLETED contracts (1500+2000=3500). B must appear first. This tests the SQL ORDER BY total_earnings DESC and the date-range filter. Both the sorting and the date-range inclusion must be correct — B appearing first only if the sum is computed correctly per user.

### Steps

```
1) Seed users A and B, one job, seed contracts A(1200), B(1500), B(2000) all in March 2026.
2) GET top-freelancers with startDate=2026-03-01&endDate=2026-03-31&limit=10, assert 2xx.
3) Unwrap list, assert list.size() >= 2.
4) Assert list.get(0).userId == uB.
```

### Pass Criteria

- **list.get(0).userId == uB**
  - *Bug it catches:* Ranking is not sorted by total earnings DESC — A ranks first despite lower earnings; or per-contract earnings are used for ranking instead of the sum per user.

---

## TC207 — S1-F6 date range with no completed contracts returns empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/reports/top-freelancers?startDate=2030-01-01&endDate=2030-01-31&limit=10`

### What it tests

A date range in the far future (2030) where no contracts exist must return 2xx with an empty list. Confirms the date filter is applied and the zero-result case is handled gracefully (not 404).

### Steps

```
1) GET top-freelancers with 2030 date range, assert 2xx, assert list.size() == 0.
```

### Pass Criteria

- **list.size() == 0**
  - *Bug it catches:* Date range filter is ignored — all contracts are included regardless of date, returning users even for an empty period.

---

## TC208 — S1-F6 startDate > endDate returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/reports/top-freelancers?startDate=2026-03-31&endDate=2026-03-01&limit=10`

### What it tests

An invalid date range where start is after end must be rejected with strictly 400. Catches controllers that pass the inverted range to the SQL query (which would silently return no results as 2xx), instead of validating the range and returning 400.

### Steps

```
1) GET top-freelancers with inverted date range, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Inverted range passed to SQL BETWEEN clause — DB returns empty results and controller returns 2xx instead of validating the range.

---

## TC209 — S1-F7 PUT /api/users/{uid}/skills/{skillId}/primary flips primary flag to target skill

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/skills/{skillId}/primary`

### What it tests

The S1-F7 set-primary-skill endpoint must (a) mark the target skill as primary (isPrimary=true) and (b) unmark the previously primary skill (isPrimary=false). Three skills are seeded: React (isPrimary=false), Vue (isPrimary=true, the current primary), Angular (isPrimary=false). After calling the endpoint for Angular, Angular must become primary and Vue must lose the primary flag. Both JDBC assertions are needed — the endpoint might set the new primary without clearing the old one (double-primary bug).

### Steps

```
1) Seed user, seed React (isPrimary=false), Vue (isPrimary=true), Angular (isPrimary=false) via _FmM1Seed.seedSkill → capture s2 (Vue), s3 (Angular).
2) PUT /api/users/{uid}/skills/{s3}/primary, assert 2xx.
3) JDBC: SELECT isPrimary for s3, assert == true.
4) JDBC: SELECT isPrimary for s2, assert == false.
```

### Pass Criteria

- **s3.isPrimary == true**
  - *Bug it catches:* Endpoint did not set the target skill as primary — the column was not updated.
- **s2.isPrimary == false (old primary cleared)**
  - *Bug it catches:* Double-primary bug — both the old and new skills have isPrimary=true because the endpoint only sets the new primary without clearing the existing one.

---

## TC210 — S1-F7 set primary skill returns 404 for non-existent user

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/999999/skills/1/primary`

### What it tests

When the user does not exist, the set-primary-skill endpoint must return strictly 404. Uses hard-coded user id 999999. Catches NPE (5xx) and 2xx no-op responses.

### Steps

```
1) PUT /api/users/999999/skills/1/primary, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* User lookup not performed before the skill update — repository.findById(999999).get() NPE (5xx) or controller proceeds to update skill regardless of user existence (2xx).

---

## TC211 — S1-F7 skill belonging to a different user returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{u1}/skills/{s2}/primary`

### What it tests

Attempting to set as primary a skill that belongs to a different user must return strictly 400. User u1 cannot manage user u2's skills. A skill seeded under u2 is used as the target in a request authenticated as u1. Catches controllers that do not verify the skill's owner before updating.

### Steps

```
1) Seed u1 and u2, seed skill s2 under u2.
2) PUT /api/users/{u1}/skills/{s2}/primary, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Cross-user skill assignment — the controller updated s2's isPrimary flag even though it belongs to u2, corrupting u2's skill data.

---

## TC212 — S1-F7 non-existent skill returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/users/{uid}/skills/999999/primary`

### What it tests

A valid user but non-existent skill id must return strictly 404. Distinct from TC210 (non-existent user) — both the user and skill must independently be validated.

### Steps

```
1) Seed user → uid.
2) PUT /api/users/{uid}/skills/999999/primary, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Skill lookup not performed — controller returns 2xx as a no-op, or NPE (5xx) from findById(999999).get().

---

## TC213 — S1-F8 GET /api/users/{uid}/profile returns totalSkills=3 with skills array

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/{uid}/profile`

### What it tests

The S1-F8 profile endpoint must return a rich DTO containing totalSkills (count of the user's skills) and a skills array listing all skills. Three skills are seeded; the DTO must report totalSkills==3 and skills.size()==3. This verifies the JOIN between users and user_skills is performed correctly and that the count is not hard-coded. The skills array allows the client to display skill details without an additional API call.

### Steps

```
1) Seed user, seed 3 skills (React, Node, TypeScript) via _FmM1Seed.seedSkill.
2) GET /api/users/{uid}/profile, assert 2xx.
3) Assert totalSkills == 3, skills array present with size 3.
```

### Pass Criteria

- **totalSkills == 3**
  - *Bug it catches:* COUNT query on user_skills is wrong — returns 0 (no JOIN), or hardcodes a value regardless of actual skill count.
- **skills array has 3 items**
  - *Bug it catches:* Skills list is not populated in the DTO — controller fetches the count but forgets to hydrate the skill list, leaving the client without skill details.

---

## TC214 — S1-F8 profile for user with 0 skills returns totalSkills=0

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/{uid}/profile`

### What it tests

A fresh user with no skills must receive a profile with totalSkills=0 (not 404). This is the zero-skills edge case — ensures the COUNT query handles empty results and returns 0 rather than null (which would NPE when auto-unboxed to int).

### Steps

```
1) Seed user (no skills).
2) GET /api/users/{uid}/profile, assert 2xx.
3) Assert totalSkills == 0.
```

### Pass Criteria

- **totalSkills == 0**
  - *Bug it catches:* COUNT(*) returns null for empty results and auto-unboxing throws NPE (5xx); must use COALESCE(COUNT(*), 0) or null-safe mapping.

---

## TC215 — S1-F8 profile for non-existent user returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/999999/profile`

### What it tests

A profile request for a non-existent user must return strictly 404. Admin token bypasses ownership. Distinct from TC214 (existing user, zero skills → 2xx) — the non-existence check must run before the skill aggregation.

### Steps

```
1) GET /api/users/999999/profile, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller runs the skill COUNT for user 999999 (returns 0 from DB) and returns a profile with totalSkills=0 instead of 404.

---

## TC216 — S1-F9 GET /api/users/preferences/language?lang=ar&minContracts=3 returns only Arabic-speaking users with ≥3 completed contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/language?lang=ar&minContracts=3`

### What it tests

The S1-F9 endpoint must filter by both language preference AND minimum completed-contract threshold. User A (lang=ar, 5 COMPLETED) must appear, user B (lang=ar, 2 COMPLETED) and user C (lang=en, 4 COMPLETED) must not. This tests a compound JSONB + JOIN filter: preferences->>'language' = 'ar' AND COUNT(COMPLETED contracts) >= 3. Both conditions are independently verified by the assertion loop — if only the language filter is applied, B appears (wrong); if only the contract count filter is applied, C appears (wrong).

### Steps

```
1) Seed A, B, C with appropriate language prefs and COMPLETED contracts.
2) GET endpoint, assert 2xx, unwrap list.
3) Assert A is present, B and C are absent.
```

### Pass Criteria

- **A present in list**
  - *Bug it catches:* Filtering is too strict — user A satisfies both conditions but is excluded due to an AND vs OR bug, or the contract count threshold is applied as > 3 instead of >= 3.
- **B absent (ar but only 2 completed)**
  - *Bug it catches:* The minContracts filter is not applied — all Arabic-speaking users returned regardless of contract count.
- **C absent (en despite high contract count)**
  - *Bug it catches:* Language filter not applied — all high-contract users returned regardless of language.

---

## TC217 — S1-F9 blank lang parameter returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/language?lang=&minContracts=1`

### What it tests

A blank lang parameter must be rejected with strictly 400. An empty language string cannot match any JSONB value meaningfully and the endpoint must validate non-blank before executing the query.

### Steps

```
1) GET with lang= (empty), assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Empty lang accepted — query runs with preferences->>'language' = '' and returns 0 results as 2xx, when the spec requires a 400 validation error for blank input.

---

## TC218 — S1-F9 lang=ar&minContracts=1 returns both users A and B

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/language?lang=ar&minContracts=1`

### What it tests

Lowering the threshold to minContracts=1 means both A (5 COMPLETED) and B (1 COMPLETED) qualify (both have at least 1). The result must contain at least 2 users. This complements TC216 by testing a lower threshold: if the comparison is > 1 instead of >= 1, B would be excluded.

### Steps

```
1) Seed A and B with lang=ar and 1 COMPLETED each.
2) GET with minContracts=1, assert 2xx, assert list.size() >= 2.
```

### Pass Criteria

- **list.size() >= 2**
  - *Bug it catches:* Strict greater-than comparison (> 1) excludes users with exactly 1 COMPLETED contract, when the spec requires >= minContracts.

---

## TC219 — S1-F9 minContracts counts only COMPLETED contracts (ACTIVE/TERMINATED ignored)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/preferences/language?lang=ar&minContracts=2`

### What it tests

The minContracts count must include only COMPLETED contracts, not ACTIVE or TERMINATED. User A (lang=ar) has 1 COMPLETED, 1 ACTIVE, 1 TERMINATED contract. The total row count is 3 but only 1 is COMPLETED, so A must NOT appear when minContracts=2. Catches: COUNT(*) used instead of COUNT WHERE status='COMPLETED'.

### Steps

```
1) Seed user A (lang=ar), job, 3 contracts with different statuses.
2) GET with lang=ar&minContracts=2, assert 2xx.
3) Assert A's id does NOT appear in the list.
```

### Pass Criteria

- **A absent from results**
  - *Bug it catches:* COUNT(*) counts all contracts regardless of status — user A has 3 total so passes the threshold of 2, but should only count 1 COMPLETED.

---

## TC220 — S1-F8 profile DTO surfaces preferences JSONB content

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/users/{uid}/profile`

### What it tests

The S1-F8 profile endpoint must include the user's preferences JSONB object in the response DTO, with the correct key-value pairs accessible. User's prefs {"language":"ar","theme":"dark"} are set; the profile response must contain a preferences object with language=="ar". This confirms the profile DTO is not stripping the preferences field when serializing the User entity.

### Steps

```
1) Seed user, set prefs via _FmM1Seed.setPrefs.
2) GET /api/users/{uid}/profile, assert 2xx.
3) Assert preferences object present with language == "ar".
```

### Pass Criteria

- **preferences object present**
  - *Bug it catches:* Profile DTO excludes the preferences field via @JsonIgnore or omits it from the record projection — clients cannot access user settings from the profile endpoint.
- **preferences.language == "ar"**
  - *Bug it catches:* Preferences are returned as an empty object or with wrong keys — the JSONB-to-Map deserialization is broken, returning {} instead of the stored content.

---

# S2 — Job Service

## M2 Features — TC35–TC53

## TC035 — GET /api/jobs/search/full-text?query=test with valid token returns 2xx + array shape

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/search/full-text?query=test`

### What it tests

The S2-F10 full-text search endpoint must accept an authenticated request with a generic query term and respond with 2xx plus a body that is either a plain JSON array or a paginated envelope containing a content array. This is the smoke test that the Elasticsearch-backed search endpoint is wired and reachable — any non-2xx reveals a routing gap, a missing security config entry, or a broken ES client connection. The dual-shape check (array OR paginated envelope) accommodates two valid implementation patterns: returning a raw list or wrapping it in a Spring Page object. A strict assertTrue with the actual body in the failure message ensures the student sees exactly what was returned. This test catches the most common student bug: the search controller is implemented but not mapped to the correct path /api/jobs/search/full-text.

### Steps

```
1) Call adminToken() to obtain a valid admin Bearer JWT.
2) Construct the search path: /api/jobs/search/full-text?query=test.
3) GET that path with the admin token via httpGetAuth.
4) Assert 2xx via assert2xx.
5) Parse the body with parseNode and assert body.isArray() OR (body.has("content") AND body.get("content").isArray()).
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Search endpoint not wired (404), or the security config's requestMatchers pattern does not include /api/jobs/search/** and the filter returns 401, or the Elasticsearch client is misconfigured causing 5xx.
- **Body is array OR paginated envelope with content array**
  - *Bug it catches:* Controller returns a non-array body (e.g., an error message string, a single object, or null) — means the search query ran but the serialization mapping is wrong.

---

## TC036 — GET /api/jobs/search/full-text without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/search/full-text?query=anything`

### What it tests

The full-text search endpoint (S2-F10) must require authentication. An unauthenticated request must be rejected with strictly 401 — not 2xx (search exposed publicly), not 5xx (auth filter crashed), not 403 (misclassification of anonymous as "authenticated-but-denied"). This is the security gate check for the search endpoint, pairing with TC35's happy path to confirm the protection is real. Strict 401 is required because the filter must intercept the request before any ES query is executed.

### Steps

```
1) Construct the search path /api/jobs/search/full-text?query=anything.
2) GET that path via httpGet (no Authorization header).
3) Read the status code.
4) Assert NOT 2xx, NOT 5xx, and strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Search endpoint is accessible without a token — either .permitAll() was accidentally applied to /api/jobs/search/** in the security config, or the filter is not registered for this route.
- **NOT 2xx**
  - *Bug it catches:* Anonymous users can search the job catalog — data exposure without authentication.
- **NOT 5xx**
  - *Bug it catches:* Auth filter NPE'd on the missing Authorization header instead of cleanly returning 401.

---

## TC037 — Search ?= returns only entities with that filter value

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup ×2), GET /api/jobs/search/full-text?<filterParam>=<filterValue0>`

### What it tests

The S2-F10 search endpoint must support filtering by the entity's primary categorical field (resolved dynamically from the manifest via s2CategoricalFilterParam()) and return only items matching that filter value. Two entities are seeded with different values of the categorical filter, then a search for the first value is issued; every result item must carry the expected filter value. The per-item assertion loop catches implementations that return all items regardless of the filter (i.e., the filter parameter is parsed but not applied to the ES query). This tests the Elasticsearch query construction — specifically that the categorical field is mapped as a term filter (keyword), not a match query (which would be analysed and might match both values).

### Steps

```
1) Call adminToken().
2) Resolve s2CategoricalFilterParam() and enumValueAt() to get two distinct filter values.
3) Create entity A via POST /api/jobs with filterValue0 and statusOpen, assert 2xx.
4) Create entity B via POST /api/jobs with filterValue1 and statusOpen, assert 2xx.
5) GET /api/jobs/search/full-text?<filterParam>=<filterValue0>, assert 2xx, unwrap the response to an array via unwrap, and assert every item has the categorical field equal to filterValue0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Filter parameter is not recognized by the search endpoint (404 on unrecognised query param, or 5xx from an ES query parse error).
- **Every result item has <filterParam> = filterValue0**
  - *Bug it catches:* The categorical filter is ignored and all indexed items are returned; or the filter is applied as a full-text match query instead of a term filter, causing cross-value matches.

---

## TC038 — Search ?status= returns only entities with that status

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup ×2), GET /api/jobs/search/full-text?status=<status0>`

### What it tests

The S2-F10 search endpoint must filter by the status enum field, which is a separate filter parameter from the primary categorical filter tested in TC37. Two entities are created with different statuses; searching by status0 must return only the entity with that status. Per-item assertion catches implementations where the status filter is accepted in the URL but not wired into the ES query. The status field is a keyword-mapped enum in Elasticsearch, so a term filter is required rather than match.

### Steps

```
1) Call adminToken(), resolve enumValueAt(entity, "status", 0) and status1 = enumValueAt(entity, "status", 1).
2) Create entity A with status0 via POST /api/jobs, assert 2xx.
3) Create entity B with status1 via POST /api/jobs, assert 2xx.
4) GET /api/jobs/search/full-text?status=<status0>, assert 2xx, unwrap to array, assert each item has status = status0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* status is not a recognized filter parameter (404 or 5xx), or the filter field is misspelled in the ES mapping so the term query returns no results and the test would fail on item count.
- **Every result has status = status0**
  - *Bug it catches:* Status filter is present in the URL but not applied to the ES query — all items regardless of status are returned, including the status1 entity.

---

## TC039 — Search ?minRating=4.0&maxRating=5.0 returns only entities with rating in [4.0, 5.0]

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup ×3), POST /api/jobs/{id}/index (reindex ×3), GET /api/jobs/search/full-text?minRating=4.0&maxRating=5.0`

### What it tests

The S2-F10 search endpoint must support minRating/maxRating range filters mapped to Elasticsearch range queries on the rating field. Three entities are created with ratings 3.0, 4.5, and 5.0; only the latter two should appear in the [4.0, 5.0] range. JDBC is used to set the numeric rating directly on the DB row (bypassing any API validation), then /index is called to push the updated value to ES. The per-item assertion loop catches range boundaries: an entity with rating 5.0 must be included (inclusive upper bound), and 3.0 must be excluded. This catches ES mapping bugs where rating is indexed as a keyword instead of a numeric type, making range queries return empty.

### Steps

```
1) Create entities TC39 Low (rating 3.0), TC39 Mid (rating 4.5), TC39 High (rating 5.0) via POST /api/jobs.
2) JDBC UPDATE each entity's rating column to the target value.
3) Call POST /api/jobs/{id}/index for each entity to push the updated rating to ES.
4) GET /api/jobs/search/full-text?minRating=4.0&maxRating=5.0, assert 2xx, unwrap to array.
5) For each item in the result that has a non-null rating, assert rating >= 4.0 && rating <= 5.0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* minRating/maxRating parameters are not recognized or cause an ES query parse exception (5xx).
- **Every result item has rating in [4.0, 5.0]**
  - *Bug it catches:* Range query is not applied — all items (including rating 3.0) are returned; or rating is indexed as a keyword making numeric range queries ineffective.

---

## TC040 — Search ?minRating=5.0&maxRating=3.0 (invalid range) returns 4xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/search/full-text?minRating=5.0&maxRating=3.0`

### What it tests

When minRating is greater than maxRating the range is semantically invalid and the endpoint must reject it with a 4xx. Strict NOT 5xx (must not crash), NOT 2xx (must not return results for an impossible range). This catches implementations that pass the inverted range to Elasticsearch without validation — ES may handle it silently by returning empty results (which would erroneously appear as 2xx success), or it may throw an exception that propagates as 5xx.

### Steps

```
1) Call adminToken().
2) GET /api/jobs/search/full-text?minRating=5.0&maxRating=3.0 with the admin token.
3) Read the status code and assert NOT 5xx, NOT 2xx, and in range 400..499.
```

### Pass Criteria

- **NOT 5xx**
  - *Bug it catches:* Inverted range is forwarded to ES without validation and ES throws a query parse exception that propagates uncaught.
- **NOT 2xx**
  - *Bug it catches:* The inverted range is silently ignored or ES returns empty results with 200 — the spec requires the server to reject invalid input, not silently succeed.
- **Status 4xx**
  - *Bug it catches:* Controller does not validate minRating <= maxRating before issuing the ES query — any input accepted unconditionally.

---

## TC041 — Search with query that matches nothing returns 2xx + empty list

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/search/full-text?query=<improbable-nonce>`

### What it tests

A full-text query that matches no indexed documents must return 2xx with an empty array (or a paginated envelope with an empty content array and size=0). This confirms that the search endpoint handles the no-results case gracefully rather than returning 404 (misclassifying "no results" as "not found") or 5xx (ES query failing on zero hits). The nonce-suffixed query string (TC41NoMatchQuery_<nonce>_xyzqwe) is designed to be improbable enough that no seed data could match it.

### Steps

```
1) Call adminToken().
2) Construct an improbable query string: TC41NoMatchQuery_<nonce()>_xyzqwe.
3) GET /api/jobs/search/full-text?query=<improbableQuery>, assert 2xx.
4) Parse the body, unwrap to array (direct array or content field).
5) Assert the array is present and size == 0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 when no results are found — conflating "no hits from search" with "resource not found".
- **Response is an array**
  - *Bug it catches:* Body is not an array-shaped structure — controller returned a non-iterable body for the no-results case.
- **Array size == 0**
  - *Bug it catches:* Controller returned partial results or did not apply the query, meaning some items leaked through even though the query should match nothing.

---

## TC042 — Search results sorted by relevance (name match ranks higher than description match)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup ×2), POST /api/jobs/{id}/index (reindex ×2), GET /api/jobs/search/full-text?query=<unique>`

### What it tests

The S2-F10 spec requires results to be sorted by relevance, meaning an entity whose name contains the search term must rank higher than one whose description contains the same term. Two entities are created: entity A has the unique token in its name, entity B has it only in the description. After indexing both, the search results must show A before B. If B does not appear at all (e.g., description is not indexed), the test only checks that A is present. This catches the common ES mapping bug where all fields are given equal boost, or where the multi_match query lacks a name^2 boost, giving description-only matches equal or higher relevance.

### Steps

```
1) Generate a unique token Tc42Word<nonce()>.
2) Create entity A with name = unique + " Kitchen" via POST /api/jobs, reindex via POST /api/jobs/{aid}/index.
3) Create entity B with a different name and description = "best authentic <unique> cuisine", reindex.
4) GET /api/jobs/search/full-text?query=<unique>, assert 2xx.
5) Locate A and B by their names in the result array. Assert A is present (idxA >= 0). If B is present, assert idxA < idxB (A ranks higher).
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Search endpoint fails when both name and description contain the term, or the re-index step returned an error.
- **A appears in results**
  - *Bug it catches:* The name field is not indexed or not included in the multi_match query, so a name-only match is not found.
- **If B present: A's index < B's index (name match ranks first)**
  - *Bug it catches:* The multi_match query gives equal boost to name and description, or description has higher boost — a bug in the ES field mapping/query definition that violates the spec's relevance-ordering requirement.

---

## TC043 — POST /api/jobs/{id}/index for an existing entity returns 2xx

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup), POST /api/jobs/{id}/index`

### What it tests

The S2-F11 manual index endpoint must accept a POST request for an existing Job id and respond with 2xx, confirming that the entity was (re)indexed in Elasticsearch. This is the smoke test for the index endpoint — it does not verify the ES document contents (TC44 does that); it only verifies the endpoint is wired, accessible with a valid token, and returns a success status for a valid entity id. Catches: endpoint not mapped (404), auth filter rejecting admin token (401), ES client throwing on the index call (5xx), or the controller not returning a success status after a successful index operation.

### Steps

```
1) Call adminToken().
2) Create a Job via POST /api/jobs with a nonce-based name, assert 2xx and extract the id.
3) POST /api/jobs/<id>/index with an empty body and the admin token.
4) Assert 2xx via assert2xx.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Index endpoint is not mapped (404), or the security config does not cover /api/jobs/{id}/index and the filter returns 401, or the Elasticsearch IndexRequest throws an unhandled exception (5xx).
- **(No body assertion in this test — body content is verified in TC44)**
  - *Bug it catches:* N/A — this is a status-only smoke test.

---

## TC044 — After indexing, ES doc fields match the PG row's attributes

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup), JDBC UPDATE rating, POST /api/jobs/{id}/index, GET /api/jobs/search/full-text?query=<unique>, JDBC SELECT name+status`

### What it tests

The S2-F11 index operation must push the current PostgreSQL row's attributes into the Elasticsearch document faithfully. A Job is created with a unique name and description, its rating is set via JDBC to 4.5 (bypassing the API), then /index is called. The test then verifies: (1) ES contains a document with the unique name via esSearchCount; (2) the search endpoint returns that document; (3) the name and status fields in the search result match the PG row. This catches implementations where only some fields are included in the ES document (e.g., rating is omitted from the index mapping, or status is not serialized).

### Steps

```
1) Create a Job with unique name and description="signature description", assert 2xx, extract id.
2) JDBC UPDATE the rating column to 4.5 for that entity.
3) POST /api/jobs/<id>/index, assert 2xx.
4) esSearchCount(s2SearchIndex(), "name", unique) — assert count >= 1 (document is in ES).
5) GET /api/jobs/search/full-text?query=<unique>, assert 2xx, find the item with the matching name.
6) JDBC SELECT name, status from the jobs table — assert they match the search result's name and status fields.
```

### Pass Criteria

- **ES count >= 1 for unique name**
  - *Bug it catches:* The index call returned 2xx but did not actually write to Elasticsearch — a fire-and-forget async call without awaiting completion, or the IndexRequest was built but never executed.
- **Search result found with matching unique name**
  - *Bug it catches:* The document was indexed with a different name field value or under the wrong index name (s2SearchIndex()).
- **name in search result matches PG row**
  - *Bug it catches:* The ES document's name field was set at creation time but not updated when the entity was modified — stale data in the index.
- **status in search result matches PG row (if present)**
  - *Bug it catches:* Status is omitted from the ES mapping or serialized as an integer instead of a string enum value.

---

## TC045 — Updating an entity via PUT (without /index) makes the new name searchable

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup), PUT /api/jobs/{id} (update name), GET /api/jobs/search/full-text?query=<newName>`

### What it tests

The S2-F11 spec requires that updating an entity automatically triggers a re-index, so the updated fields become searchable without requiring an explicit /index call. An entity is created with an original name, then its name is changed via PUT; immediately after, a search for the new name must return the entity. This catches the most common implementation shortcut: the index-on-update hook is missing (the service only calls repository.save() without also calling elasticsearchClient.index()), meaning the ES document stays stale with the old name.

### Steps

```
1) Create a Job with origName = "TC45 OriginalName_<nonce>", assert 2xx, extract id.
2) PUT /api/jobs/<id> with a body containing newName = "TC45_NewName_<nonce>", assert 2xx.
3) GET /api/jobs/search/full-text?query=<newName>, assert 2xx.
4) Unwrap the response to an array and search for an item with name == newName.
5) Assert found == true.
```

### Pass Criteria

- **PUT status 200..299**
  - *Bug it catches:* Update endpoint broke after the auto-reindex hook was added — transactional complexity in the service caused the save to fail.
- **Search by new name finds the entity**
  - *Bug it catches:* Auto-reindex-on-update is not implemented — the @Service only calls repository.save() without calling elasticsearchOperations.save() or elasticsearchClient.index() post-save. The most common M2 oversight for this spec feature.

---

## TC046 — POST /api/jobs/<Long.MAX_VALUE>/index returns strictly 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs/<Long.MAX_VALUE>/index`

### What it tests

The S2-F11 index endpoint must return strictly 404 when the requested entity id does not exist in PostgreSQL. Using Long.MAX_VALUE (9223372036854775807) as the id guarantees no real entity has that id. Strict 404 (NOT 2xx, NOT 5xx): NOT 2xx means the controller indexed a non-existent entity or silently succeeded without checking existence; NOT 5xx means Optional.get() or repository.findById() threw NoSuchElementException/NPE. This catches the most common student pattern: looking up the entity then calling orElseThrow(() -> new ResponseStatusException(NOT_FOUND)) — which is correct — versus orElseGet(() -> null) and then NPE-ing on the null entity during index construction (5xx).

### Steps

```
1) Call adminToken().
2) POST /api/jobs/<Long.MAX_VALUE>/index with an empty body and the admin token.
3) Read the status code and assert NOT 2xx, NOT 5xx, strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Any non-404 signals a mishandled not-found case.
- **NOT 2xx**
  - *Bug it catches:* Controller returned success without finding the entity — perhaps a default/null entity was indexed to ES, silently creating a garbage document.
- **NOT 5xx**
  - *Bug it catches:* repository.findById(Long.MAX_VALUE).get() threw NoSuchElementException uncaught, or the ES index call NPE'd on a null entity reference.

---

## TC047 — POST /api/jobs/{id}/index without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/jobs (setup), POST /api/jobs/{id}/index (no auth)`

### What it tests

The S2-F11 index endpoint must require authentication. An unauthenticated POST must return strictly 401. A real entity is created first so the id is valid — this ensures the 401 comes from the auth filter, not from a 404 on a non-existent id. This test pairs with TC43 (auth accepted) to confirm the security requirement is real.

### Steps

```
1) Call adminToken(), create a Job, assert 2xx, extract id.
2) POST /api/jobs/<id>/index via httpPost (no Authorization header).
3) Read the status code and assert NOT 2xx, strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Index endpoint is publicly accessible — anyone can trigger re-indexing without authentication, potentially causing ES to be flooded or stale documents to be written.
- **NOT 2xx**
  - *Bug it catches:* The security config has .permitAll() on POST /api/jobs/** or the filter is not registered for this path.

---

## TC048 — GET /api/jobs/{id}/dashboard returns 2xx + DTO with totalOrders/totalRevenue

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/1/dashboard`

### What it tests

The S2-F12 dashboard endpoint must accept a valid admin token, aggregate proposal data for the given Job id, and return a DTO containing at least totalOrders (or total_orders) and totalRevenue (or total_revenue). The pre-seeded Job with id=1 is used — the seed manifest guarantees at least one proposal linked to it. The dual-name check (`totalOrders

### Steps

```
total_orders`) accommodates camelCase and snake_case serialization styles. This is the happy-path smoke test for the dashboard; aggregate correctness is verified in TC49. Catches: endpoint not mapped (404), missing DTO fields, or wrong JSON property names.
```

### Pass Criteria

  Admin token; pre-seeded Job id=1.

---

## TC049 — Dashboard totalOrders/totalRevenue match values aggregated from PG (uses pre-seed)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/1/dashboard (with admin), JDBC COUNT + SUM on proposals table`

### What it tests

The S2-F12 dashboard must compute totalOrders as the exact count of Proposals linked to the Job and totalRevenue as the exact sum of their totalAmount fields — matching the PostgreSQL aggregate directly. JDBC queries compute the expected values independently and the test asserts both match within a 0.01 tolerance for floating-point revenue. This catches incorrect aggregate SQL: SELECT COUNT(*) FROM proposals (missing WHERE clause), using wrong FK column, wrong amount column, or computing average instead of sum. The pre-seeded Job id=1 is used and its proposal data is available from the seed manifest.

### Steps

```
1) Call adminToken(), GET /api/jobs/1/dashboard, assert 2xx.
2) JDBC: SELECT COUNT(*) FROM <proposalsTable> WHERE <fkCol>=1 → expectedCount.
3) JDBC: SELECT COALESCE(SUM(<amtCol>), 0) FROM <proposalsTable> WHERE <fkCol>=1 → expectedRevenue.
4) Read totalOrders/total_orders and totalRevenue/total_revenue from the response JSON.
5) assertEquals(expectedCount, actualCount) and assertEquals(expectedRevenue, actualRevenue, 0.01).
```

### Pass Criteria

- **totalOrders == JDBC COUNT**
  - *Bug it catches:* Dashboard counts all proposals in the DB (missing WHERE clause), or uses a stale cache value that was not invalidated after the test's seed truncation.
- **totalRevenue == JDBC SUM (within 0.01)**
  - *Bug it catches:* Revenue is computed as average instead of sum, or the wrong column is summed (e.g., bidAmount instead of totalAmount), or the amount is not included in the query at all and defaults to 0.

---

## TC050 — After GET /dashboard, an event must appear in the spec-defined Mongo collection

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/1/dashboard (with admin), MongoDB count on s2EventsCollection()`

### What it tests

The S2-F12 spec requires that every dashboard view be logged to MongoDB. The test captures the document count in s2EventsCollection() before calling the dashboard, calls the dashboard, then asserts the count increased. This confirms that the event logging is wired in the service layer and that the MongoDB connection is functional. Catches: event logging omitted entirely, wrong collection name, MongoDB write wrapped in a try-catch that swallows failures silently (count does not increase but no error), or async event write that fires after the HTTP response is sent (timing issue — the test asserts synchronously).

### Steps

```
1) Assert mongo != null — fail with a clear message if MongoDB is unreachable.
2) Call adminToken().
3) Read before = mongo.getCollection(s2EventsCollection()).countDocuments().
4) GET /api/jobs/1/dashboard, assert 2xx.
5) Read after = mongo.getCollection(s2EventsCollection()).countDocuments().
6) Assert after > before.
```

### Pass Criteria

- **after > before (at least one event logged)**
  - *Bug it catches:* Dashboard event logging is not implemented; or the service writes to the wrong MongoDB collection (count in the correct collection stays unchanged); or the Mongo write is in a try-catch {} that silently swallows the exception.

---

## TC051 — GET /api/jobs/<Long.MAX_VALUE>/dashboard returns strictly 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/<Long.MAX_VALUE>/dashboard`

### What it tests

The dashboard endpoint must return strictly 404 for a non-existent Job id. Long.MAX_VALUE guarantees the id does not exist. NOT 2xx (must not return a dashboard for a ghost entity), NOT 5xx (must not NPE on missing entity). This catches the same Optional.get() pattern bug as TC46, applied to the dashboard path.

### Steps

```
1) Call adminToken().
2) GET /api/jobs/<Long.MAX_VALUE>/dashboard, assert NOT 2xx, NOT 5xx, strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller returned a dashboard with zero values for a non-existent entity (2xx), or findById().get() threw NoSuchElementException (5xx).

---

## TC052 — Dashboard for an entity with no proposals returns 2xx + totalOrders=0 + totalRevenue=0

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC DELETE from proposals table (cleanup), GET /api/jobs/3/dashboard`

### What it tests

When a Job has no associated Proposals, the dashboard must return 2xx with totalOrders=0 and totalRevenue=0.0 (not null, not omitted, not 404). Pre-seeded Job id=3 is used; JDBC defensively deletes any proposals linked to id=3 to ensure a clean baseline. This catches implementations where SUM of an empty set returns SQL NULL — which then appears in the DTO as null or causes a NullPointerException (5xx) instead of being handled via COALESCE(SUM(...), 0).

### Steps

```
1) Call adminToken().
2) JDBC: DELETE FROM <proposalsTable> WHERE <fkCol>=3 to ensure zero proposals for id=3.
3) GET /api/jobs/3/dashboard, assert 2xx.
4) Read totalOrders and totalRevenue from the response.
5) Assert totalOrders == 0L and totalRevenue == 0.0 (within 0.01).
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 when no proposals exist — conflating "no data" with "entity not found".
- **totalOrders == 0**
  - *Bug it catches:* COUNT on empty set returned 1 (off-by-one), or an incorrect WHERE clause still matches some proposals.
- **totalRevenue == 0.0**
  - *Bug it catches:* SUM on empty set returned SQL NULL and the DTO serialised it as null — or a NPE was thrown during DTO construction. The fix is COALESCE(SUM(amount), 0).

---

## TC053 — GET /api/jobs/{id}/dashboard without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/jobs/1/dashboard (no auth)`

### What it tests

The S2-F12 dashboard endpoint must require authentication. An unauthenticated GET must return strictly 401. Uses Job id=1 (pre-seeded) so the 401 comes from the auth filter, not from a 404 on a missing entity. Pairs with TC48 (auth accepted) to confirm the protection is real.

### Steps

```
1) GET /api/jobs/1/dashboard via httpGet (no Authorization header).
2) Read the status code and assert NOT 2xx, strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Dashboard endpoint is publicly accessible — anyone can retrieve job analytics without authentication.
- **NOT 2xx**
  - *Bug it catches:* The security config has .permitAll() for GET /api/jobs/** or the filter is not registered for this path.

---

## M1 Features — TC221–TC256

## TC221 — S2-F1 GET /api/jobs/search?status=OPEN&minBudget=100&maxBudget=2000 returns only OPEN jobs

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?status=OPEN&minBudget=100&maxBudget=2000`

### What it tests

The S2-F1 job search endpoint must filter by status and budget range simultaneously. Three jobs are seeded: J1 (OPEN, budget 500), J2 (CLOSED, budget 1000), J3 (OPEN, budget 1500). The query should return J1 and J3 but not J2 (wrong status). Every result item must have status=OPEN. This tests combined multi-filter behavior — if either the status or budget filter is missing, wrong items appear.

### Steps

```
1) Seed J1 (OPEN, 50-500), J2 (CLOSED, 100-1000), J3 (OPEN, 200-1500).
2) GET /api/jobs/search?status=OPEN&minBudget=100&maxBudget=2000, assert 2xx.
3) For every item in list, assert status == "OPEN".
```

### Pass Criteria

- **Every result has status=OPEN**
  - *Bug it catches:* Status filter ignored — the CLOSED job J2 appears in results; or budget filter ignored — jobs outside [100,2000] appear.

---

## TC222 — S2-F1 job search by budget range minBudget=300&maxBudget=600 returns only 500-budget job

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?minBudget=300&maxBudget=600`

### What it tests

Three jobs are seeded at budget max 200, 500, 1000. The [300, 600] range must include only the 500-budget job. At least 1 result is expected. This tests that the budget filter applies to the budgetMax field and that both the lower and upper bound are respected.

### Steps

```
1) Seed J1 (max=200), J2 (max=500), J3 (max=1000).
2) GET /api/jobs/search?minBudget=300&maxBudget=600, assert 2xx.
3) Assert list.size() >= 1.
```

### Pass Criteria

- **list.size() >= 1**
  - *Bug it catches:* Budget filter is applied to budgetMin instead of budgetMax, or the range comparison uses strict inequality excluding boundary values.

---

## TC223 — S2-F1 job search with minBudget > maxBudget returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?minBudget=2000&maxBudget=100`

### What it tests

An inverted budget range must be rejected with strictly 400. Catches controllers that pass the inverted range to the SQL query (which silently returns empty results and 2xx), instead of validating the range.

### Steps

```
1) GET with minBudget=2000&maxBudget=100, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Inverted range passed to SQL BETWEEN — DB returns no results with 200, when the spec requires 400 for invalid input.

---

## TC224 — S2-F2 PUT /api/jobs/{jid}/requirements merges JSONB: experience preserved, languages updated, certifications added

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/requirements`

### What it tests

The S2-F2 requirements update endpoint must perform JSONB merge semantics (same as S1-F2 for user preferences). Initial requirements: {"experience":"5","languages":["en"]}; PUT body: {"languages":["en","ar"],"certifications":["AWS"]}. Expected result: experience preserved as "5", languages updated to 2-element array, certifications added with ["AWS"]. This tests that the JSONB `

### Steps

```
` merge operator is used rather than full overwrite.
```

### Pass Criteria

  Admin token; job with initial requirements set via _FmM1Seed.setJobRequirements.

---

## TC225 — S2-F2 same-key requirements overwrite

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/requirements`

### What it tests

Existing key experience:"5" must be overwritten to experience:"10" when the PUT body provides the same key with a new value. Complements TC224 (new key addition) by confirming new-value-wins for existing keys.

### Steps

```
1) Seed job, set requirements {"experience":"5"}.
2) PUT requirements with body {"experience":"10"}, assert 2xx.
3) Assert experience == "10" in response.
```

### Pass Criteria

- **experience == "10"**
  - *Bug it catches:* Merge operator guards against overwriting existing keys (wrong — merge should always allow new-value-wins for duplicate keys).

---

## TC226 — S2-F2 requirements update for non-existent job returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/999999/requirements`

### What it tests

A requirements update for a non-existent job must return strictly 404.

### Steps

```
1) PUT /api/jobs/999999/requirements with body {"x":"y"}, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller proceeds to update a non-existent row — JDBC UPDATE affects 0 rows and controller returns 2xx as a no-op instead of 404.

---

## TC227 — S2-F3 GET /api/jobs/{jid}/proposal-summary returns correct aggregates (5 proposals, avg=120, low=80, high=200)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/{jid}/proposal-summary?startDate=2026-04-01&endDate=2026-04-30`

### What it tests

The S2-F3 proposal summary endpoint must aggregate all proposals on a job within a date range: totalProposals, averageBidAmount, lowestBid, highestBid. Five SUBMITTED proposals are seeded with bids 80, 100, 120, 100, 200. Expected: total=5, avg=120, low=80, high=200. This tests that the correct aggregate SQL functions are used (COUNT, AVG, MIN, MAX) and that the date range filter is applied. Floating point tolerance of ±0.5 accommodates rounding.

### Steps

```
1) Seed job → jid, seed 5 proposals with bids 80, 100, 120, 100, 200 all in April 2026.
2) GET proposal-summary with April date range, assert 2xx.
3) Assert totalProposals==5, averageBidAmount==120±0.5, lowestBid==80±0.5, highestBid==200±0.5.
```

### Pass Criteria

- **totalProposals == 5**
  - *Bug it catches:* COUNT does not include all statuses or is filtered to ACCEPTED only.
- **averageBidAmount == 120**
  - *Bug it catches:* AVG computed incorrectly — perhaps DISTINCT bids, or wrong denominator.
- **lowestBid == 80 / highestBid == 200**
  - *Bug it catches:* MIN/MAX swapped or computed from wrong field.

---

## TC228 — S2-F3 proposal summary for job with no proposals returns zeros

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/{jid}/proposal-summary?startDate=2026-04-01&endDate=2026-04-30`

### What it tests

A job with no proposals must return totalProposals=0, not 404. Confirms the zero-result case is handled — SQL aggregates on empty sets return NULL which must be null-safely mapped to 0.

### Steps

```
1) Seed job → jid (no proposals).
2) GET proposal-summary, assert 2xx, assert totalProposals == 0.
```

### Pass Criteria

- **totalProposals == 0**
  - *Bug it catches:* Controller returns 404 for empty results, or COUNT(*) on empty set returns null causing NPE.

---

## TC229 — S2-F3 proposal summary for non-existent job returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/999999/proposal-summary?startDate=2026-04-01&endDate=2026-04-30`

### What it tests

A non-existent job must return strictly 404 for the proposal summary. Distinct from TC228 (existing job, no proposals → 2xx zeros).

### Steps

```
1) GET proposal-summary for jid=999999, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller runs aggregation for job 999999 and returns 2xx with zeros instead of checking job existence first.

---

## TC230 — S2-F4 PUT /api/jobs/{jid}/close sets status=CLOSED and rejects all SUBMITTED proposals

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/close`

### What it tests

The S2-F4 close-job endpoint must (a) set the job's status to CLOSED and (b) reject all SUBMITTED proposals on that job. Both are verified via JDBC after the 2xx response. The cascade rejection is the critical business rule — without it, freelancers may have proposals they think are pending even though the job is closed.

### Steps

```
1) Seed OPEN job → jid, seed SUBMITTED proposal → propId.
2) PUT /api/jobs/{jid}/close, assert 2xx.
3) JDBC: job status == "CLOSED".
4) JDBC: proposal status == "REJECTED".
```

### Pass Criteria

- **Job status = CLOSED in DB**
  - *Bug it catches:* Close endpoint returns 2xx but doesn't update the status field.
- **Proposal status = REJECTED in DB**
  - *Bug it catches:* Close endpoint closes the job but doesn't cascade the rejection to pending proposals — freelancers remain in limbo with open proposals on a closed job.

---

## TC231 — S2-F4 close job with ACTIVE contract returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/close`

### What it tests

A job with an ACTIVE contract cannot be closed (there is active work in progress). The endpoint must return strictly 400. An ACTIVE contract is seeded against the job.

### Steps

```
1) Seed OPEN job, seed ACTIVE contract referencing it.
2) PUT /api/jobs/{jid}/close, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Job closed despite having an active contract — contract is now orphaned with no parent job in an active state, causing inconsistent cross-service data.

---

## TC232 — S2-F4 close non-existent job returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/999999/close`

### What it tests

Closing a non-existent job must return strictly 404.

### Steps

```
1) PUT /api/jobs/999999/close, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* JDBC UPDATE affects 0 rows and controller returns 2xx as a no-op, or NPE (5xx) from findById(999999).get().

---

## TC233 — S2-F5 GET /api/jobs/requirements/search?key=experience&value=5 returns matching jobs

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/requirements/search?key=experience&value=5`

### What it tests

The S2-F5 JSONB requirements search on jobs must find jobs whose requirements JSONB contains the specified key-value pair. Two jobs are seeded: J1 with {"experience":"5"} and J2 with {"experience":"10"}. Searching for key=experience,value=5 must return at least 1 result (J1). Same JSONB extraction pattern as S1-F5 for users but applied to the Job entity's requirements field.

### Steps

```
1) Seed J1, J2, set requirements.
2) GET /api/jobs/requirements/search?key=experience&value=5, assert 2xx.
3) Assert list.size() >= 1.
```

### Pass Criteria

- **list.size() >= 1**
  - *Bug it catches:* JSONB key extraction on requirements field is wrong — the query returns 0 because the field name or JSONB path is misspelled.

---

## TC234 — S2-F6 GET /api/jobs/reports/top-budget ranks 5000-budget above 1000-budget

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/reports/top-budget?limit=10`

### What it tests

The S2-F6 top-budget jobs report must rank jobs by their budgetMax in descending order. J1 (budgetMax=1000) and J2 (budgetMax=5000) are seeded; J2 must appear first. The first result's id must equal J2's. Tests the ORDER BY budgetMax DESC clause.

### Steps

```
1) Seed JLow (max=1000) → j1, JHigh (max=5000) → j2.
2) GET top-budget with limit=10, assert 2xx.
3) Assert list.get(0).id == j2.
```

### Pass Criteria

- **list.get(0).id == j2**
  - *Bug it catches:* Sorted by budgetMin instead of budgetMax, or sorted ASC instead of DESC — j1 (lower max) ranks first.

---

## TC235 — S2-F6 limit parameter caps results

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/reports/top-budget?limit=2`

### What it tests

The limit parameter must cap the number of results returned. Five jobs are seeded; requesting limit=2 must return at most 2 items. Tests that the LIMIT clause is applied to the SQL query rather than fetched-all-then-sliced in memory (which would still cap correctly but is inefficient — however the test only checks the output count).

### Steps

```
1) Seed 5 jobs with increasing budgets.
2) GET top-budget with limit=2, assert 2xx.
3) Assert list.size() <= 2.
```

### Pass Criteria

- **list.size() <= 2**
  - *Bug it catches:* limit parameter is parsed but not applied to the query — all 5 (or more) jobs returned.

---

## TC236 — S2-F7 POST /api/jobs/{jid}/rate for COMPLETED contract returns 2xx

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/jobs/{jid}/rate`

### What it tests

The S2-F7 rate-job endpoint must accept a rating (1–5) for a job whose contract is COMPLETED. Body: {"contractId": cid, "rating": 5}. The happy path smoke test asserts only 2xx — no specific DB assertion is required for this basic test.

### Steps

```
1) Seed job → jid, seed COMPLETED contract → cid.
2) POST /api/jobs/{jid}/rate with {"contractId":cid,"rating":5}, assert 2xx.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Rating endpoint not wired (404), auth guard missing (401), or COMPLETED contract lookup fails (5xx).

---

## TC237 — S2-F7 rating > 5 returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/jobs/{jid}/rate`

### What it tests

A rating value of 6 (out of range) must be rejected with strictly 400. Tests @Max(5) or equivalent validation on the rating field.

### Steps

```
1) POST /api/jobs/{jid}/rate with rating=6, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Rating validation is missing — a rating of 6 is stored in the DB (out-of-range data), or the validation annotation is not processed because @Valid is absent from the controller parameter.

---

## TC238 — S2-F7 rating an ACTIVE contract returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/jobs/{jid}/rate`

### What it tests

Only COMPLETED contracts can be rated. Providing an ACTIVE contract id must return strictly 400. Tests the business rule that a job can only be rated after completion.

### Steps

```
1) Seed job → jid, seed ACTIVE contract → cid.
2) POST /api/jobs/{jid}/rate with rating=4, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Status check is absent — rating is stored for an in-progress contract, allowing clients to rate work before it is complete.

---

## TC239 — S2-F7 rating with non-existent contract returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/jobs/{jid}/rate`

### What it tests

A rating request referencing a non-existent contractId must return strictly 404.

### Steps

```
1) Seed job → jid.
2) POST /api/jobs/{jid}/rate with contractId=999999,rating=4, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Contract lookup not performed — controller returns 2xx as a no-op for a missing contract, or NPE (5xx) from findById(999999).get().

---

## TC240 — S2-F8 PUT /api/jobs/{jid}/attachments/{aid}/verify sets verified=true and verifiedAt/verifiedBy in metadata

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/attachments/{aid}/verify`

### What it tests

The S2-F8 attachment verification endpoint must set verified=true on the attachment. Body: {"verifiedBy": adminId} where adminId is an ADMIN-role user. The JDBC check verifies the verified column is actually set to true in the database — not just returned in the response. An unexpired attachment and an ADMIN-role verifier are seeded.

### Steps

```
1) Seed job → jid, seed unexpired attachment → aid, seed ADMIN user → adminId.
2) PUT /api/jobs/{jid}/attachments/{aid}/verify with {"verifiedBy":adminId}.
3) Assert 2xx.
4) JDBC: SELECT verified for attachment aid, assert == true.
```

### Pass Criteria

- **verified == true in DB**
  - *Bug it catches:* Controller returns 2xx but doesn't actually update the verified column — the response might show verified:true from the in-memory object but the DB row is unchanged.

---

## TC241 — S2-F8 non-ADMIN verifier returns 403

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/attachments/{aid}/verify`

### What it tests

If verifiedBy references a FREELANCER (non-ADMIN) user, the endpoint must return strictly 403. Only ADMIN-role users are permitted to verify attachments. This tests the role check on the verifiedBy field — not the JWT role but the role of the referenced user in the DB.

### Steps

```
1) Seed job → jid, seed unexpired attachment → aid, seed FREELANCER user → freelancerId.
2) PUT verify with {"verifiedBy":freelancerId}, assert status == 403.
```

### Pass Criteria

- **Status strictly 403**
  - *Bug it catches:* The role of the verifiedBy user is not checked — any user id can verify attachments, bypassing the admin-only requirement.

---

## TC242 — S2-F8 expired attachment returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/attachments/{aid}/verify`

### What it tests

An expired attachment (expiresAt in the past, set via _FmM1Seed.pastDate()) cannot be verified. Must return strictly 400. Catches endpoints that verify attachments without checking their expiry.

### Steps

```
1) Seed job → jid, seed past-dated attachment → aid, seed ADMIN user → adminId.
2) PUT verify, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Expiry check is absent — expired attachments can be verified and mislead clients into thinking old documents are current and valid.

---

## TC243 — S2-F8 attachment belonging to different job returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{j1}/attachments/{aid}/verify`

### What it tests

Attachment aid belongs to job J2, but the request uses J1's id in the path. Must return strictly 400 — the attachment-to-job relationship must be verified. Catches controllers that look up the attachment by id alone, ignoring the job scoping in the URL.

### Steps

```
1) Seed J1, J2, attachment aid under J2, ADMIN user adminId.
2) PUT /api/jobs/{j1}/attachments/{aid}/verify, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Attachment is verified without validating it belongs to the specified job — any attachment can be verified via any job's endpoint path.

---

## TC244 — S2-F9 GET /api/jobs/attachments/expired returns jobs with expired attachments

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/attachments/expired`

### What it tests

The S2-F9 expired-attachments alert must return jobs that have at least one attachment whose expiresAt is in the past. A job with two past-dated attachments is seeded. The list must contain at least 1 entry. Tests the SQL WHERE expiresAt < NOW() filter.

### Steps

```
1) Seed job → jid, seed 2 past-dated attachments via _FmM1Seed.pastDate().
2) GET /api/jobs/attachments/expired, assert 2xx.
3) Assert list.size() >= 1.
```

### Pass Criteria

- **list.size() >= 1**
  - *Bug it catches:* expiresAt < NOW() filter not applied — endpoint returns an empty list even though past-dated attachments exist.

---

## TC245 — S2-F9 future-expiry attachments are excluded from expired alert

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/attachments/expired`

### What it tests

Jobs whose attachments have a future expiresAt (set via _FmM1Seed.futureDate()) must NOT appear in the expired alert. The seeded job's id must not appear in any item of the response list.

### Steps

```
1) Seed job → jid, seed one future-dated attachment.
2) GET expired alert, assert 2xx.
3) Assert jid NOT in any result item's id.
```

### Pass Criteria

- **jid absent from results**
  - *Bug it catches:* Expiry filter is absent — ALL jobs with attachments (even future-expiry) appear in the alert, generating false positives.

---

## TC246 — S2-F9 expired attachments alert DTO shape includes jobId and expiredCount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/attachments/expired`

### What it tests

The response DTO for expired attachments must include jobId (or id) and expiredCount fields per spec. One past-dated attachment is seeded; the first list item is inspected for field presence. Tests DTO projection — a plain Job entity returned directly would lack expiredCount.

### Steps

```
1) Seed job → jid, seed past-dated attachment.
2) GET expired, assert 2xx, assert list.size() >= 1.
3) Assert first item has jobId or id AND expiredCount or expired_count.
```

### Pass Criteria

- **jobId present**
  - *Bug it catches:* DTO returns the raw Job entity without the alert-specific fields — expiredCount absent so clients cannot determine how many attachments need renewal.
- **expiredCount present**
  - *Bug it catches:* DTO projection is incomplete — the alert endpoint returns a list of job ids without the count of expired attachments per job.

---

## TC247 — S2-F1 search by title partial match returns matching jobs

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?title=Web`

### What it tests

Partial title match: searching for "Web" must return jobs whose title contains "Web" (e.g., "Web Frontend" and "Web Backend"). One "Mobile App" job seeded must not appear. At least 2 Web-titled jobs must match. Tests LIKE/ILIKE or Spring Data ContainingIgnoreCase on the title field.

### Steps

```
1) Seed 3 jobs.
2) GET /api/jobs/search?title=Web, assert 2xx.
3) Count items with title containing "Web", assert count >= 2.
```

### Pass Criteria

- **count >= 2**
  - *Bug it catches:* Title filter uses exact match — only "Web" literal matches; or filter is applied to a different field.

---

## TC248 — S2-F3 proposal summary with startDate > endDate returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/{jid}/proposal-summary?startDate=2026-04-30&endDate=2026-04-01`

### What it tests

An inverted date range for the proposal summary must be rejected with 400. Catches controllers that pass the inverted range to SQL (silently returns empty results as 2xx).

### Steps

```
1) GET proposal-summary with inverted range, assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Date range not validated — empty SQL result returned as 2xx with totalProposals=0 instead of a 400 error.

---

## TC249 — S2-F5 requirements search with blank key returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/requirements/search?key=&value=5`

### What it tests

Blank key must be rejected with 400 for the job requirements search. Same validation as S1-F5 (user preferences search) but applied to S2.

### Steps

```
1) GET with key= (empty), assert status == 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Empty key passed to the JSONB query — returns 2xx with empty results instead of rejecting the invalid input.

---

## TC250 — S2-F1 search by status=OPEN only returns OPEN jobs

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?status=OPEN`

### What it tests

Status-only filter (no budget): searching with only status=OPEN must return only OPEN jobs. An OPEN and a CLOSED job are seeded. Every result item must have status=="OPEN".

### Steps

```
1) Seed OpenJ (OPEN) and ClosedJ (CLOSED).
2) GET /api/jobs/search?status=OPEN, assert 2xx.
3) For every item, assert status == "OPEN".
```

### Pass Criteria

- **Every result has status=OPEN**
  - *Bug it catches:* Status filter not applied when no budget range is specified — all jobs returned regardless of status.

---

## TC251 — S2-F6 limit=0 for top-budget does not 5xx

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/reports/top-budget?limit=0`

### What it tests

A limit of 0 must not crash the server (5xx). The test only asserts NOT 5xx — a 4xx (rejected) or 2xx (empty list) are both acceptable. Catches LIMIT 0 in SQL or PageRequest.of(0, 0) causing exceptions.

### Steps

```
1) GET top-budget with limit=0, assert NOT 5xx.
```

### Pass Criteria

- **NOT 5xx**
  - *Bug it catches:* LIMIT 0 causes a SQL error, or PageRequest.of(page, 0) throws IllegalArgumentException propagated as 500.

---

## TC252 — S2-F8 verify non-existent attachment returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/attachments/999999/verify`

### What it tests

Verifying a non-existent attachment must return strictly 404.

### Steps

```
1) Seed job → jid, ADMIN user → adminId.
2) PUT /api/jobs/{jid}/attachments/999999/verify, assert status == 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Attachment lookup not performed — controller returns 2xx as a no-op or NPE (5xx).

---

## TC253 — S2-F9 expired alert returns empty list when no expired attachments

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/attachments/expired`

### What it tests

When no attachments are expired, the alert must return 2xx with an empty list. Distinct from TC245 (future-only job excluded) — here there are zero past-dated attachments at all.

### Steps

```
1) GET /api/jobs/attachments/expired, assert 2xx, assert list.size() == 0.
```

### Pass Criteria

- **list.size() == 0**
  - *Bug it catches:* Query returns all attachments regardless of expiry — when there are no jobs, it returns empty, but the filter was never actually applied.

---

## TC254 — S2-F4 close already-CLOSED job returns 400 or 2xx (idempotent)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/jobs/{jid}/close`

### What it tests

Closing an already-CLOSED job must return either 400 (not allowed) or 2xx (idempotent no-op). It must NOT 5xx. This tests the guard against double-close operations.

### Steps

```
1) Seed job with status=CLOSED → jid.
2) PUT /api/jobs/{jid}/close, assert code == 400 OR code/100 == 2.
```

### Pass Criteria

- **code == 400 or 2xx**
  - *Bug it catches:* Controller attempts to close an already-closed job and cascades re-rejection to proposals (double-rejection bug), or NPE (5xx) from checking active contracts on a CLOSED job.

---

## TC255 — S2-F1 search by category=DESIGN returns only DESIGN jobs

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/search?category=DESIGN`

### What it tests

Category filter: searching with category=DESIGN must return only jobs with category DESIGN. A WEB_DEV job and two DESIGN jobs are seeded. Every result item must have category=="DESIGN".

### Steps

```
1) Seed WJ (WEB_DEV), DJ1 (DESIGN), DJ2 (DESIGN).
2) GET /api/jobs/search?category=DESIGN, assert 2xx.
3) For every item, assert category == "DESIGN".
```

### Pass Criteria

- **Every result has category=DESIGN**
  - *Bug it catches:* Category filter ignored — WEB_DEV job appears in results; or filter is applied as a LIKE which might match unexpected categories.

---

## TC256 — S2-F6 top-budget DTO includes jobId, title, and budgetMax

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/jobs/reports/top-budget?limit=10`

### What it tests

The top-budget response DTO must include jobId (or id), title, and budgetMax (or budget_max) fields per spec. One job with one proposal is seeded; the first list item is inspected for field presence. Tests that the DTO is not a raw Job entity (which would lack aggregated fields) but a dedicated projection.

### Steps

```
1) Seed job → jid with budgetMax=1000, seed one proposal.
2) GET top-budget, assert 2xx, assert list.size() >= 1.
3) Assert first item has jobId/id, title, budgetMax/budget_max.
```

### Pass Criteria

- **jobId present**
  - *Bug it catches:* DTO uses internal entity field names or returns the entity directly without a projection.
- **budgetMax present**
  - *Bug it catches:* Projection omits budget — the most important field for the top-budget report.

---

# S3 — Proposal Service

## M2 Features — TC54–TC99

## TC054 — Dashboard returns totalProposals/acceptanceRate/averageBidAmount/averageEstimatedDays/proposalsByStatus

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 analytics dashboard endpoint must aggregate five metrics over a date range: totalProposals, acceptanceRate, averageBidAmount, averageEstimatedDays, and proposalsByStatus. Ten proposals are seeded via _FmM2.prop() (6 ACCEPTED, 2 REJECTED, 2 SUBMITTED) with known bid amounts and estimated days; the test asserts totalProposals=10 and acceptanceRate=0.6 (6/10). This is the compound happy-path test that validates the overall response shape and two specific computed values — the remaining three metrics are isolated in TC55–TC59. Catches: missing fields in the DTO, wrong acceptance rate formula (e.g., dividing by accepted count instead of total), or the date filter not applied (including proposals from other date ranges).

### Steps

```
1) Seed 10 proposals via _FmM2.prop() with known statuses, bid amounts, and estimated days, all dated in September 2026.
2) Call adminToken().
3) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
4) Parse the body, assert totalProposals == 10 via _FmM2.rL(j, "totalProposals", "total_proposals").
5) Assert acceptanceRate == 0.6 (within 0.01) via _FmM2.rD(j, "acceptanceRate", "acceptance_rate").
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Analytics endpoint not mapped (404), or aggregation query crashes (5xx), or date filter parameters are not recognized.
- **totalProposals == 10**
  - *Bug it catches:* Count includes proposals outside the date range; or the date filter is applied as exclusive instead of inclusive; or the WHERE clause uses the wrong column.
- **acceptanceRate == 0.6**
  - *Bug it catches:* Rate computed as ACCEPTED/ACCEPTED (division by wrong denominator), or ACCEPTED/REJECTED (wrong denominator), or the rate field is omitted from the DTO entirely.

---

## TC055 — Dashboard.totalProposals equals exact count of proposals in range

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 totalProposals field must equal the exact count of proposals whose submittedAt date falls within the query range — no more, no less. Seven SUBMITTED proposals are seeded in September 2026 and the dashboard must report exactly 7. This isolates the count metric from the other metrics tested in TC54 to provide a pinpointed failure message when the count is wrong. Catches: date filter being applied as exclusive (count = 6 when startDate falls on the first proposal's date), or COUNT(*) using a cross-join (count inflated), or the query aggregating over all proposals regardless of date range.

### Steps

```
1) Seed 7 SUBMITTED proposals with submittedAt dates 2026-09-01 through 2026-09-07 via _FmM2.prop().
2) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
3) Assert _FmM2.rL(parseNode(body), "totalProposals", "total_proposals") == 7.
```

### Pass Criteria

- **totalProposals == 7**
  - *Bug it catches:* Date range filter is exclusive on the boundary (returns 6), or uses the wrong date column (createdAt vs submittedAt), or the WHERE clause is missing and all proposals in the DB are counted.

---

## TC056 — Dashboard.averageBidAmount = SUM(bidAmount)/COUNT

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 averageBidAmount field must equal the arithmetic mean of all bidAmount values in the date range. Three proposals are seeded with bid amounts 100, 200, and 300 — the expected average is 200.0. Asserts via _FmM2.rD() which accepts averageBidAmount, average_bid_amount, or avgBidAmount as valid field names. Catches: returning the sum instead of the average (SQL SUM without AVG), using integer division that truncates (e.g., (int)(total/count) returns 200 correctly here but would truncate non-even averages), or averaging only ACCEPTED bids when the spec averages all bids.

### Steps

```
1) Seed 3 SUBMITTED proposals with bidAmount 100.0, 200.0, 300.0 dated 2026-09-10/11/12 via _FmM2.prop().
2) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
3) Assert averageBidAmount == 200.0 within 0.01 via _FmM2.rD().
```

### Pass Criteria

- **averageBidAmount == 200.0 (within 0.01)**
  - *Bug it catches:* AVG was replaced with SUM (returns 600), or integer division truncated the mean, or the average is filtered to only ACCEPTED proposals (which returns -1 since all three are SUBMITTED).

---

## TC057 — Dashboard.averageEstimatedDays = SUM(estimatedDays)/COUNT

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 averageEstimatedDays field must equal the arithmetic mean of all estimatedDays values in the date range. Three proposals with 4, 8, and 12 days yield an expected average of 8.0. Tests the same arithmetic-mean pattern as TC56 but for a separate integer column, catching implementations that average one field but not the other. Also verifies the field naming via _FmM2.rD() accepting averageEstimatedDays, average_estimated_days, or avgEstimatedDays.

### Steps

```
1) Seed 3 SUBMITTED proposals with estimatedDays 4, 8, 12 dated 2026-09-10/11/12 via _FmM2.prop().
2) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
3) Assert averageEstimatedDays == 8.0 within 0.01 via _FmM2.rD().
```

### Pass Criteria

- **averageEstimatedDays == 8.0 (within 0.01)**
  - *Bug it catches:* Field omitted from the DTO (returns -1), or the wrong column is averaged (e.g., bidAmount column reused), or integer division on an int type causes truncation for non-even averages.

---

## TC058 — Dashboard.acceptanceRate = ACCEPTED / total

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 acceptanceRate must be computed as COUNT(ACCEPTED) / COUNT(*) (not COUNT(ACCEPTED) / COUNT(REJECTED), or just COUNT(ACCEPTED)). Eight proposals are seeded: 5 ACCEPTED and 3 REJECTED, giving rate = 5/8 = 0.625. The tolerance of 0.001 (tighter than the 0.01 used elsewhere) tests that the denominator is total proposals and the formula is a floating-point division. Catches: using COUNT(REJECTED) as the denominator (gives 5/3 = 1.667), returning integer 0 or 1 instead of a fraction, or computing count of accepted minus count of rejected instead of ratio.

### Steps

```
1) Seed 5 ACCEPTED and 3 REJECTED proposals in September 2026 via _FmM2.prop().
2) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
3) Assert acceptanceRate == 0.625 within 0.001 via _FmM2.rD(j, "acceptanceRate", "acceptance_rate").
```

### Pass Criteria

- **acceptanceRate == 0.625 (within 0.001)**
  - *Bug it catches:* Denominator is REJECTED count instead of total (returns 5/3 ≈ 1.67); or the rate is an integer rounded down to 0; or the field is not present in the DTO (returns -1).

---

## TC059 — Dashboard.proposalsByStatus has all 5 Proposal statuses, each count=1

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The S3-F10 proposalsByStatus field must be a map/object containing all 5 Proposal statuses (SUBMITTED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN) as keys, each with a count. One proposal of each status is seeded; every key must have value 1. This tests that the GROUP BY status query includes all possible enum values — not just the statuses that appear in the data (implementations using Map.of(row.status, row.count) from a SQL GROUP BY will miss statuses with zero occurrences, but with one of each, that's not an issue here). The per-key assertion gives a precise message identifying which specific status is missing or has the wrong count.

### Steps

```
1) Seed 5 proposals, one each with status SUBMITTED, SHORTLISTED, ACCEPTED, REJECTED, WITHDRAWN, dated 2026-09-10 through 2026-09-14 via _FmM2.prop().
2) GET /api/proposals/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30, assert 2xx.
3) Extract proposalsByStatus via _FmM2.rO(j, "proposalsByStatus", "proposals_by_status"), assert not null.
4) For each of the 5 statuses, assert bd.has(status) and bd.get(status).asLong() == 1.
```

### Pass Criteria

- **proposalsByStatus not null**
  - *Bug it catches:* Field omitted from the DTO or named differently (e.g., byStatus instead of proposalsByStatus).
- **Each of 5 status keys is present**
  - *Bug it catches:* The GROUP BY query returned only statuses present in data but the DTO wrapper did not fill in the missing statuses with zero.
- **Each count == 1**
  - *Bug it catches:* Duplicate rows in the aggregate (cross-join with another table), or the GROUP BY is on the wrong column.

---

## TC060 — Dashboard with no proposals in range returns totalProposals=0, averageBidAmount=0

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

A date range far in the future (year 2099) guarantees no proposals exist in that range. The dashboard must return 2xx with totalProposals=0 and averageBidAmount=0.0 (not null, not error, not 404). Catches: AVG returning SQL NULL for an empty set (which serializes as JSON null and causes asDouble() to return 0 — this would actually pass; the real bug is a NPE during DTO construction on the null Average), or the endpoint returning 404 for an empty result set.

### Steps

```
1) Call adminToken().
2) GET /api/proposals/analytics/dashboard?startDate=2099-01-01&endDate=2099-01-31, assert 2xx.
3) Assert totalProposals == 0 and averageBidAmount == 0.0 (within 0.01).
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 when no data exists in the range.
- **totalProposals == 0**
  - *Bug it catches:* Date filter is ignored and all proposals are counted (range-agnostic query).
- **averageBidAmount == 0.0**
  - *Bug it catches:* AVG returned SQL NULL and NPE was thrown during DTO construction instead of defaulting to 0.

---

## TC061 — Proposal at exactly startDate is included

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC UPDATE submittedAt to 2026-05-01 00:00:00, GET /api/proposals/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

The S3-F10 date range filter must be inclusive on the lower bound: a proposal with submittedAt = 2026-05-01 00:00:00 (exactly midnight on startDate) must be counted. The submittedAt timestamp is set via JDBC (not API) to guarantee precision, and then the dashboard is queried with startDate=2026-05-01. Asserts totalProposals == 1. Catches: WHERE submittedAt > :startDate (exclusive lower bound) instead of >= :startDate.

### Steps

```
1) Seed one proposal via _FmM2.prop(), capture its id.
2) JDBC UPDATE submittedAt to Timestamp.valueOf("2026-05-01 00:00:00") for that proposal.
3) GET /api/proposals/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31, assert 2xx.
4) Assert totalProposals == 1.
```

### Pass Criteria

- **totalProposals == 1**
  - *Bug it catches:* Lower bound is exclusive (WHERE submittedAt > :startDate), so a proposal at exactly midnight on startDate is excluded and totalProposals returns 0.

---

## TC062 — Proposal at exactly endDate is included

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC UPDATE submittedAt to 2026-05-31 23:59:59, GET /api/proposals/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

The S3-F10 date range filter must be inclusive on the upper bound: a proposal with submittedAt = 2026-05-31 23:59:59 (one second before midnight on endDate) must be counted. submittedAt is set via JDBC to that exact timestamp and the dashboard is queried with endDate=2026-05-31. Asserts totalProposals == 1. Catches: WHERE submittedAt < :endDate (exclusive upper bound) or WHERE submittedAt < :endDate + 1 day with off-by-one.

### Steps

```
1) Seed one proposal via _FmM2.prop(), capture its id.
2) JDBC UPDATE submittedAt to Timestamp.valueOf("2026-05-31 23:59:59").
3) GET /api/proposals/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31, assert 2xx.
4) Assert totalProposals == 1.
```

### Pass Criteria

- **totalProposals == 1**
  - *Bug it catches:* Upper bound is exclusive (WHERE submittedAt < :endDate), so the end-of-day proposal is excluded. The filter must use <= :endDate or convert endDate to endDate + 1 day - 1ms.

---

## TC063 — Dashboard with startDate > endDate returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-04-01&endDate=2026-03-01`

### What it tests

When startDate is after endDate the range is logically invalid and the endpoint must return strictly 400. The controller must validate the date range before executing the aggregation query. Catches: implementations that pass the inverted range to the JPQL/native SQL query which returns empty results with 200 instead of rejecting the request; also catches controllers that NPE or crash on inverted dates (5xx).

### Steps

```
1) Call adminToken().
2) GET /api/proposals/analytics/dashboard?startDate=2026-04-01&endDate=2026-03-01, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Controller does not validate startDate <= endDate and passes the inverted range to the query (returns 200 with empty result), or the inverted range causes a query exception (5xx).

---

## TC064 — Dashboard without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31 (no auth)`

### What it tests

The S3-F10 analytics dashboard must require authentication. An unauthenticated GET must return strictly 401. This confirms the security config covers the /api/proposals/analytics/** path pattern.

### Steps

```
1) GET /api/proposals/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31 via httpGet (no Authorization header).
2) Assert strictly 401 from r.statusCode().
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Analytics endpoint is publicly accessible — security config does not cover /api/proposals/analytics/**.

---

## TC065 — Dashboard with malformed JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31 with token xxx.yyy.zzz`

### What it tests

The S3-F10 analytics endpoint must reject a structurally-invalid JWT (three dot-separated non-base64 segments) with strictly 401. The token xxx.yyy.zzz is chosen because it has the three-segment shape of a JWT but the segments are not valid base64-encoded JSON — causing MalformedJwtException which must be caught and translated to 401. Catches: MalformedJwtException propagating uncaught as 5xx.

### Steps

```
1) GET /api/proposals/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31 via httpGetAuth(..., "xxx.yyy.zzz").
2) Assert strictly 401 from r.statusCode().
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException from JJWT propagated as 5xx because the filter doesn't catch parse exceptions; or the filter accepted a syntactically invalid token (2xx).

---

## TC066 — First dashboard call writes ANALYTICS_VIEWED to proposal_events

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31, MongoDB count on s3EventsCollection()`

### What it tests

The S3-F10 spec requires logging an ANALYTICS_VIEWED event to MongoDB on every dashboard call. The test counts documents in s3EventsCollection() with action="ANALYTICS_VIEWED" before and after the dashboard call, asserting the count increased by at least 1. This catches: event logging omitted, wrong collection name, action field spelled differently, or the write wrapped in a silent try-catch.

### Steps

```
1) Assert mongo != null.
2) Count before in mongo.getCollection(s3EventsCollection()) filtered by {action: "ANALYTICS_VIEWED"}.
3) Call adminToken(), GET dashboard URL, assert 2xx.
4) Count after, assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* ANALYTICS_VIEWED event is not logged (0 increase); or is logged to the wrong collection; or uses a different action field value (e.g., "DASHBOARD_VIEWED" instead of "ANALYTICS_VIEWED").

---

## TC067 — Second dashboard call (cache hit) still logs ANALYTICS_VIEWED

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two consecutive GET /api/proposals/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31 calls`

### What it tests

The ANALYTICS_VIEWED event must be logged on every dashboard call, including cache hits. The test calls the dashboard twice with the same parameters, counts the events after each call, and asserts the count increased after the second call too. This catches implementations where the event logging is inside the @Cacheable method body — when the cache returns without executing the method body, no event is logged. The fix requires logging the event outside (or after) the cache boundary.

### Steps

```
1) Call the dashboard URL twice with the same parameters via httpGetAuth.
2) After the first call, count after1 in the events collection filtered by {action: "ANALYTICS_VIEWED"}.
3) After the second call, count after2, assert after2 > after1.
```

### Pass Criteria

- **after2 > after1**
  - *Bug it catches:* Event logging is inside the @Cacheable method; on a cache hit the method body (and the log call) is bypassed entirely — the second call does not log an event.

---

## TC068 — First dashboard call populates Redis with TTL ≤ 600s

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/analytics/dashboard?startDate=2026-08-01&endDate=2026-08-31, redis.dbSize() before and after`

### What it tests

The S3-F10 caching requirement mandates that dashboard results are stored in Redis. The test measures redis.dbSize() before and after the first dashboard call for a fresh date range (August 2026 — unlikely to have a pre-existing cache entry) and asserts the size increased. This confirms that the @Cacheable annotation is wired to the Redis cache manager and that the cache key includes the date range.

### Steps

```
1) Assert redis != null.
2) Record before = redis.dbSize().
3) GET dashboard URL for August 2026, assert 2xx.
4) Record after = redis.dbSize(), assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* @Cacheable annotation is present but the Redis cache manager is not configured (Spring falls back to in-memory ConcurrentMapCache which doesn't show up in redis.dbSize()); or the cache name in @Cacheable doesn't match the configured Redis cache name.

---

## TC069 — Two identical dashboard requests return identical bodies (cached)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/proposals/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31 with same parameters`

### What it tests

The S3-F10 cache must return an identical body for repeated requests with the same parameters. Two consecutive calls are made and their bodies are compared with assertEquals. This is the functional cache correctness test — even without checking Redis directly, if the two bodies differ it means caching is not working (each call re-aggregates with a potentially different result due to concurrent test data, though in practice the bodies should match since no data changes between calls).

### Steps

```
1) GET dashboard URL twice with the same parameters via httpGetAuth, capturing r1.body() and r2.body().
2) Assert both are 2xx, then assertEquals(r1.body(), r2.body()).
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* The two bodies differ — implies caching is not wired and each call re-aggregates independently (non-deterministic due to floating-point and ordering), or the cache key includes a timestamp component that differs between calls.

---

## TC070 — Record interaction on SUBMITTED proposal creates PROPOSED_TO with proposalCount=1

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction, Neo4j _FmM2.proposalCount()`

### What it tests

The S3-F11 record-interaction endpoint must create a PROPOSED_TO relationship in Neo4j between the Freelancer node and the Job node with proposalCount=1 when first called on a SUBMITTED proposal. The test seeds a real user, a real Job, a SUBMITTED Proposal, then calls the endpoint and queries Neo4j directly via _FmM2.proposalCount() to read the proposalCount (or aliased field bookingCount/orderCount) from the relationship. Strict 2xx plus proposalCount == 1 in Neo4j. Catches: endpoint returning 2xx but not writing to Neo4j, or writing to the wrong relationship label, or setting proposalCount to 0 instead of 1 on first creation.

### Steps

```
1) seedAndLoginUser("tc70u") to get a real user with id uid.
2) _FmM2.job() to create a Job with id jid.
3) _FmM2.prop() to create a SUBMITTED Proposal pid linking uid → jid.
4) POST /api/proposals/<pid>/record-interaction with admin token, assert 2xx.
5) Call _FmM2.proposalCount(this, uid, jid) to read from Neo4j, assert count == 1.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Endpoint not mapped (404), or auth filter rejects admin token on this path (401), or Neo4j write throws and propagates as 5xx.
- **proposalCount == 1 in Neo4j**
  - *Bug it catches:* The endpoint returned 2xx but never wrote to Neo4j (fire-and-forget event that was dropped), or wrote the relationship with proposalCount=0, or used the wrong node/relationship labels.

---

## TC071 — Same proposalId recorded twice keeps proposalCount=1

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction twice (same pid), Neo4j _FmM2.proposalCount()`

### What it tests

The S3-F11 record-interaction endpoint must be idempotent: recording the same proposal a second time must not increment proposalCount beyond 1. Two consecutive calls are made with the same pid; both must return 2xx and the Neo4j proposalCount must still be 1 after the second call. This catches naive implementations that always CREATE a new relationship or always SET r.proposalCount = r.proposalCount + 1 without checking for an existing edge — which would produce proposalCount=2. The correct pattern is MERGE (a)-[r:PROPOSED_TO]->(b) ON CREATE SET r.proposalCount = 1.

### Steps

```
1) Set up user, Job, SUBMITTED Proposal as in TC70.
2) POST record-interaction for the same pid twice with admin token, both assert 2xx.
3) _FmM2.proposalCount(this, uid, jid), assert count == 1.
```

### Pass Criteria

- **Both POST calls return 2xx**
  - *Bug it catches:* Second call returned a non-2xx (e.g., 400 "already recorded" without idempotency spec requirement for this endpoint).
- **proposalCount == 1 after second call**
  - *Bug it catches:* proposalCount was incremented to 2 — the relationship is CREATEd unconditionally instead of MERGEd.

---

## TC072 — Two distinct SUBMITTED proposals same user→job → proposalCount=2

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{p1}/record-interaction, POST /api/proposals/{p2}/record-interaction, Neo4j count`

### What it tests

The S3-F11 spec states that recording a new/distinct proposal (different pid but same user→job pair) should increment proposalCount on the existing PROPOSED_TO edge. Two distinct proposals (p1 and p2) between the same user and job are recorded; the edge's proposalCount must be 2. This pairs with TC71 (same pid idempotent) to fully define the counting logic: same pid → no increment, different pid → increment. Catches: implementations that key idempotency on (user, job) instead of (proposalId), causing two distinct proposals to also be treated as duplicates and leaving count=1.

### Steps

```
1) Seed user A, Job J, Proposals p1 and p2 (distinct pids, both SUBMITTED) linking A → J.
2) POST record-interaction for p1 and p2 (in order) with admin token, both assert 2xx.
3) _FmM2.proposalCount(this, uid, jid), assert count == 2.
```

### Pass Criteria

- **proposalCount == 2**
  - *Bug it catches:* Idempotency key is (freelancerId, jobId) instead of proposalId — two distinct proposals by the same user for the same job are treated as duplicates, leaving count=1 instead of incrementing.

---

## TC073 — Recording interaction for a different job creates a new edge

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two POST /api/proposals/{px}/record-interaction (different jobs), Two Neo4j _FmM2.proposalCount() queries`

### What it tests

The S3-F11 Neo4j model requires a separate PROPOSED_TO edge for each (user, job) pair. Recording proposals for two different jobs (J1 and J2) by the same user must create two independent edges, each with proposalCount=1. This tests that the MERGE query keys on (freelancerId, jobId) as a pair — not just on freelancerId (which would incorrectly share a single edge).

### Steps

```
1) Seed user A, Jobs J1 and J2, Proposal p1 (A→J1) and p2 (A→J2).
2) POST record-interaction for p1 (A→J1) and p2 (A→J2), both assert 2xx.
3) _FmM2.proposalCount(this, uid, j1), assert == 1; _FmM2.proposalCount(this, uid, j2), assert == 1.
```

### Pass Criteria

- **Edge (A→J1) proposalCount == 1**
  - *Bug it catches:* The MERGE matched on freelancerId alone and merged both proposals into one edge.
- **Edge (A→J2) proposalCount == 1**
  - *Bug it catches:* Second recording reused the J1 edge (wrong jobId in MERGE key), inflating J1's count to 2 and leaving J2 with no edge.

---

## TC074 — Recording an ACCEPTED proposal returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction on an ACCEPTED Proposal`

### What it tests

The S3-F11 spec restricts record-interaction to proposals with status SUBMITTED (or SHORTLISTED, tested in TC84). An ACCEPTED proposal must return strictly 400. This catches implementations that check for SUBMITTED only and forward ACCEPTED through (writing a Neo4j edge for an already-accepted proposal is semantically wrong). The assertion is strict 400 (not 4xx range) per the assertEquals(400, r.statusCode()) in the Java code.

### Steps

```
1) Seed one ACCEPTED proposal via _FmM2.prop(this, 1L, 1L, "ACCEPTED", 100.0, 5, "2026-04-10").
2) POST /api/proposals/<pid>/record-interaction with admin token.
3) Assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Status check is absent or only checks != SUBMITTED without explicitly listing allowed statuses — ACCEPTED proposals are processed and a PROPOSED_TO edge is created, violating the spec's status restriction.

---

## TC075 — Recording a REJECTED proposal returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction on a REJECTED Proposal`

### What it tests

A REJECTED proposal must also return 400 from the S3-F11 record-interaction endpoint. This pairs with TC74 (ACCEPTED→400) and TC84 (SHORTLISTED→400) to fully cover the invalid-status matrix. REJECTED is semantically "already decided against" — recording a Neo4j interaction for it is meaningless. Strict 400 per assertEquals(400, r.statusCode()).

### Steps

```
1) Seed one REJECTED proposal via _FmM2.prop(this, 1L, 1L, "REJECTED", 100.0, 5, "2026-04-10").
2) POST /api/proposals/<pid>/record-interaction with admin token.
3) Assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Status validation only guards ACCEPTED but passes REJECTED through — or uses a whitelist that includes REJECTED by mistake.

---

## TC076 — Record interaction for non-existent proposal returns 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/999999/record-interaction`

### What it tests

The S3-F11 endpoint must return strictly 404 for a proposal id that does not exist in the database. 999999 is used as the improbable id. Strict 404 (not 5xx from Optional.get() NPE). Catches the same not-found handling pattern as TC46/TC51 applied to this endpoint.

### Steps

```
1) Call adminToken().
2) POST /api/proposals/999999/record-interaction with admin token.
3) Assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* repository.findById(999999) threw NoSuchElementException (5xx), or the controller returned 2xx for a non-existent proposal.

---

## TC077 — Record interaction without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction (no auth)`

### What it tests

The S3-F11 record-interaction endpoint must require authentication. An unauthenticated POST must return strictly 401. A real SUBMITTED proposal is seeded first (valid pid) so the 401 comes from the auth filter, not from a 404 on a missing proposal.

### Steps

```
1) Seed one SUBMITTED proposal via _FmM2.prop().
2) POST /api/proposals/<pid>/record-interaction via httpPost (no Authorization header).
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Record-interaction is publicly accessible — anyone can trigger Neo4j writes without authentication.

---

## TC078 — Record interaction with bogus JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction with token xxx.yyy.zzz`

### What it tests

The S3-F11 endpoint must reject a structurally-invalid JWT with strictly 401. Uses the same xxx.yyy.zzz malformed token pattern as TC65. A real proposal is seeded so the id is valid — the 401 must come from signature verification failure, not from a missing proposal.

### Steps

```
1) Seed one SUBMITTED proposal via _FmM2.prop().
2) POST /api/proposals/<pid>/record-interaction via httpPostAuth(..., "xxx.yyy.zzz").
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx, or the filter accepted an invalid token (2xx).

---

## TC079 — PROPOSED_TO edge has lastProposalDate property after recording

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction, Neo4j relationship query`

### What it tests

The S3-F11 PROPOSED_TO relationship must carry a lastProposalDate property (or an alias: last_proposal_date, lastProposal, lastBookingDate, lastOrderDate). After calling record-interaction, a Cypher query retrieves the relationship and checks that its toString() representation contains one of these field names. Catches implementations that set proposalCount on the edge but omit lastProposalDate.

### Steps

```
1) Seed user, Job, SUBMITTED Proposal.
2) POST record-interaction with admin token, assert 2xx.
3) neo4jExec() to fetch the PROPOSED_TO relationship using s3GraphUserLabel(), s3GraphRelationship(), s3GraphCatalogLabel() and parameters {u: uid, p: jid}.
4) Assert rows is not empty, rel is not null, and rel.toString() contains lastProposalDate or one of its aliases.
```

### Pass Criteria

- **Relationship returned from Neo4j**
  - *Bug it catches:* The MERGE wrote the relationship without the lastProposalDate property — the relationship string representation will not contain the expected field name.
- **String contains lastProposalDate (or alias)**
  - *Bug it catches:* Property is named differently (e.g., lastInteractionDate) — fails unless one of the accepted aliases is matched.

---

## TC080 — Neo4j Job node exists with the seeded job id

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction, neo4jNodeCount(s3GraphCatalogLabel(), jid)`

### What it tests

After record-interaction, a Neo4j node with label s3GraphCatalogLabel() (i.e., the Job/catalog entity label) and id=jid must exist. The test calls neo4jNodeCount() and asserts count >= 1. This is distinct from the edge test (TC70): it specifically verifies that the Job node was created/merged in Neo4j, not just the relationship. Catches: the MERGE creates the relationship but uses an auto-generated Neo4j id instead of setting the id property to jid, making future lookups by {id:jid} return 0.

### Steps

```
1) Seed user, Job (id=jid), SUBMITTED Proposal.
2) POST record-interaction, assert 2xx.
3) neo4jNodeCount(s3GraphCatalogLabel(), jid), assert >= 1.
```

### Pass Criteria

- **count >= 1**
  - *Bug it catches:* Job node was created without the id property set (e.g., CREATE (j:Job) without {id: jid}), or the node label doesn't match s3GraphCatalogLabel(), making the {id:jid} lookup return 0.

---

## TC081 — Neo4j Freelancer node exists with the proposal's user id

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction, neo4jNodeCount(s3GraphUserLabel(), uid)`

### What it tests

After record-interaction, a Neo4j node with label s3GraphUserLabel() (i.e., the Freelancer user label) and id=uid must exist. Mirrors TC80 for the user/freelancer side of the relationship. Catches: Freelancer node created without setting its id property to uid, or the node label doesn't match s3GraphUserLabel().

### Steps

```
1) Seed user (capture uid), Job, SUBMITTED Proposal.
2) POST record-interaction, assert 2xx.
3) neo4jNodeCount(s3GraphUserLabel(), uid), assert >= 1.
```

### Pass Criteria

- **count >= 1**
  - *Bug it catches:* Freelancer node created without {id: uid} property, or uses the wrong label (e.g., User instead of Freelancer).

---

## TC082 — Record interaction writes INTERACTION_RECORDED to proposal_events

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction, MongoDB count on s3EventsCollection()`

### What it tests

The S3-F11 spec requires logging an INTERACTION_RECORDED event to MongoDB when a new (non-idempotent) interaction is recorded. The test counts {action: "INTERACTION_RECORDED"} documents before and after the call, asserting the count increased. Catches: event logging omitted, wrong collection name, wrong action value, or silent try-catch swallowing the MongoDB write failure.

### Steps

```
1) Assert mongo != null.
2) Count before in s3EventsCollection() filtered by {action: "INTERACTION_RECORDED"}.
3) Seed user, Job, SUBMITTED Proposal, call record-interaction with admin token, assert 2xx.
4) Count after, assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* INTERACTION_RECORDED event not logged, or logged to the wrong collection, or the action field has a different value (e.g., "PROPOSAL_RECORDED").

---

## TC083 — Second (idempotent) call does NOT log INTERACTION_RECORDED again

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two POST /api/proposals/{pid}/record-interaction (same pid), MongoDB event count comparison`

### What it tests

The S3-F11 idempotency specification requires that a second call with the same proposal id does NOT log another INTERACTION_RECORDED event. The test calls record-interaction twice with the same pid, counts events after each call, and asserts the count did NOT increase after the second call. This catches implementations that always log the event unconditionally — they must only log when a new relationship is actually created (i.e., the MERGE's ON CREATE branch).

### Steps

```
1) Seed user, Job, SUBMITTED Proposal.
2) POST record-interaction (first call) with admin token, assert 2xx. Count after1 events.
3) POST record-interaction (second call, same pid), assert 2xx. Count after2 events.
4) Assert after1 == after2 (no new event logged on idempotent retry).
```

### Pass Criteria

- **after1 == after2**
  - *Bug it catches:* Event logging fires unconditionally on every call regardless of idempotency — the event should only be logged when ON CREATE branch of the MERGE executes (i.e., first time the edge is created).

---

## TC084 — Recording a SHORTLISTED proposal returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/proposals/{pid}/record-interaction on a SHORTLISTED Proposal`

### What it tests

Completes the invalid-status matrix for record-interaction (TC74=ACCEPTED→400, TC75=REJECTED→400, TC84=SHORTLISTED→400). A SHORTLISTED proposal is one that has been reviewed but not yet accepted or rejected — the spec restricts record-interaction to SUBMITTED proposals only. Strict 400 per assertEquals(400, r.statusCode()).

### Steps

```
1) Seed one SHORTLISTED proposal via _FmM2.prop(this, 1L, 1L, "SHORTLISTED", 100.0, 5, "2026-04-10").
2) POST /api/proposals/<pid>/record-interaction with admin token.
3) Assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Validation allows SHORTLISTED through because the condition checks status != ACCEPTED && status != REJECTED but doesn't also exclude SHORTLISTED.

---

## TC085 — Recs for A (proposed J1,J2) include J3 (B proposed J1) and J4 (C proposed J2); exclude J1,J2

**Tags:** `public` `features_m2`  
**Endpoint(s):** `_FmM2.proposeAndRecord() ×6, GET /api/proposals/recommendations?freelancerId=<aid>`

### What it tests

The S3-F12 recommendations algorithm: for freelancer A who proposed to J1 and J2, find other freelancers (B, C) who proposed to the same jobs, then recommend jobs that B and C proposed to that A has NOT proposed to. B proposed J1+J3 → J3 recommended; C proposed J2+J4 → J4 recommended. J1 and J2 must NOT appear (A already proposed to them). This is the full spec composite scenario. Catches: including already-proposed jobs (missing "exclude seen" filter), not following the collaborative-filtering graph traversal at all (returning empty), or returning jobs from freelancers with no shared history.

### Steps

```
1) Seed users A, B, C and Jobs J1–J4.
2) A proposes J1, J2; B proposes J1, J3; C proposes J2, J4 via _FmM2.proposeAndRecord().
3) GET recommendations for A with A's own token.
4) Parse result array, extract job ids via _FmM2.rL(it, "jobId", "id").
5) Assert ids contains J3 and J4; assert ids does NOT contain J1 or J2.
```

### Pass Criteria

- **J3 in recommendations**
  - *Bug it catches:* Collaborative filtering traversal doesn't follow B's other jobs from J1.
- **J4 in recommendations**
  - *Bug it catches:* Traversal doesn't follow C's other jobs from J2.
- **J1 NOT in recommendations**
  - *Bug it catches:* Already-proposed jobs are not excluded — A sees J1 again even though they already proposed to it.
- **J2 NOT in recommendations**
  - *Bug it catches:* Same as J1 — already-proposed jobs leak into the result.

---

## TC086 — Recommendations with limit=2 returns at most 2 jobs

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid>&limit=2`

### What it tests

The S3-F12 limit parameter must cap the number of recommendations returned. Five potential recommendation jobs are seeded (B proposes J1–J5, A proposes J1); with limit=2, the response must contain at most 2 entries. The assertion arr.size() <= 2 is lenient enough to pass if fewer than 2 recommendations are available but strict enough to catch unlimited result sets. Catches: limit parameter accepted in the URL but not applied to the Neo4j query (Cypher LIMIT clause missing).

### Steps

```
1) Seed users A and B; Jobs J1–J5.
2) A proposes J1; B proposes J1–J5 via proposeAndRecord() with admin token.
3) GET recommendations for A with limit=2, using A's own token, assert 2xx.
4) Unwrap content if present, assert arr.size() <= 2.
```

### Pass Criteria

- **arr.size() <= 2**
  - *Bug it catches:* The limit query parameter is parsed but not applied to the Cypher LIMIT clause — all 4 recommended jobs (J2–J5) are returned instead of at most 2.

---

## TC087 — Recommendations without limit param uses spec default

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid> (no limit param)`

### What it tests

The S3-F12 endpoint must function correctly when no limit parameter is provided, applying the spec's default limit. The test only asserts 2xx — it does not validate the specific default value (that is an implementation detail in the spec but not checked here). This is a robustness test ensuring the absence of limit does not cause NPE, 400, or any non-2xx.

### Steps

```
1) Seed user A via seedAndLoginUser(), capture token.
2) GET recommendations for A without limit parameter.
3) Assert 2xx via assert2xx.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Omitting limit causes a NullPointerException in the service (e.g., @RequestParam int limit without defaultValue) which propagates as 5xx, or the endpoint returns 400 because limit is treated as required.

---

## TC088 — Freelancer A querying recommendations for B returns 403

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<bid> using A's token`

### What it tests

The S3-F12 endpoint must enforce ownership: a freelancer may only retrieve their own recommendations, not another user's. Using B's freelancerId with A's token must return strictly 403. Catches: the ownership check is absent — any authenticated user can retrieve any freelancer's recommendations, leaking their proposal history and collaborative filtering data.

### Steps

```
1) Seed users A and B, capture B's id and A's token.
2) GET recommendations with freelancerId=bid and A's token.
3) Assert strictly 403.
```

### Pass Criteria

- **Status strictly 403**
  - *Bug it catches:* No ownership check in the recommendations endpoint — any authenticated user can read any other freelancer's recommendations, exposing their proposal history.

---

## TC089 — Admin can query any freelancer's recommendations (200)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid> with admin token`

### What it tests

The ADMIN role must bypass the S3-F12 ownership check, allowing an admin to retrieve any freelancer's recommendations. This is the positive RBAC counterpart to TC88's denial. Admin token is used against a freshly seeded user's id. Catches: ownership check that denies ADMIN the same way it denies regular users (missing role bypass in the condition).

### Steps

```
1) Seed user A via seedAndLoginUser(), capture aid.
2) GET recommendations with freelancerId=aid and adminToken().
3) Assert 2xx via assert2xx.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Admin override is missing from the ownership check — admin receives 403 the same way a non-owner freelancer does in TC88.

---

## TC090 — Unknown freelancerId returns 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=999999 with admin token`

### What it tests

The S3-F12 endpoint must return strictly 404 when the freelancerId query parameter does not correspond to any user in the database. Admin token bypasses the ownership check so the response reaches the user-not-found path. Catches: Optional.get() NPE (5xx), returning empty recommendations for a non-existent user (2xx with empty list), or returning 400 instead of 404.

### Steps

```
1) GET recommendations with freelancerId=999999 and admin token.
2) Assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller returned empty results (2xx) for a non-existent freelancer — conflates "no history" with "user not found"; or findById(999999) threw NoSuchElementException (5xx).

---

## TC091 — Recommendations without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=1 (no auth)`

### What it tests

The S3-F12 endpoint must require authentication. Unauthenticated GET returns strictly 401.

### Steps

```
1) GET /api/proposals/recommendations?freelancerId=1 via httpGet (no Authorization header).
2) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Recommendations endpoint is publicly accessible without a token.

---

## TC092 — Recommendations with bogus JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=1 with token xxx.yyy.zzz`

### What it tests

The S3-F12 endpoint must reject malformed JWT with strictly 401. Same pattern as TC65/TC78 applied to the recommendations endpoint.

### Steps

```
1) GET /api/proposals/recommendations?freelancerId=1 via httpGetAuth(..., "xxx.yyy.zzz").
2) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx, or the token accepted (2xx).

---

## TC093 — Freelancer with no proposal history returns empty list

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid> (no prior proposeAndRecord calls)`

### What it tests

A freelancer who has never had a proposal interaction recorded in Neo4j should receive an empty recommendations list (not 404, not 5xx). The test seeds a fresh user, calls the recommendations endpoint without recording any interactions first, and asserts 2xx plus empty array. This tests the "no graph history" edge case — the Neo4j traversal should return 0 results gracefully rather than throwing on a null/missing node.

### Steps

```
1) Seed user A via seedAndLoginUser(), capture aid and token.
2) GET recommendations for A without prior proposeAndRecord calls, assert 2xx.
3) Unwrap content if present, assert arr.size() == 0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 for a user with no Neo4j node (the user exists in PG but has no node in Neo4j yet).
- **arr.size() == 0**
  - *Bug it catches:* Recommendations from other unrelated data leak into the result — the graph traversal is not properly scoped to the requesting freelancer's history.

---

## TC094 — Each recommendation entry has jobId, title, category, score fields

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid> with collaborative-filter setup`

### What it tests

The S3-F12 recommendation DTO must include at minimum: jobId (or job_id or id), title, and either category or score. The test sets up a one-hop recommendation (A→J1, B→J1+J2 → J2 recommended for A) and validates the first recommendation item's fields. Catches: recommendations are returned as bare job ids (no DTO shape), or the DTO omits title (the field name most commonly forgotten), or score/category are missing.

### Steps

```
1) Seed users A and B; Jobs J1 and J2.
2) A→J1 and B→J1, B→J2 via proposeAndRecord(). J2 is now recommended for A.
3) GET recommendations for A with A's token, assert 2xx, at least 1 recommendation.
4) Check arr.get(0) for fields jobId/job_id/id, title, and category/score.
```

### Pass Criteria

- **jobId (or alias) present**
  - *Bug it catches:* DTO returns only the job title without an id field — downstream clients cannot follow a link to the job.
- **title present**
  - *Bug it catches:* DTO returns only the id and score — no human-readable label for the recommendation.
- **category or score present**
  - *Bug it catches:* Recommendation DTO is missing the relevance metadata fields required by the spec.

---

## TC095 — First call populates Redis cache

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid>, redis.dbSize() before and after`

### What it tests

The S3-F12 caching requirement: recommendations must be cached in Redis after the first call. redis.dbSize() before and after confirms a new cache entry was added. Same pattern as TC68 applied to the recommendations endpoint.

### Steps

```
1) Seed user A, capture token.
2) Record before = redis.dbSize().
3) GET recommendations for A, assert 2xx.
4) Record after = redis.dbSize(), assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* @Cacheable is wired to an in-memory cache manager rather than Redis, or the cache name doesn't match the Redis configuration.

---

## TC096 — Two identical recommendation requests return identical bodies (cached)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two identical GET /api/proposals/recommendations?freelancerId=<aid>`

### What it tests

Two consecutive calls with the same freelancerId must return identical response bodies. Same pattern as TC69 applied to the recommendations endpoint.

### Steps

```
1) Seed user A, capture token.
2) GET recommendations twice with same parameters, capture r1.body() and r2.body(), both assert 2xx.
3) assertEquals(r1.body(), r2.body()).
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* Each call re-queries Neo4j non-deterministically (different ordering), or caching is not applied so the two calls return differently-ordered lists.

---

## TC097 — Different freelancerId produces independent cache results

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid> and ?freelancerId=<bid>`

### What it tests

The recommendations cache key must include freelancerId so that different users have independent cache entries. Two different users are queried and both calls must return 2xx — if the cache key were global (not per-freelancer), the second call would return the first user's recommendations. Tests that @Cacheable(key="#freelancerId") is set correctly.

### Steps

```
1) Seed users A and B.
2) GET recommendations for A with admin token, assert 2xx.
3) GET recommendations for B with admin token, assert 2xx.
```

### Pass Criteria

- **Both calls return 200..299**
  - *Bug it catches:* The cache key is not per-freelancer (e.g., no key in @Cacheable) — the second call returns the cached response for user A as if it were user B's recommendations, or the second call crashes because the cached DTO doesn't match user B's structure.

---

## TC098 — Negative limit returns a 4xx (validation)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/proposals/recommendations?freelancerId=<aid>&limit=-1`

### What it tests

A negative limit is semantically invalid and must be rejected with a 4xx. NOT 5xx (must not crash), NOT 2xx (must not return results with a negative limit). Mirrors TC29 for the activity endpoint but applied to the recommendations limit parameter.

### Steps

```
1) Seed user A, capture token.
2) GET recommendations with freelancerId=aid&limit=-1, capture status code.
3) Assert NOT 5xx and NOT 2xx.
```

### Pass Criteria

- **NOT 5xx**
  - *Bug it catches:* Negative limit is passed to the Neo4j LIMIT clause without validation — Cypher may throw on LIMIT -1.
- **NOT 2xx**
  - *Bug it catches:* Negative limit is treated as "unlimited" or silently clamped to 0/positive — the spec requires rejecting invalid input with a 4xx.

---

## TC099 — Freelancer who shares no jobs with anyone gets empty recs

**Tags:** `public` `features_m2`  
**Endpoint(s):** `_FmM2.proposeAndRecord() for unique job, GET /api/proposals/recommendations?freelancerId=<aid>`

### What it tests

If a freelancer (A) has only proposed to a job that no other freelancer has proposed to, there are no "neighbours" in the collaborative-filter graph and the result must be empty. The test records A→JUnique (only A has proposed to JUnique), then fetches A's recommendations and asserts 0 results. Catches: the collaborative filter incorrectly recommends JUnique itself to A (A has already proposed there), or returns jobs from unrelated graph paths.

### Steps

```
1) Seed user A and a unique Job JUnique via _FmM2.job().
2) _FmM2.proposeAndRecord(this, aid, jUnique, tok).
3) GET recommendations for A, assert 2xx, unwrap array, assert arr.size() == 0.
```

### Pass Criteria

- **arr.size() == 0**
  - *Bug it catches:* The recommendation query returns A's own already-proposed jobs (JUnique) because the "already seen" exclusion is missing; or returns jobs from other freelancers in the DB who are unrelated to A's graph neighbourhood.

---

## M1 Features — TC257–TC298

## TC257 — status=SUBMITTED + date range returns matching proposals

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/search?status=SUBMITTED&startDate=...&endDate=...`

### What it tests

Verifies S3-F1 proposal search by status and date range filter. Seeds 4 proposals (3 SUBMITTED, 1 REJECTED) with varying dates across March and April 2026, then queries for SUBMITTED in March only. Confirms the count is exactly 2, catching students who ignore the date bounds or include wrong-status proposals. Response is normalised to handle both plain array and paginated content wrapper.

### Steps

```
1) _FmM1Seed.seedUser() → uid.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() ×4 with mixed statuses and March/April dates.
4) adminToken() → tok.
5) httpGetAuth("/api/proposals/search?status=SUBMITTED&startDate=2026-03-01&endDate=2026-03-31", tok) → r.
6) assert2xx(r, "TC257").
7) parseNode(r.body()) → normalise to list.
8) assertEquals(2, list.size(), ...).
```

### Pass Criteria

- **assertEquals(2, list.size(), "TC257: 2 SUBMITTED in March expected; got " + list.size())**
  - *Bug it catches:* Date range filter not applied — all SUBMITTED proposals returned regardless of date, or REJECTED proposals leaked into results.

---

## TC258 — Search results sorted by submittedAt DESC

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/search?status=SUBMITTED&startDate=...&endDate=...`

### What it tests

Verifies S3-F1 that search results are sorted by submittedAt descending (most recent first). Seeds 3 SUBMITTED proposals on March 5, 15, and 25 then checks that at least 3 results are returned. The assertion is deliberately lenient on ordering proof (size ≥ 3) to avoid brittle date-comparison logic, but confirms all seeded entries are present. Common student bug: default JPA repository sort that returns in insertion order rather than applying ORDER BY submittedAt DESC.

### Steps

```
1) _FmM1Seed.seedUser() → uid.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() ×3 with dates 2026-03-05, 2026-03-25, 2026-03-15.
4) adminToken() → tok.
5) httpGetAuth("/api/proposals/search?status=SUBMITTED&startDate=2026-03-01&endDate=2026-03-31", tok) → r.
6) assert2xx(r, "TC258").
7) parseNode(r.body()) → normalise to list.
8) assertTrue(list.size() >= 3, ...).
```

### Pass Criteria

- **assertTrue(list.size() >= 3, "TC258: at least 3 results expected")**
  - *Bug it catches:* Search endpoint paginates or trims results before applying filter, dropping seeded proposals from the returned list.

---

## TC259 — Search with status=ACCEPTED returns empty when none accepted

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/search?status=ACCEPTED`

### What it tests

Verifies S3-F1 that filtering by a status with zero matching proposals returns an empty list (not an error). Seeds one SUBMITTED proposal then queries for ACCEPTED, expecting size == 0 with a 2xx response. Students frequently return 404 or throw an unhandled exception when the result set is empty, or they ignore the status filter and return all proposals. Handles both array and paginated response shapes.

### Steps

```
1) _FmM1Seed.seedUser() → uid.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() with status SUBMITTED.
4) adminToken() → tok.
5) httpGetAuth("/api/proposals/search?status=ACCEPTED", tok) → r.
6) assert2xx(r, "TC259").
7) parseNode(r.body()) → normalise to list.
8) assertEquals(0, list.size(), ...).
```

### Pass Criteria

- **assertEquals(0, list.size(), "TC259: empty list expected; got " + list.size())**
  - *Bug it catches:* Status filter ignored — SUBMITTED proposals returned for ACCEPTED query, or endpoint throws 404/500 on empty results.

---

## TC260 — status=REJECTED only filter returns REJECTED proposals only

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/search?status=REJECTED`

### What it tests

Verifies S3-F1 that status-only filter (no date range) works and that every item in the result has the correct status. Seeds 1 SUBMITTED and 2 REJECTED proposals then confirms each returned item has status == "REJECTED". Students often omit per-item status validation and allow cross-status leakage when no date range is provided. This test exercises the optional date range — it must be truly optional, not required.

### Steps

```
1) _FmM1Seed.seedUser() → uid.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() ×3 with SUBMITTED then REJECTED ×2.
4) adminToken() → tok.
5) httpGetAuth("/api/proposals/search?status=REJECTED", tok) → r.
6) assert2xx(r, "TC260").
7) parseNode(r.body()) → normalise to list.
8) For each item: assertEquals("REJECTED", it.get("status").asText(), ...).
```

### Pass Criteria

- **assertEquals("REJECTED", st, "TC260: every result must be REJECTED")**
  - *Bug it catches:* Status filter not applied when date range is absent — SUBMITTED proposals leak into REJECTED-filtered results.

---

## TC261 — Accept SUBMITTED proposal: proposal=ACCEPTED, job=IN_PROGRESS, ACTIVE Contract created

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/accept`

### What it tests

Verifies S3-F2 happy-path acceptance of a SUBMITTED proposal. After PUT, checks two DB columns directly: proposal status must be ACCEPTED and job status must be IN_PROGRESS. This covers the multi-table side-effect requirement of S3-F2; students frequently update the proposal but forget to transition the job. Uses columnByField for dynamic column-name resolution and direct JDBC queries.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=SUBMITTED, bid=250 → pid.
4) adminToken() → tok.
5) httpPutAuth("/api/proposals/" + pid + "/accept", "", tok) → r.
6) assert2xx(r, "TC261").
7) jdbc.queryForObject(SELECT status FROM Proposal WHERE id=pid) → pStatus.
8) assertEquals("ACCEPTED", pStatus, ...).
9) jdbc.queryForObject(SELECT status FROM Job WHERE id=jid) → jStatus.
10) assertEquals("IN_PROGRESS", jStatus, ...).
```

### Pass Criteria

- **assertEquals("ACCEPTED", pStatus, "TC261: proposal.status=ACCEPTED expected; got " + pStatus)**
  - *Bug it catches:* Proposal status not persisted — service returns 200 without writing to DB.
- **assertEquals("IN_PROGRESS", jStatus, "TC261: job.status=IN_PROGRESS expected; got " + jStatus)**
  - *Bug it catches:* Job status not updated on acceptance — student only updates proposal entity, ignoring the job transition requirement.

---

## TC262 — Accept non-existent proposal returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/999999/accept`

### What it tests

Verifies S3-F2 error handling for missing resource. Calls accept on a hardcoded non-existent ID (999999) and asserts HTTP 404. No seeding required. Students commonly return 500 (unhandled EmptyResultDataAccessException) or 400 instead of the correct 404. The assertion style is a direct status-code equality check rather than assert2xx, because a 2xx would be wrong here.

### Steps

```
1) adminToken() → tok.
2) httpPutAuth("/api/proposals/999999/accept", "", tok) → r.
3) assertEquals(404, r.statusCode(), ...).
```

### Pass Criteria

- **assertEquals(404, r.statusCode(), "TC262: must be 404; got " + r.statusCode())**
  - *Bug it catches:* Missing entity not handled — service throws uncaught exception mapping to 500, or returns 400 instead of 404.

---

## TC263 — Accept REJECTED proposal returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/accept`

### What it tests

Verifies S3-F2 state-machine guard: only SUBMITTED or SHORTLISTED proposals may be accepted; REJECTED must yield 400. Seeds a REJECTED proposal and attempts acceptance. Students often skip the state-machine check entirely and allow any proposal regardless of status to be accepted. The direct status-code assertion (not assert2xx) is chosen because any 2xx response would indicate a state-machine defect.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=REJECTED → pid.
4) adminToken() → tok.
5) httpPutAuth("/api/proposals/" + pid + "/accept", "", tok) → r.
6) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **assertEquals(400, r.statusCode(), "TC263: must be 400 for REJECTED; got " + r.statusCode())**
  - *Bug it catches:* Status check absent — REJECTED proposal accepted without validation, corrupting the state machine.

---

## TC264 — Accept SHORTLISTED proposal succeeds

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/accept`

### What it tests

Verifies S3-F2 that SHORTLISTED (as well as SUBMITTED) is a valid pre-acceptance state. Seeds a SHORTLISTED proposal, calls accept, and verifies via JDBC that the proposal is now ACCEPTED. Students who only allow SUBMITTED → ACCEPTED in their state machine will incorrectly reject this call with 400. Uses columnByField for dynamic column resolution.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=SHORTLISTED → pid.
4) adminToken() → tok.
5) httpPutAuth("/api/proposals/" + pid + "/accept", "", tok) → r.
6) assert2xx(r, "TC264").
7) jdbc.queryForObject(SELECT status FROM Proposal WHERE id=pid) → pStatus.
8) assertEquals("ACCEPTED", pStatus, ...).
```

### Pass Criteria

- **assertEquals("ACCEPTED", pStatus, "TC264: SHORTLISTED→ACCEPTED expected; got " + pStatus)**
  - *Bug it catches:* State machine only allows SUBMITTED → ACCEPTED, blocking the valid SHORTLISTED → ACCEPTED transition.

---

## TC265 — Accept creates ACTIVE Contract with agreedAmount=bidAmount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/accept`

### What it tests

Verifies S3-F2 that accepting a proposal atomically creates a Contract with status=ACTIVE and agreedAmount equal to the proposal's bidAmount. Queries the Contract table by proposal FK, agreedAmount, and status=ACTIVE. Students often implement accept but omit the contract creation side-effect, or set agreedAmount to zero/null instead of copying bidAmount. Uses dynamic column resolution via columnByField.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=SUBMITTED, bid=750 → pid.
4) adminToken() → tok.
5) httpPutAuth("/api/proposals/" + pid + "/accept", "", tok) → r.
6) assert2xx(r, "TC265").
7) jdbc.queryForObject(SELECT COUNT(*) FROM Contract WHERE proposal=pid AND agreedAmount=750 AND status='ACTIVE') → count.
8) assertTrue(count >= 1L, ...).
```

### Pass Criteria

- **assertTrue(count != null && count >= 1L, "TC265: ACTIVE contract w/ proposal=" + pid + " agreedAmount=750 expected; got " + count)**
  - *Bug it catches:* Contract not created on acceptance, or agreedAmount not copied from bidAmount — students forget the transactional side-effect of accept.

---

## TC266 — Estimate returns bidAmount/platformFee/freelancerPayout/feePercentage/estimatedDailyRate

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/estimate`

### What it tests

Verifies S3-F3 that the fee estimate endpoint returns all five required fields with correct value constraints. Posts {bidAmount:500, estimatedDays:10} and checks bidAmount≈500, platformFee>0, 0<freelancerPayout<bidAmount, and feePercentage in [0.10, 0.20]. Uses _FmM2.rD() to read fields tolerating camelCase or snake_case naming. Students often omit feePercentage or estimatedDailyRate from the response DTO, or compute platformFee as zero.

### Steps

```
1) adminToken() → tok.
2) httpPostAuth("/api/proposals/estimate", "{\"bidAmount\":500.0,\"estimatedDays\":10}", tok) → r.
3) assert2xx(r, "TC266").
4) parseNode(r.body()) → j.
5) _FmM2.rD(j, "bidAmount", "bid_amount") → bid.
6) _FmM2.rD(j, "platformFee", "platform_fee") → fee.
7) _FmM2.rD(j, "freelancerPayout", "freelancer_payout") → payout.
8) _FmM2.rD(j, "feePercentage", "fee_percentage") → pct.
9) Assertions on all four values.
```

### Pass Criteria

- **assertEquals(500.0, bid, 0.5, "TC266: bidAmount=500; got " + bid)**
  - *Bug it catches:* bidAmount not echoed in response or mapped to wrong DTO field.
- **assertTrue(pct >= 0.10 && pct <= 0.20, "TC266: feePercentage in [0.10,0.20]; got " + pct)**
  - *Bug it catches:* Fee percentage hardcoded outside spec range or not included in response.

---

## TC267 — Few competing proposals → 20% fee

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/estimate`

### What it tests

Verifies S3-F3 dynamic fee logic: with low market demand (few active proposals near the bid band) the platform fee percentage should approach the high end (up to 20%). Posts {bidAmount:1000, estimatedDays:5} on a clean DB with no competing proposals and asserts feePercentage in [0.10, 0.20]. The range assertion is intentionally wide because spec says dynamic — students may hardcode 10% ignoring the demand signal, but the range still catches values outside bounds.

### Steps

```
1) adminToken() → tok.
2) httpPostAuth("/api/proposals/estimate", "{\"bidAmount\":1000.0,\"estimatedDays\":5}", tok) → r.
3) assert2xx(r, "TC267").
4) parseNode(r.body()) → j.
5) _FmM2.rD(j, "feePercentage", "fee_percentage") → pct.
6) assertTrue(pct >= 0.10 && pct <= 0.20, ...).
```

### Pass Criteria

- **assertTrue(pct >= 0.10 && pct <= 0.20, "TC267: feePercentage in [0.10,0.20]; got " + pct)**
  - *Bug it catches:* Fee percentage computed outside spec bounds (e.g., returned as raw integer 10 instead of 0.10, or hardcoded to 0.05).

---

## TC268 — Estimate is read-only — no Proposal row created

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/estimate`

### What it tests

Verifies S3-F3 that the estimate endpoint is a pure calculation and does not persist any Proposal record. Snapshots the Proposal table count before the call, calls estimate, then re-counts and asserts no change. Students sometimes implement estimate by creating a draft Proposal entity and immediately deleting it, or accidentally save the entity without rolling back. This assertion directly guards idempotency.

### Steps

```
1) jdbc.queryForObject("SELECT COUNT(*) FROM Proposal") → beforeCount.
2) adminToken() → tok.
3) httpPostAuth("/api/proposals/estimate", "{\"bidAmount\":250.0,\"estimatedDays\":6}", tok) → r.
4) assert2xx(r, "TC268").
5) jdbc.queryForObject("SELECT COUNT(*) FROM Proposal") → afterCount.
6) assertEquals(beforeCount, afterCount, ...).
```

### Pass Criteria

- **assertEquals(beforeCount, afterCount, "TC268: no Proposal must be persisted")**
  - *Bug it catches:* Estimate endpoint accidentally persists a Proposal entity to the database instead of returning a transient calculation.

---

## TC269 — estimatedDailyRate = bidAmount / estimatedDays

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/estimate`

### What it tests

Verifies S3-F3 that the estimatedDailyRate field is computed correctly as bidAmount / estimatedDays. Posts {bidAmount:600, estimatedDays:10} and asserts estimatedDailyRate ≈ 60.0 with ±0.5 tolerance. Students frequently omit this field from the response DTO or use integer division (truncating to 60 but failing on non-even divisions). The _FmM2.rD() helper handles camelCase/snake_case variants.

### Steps

```
1) adminToken() → tok.
2) httpPostAuth("/api/proposals/estimate", "{\"bidAmount\":600.0,\"estimatedDays\":10}", tok) → r.
3) assert2xx(r, "TC269").
4) parseNode(r.body()) → j.
5) _FmM2.rD(j, "estimatedDailyRate", "estimated_daily_rate") → rate.
6) assertEquals(60.0, rate, 0.5, ...).
```

### Pass Criteria

- **assertEquals(60.0, rate, 0.5, "TC269: estimatedDailyRate=60; got " + rate)**
  - *Bug it catches:* estimatedDailyRate omitted from response DTO, or computed with integer division losing decimal precision.

---

## TC270 — freelancerPayout + platformFee = bidAmount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/estimate`

### What it tests

Verifies S3-F3 mathematical integrity: freelancerPayout and platformFee must sum to exactly bidAmount (±1.0 tolerance for floating-point rounding). Posts {bidAmount:800, estimatedDays:4} and asserts the accounting identity holds. Students often compute payout and fee independently using separate rounding steps, which can introduce small discrepancies; this test catches any implementation that violates the zero-sum constraint.

### Steps

```
1) adminToken() → tok.
2) httpPostAuth("/api/proposals/estimate", "{\"bidAmount\":800.0,\"estimatedDays\":4}", tok) → r.
3) assert2xx(r, "TC270").
4) parseNode(r.body()) → j.
5) _FmM2.rD(j, "bidAmount", "bid_amount") → bid.
6) _FmM2.rD(j, "platformFee", "platform_fee") → fee.
7) _FmM2.rD(j, "freelancerPayout", "freelancer_payout") → payout.
8) assertEquals(bid, fee + payout, 1.0, ...).
```

### Pass Criteria

- **assertEquals(bid, fee + payout, 1.0, "TC270: payout+fee=bid; got payout=" + payout + " fee=" + fee + " bid=" + bid)**
  - *Bug it catches:* Fee and payout computed with independent rounding, or payout set as a fixed ratio leaving a remainder unaccounted for.

---

## TC271 — Complete ACCEPTED proposal: contract→COMPLETED, job→CLOSED, PENDING Payout created

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/complete`

### What it tests

Verifies S3-F4 happy-path completion of an ACCEPTED proposal. Seeds a full chain (user → job IN_PROGRESS → proposal ACCEPTED → contract ACTIVE), calls complete, then checks via JDBC that contract.status=COMPLETED and job.status=CLOSED. Students frequently implement the proposal state change but forget to cascade the status updates to Contract and Job entities. Two separate DB assertions cover both side-effects.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() status=IN_PROGRESS → jid.
3) _FmM1Seed.seedProposal() status=ACCEPTED → pid.
4) _FmM1Seed.seedContract() status=ACTIVE, agreedAmount=400 → cid.
5) adminToken() → tok.
6) httpPutAuth("/api/proposals/" + pid + "/complete", "", tok) → r.
7) assert2xx(r, "TC271").
8) jdbc.queryForObject(SELECT status FROM Contract WHERE id=cid) → cStatus.
9) assertEquals("COMPLETED", cStatus, ...).
10) jdbc.queryForObject(SELECT status FROM Job WHERE id=jid) → jStatus.
11) assertEquals("CLOSED", jStatus, ...).
```

### Pass Criteria

- **assertEquals("COMPLETED", cStatus, "TC271: contract.status=COMPLETED expected; got " + cStatus)**
  - *Bug it catches:* Contract status not updated on proposal completion — student only transitions the proposal entity.
- **assertEquals("CLOSED", jStatus, "TC271: job.status=CLOSED expected; got " + jStatus)**
  - *Bug it catches:* Job status not transitioned to CLOSED — student forgets to close the job when the project is complete.

---

## TC272 — Complete creates PENDING Payout with amount=agreedAmount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/complete`

### What it tests

Verifies S3-F4 that completing a proposal atomically creates a Payout record with status=PENDING and amount equal to the contract's agreedAmount. Queries the Payout table by contract FK, status=PENDING, and amount=350. Students often implement complete without the Payout creation side-effect, or set the payout amount to zero instead of copying agreedAmount. Uses dynamic column-name resolution via columnByField.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=ACCEPTED, bid=350 → pid.
4) _FmM1Seed.seedContract() status=ACTIVE, agreedAmount=350 → cid.
5) adminToken() → tok.
6) httpPutAuth("/api/proposals/" + pid + "/complete", "", tok) → r.
7) assert2xx(r, "TC272").
8) jdbc.queryForObject(SELECT COUNT(*) FROM Payout WHERE contract=cid AND status='PENDING' AND amount=350) → count.
9) assertTrue(count >= 1L, ...).
```

### Pass Criteria

- **assertTrue(count != null && count >= 1L, "TC272: PENDING payout w/ contract=" + cid + " amount=350 expected; got " + count)**
  - *Bug it catches:* Payout not created on completion, or amount not copied from agreedAmount — student omits the transactional Payout creation side-effect.

---

## TC273 — Complete SUBMITTED proposal returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/complete`

### What it tests

Verifies S3-F4 state-machine guard: only ACCEPTED proposals may be completed; SUBMITTED must yield 400. Seeds a SUBMITTED proposal and attempts completion without an ACTIVE contract. Students frequently omit the status pre-condition check and allow any proposal to be completed, creating dangling Payout records. The assertion is a direct 400 status check rather than assert2xx.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() status=SUBMITTED → pid.
4) adminToken() → tok.
5) httpPutAuth("/api/proposals/" + pid + "/complete", "", tok) → r.
6) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **assertEquals(400, r.statusCode(), "TC273: must be 400 for SUBMITTED; got " + r.statusCode())**
  - *Bug it catches:* State-machine guard absent — SUBMITTED proposal completed without validation, bypassing the ACCEPTED pre-condition.

---

## TC274 — Complete ACCEPTED proposal w/o ACTIVE contract returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/complete`

### What it tests

Verifies S3-F4 that completion requires an ACTIVE contract to exist; if none exists the endpoint must return 400. Seeds an ACCEPTED proposal on an IN_PROGRESS job but deliberately seeds no contract. Students who only check proposal status (ACCEPTED) without verifying the linked contract existence will incorrectly return 200. This test distinguishes the two independent pre-conditions of complete.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() status=IN_PROGRESS → jid.
3) _FmM1Seed.seedProposal() status=ACCEPTED → pid.
4) (No contract seeded — intentional.)
5) adminToken() → tok.
6) httpPutAuth("/api/proposals/" + pid + "/complete", "", tok) → r.
7) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **assertEquals(400, r.statusCode(), "TC274: must be 400 when no ACTIVE contract; got " + r.statusCode())**
  - *Bug it catches:* Contract existence not checked before completion — service returns 200 with NullPointerException suppressed, or creates an orphaned Payout.

---

## TC275 — Complete non-existent proposal returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/999999/complete`

### What it tests

Verifies S3-F4 error handling for a missing resource. Calls complete on hardcoded non-existent ID 999999 and asserts HTTP 404. No seeding required. Students commonly propagate an unhandled EmptyResultDataAccessException as a 500, or return 400. The assertion is a direct status-code equality check to distinguish 404 from other error codes.

### Steps

```
1) adminToken() → tok.
2) httpPutAuth("/api/proposals/999999/complete", "", tok) → r.
3) assertEquals(404, r.statusCode(), ...).
```

### Pass Criteria

- **assertEquals(404, r.statusCode(), "TC275: must be 404; got " + r.statusCode())**
  - *Bug it catches:* Missing entity not handled — unhandled exception maps to 500, or service conflates "not found" with "invalid state" and returns 400.

---

## TC276 — metadata?key=source&value=referral matches proposals with metadata.source=referral

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/metadata/search?key=source&value=referral`

### What it tests

Verifies S3-F5 JSONB metadata search. Seeds 3 proposals then patches their metadata column directly via JDBC (one with source=referral, one source=direct, one source=referral). Queries the metadata search endpoint and asserts exactly 2 results are returned. Students often implement simple string LIKE search instead of JSONB key-value lookup, or return all proposals ignoring the value filter. Uses columnByField for dynamic column resolution and direct jdbc.update for JSONB injection.

### Steps

```
1) _FmM1Seed.seedUser() → fr.
2) _FmM1Seed.seedJob() → jid.
3) _FmM1Seed.seedProposal() ×3 → p1, p2, p3.
4) columnByField("Proposal", "metadata") → mCol.
5) jdbc.update("UPDATE Proposal SET metadata=?::jsonb WHERE id=?", "{\"source\":\"referral\"}", p1).
6) jdbc.update(...) → source=direct for p2.
7) jdbc.update(...) → source=referral for p3.
8) adminToken() → tok.
9) httpGetAuth("/api/proposals/metadata/search?key=source&value=referral", tok) → r.
10) assert2xx(r, "TC276").
11) parseNode(r.body()) → list.
12) assertEquals(2, list.size(), ...).
```

### Pass Criteria

- **assertEquals(2, list.size(), "TC276: 2 referral proposals expected; got " + list.size())**
  - *Bug it catches:* Metadata search uses string LIKE instead of JSONB containment operator, returning wrong results or failing entirely when the column is JSONB type.

---

## TC277 — metadata search w/ unknown value returns empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/metadata/search?key=source&value=zzznoexist`

### What it tests

Verifies S3-F5 that querying metadata with a value that matches nothing returns an empty list with a 2xx status, not an error. No proposals are seeded (clean state after table drop). The value=zzznoexist sentinel is chosen to never appear in any seed data. Students who throw an exception or return 404 on empty JSONB query results fail this check. Handles both plain array and paginated content wrapper.

### Steps

```
1) adminToken() → tok.
2) httpGetAuth("/api/proposals/metadata/search?key=source&value=zzznoexist", tok) → r.
3) assert2xx(r, "TC277").
4) parseNode(r.body()) → normalise to list.
5) assertEquals(0, list.size(), ...).
```

### Pass Criteria

- **assertEquals(0, list.size(), "TC277: empty list expected; got " + list.size())**
  - *Bug it catches:* Endpoint throws 404 or 500 on empty JSONB search result instead of returning an empty collection with 200 OK.

---

## TC278 — Blank key returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/metadata/search?key=&value=referral`

### What it tests

Verifies S3-F5 metadata search rejects a blank key query parameter with HTTP 400. The endpoint must validate that neither key nor value is empty before executing the JSONB search. A common student bug is skipping blank-string validation and treating an empty key as a wildcard, returning 200 with unfiltered results. The direct assertEquals(400, ...) style catches the status code precisely without allowing 404 or 422 to pass.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpGetAuth("/api/proposals/metadata/search?key=&value=referral", tok).
4) Assert status code == 400.
```

### Pass Criteria

- **"TC278: must be 400; got " + r.statusCode()**
  - *Bug it catches:* Student omits blank-key validation and returns 200 or runs an empty-key JSONB scan.

---

## TC279 — Blank value returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/metadata/search?key=source&value=`

### What it tests

Verifies S3-F5 metadata search rejects a blank value query parameter with HTTP 400. Both key and value must be non-blank for the JSONB path lookup to be meaningful. A common student bug is allowing an empty value and returning all proposals whose metadata contains the key regardless of value. The direct status-code assertion confirms rejection without relying on response body content.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpGetAuth("/api/proposals/metadata/search?key=source&value=", tok).
4) Assert status code == 400.
```

### Pass Criteria

- **"TC279: must be 400; got " + r.statusCode()**
  - *Bug it catches:* Student treats blank value as a match-all wildcard and returns 200 instead of rejecting the request.

---

## TC280 — Analytics returns total/accepted/rejected/totalBidValue/averageBid/acceptanceRate

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/analytics?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Verifies S3-F6 analytics endpoint returns correct aggregate counts for totalProposals, acceptedProposals, and rejectedProposals within a date range. Five proposals are seeded (2 ACCEPTED, 2 REJECTED, 1 SUBMITTED) and the endpoint must count each status bucket accurately. A common student bug is counting all statuses as "accepted" or off-by-one in the filter boundary. _FmM2.rL() tolerates both camelCase and snake_case field names in the response, making it robust against naming style choices.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user (_FmM1Seed.seedUser) with role FREELANCER and job (_FmM1Seed.seedJob).
3) Seed 5 proposals with varied statuses via _FmM1Seed.seedProposal.
4) Obtain tok = adminToken().
5) Call httpGetAuth("/api/proposals/analytics?startDate=2026-03-01&endDate=2026-03-31", tok).
6) assert2xx(r, "TC280").
7) parseNode(r.body()) then read totalProposals, acceptedProposals, rejectedProposals via _FmM2.rL().
8) Assert total==5, accepted==2, rejected==2.
```

### Pass Criteria

- **"TC280: totalProposals=5; got " + total**
  - *Bug it catches:* Student counts proposals outside the date range or counts all statuses under one bucket.
- **"TC280: acceptedProposals=2; got " + accepted**
  - *Bug it catches:* Student conflates ACCEPTED with SHORTLISTED or miscounts status groups.
- **"TC280: rejectedProposals=2; got " + rejected**
  - *Bug it catches:* Student omits a WHERE clause for REJECTED status in the query.

---

## TC281 — acceptanceRate = 2/5 = 0.4

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/analytics?startDate=2026-04-01&endDate=2026-04-30`

### What it tests

Verifies S3-F6 analytics computes acceptanceRate as accepted/total (2 ACCEPTED out of 5). Five proposals are seeded (2 ACCEPTED, 3 REJECTED) and the rate must equal 0.4 within a 0.05 tolerance. A common student bug is dividing by the wrong denominator (e.g., accepted+rejected, skipping SUBMITTED/WITHDRAWN) or using integer division that truncates to 0. The assertEquals(0.4, rate, 0.05, ...) form catches both calculation errors while tolerating minor floating-point variance.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed 5 proposals via _FmM1Seed.seedProposal (2 ACCEPTED, 3 REJECTED).
4) Obtain tok = adminToken().
5) Call httpGetAuth("/api/proposals/analytics?startDate=2026-04-01&endDate=2026-04-30", tok).
6) assert2xx(r, "TC281").
7) Read acceptanceRate via _FmM2.rD(j, "acceptanceRate", "acceptance_rate").
8) Assert rate == 0.4 ± 0.05.
```

### Pass Criteria

- **"TC281: acceptanceRate=0.4; got " + rate**
  - *Bug it catches:* Student uses integer division or wrong denominator (e.g., non-withdrawn only), producing 0 or 0.666.

---

## TC282 — averageBid = (100+200+300)/3 = 200

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/analytics?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

Verifies S3-F6 analytics computes averageBid as the arithmetic mean of all proposal bid amounts within the range. Three SUBMITTED proposals with bids 100, 200, 300 are seeded; the expected average is 200.0 within a tolerance of 1.0. A common student bug is computing averageBid as totalBidValue without dividing, or averaging only ACCEPTED proposals. The delta tolerance of 1.0 accommodates minor rounding in SQL AVG vs. application-level division.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed 3 proposals via _FmM1Seed.seedProposal with amounts 100.0, 200.0, 300.0.
4) Obtain tok = adminToken().
5) Call httpGetAuth("/api/proposals/analytics?startDate=2026-05-01&endDate=2026-05-31", tok).
6) assert2xx(r, "TC282").
7) Read averageBid via _FmM2.rD(j, "averageBid", "average_bid").
8) Assert avg == 200.0 ± 1.0.
```

### Pass Criteria

- **"TC282: averageBid=200; got " + avg**
  - *Bug it catches:* Student returns totalBidValue in the averageBid field or filters average to only certain statuses, skewing the result.

---

## TC283 — Empty date range returns totalProposals=0

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/analytics?startDate=2030-01-01&endDate=2030-01-31`

### What it tests

Verifies S3-F6 analytics returns zero counts for a date range that contains no proposals rather than an error. No seed data is inserted for the far-future range, so the endpoint must produce a valid response body with totalProposals=0 instead of a 404 or 500. A common student bug is returning null, an empty body, or an error when no data matches the range. The assert2xx plus rL() chain confirms a valid structured response even for the empty case.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpGetAuth("/api/proposals/analytics?startDate=2030-01-01&endDate=2030-01-31", tok).
4) assert2xx(r, "TC283").
5) Parse body and read totalProposals via _FmM2.rL(j, "totalProposals", "total_proposals").
6) Assert total == 0.
```

### Pass Criteria

- **"TC283: totalProposals=0; got " + total**
  - *Bug it catches:* Student throws an exception or returns 404/500 when the query result set is empty instead of returning zero-value analytics.

---

## TC284 — start>end returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/analytics?startDate=2026-03-31&endDate=2026-03-01`

### What it tests

Verifies S3-F6 analytics validates that startDate is not after endDate, returning 400 for an inverted range. The endpoint must reject logically invalid date parameters before executing any query. A common student bug is silently swapping the dates or returning an empty result set (200) instead of rejecting the inverted range. The direct assertEquals(400, ...) check ensures proper input validation rather than silent acceptance.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpGetAuth("/api/proposals/analytics?startDate=2026-03-31&endDate=2026-03-01", tok).
4) Assert status code == 400.
```

### Pass Criteria

- **"TC284: must be 400; got " + r.statusCode()**
  - *Bug it catches:* Student swaps start/end internally and returns 200 with zero results instead of rejecting the invalid range.

---

## TC285 — Withdraw SUBMITTED proposal sets status=WITHDRAWN

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/withdraw`

### What it tests

Verifies S3-F7 withdraw endpoint transitions a SUBMITTED proposal's status to WITHDRAWN and returns 2xx. After the HTTP call the test reads the DB column directly to confirm the persisted value. A common student bug is returning 200 but not committing the status change, or using a non-standard status string like "WITHDRAW". The DB probe via jdbc.queryForObject with columnByField and tableName makes the check naming-convention-agnostic.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Obtain tok = adminToken().
5) Call httpPutAuth("/api/proposals/" + pid + "/withdraw", "", tok).
6) assert2xx(r, "TC285").
7) Query DB: SELECT status::text FROM proposals WHERE id=pid using columnByField("Proposal","status") and tableName("Proposal").
8) Assert value equals "WITHDRAWN".
```

### Pass Criteria

- **"TC285: proposal.status=WITHDRAWN expected; got " + pStatus**
  - *Bug it catches:* Student returns 200 but stores "WITHDRAW", "withdrawn", or does not persist the state change at all.

---

## TC286 — Withdraw SHORTLISTED proposal succeeds

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/withdraw`

### What it tests

Verifies S3-F7 withdraw also accepts a SHORTLISTED proposal (not only SUBMITTED), transitioning it to WITHDRAWN. Some spec implementations mistakenly only allow SUBMITTED→WITHDRAWN and reject SHORTLISTED with 400. The DB probe confirms the transition actually persisted. This test separates a narrow implementation (only SUBMITTED) from the correct broader rule covering both withdrawable statuses.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SHORTLISTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Obtain tok = adminToken().
5) Call httpPutAuth("/api/proposals/" + pid + "/withdraw", "", tok).
6) assert2xx(r, "TC286").
7) Query DB status column via columnByField / tableName.
8) Assert value equals "WITHDRAWN".
```

### Pass Criteria

- **"TC286: SHORTLISTED→WITHDRAWN; got " + pStatus**
  - *Bug it catches:* Student only allows SUBMITTED to be withdrawn and returns 400 for SHORTLISTED proposals.

---

## TC287 — Withdraw ACCEPTED proposal returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/withdraw`

### What it tests

Verifies S3-F7 withdraw rejects an already-ACCEPTED proposal with HTTP 400, since ACCEPTED proposals cannot be withdrawn once a contract is in progress. This guards the business rule that status transitions are one-directional after acceptance. A common student bug is allowing any proposal to be withdrawn regardless of status. The direct assertEquals(400, ...) assertion is intentionally strict to prevent 422 or 409 from passing silently.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and IN_PROGRESS job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed ACCEPTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Obtain tok = adminToken().
5) Call httpPutAuth("/api/proposals/" + pid + "/withdraw", "", tok).
6) Assert status code == 400.
```

### Pass Criteria

- **"TC287: must be 400 for ACCEPTED; got " + r.statusCode()**
  - *Bug it catches:* Student skips status validation and allows ACCEPTED proposals to be withdrawn, breaking contract integrity.

---

## TC288 — Withdraw non-existent proposal returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/999999/withdraw`

### What it tests

Verifies S3-F7 withdraw returns 404 when the target proposal ID does not exist. The test uses the sentinel ID 999999 which is guaranteed not to be present after table truncation. A common student bug is returning 400 (bad request) or 500 (unhandled EntityNotFoundException) instead of a proper 404. The direct status assertion confirms the HTTP semantics are correctly mapped from the service exception.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpPutAuth("/api/proposals/999999/withdraw", "", tok).
4) Assert status code == 404.
```

### Pass Criteria

- **"TC288: must be 404; got " + r.statusCode()**
  - *Bug it catches:* Student propagates an unhandled EntityNotFoundException as 500 or maps it to 400 instead of 404.

---

## TC289 — Withdrawing last active proposal reverts IN_PROGRESS job to OPEN

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/proposals/{id}/withdraw`

### What it tests

Verifies S3-F7 side-effect: when the last non-withdrawn proposal for an IN_PROGRESS job is withdrawn, the job status should revert to OPEN. The test seeds one SUBMITTED proposal on an IN_PROGRESS job and confirms after withdrawal that the job is either OPEN (revert applied) or IN_PROGRESS (implementation chose not to revert). The lenient assertTrue with an OR condition acknowledges that the spec does not mandate this side effect strictly, but if revert is implemented it must be OPEN. The DB probe uses columnByField("Job","status").

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and IN_PROGRESS job via _FmM1Seed.seedUser / _FmM1Seed.seedJob; capture jid.
3) Seed SUBMITTED proposal on that job via _FmM1Seed.seedProposal; capture pid.
4) Obtain tok = adminToken().
5) Call httpPutAuth("/api/proposals/" + pid + "/withdraw", "", tok).
6) Assert 2xx (assertTrue(code/100==2, ...)).
7) Query Job status via columnByField("Job","status") and tableName("Job") where id=jid.
8) Assert status is "OPEN" or "IN_PROGRESS".
```

### Pass Criteria

- **"TC289: withdraw should succeed; got " + code**
  - *Bug it catches:* Withdraw itself fails when an IN_PROGRESS job is involved, breaking the operation entirely.
- **"TC289: job must be OPEN (revert) or IN_PROGRESS (no revert if other rules); got " + jStatus**
  - *Bug it catches:* Student sets job to an invalid status (e.g., CLOSED or NULL) after the last proposal is withdrawn.

---

## TC290 — POST milestones inserts ProposalMilestone rows w/ status=PENDING

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/{id}/milestones`

### What it tests

Verifies S3-F8 milestone creation: posting an array of milestone objects for a SUBMITTED proposal inserts the correct number of ProposalMilestone rows in the database. The test sends 2 milestone objects and confirms exactly 2 rows are persisted via a direct JDBC COUNT query. A common student bug is only persisting the first milestone or ignoring the list entirely. columnByField("ProposalMilestone","proposal") resolves the FK column name dynamically.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Build JSON array body with 2 milestone objects (Phase 1: 300, Phase 2: 500).
5) Obtain tok = adminToken().
6) Call httpPostAuth("/api/proposals/" + pid + "/milestones", body, tok).
7) assert2xx(r, "TC290").
8) JDBC COUNT query on ProposalMilestone table filtered by proposal FK; assert count == 2.
```

### Pass Criteria

- **"TC290: 2 milestones inserted; got " + cnt**
  - *Bug it catches:* Student only saves the first element of the array or stores milestones in-memory without persisting to the DB.

---

## TC291 — Add milestones to REJECTED proposal returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/{id}/milestones`

### What it tests

Verifies S3-F8 milestone creation rejects milestones on a REJECTED proposal with HTTP 400. Only proposals in certain statuses (e.g., SUBMITTED, SHORTLISTED) should be eligible for milestone additions. A common student bug is skipping status validation entirely and inserting milestones regardless of proposal state. The direct assertEquals(400, ...) ensures no 2xx is returned even if the milestone data itself is valid.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed REJECTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Build JSON body with one valid milestone object.
5) Obtain tok = adminToken().
6) Call httpPostAuth("/api/proposals/" + pid + "/milestones", body, tok).
7) Assert status code == 400.
```

### Pass Criteria

- **"TC291: must be 400 for REJECTED; got " + r.statusCode()**
  - *Bug it catches:* Student inserts milestones without checking the proposal status, allowing milestone creation on terminal-state proposals.

---

## TC292 — Milestone missing title returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/{id}/milestones`

### What it tests

Verifies S3-F8 milestone creation validates required fields, rejecting a milestone payload that omits the title field with HTTP 400. Field-level validation must be applied before persistence. A common student bug is relying only on database NOT NULL constraints and returning 500 instead of a proper 400 validation error. The test uses a SUBMITTED proposal so the proposal status is not the rejection cause.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Build JSON body with milestone missing the title field.
5) Obtain tok = adminToken().
6) Call httpPostAuth("/api/proposals/" + pid + "/milestones", body, tok).
7) Assert status code == 400.
```

### Pass Criteria

- **"TC292: must be 400 for missing title; got " + r.statusCode()**
  - *Bug it catches:* Student omits @NotBlank / @Valid on the milestone DTO, causing a 500 DataIntegrityViolationException instead of a 400.

---

## TC293 — Total milestone amount > bidAmount returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/{id}/milestones`

### What it tests

Verifies S3-F8 business rule that the sum of milestone amounts must not exceed the proposal's bidAmount, returning 400 when the total exceeds the bid. The proposal has bidAmount=500 and two milestones totaling 600 are submitted. A common student bug is skipping the sum-validation check entirely and persisting over-budget milestones. This enforces the contract that milestones are a breakdown of the agreed bid, not an extension of it.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal with bidAmount=500 via _FmM1Seed.seedProposal; capture pid.
4) Build JSON body with two milestones totaling 600 (300+300).
5) Obtain tok = adminToken().
6) Call httpPostAuth("/api/proposals/" + pid + "/milestones", body, tok).
7) Assert status code == 400.
```

### Pass Criteria

- **"TC293: must be 400 (600>500); got " + r.statusCode()**
  - *Bug it catches:* Student does not validate the sum of milestone amounts against bidAmount, silently persisting an over-budget milestone plan.

---

## TC294 — Add milestones to non-existent proposal returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/proposals/999999/milestones`

### What it tests

Verifies S3-F8 returns 404 when the target proposal does not exist. Uses sentinel ID 999999 which is guaranteed absent after table truncation. A common student bug is returning 400 (from a validation path that runs before the existence check) or 500 (unhandled exception). Proper service layering should check existence first and throw a not-found exception before any field validation.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Build a valid single-milestone JSON body.
3) Obtain tok = adminToken().
4) Call httpPostAuth("/api/proposals/999999/milestones", body, tok).
5) Assert status code == 404.
```

### Pass Criteria

- **"TC294: must be 404; got " + r.statusCode()**
  - *Bug it catches:* Student runs validation before the existence check, returning 400 for missing fields on a non-existent parent, or propagates EntityNotFoundException as 500.

---

## TC295 — Details DTO has totalMilestones, completedMilestones, milestones list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/{id}/details`

### What it tests

Verifies S3-F9 proposal details endpoint returns a rich DTO containing totalMilestones, completedMilestones (counting both COMPLETED and APPROVED statuses), and a milestones array with all entries. Three milestones are seeded (COMPLETED, APPROVED, PENDING); total must be 3 and completedMilestones must be 2. A common student bug is only counting COMPLETED and ignoring APPROVED, or returning the milestone list without the summary counts. _FmM2.rO() resolves the milestones array field with camelCase/snake_case tolerance.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed ACCEPTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Seed 3 milestones via _FmM1Seed.seedMilestone (orders 1,2,3; statuses COMPLETED, APPROVED, PENDING).
5) Obtain tok = adminToken().
6) Call httpGetAuth("/api/proposals/" + pid + "/details", tok).
7) assert2xx(r, "TC295").
8) Parse body; read totalMilestones and completedMilestones via _FmM2.rL(); assert 3 and 2 respectively.
9) Read milestones via _FmM2.rO(); assert not null and size == 3.
```

### Pass Criteria

- **"TC295: totalMilestones=3; got " + total**
  - *Bug it catches:* Student omits the totalMilestones count from the DTO or counts only milestones in PENDING status.
- **"TC295: completedMilestones=2 (COMPLETED+APPROVED); got " + completed**
  - *Bug it catches:* Student counts only COMPLETED status and ignores APPROVED, returning 1 instead of 2.
- **"TC295: milestones array required; body=" + r.body()**
  - *Bug it catches:* Student returns only the summary counts without embedding the milestones list in the response.

---

## TC296 — Details for proposal with no milestones returns totalMilestones=0

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/{id}/details`

### What it tests

Verifies S3-F9 details endpoint handles a proposal with zero milestones gracefully, returning a valid DTO with totalMilestones=0 rather than null or an error. A common student bug is throwing a NullPointerException or returning an empty body when the milestones list is empty. The assert2xx + rL() chain confirms both the HTTP success and the zero-value field in a well-formed response body.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Obtain tok = adminToken().
5) Call httpGetAuth("/api/proposals/" + pid + "/details", tok).
6) assert2xx(r, "TC296").
7) Parse body and read totalMilestones via _FmM2.rL(j, "totalMilestones", "total_milestones").
8) Assert total == 0.
```

### Pass Criteria

- **"TC296: totalMilestones=0; got " + total**
  - *Bug it catches:* Student returns null or omits the field when there are no milestones, causing a NullPointerException in the parser or a wrong non-zero value.

---

## TC297 — Details milestones list is ordered by milestoneOrder ASC

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/{id}/details`

### What it tests

Verifies S3-F9 the milestones array in the details DTO is sorted ascending by milestoneOrder. Three milestones are intentionally seeded in non-monotonic insertion order (3,1,2) and the response must return them ordered (1,2,3). A common student bug is returning milestones in insertion order (3,1,2) or sorted by ID rather than by the explicit milestoneOrder field. The assertion iterates each element checking ord >= prev, catching any out-of-order pair. The field accessor checks milestoneOrder, milestone_order, and order to handle naming variants.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Seed user and job via _FmM1Seed.seedUser / _FmM1Seed.seedJob.
3) Seed SUBMITTED proposal via _FmM1Seed.seedProposal; capture pid.
4) Seed 3 milestones via _FmM1Seed.seedMilestone in non-monotonic order (milestoneOrder 3, then 1, then 2).
5) Obtain tok = adminToken().
6) Call httpGetAuth("/api/proposals/" + pid + "/details", tok).
7) assert2xx(r, "TC297").
8) Parse body; extract milestones via _FmM2.rO(); iterate and assert each milestoneOrder >= previous.
```

### Pass Criteria

- **"TC297: milestones not ASC by order; prev=" + prev + " curr=" + ord**
  - *Bug it catches:* Student returns milestones in insertion or creation-time order instead of sorting by the milestoneOrder field, breaking the defined sequence.

---

## TC298 — Details for non-existent proposal returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/proposals/999999/details`

### What it tests

Verifies S3-F9 details endpoint returns 404 for a proposal ID that does not exist. Uses sentinel ID 999999 guaranteed absent after truncation. A common student bug is returning 200 with a null body or an empty DTO when the proposal is not found, or propagating EntityNotFoundException as a 500. The direct assertEquals(404, ...) check enforces correct HTTP semantics for not-found resources on a read endpoint.

### Steps

```
1) Set BASE_URL = orderServiceUrl.
2) Obtain tok = adminToken().
3) Call httpGetAuth("/api/proposals/999999/details", tok).
4) Assert status code == 404.
```

### Pass Criteria

- **"TC298: must be 404; got " + r.statusCode()**
  - *Bug it catches:* Student returns 200 with an empty/null body or 500 from an unhandled EntityNotFoundException instead of a proper 404.

---

# S4 — Contract Service

## M2 Features — TC100–TC135

## TC100 — Dashboard returns totalContracts/averageContractValue/completionRate/averageContractDurationDays/contractsByStatus

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-04-01&endDate=2026-04-30`

### What it tests

The S4-F10 contract analytics dashboard must aggregate five metrics: totalContracts, averageContractValue, completionRate, averageContractDurationDays, and contractsByStatus. Seven contracts are seeded with varied statuses (3 COMPLETED, 2 ACTIVE, 1 TERMINATED, 1 DISPUTED); the test asserts totalContracts=7. The compound happy-path test validates the overall shape — individual metrics are verified in TC101–TC106. Catches: endpoint not mapped, missing DTO fields, or date filter ignoring the April 2026 range and counting all contracts.

### Steps

```
1) Seed 7 contracts with statuses COMPLETED(×3), ACTIVE(×2), TERMINATED(×1), DISPUTED(×1), all with startDate in April 2026.
2) GET /api/contracts/analytics?startDate=2026-04-01&endDate=2026-04-30, assert 2xx.
3) Assert _FmM2.rL(j, "totalContracts", "total_contracts") == 7.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Contract analytics endpoint not mapped (404), or date filter parameters are not recognized, or aggregation query crashes (5xx).
- **totalContracts == 7**
  - *Bug it catches:* Date filter is ignored (returns all contracts), or COUNT uses a cross-join, or the filter is exclusive on boundaries.

---

## TC101 — Dashboard.totalContracts equals exact count of contracts in range

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

Five ACTIVE contracts are seeded in September 2026 and totalContracts must equal exactly 5. Isolates the count metric from TC100's compound test to give a pinpointed failure message. Catches: WHERE clause missing, or using the wrong date column (createdAt vs startDate).

### Steps

```
1) Seed 5 ACTIVE contracts in September 2026 via _FmM2S4.contract().
2) GET analytics for September 2026, assert 2xx.
3) Assert totalContracts == 5.
```

### Pass Criteria

- **totalContracts == 5**
  - *Bug it catches:* Date range filter is missing or uses the wrong column — all contracts in the DB are counted instead of just the 5 in September.

---

## TC102 — Dashboard.averageContractValue = SUM(agreedAmount)/COUNT

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

Three contracts are seeded with agreedAmount 100, 200, and 300; the expected average is 200.0. Tests that AVG(agreedAmount) is used rather than SUM. Accepts field names averageContractValue or average_contract_value.

### Steps

```
1) Seed 3 ACTIVE contracts with agreed amounts 100, 200, 300 in September 2026.
2) GET analytics, assert 2xx.
3) Assert averageContractValue == 200.0 within 0.01.
```

### Pass Criteria

- **averageContractValue == 200.0**
  - *Bug it catches:* Field returns SUM (600) instead of AVG; or integer division truncates; or the field is omitted entirely.

---

## TC103 — Dashboard.completionRate = COMPLETED / total

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

Three COMPLETED and five ACTIVE contracts yield completionRate = 3/8 = 0.375. Tests the rate formula: numerator is COMPLETED count, denominator is total count. Accepts field names completionRate or completion_rate.

### Steps

```
1) Seed 3 COMPLETED and 5 ACTIVE contracts in September 2026 via _FmM2S4.contract().
2) GET analytics, assert 2xx.
3) Assert completionRate == 0.375 within 0.01.
```

### Pass Criteria

- **completionRate == 0.375**
  - *Bug it catches:* Rate uses wrong denominator (COMPLETED/COMPLETED = 1.0), or uses ACTIVE as denominator, or integer division returns 0.

---

## TC104 — Dashboard.averageContractDurationDays = avg(endDate-startDate) for COMPLETED only

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

Two COMPLETED contracts (7-day and 14-day durations) and one ACTIVE contract (no endDate) are seeded; the average duration should be ~10.5 days (average of 7+14 for COMPLETED only). ACTIVE contracts have no endDate and must be excluded. The tolerance range is 9.5–11.5 to account for boundary interpretation.

### Steps

```
1) Seed COMPLETED contract 1 (2026-09-01 to 2026-09-08, 7 days) and COMPLETED contract 2 (2026-09-01 to 2026-09-15, 14 days) and one ACTIVE (null endDate).
2) GET analytics, assert 2xx.
3) Assert averageContractDurationDays in [9.5, 11.5].
```

### Pass Criteria

- **averageContractDurationDays in [9.5, 11.5]**
  - *Bug it catches:* ACTIVE contracts (with null endDate) are included in the duration calculation causing NPE or incorrect averaging; or the duration uses wrong date columns; or the field is omitted.

---

## TC105 — averageContractDurationDays=0 when no COMPLETED contracts (avoid /0)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

When there are no COMPLETED contracts in the range, averageContractDurationDays must return 0.0 (not null, not NPE/5xx, not NaN). A single ACTIVE contract with no endDate is seeded. Tests the zero-division case in the duration average.

### Steps

```
1) Seed 1 ACTIVE contract in September 2026 (no endDate).
2) GET analytics, assert 2xx.
3) Assert averageContractDurationDays == 0.0 within 0.01.
```

### Pass Criteria

- **averageContractDurationDays == 0.0**
  - *Bug it catches:* Division by zero when COUNT(COMPLETED) = 0, causing NaN or ArithmeticException (5xx); or returning null instead of 0.

---

## TC106 — Dashboard.contractsByStatus contains entries for ACTIVE/COMPLETED/TERMINATED/DISPUTED

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

One contract of each of the 4 statuses (ACTIVE, COMPLETED, TERMINATED, DISPUTED) is seeded; contractsByStatus must contain all 4 keys with count=1. Per-key assertions give precise failure messages. Accepts field names contractsByStatus or contracts_by_status.

### Steps

```
1) Seed 4 contracts in September 2026, one each with status ACTIVE, COMPLETED, TERMINATED, DISPUTED.
2) GET analytics, assert 2xx.
3) Extract contractsByStatus via _FmM2.rO(), assert not null, assert each of 4 keys present with value 1.
```

### Pass Criteria

- **contractsByStatus not null**
  - *Bug it catches:* Field omitted from DTO or named differently.
- **Each of 4 status keys present with count=1**
  - *Bug it catches:* GROUP BY only returns statuses with data (missing zero-fill), or uses wrong GROUP BY column.

---

## TC107 — Dashboard with no contracts in range returns all-zero counts

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

Future date range (2099) guarantees no contracts. totalContracts must equal 0 (not 404, not 5xx). Tests null-safety of all aggregate expressions when the underlying query returns no rows.

### Steps

```
1) GET analytics for January 2099, assert 2xx.
2) Assert totalContracts == 0.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Controller returns 404 for empty result.
- **totalContracts == 0**
  - *Bug it catches:* Count query is range-agnostic (counts all contracts).

---

## TC108 — Dashboard with startDate > endDate returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-04-01&endDate=2026-03-01`

### What it tests

Inverted date range must return strictly 400. Same pattern as TC63 applied to the contract analytics endpoint.

### Steps

```
1) GET analytics with startDate=2026-04-01&endDate=2026-03-01, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Inverted range passed to query returns empty 2xx, or causes DB exception (5xx).

---

## TC109 — Dashboard without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-04-01&endDate=2026-04-30 (no auth)`

### What it tests

Contract analytics endpoint must require authentication. Unauthenticated GET must return strictly 401.

### Steps

```
1) GET /api/contracts/analytics via httpGet (no Authorization header).
2) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Security config doesn't cover /api/contracts/analytics.

---

## TC110 — Dashboard with malformed JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-04-01&endDate=2026-04-30 with token xxx.yyy.zzz`

### What it tests

Contract analytics endpoint must reject a malformed JWT with 401. Same pattern as TC65/TC78 applied to the contract service.

### Steps

```
1) GET /api/contracts/analytics with "xxx.yyy.zzz" as token.
2) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx on the contract service.

---

## TC111 — First dashboard call writes ANALYTICS_VIEWED to contract_events

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-07-01&endDate=2026-07-31, MongoDB count on s4EventsCollection`

### What it tests

Contract analytics must log ANALYTICS_VIEWED to s4EventsCollection (contract_events). Count before and after the call, assert count increased. Same pattern as TC66 applied to the S4 service.

### Steps

```
1) Assert mongo != null.
2) Count {action: "ANALYTICS_VIEWED"} in theme().get("s4EventsCollection") before.
3) GET analytics, assert 2xx.
4) Count after, assert increased.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* ANALYTICS_VIEWED not logged for contract analytics; wrong collection used.

---

## TC112 — Second dashboard call (cache hit) still logs ANALYTICS_VIEWED

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/contracts/analytics?startDate=2026-07-01&endDate=2026-07-31`

### What it tests

ANALYTICS_VIEWED must be logged on every call including cache hits, matching the pattern of TC67 for the contract service.

### Steps

```
1) Call analytics twice with same parameters.
2) Count events after each call, assert count increased after the second.
```

### Pass Criteria

- **after2 > after1**
  - *Bug it catches:* Event logging inside @Cacheable method body — bypassed on cache hit.

---

## TC113 — Two identical dashboard requests return identical bodies

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/contracts/analytics?startDate=2026-07-01&endDate=2026-07-31`

### What it tests

Same pattern as TC69/TC96: two identical requests must return the same body.

### Steps

```
1) GET analytics twice, compare r1.body() and r2.body(), both assert 2xx.
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* Cache not wired — each call re-aggregates non-deterministically.

---

## TC114 — Insert contract after first call → cached body still returned

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/contracts/analytics?startDate=2026-11-01&endDate=2026-11-30 with _FmM2S4.contract() between calls`

### What it tests

The cache must serve the pre-insert result even after a new contract is inserted between the two calls. Records totalContracts from the first call (t1), inserts a new contract in November 2026, makes a second call, asserts totalContracts still equals t1. This proves the cache is not immediately invalidated on write.

### Steps

```
1) GET analytics for November 2026, capture t1 = totalContracts.
2) Insert a new ACTIVE contract in November 2026 via _FmM2S4.contract().
3) GET analytics again, assert totalContracts == t1 (cache hit, no re-aggregation).
```

### Pass Criteria

- **t1 == t2**
  - *Bug it catches:* The cache is invalidated on every write (by a @CacheEvict on the save method), meaning the second call re-aggregates and t2 = t1+1 — this would indicate over-eager cache invalidation.

---

## TC115 — Contract with startDate exactly at range start is included

**Tags:** `public` `features_m2`  
**Endpoint(s):** `_FmM2S4.contract() with startDate 2026-05-01, GET /api/contracts/analytics?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

The date range filter must be inclusive on the lower bound for contracts. A contract with startDate=2026-05-01 must be counted when querying with startDate=2026-05-01. Asserts totalContracts == 1. Same pattern as TC61 for proposals.

### Steps

```
1) Seed one ACTIVE contract with startDate=2026-05-01.
2) GET analytics for May 2026, assert 2xx.
3) Assert totalContracts == 1.
```

### Pass Criteria

- **totalContracts == 1**
  - *Bug it catches:* Lower bound is exclusive (WHERE startDate > :startDate), excluding the contract seeded on exactly the boundary date.

---

## TC116 — Contracts outside [startDate, endDate] are excluded

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two _FmM2S4.contract() calls (one inside range, one outside), GET /api/contracts/analytics?startDate=2026-06-01&endDate=2026-06-30`

### What it tests

One contract in June (in-range) and one in August (out-of-range) are seeded; querying for June must return totalContracts=1. Catches: WHERE clause is missing or uses inclusive range that captures August.

### Steps

```
1) Seed contract A (June 15) and contract B (August 15).
2) GET analytics for June, assert 2xx.
3) Assert totalContracts == 1.
```

### Pass Criteria

- **totalContracts == 1**
  - *Bug it catches:* Date filter is absent and both contracts are counted (returns 2), or the filter uses the wrong column.

---

## TC117 — Dashboard.contractsByStatus.DISPUTED counts only DISPUTED contracts

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/analytics?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

Three DISPUTED and four ACTIVE contracts are seeded; contractsByStatus.DISPUTED must equal exactly 3. Isolates the DISPUTED count to catch GROUP BY confusion between statuses.

### Steps

```
1) Seed 3 DISPUTED + 4 ACTIVE contracts in September 2026.
2) GET analytics, assert 2xx.
3) Extract contractsByStatus.DISPUTED, assert == 3.
```

### Pass Criteria

- **contractsByStatus.DISPUTED == 3**
  - *Bug it catches:* DISPUTED is counted as ACTIVE or vice versa; or all statuses are aggregated into one bucket (GROUP BY bug).

---

## TC118 — Track milestone writes one row to Cassandra contract_milestone_events

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track, Cassandra row count via _FmM2S4.cassRowCount()`

### What it tests

The S4-F11 track-milestone endpoint must write one row to the Cassandra contract_milestone_events table and return HTTP 201. The test seeds a contract, tracks one milestone (milestoneOrder=1, status=COMPLETED, recordedBy=1), then calls _FmM2S4.cassRowCount(this, cid) to count rows for that contract_id in Cassandra. Strict 201 per assertEquals(201, ...).

### Steps

```
1) Seed ACTIVE contract (cid) via _FmM2S4.contract().
2) POST /api/contracts/<cid>/milestones/track with body {milestoneOrder:1, status:"COMPLETED", recordedBy:1, notes:"phase one done"} and admin token.
3) Assert strictly 201.
4) _FmM2S4.cassRowCount(this, cid), assert == 1.
```

### Pass Criteria

- **Status strictly 201**
  - *Bug it catches:* Returns 200 (missing @ResponseStatus(CREATED)) or 404 (contract lookup failed) or 5xx (Cassandra write failed).
- **Cassandra row count == 1**
  - *Bug it catches:* The controller returned 201 but the Cassandra write was fire-and-forget and never completed; or the row was inserted with the wrong contract_id partition key.

---

## TC119 — Three sequential tracks append three rows to Cassandra

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Three POST /api/contracts/{cid}/milestones/track with different statuses`

### What it tests

Each track call must append a new row (not upsert/overwrite). Three sequential tracks with milestoneOrder 1/2/3 and statuses PENDING/IN_PROGRESS/COMPLETED must result in 3 distinct rows. Cassandra's time-series append semantics mean each write uses a new auto-generated timestamp as the clustering key, so all 3 rows co-exist for the same contract_id. Catches: the service uses UPDATE (upsert) instead of INSERT — the third track overwrites the first, leaving only 1 row.

### Steps

```
1) Seed one ACTIVE contract.
2) POST track for status PENDING (milestoneOrder=1), assert 201.
3) POST track for status IN_PROGRESS (milestoneOrder=2), assert 201.
4) POST track for status COMPLETED (milestoneOrder=3), assert 201.
5) _FmM2S4.cassRowCount(this, cid), assert == 3.
```

### Pass Criteria

- **All 3 POST calls return 201**
  - *Bug it catches:* The second or third track fails (e.g., duplicate key error if using the wrong primary key design).
- **Cassandra row count == 3**
  - *Bug it catches:* Cassandra UPDATE was used instead of INSERT — rows are upserted and the total remains 1 (or 2 if the clustering key includes milestoneOrder but not timestamp).

---

## TC120 — Cassandra row carries status column matching request body

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track with status="APPROVED", _FmM2S4.cassFirstRow()`

### What it tests

After tracking a milestone with status=APPROVED, the Cassandra row must have status="APPROVED". _FmM2S4.cassFirstRow() retrieves the first row for contract_id=cid and the test reads the status column. Catches: the status is not persisted to Cassandra (column omitted from the INSERT), or is hardcoded to a default value.

### Steps

```
1) Seed ACTIVE contract, track with status="APPROVED", assert 201.
2) _FmM2S4.cassFirstRow(this, cid), assert row.get("status") == "APPROVED".
```

### Pass Criteria

- **Row status == "APPROVED"**
  - *Bug it catches:* status column is absent from the Cassandra row; or the status is not mapped from the request body DTO; or a default/hardcoded status is stored instead.

---

## TC121 — Cassandra row's recorded_by column carries the request body actorId

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track with recordedBy=42, _FmM2S4.cassFirstRow()`

### What it tests

After tracking with recordedBy=42, the Cassandra row must have recorded_by=42 (or recordedBy=42 depending on column naming). _FmM2S4.cassFirstRow() reads the first row and checks recorded_by or recordedBy key. Catches: the column is not persisted, or the authenticated user's id is used instead of the request body's recordedBy field.

### Steps

```
1) Seed ACTIVE contract, track with recordedBy=42, assert 201.
2) _FmM2S4.cassFirstRow(this, cid), check row.get("recorded_by") or row.get("recordedBy"), assert == 42.
```

### Pass Criteria

- **recorded_by == 42**
  - *Bug it catches:* recordedBy is not mapped from the request DTO (null stored); or the authenticated user id is substituted instead of the request body value; or the column name in Cassandra is misspelled.

---

## TC122 — Track with invalid status returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track with status="BANANA"`

### What it tests

A milestone track request with an unrecognized status value must return strictly 400. The controller must validate the status field against its allowed enum values before writing to Cassandra. Strict 400 per assertEquals(400, ...).

### Steps

```
1) Seed one ACTIVE contract.
2) POST track with body {milestoneOrder:1, status:"BANANA", recordedBy:1, notes:"x"}.
3) Assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* The status field is stored as a plain String with no enum validation, so any value is accepted and written to Cassandra (data corruption).

---

## TC123 — Track on non-existent contract returns 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/999999/milestones/track`

### What it tests

The track endpoint must verify that the contract exists in PostgreSQL before writing to Cassandra, returning 404 if not found. Strict 404 per assertEquals(404, ...).

### Steps

```
1) POST track for contract id 999999 with valid milestone body.
2) Assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller writes to Cassandra without first looking up the contract in PG — orphan Cassandra rows accumulate; or Optional.get() throws NPE (5xx).

---

## TC124 — Track without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track (no auth)`

### What it tests

The track-milestone endpoint must require authentication. Unauthenticated POST returns strictly 401. A real contract is seeded first so the 401 comes from the auth filter.

### Steps

```
1) Seed one ACTIVE contract.
2) POST track via httpPost (no Authorization header).
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Milestone tracking is publicly accessible without a token.

---

## TC125 — Track with bogus JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track with token xxx.yyy.zzz`

### What it tests

Malformed JWT must be rejected with 401. Same pattern as other auth-rejection tests applied to the track endpoint.

### Steps

```
1) Seed one ACTIVE contract.
2) POST track via httpPostAuth(..., "xxx.yyy.zzz").
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx on the contract service track endpoint.

---

## TC126 — Track writes MILESTONE_TRACKED to MongoDB contract_events

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track, MongoDB count on s4EventsCollection`

### What it tests

The S4-F11 spec requires logging a MILESTONE_TRACKED event to MongoDB after each successful track call. Count {action: "MILESTONE_TRACKED"} documents before and after, assert count increased.

### Steps

```
1) Assert mongo != null. Count before with {action: "MILESTONE_TRACKED"} in s4EventsCollection.
2) Seed contract, POST track (assert 201).
3) Count after, assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* MILESTONE_TRACKED event not logged, wrong collection, wrong action name (e.g., "TRACK_RECORDED").

---

## TC127 — Successful track returns HTTP 201 Created

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track`

### What it tests

The track endpoint must return strictly HTTP 201 Created (not 200 OK) for a successful write. The spec mandates 201 for creation operations. Strict per assertEquals(201, ...).

### Steps

```
1) Seed one ACTIVE contract.
2) POST track with valid milestone body, admin token.
3) Assert strictly 201.
```

### Pass Criteria

- **Status strictly 201**
  - *Bug it catches:* Controller uses @PostMapping without @ResponseStatus(HttpStatus.CREATED), defaulting to 200 OK — a spec violation that breaks API clients expecting 201.

---

## TC128 — Timeline returns array containing a ContractMilestoneDTO entry

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track (setup), GET /api/contracts/{cid}/milestones/timeline?startTime=...&endTime=...`

### What it tests

The S4-F12 timeline endpoint must return an array (or paginated envelope) containing at least one ContractMilestoneDTO after a track call. This is the happy-path smoke test for the timeline read. The time range is set to 2026-01-01T00:00:00 to 2099-12-31T23:59:59 to guarantee the just-tracked milestone falls within range.

### Steps

```
1) Seed ACTIVE contract, track one milestone (assert 201).
2) GET timeline with broad time range, assert 2xx.
3) Unwrap content if present, assert arr.isArray() && arr.size() >= 1.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Timeline endpoint not mapped (404), or Cassandra query crashes (5xx), or the time range is parsed incorrectly.
- **Array has >= 1 entry**
  - *Bug it catches:* Cassandra row was written but the read query used the wrong partition key or time range filter excluded the just-written row.

---

## TC129 — Most recent track appears first (DESC order)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two POST /api/contracts/{cid}/milestones/track, GET /api/contracts/{cid}/milestones/timeline`

### What it tests

The S4-F12 timeline must return entries sorted by timestamp DESC (most recent first). Two tracks are posted with a 50ms sleep between them; the second has status=COMPLETED. The first timeline entry must have status=COMPLETED. Catches: Cassandra query uses ORDER BY timestamp ASC instead of DESC.

### Steps

```
1) Seed ACTIVE contract. POST track b1 (status=PENDING), sleep 50ms, POST track b2 (status=COMPLETED), both assert 201.
2) GET timeline, assert 2xx, at least 2 entries.
3) Assert arr.get(0).get("status").asText() == "COMPLETED".
```

### Pass Criteria

- **First entry status == "COMPLETED"**
  - *Bug it catches:* Timeline is sorted ASC — the first entry is the oldest (PENDING), not the most recent (COMPLETED). The Cassandra clustering order is ASC when it should be DESC (or the query lacks an explicit ORDER BY timestamp DESC).

---

## TC130 — startTime/endTime range narrows results

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track (setup), GET /api/contracts/{cid}/milestones/timeline?startTime=2099-01-01T00:00:00&endTime=2099-12-31T23:59:59`

### What it tests

A future time range (year 2099) must return 0 entries even though a just-tracked milestone exists. This confirms the startTime/endTime filter is applied as a Cassandra range query on the timestamp clustering column.

### Steps

```
1) Seed ACTIVE contract, track one milestone (assert 201).
2) GET timeline with startTime=2099-01-01T00:00:00&endTime=2099-12-31T23:59:59, assert 2xx.
3) Assert arr.size() == 0.
```

### Pass Criteria

- **arr.size() == 0**
  - *Bug it catches:* Time range filter is ignored — all rows for the contract are returned regardless of the timestamp range, including the just-tracked row.

---

## TC131 — Each entry has timestamp, milestoneOrder, status, recordedBy, notes

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/contracts/{cid}/milestones/track with milestoneOrder=7, status="IN_PROGRESS", recordedBy=3, notes="hello", GET /api/contracts/{cid}/milestones/timeline`

### What it tests

Each timeline entry DTO must include all 5 required fields: timestamp, milestoneOrder (or milestone_order), status, recordedBy (or recorded_by), and notes. Per-field assertTrue assertions pinpoint which specific field is missing.

### Steps

```
1) Seed ACTIVE contract, track with milestoneOrder=7, status="IN_PROGRESS", recordedBy=3, notes="hello", assert 201.
2) GET timeline with broad range, assert 2xx, at least 1 entry.
3) Assert each field present in arr.get(0).
```

### Pass Criteria

- **timestamp present**
  - *Bug it catches:* Clustering key not mapped into the DTO response.
- **milestoneOrder (or alias) present**
  - *Bug it catches:* Field excluded from DTO mapping.
- **status present**
  - *Bug it catches:* Status column not projected in the CQL SELECT.
- **recordedBy (or alias) present**
  - *Bug it catches:* Actor id column omitted.
- **notes present**
  - *Bug it catches:* Notes column not in CQL SELECT or DTO mapping.

---

## TC132 — Timeline for non-existent contract returns 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/999999/milestones/timeline?startTime=2026-01-01T00:00:00&endTime=2099-12-31T23:59:59`

### What it tests

Timeline must verify the contract exists in PostgreSQL before querying Cassandra, returning 404 for a non-existent contract id. Strict 404 per assertEquals(404, ...).

### Steps

```
1) GET timeline for contract id 999999 with broad time range.
2) Assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* Controller queries Cassandra directly without verifying the PG contract exists — returns an empty array (2xx) for non-existent contracts.

---

## TC133 — Timeline without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/{cid}/milestones/timeline?... (no auth)`

### What it tests

Timeline endpoint requires authentication. Unauthenticated GET returns strictly 401. A real contract is seeded to ensure the 401 is from the auth filter.

### Steps

```
1) Seed one ACTIVE contract.
2) GET timeline via httpGet (no Authorization header).
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Timeline is publicly accessible without a token.

---

## TC134 — Timeline with bogus JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/contracts/{cid}/milestones/timeline?... with token xxx.yyy.zzz`

### What it tests

Malformed JWT must be rejected with 401 on the timeline endpoint.

### Steps

```
1) Seed one ACTIVE contract.
2) GET timeline via httpGetAuth(..., "xxx.yyy.zzz").
3) Assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx.

---

## TC135 — Two identical timeline requests return identical bodies

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/contracts/{cid}/milestones/timeline?... with same parameters`

### What it tests

The S4-F12 timeline must be cached: two identical requests return identical bodies. Same pattern as TC69/TC96/TC113 applied to the timeline endpoint.

### Steps

```
1) Seed ACTIVE contract, track one milestone (assert 201).
2) GET timeline twice with same URL, both assert 2xx.
3) assertEquals(r1.body(), r2.body()).
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* Cache not wired to the timeline endpoint — each call re-queries Cassandra, results may differ in ordering or timestamp formatting.

---

## M1 Features — TC299–TC337

## TC299 — Returns the user's ACTIVE contract

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/user/{userId}/active`

### What it tests

Verifies S4-F1: a freelancer with one ACTIVE contract receives that contract in the response. Tests the happy-path lookup by user ID filtered on status=ACTIVE. A common student bug is returning all contracts for the user rather than filtering by status, or returning a list instead of a single object. The assertion style checks both the HTTP status and the returned contract ID to confirm the correct record is selected.

### Steps

```
1) Seed FREELANCER user with _FmM1Seed.seedUser.
2) Seed job with _FmM1Seed.seedJob.
3) Seed ACTIVE contract with _FmM1Seed.seedContract, capture cid.
4) Call httpGetAuth("/api/contracts/user/{fr}/active", adminToken()).
5) Call assert2xx(r, "TC299").
6) Extract id via _FmM2.rL(parseNode(r.body()), "id", "contractId", "contract_id").
7) assertEquals(cid, id).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint returns 4xx or 5xx when a valid ACTIVE contract exists.
- **assertEquals(cid, id) — exact contract ID match**
  - *Bug it catches:* Student returns wrong contract (e.g., wrong user, or most recently created regardless of status).

---

## TC300 — When multiple ACTIVE contracts exist, returns most recent by createdAt

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/user/{userId}/active`

### What it tests

Verifies S4-F1 tie-breaking rule: when a user has multiple ACTIVE contracts the endpoint must return the one with the most recent createdAt. Tests ordering/selection logic beyond simple status filtering. Students commonly return the first inserted record or an arbitrary row rather than sorting by createdAt DESC and taking the top. The assertion compares the returned ID against the known latest-seeded contract.

### Steps

```
1) Seed FREELANCER user with _FmM1Seed.seedUser.
2) Seed job with _FmM1Seed.seedJob.
3) Seed three ACTIVE contracts in ascending date order via _FmM1Seed.seedContract; capture the last as latest.
4) Call httpGetAuth("/api/contracts/user/{fr}/active", adminToken()).
5) Call assert2xx(r, "TC300").
6) Extract id via _FmM2.rL(parseNode(r.body()), "id", "contractId", "contract_id").
7) assertEquals(latest, id).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint errors when multiple ACTIVE contracts exist.
- **assertEquals(latest, id) — most-recent contract returned**
  - *Bug it catches:* Student returns first inserted rather than most recent by createdAt.

---

## TC301 — User with no ACTIVE contracts returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/user/{userId}/active`

### What it tests

Verifies S4-F1 error path: a user who has only COMPLETED contracts (no ACTIVE) must receive 404, not an empty body or 200. Tests that the status filter is applied correctly and a missing result is surfaced as 404 rather than null/empty. Students often return 200 with null or an empty object instead of a proper 404. The assertion checks the HTTP status code directly.

### Steps

```
1) Seed FREELANCER user with _FmM1Seed.seedUser.
2) Seed job with _FmM1Seed.seedJob.
3) Seed COMPLETED contract via _FmM1Seed.seedContract.
4) Call httpGetAuth("/api/contracts/user/{fr}/active", adminToken()).
5) assertEquals(404, r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode()) — 404 when no ACTIVE contract exists**
  - *Bug it catches:* Student returns 200 with null or empty body instead of 404 when no ACTIVE contract is found for the user.

---

## TC302 — Non-existent userId returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/user/9999999/active`

### What it tests

Verifies S4-F1 error path for unknown user: a request with a user ID that does not exist in the database must return 404. Tests that the service performs user existence validation before querying contracts. Students may skip user validation and simply return an empty result (200) when the user is not found. The assertion checks the raw status code.

### Steps

```
1) Call httpGetAuth("/api/contracts/user/9999999/active", adminToken()).
2) assertEquals(404, r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode()) — 404 for unknown userId**
  - *Bug it catches:* Student skips user-existence check and returns 200/empty when the user does not exist.

---

## TC303 — Adds progressPercentage and lastActivityDate to metadata JSONB

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/{contractId}/progress`

### What it tests

Verifies S4-F2 happy path: PUTting a progress payload merges progressPercentage and lastActivityDate into the contract's JSONB metadata column and returns the updated metadata in the response. Tests that JSONB merge (not replace) semantics are applied and the response body exposes the metadata object. Students often replace the entire metadata rather than merging, losing existing keys. The assertion inspects the returned metadata node for the expected value.

### Steps

```
1) Seed user, job, and ACTIVE contract via _FmM1Seed.
2) Call httpPutAuth("/api/contracts/{cid}/progress", body, adminToken()) with {"progressPercentage":50,"lastActivityDate":"2026-03-15"}.
3) Call assert2xx(r, "TC303").
4) Extract meta via _FmM2.rO(parseNode(r.body()), "metadata", "metaData").
5) assertNotNull(meta).
6) assertEquals(50, meta.path("progressPercentage").asInt()).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint rejects a valid progress update or errors on JSONB write.
- **assertNotNull(meta) — metadata key present in response**
  - *Bug it catches:* Student omits metadata from the response DTO.
- **assertEquals(50, meta.path("progressPercentage").asInt()) — value correctly stored**
  - *Bug it catches:* Student stores the value under a different key or as a string instead of integer.

---

## TC304 — Re-PUT with same key overwrites the value

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/{contractId}/progress`

### What it tests

Verifies S4-F2 overwrite semantics: when the same JSONB key already exists, a second PUT must overwrite it with the new value. Tests that the merge operation replaces duplicate keys rather than ignoring or appending them. Students may use PostgreSQL `

### Steps

```
` concatenation which does overwrite, but some implement custom merge logic that fails to replace existing keys. The assertion checks the new value is reflected.
```

### Pass Criteria

  ACTIVE contract seeded; metadata pre-set to {"progressPercentage":30} via _FmM1Seed.setContractMetadata

---

## TC305 — Updating progress on non-existent contract returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/9999999/progress`

### What it tests

Verifies S4-F2 error path: a progress update targeting a contract that does not exist must return 404. Tests that the service validates contract existence before attempting any JSONB update. Students often let the database silently update zero rows and return 200, which masks the missing-resource bug. The assertion checks the HTTP status code directly.

### Steps

```
1) Call httpPutAuth("/api/contracts/9999999/progress", {"progressPercentage":50}, adminToken()).
2) assertEquals(404, r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode()) — 404 for missing contract**
  - *Bug it catches:* Student updates zero rows silently and returns 200 instead of 404 when the contract ID does not exist.

---

## TC306 — Merging new keys preserves untouched existing keys

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/{contractId}/progress`

### What it tests

Verifies S4-F2 JSONB merge preserves pre-existing keys that are not part of the incoming payload. Ensures students use a merge strategy (e.g., PostgreSQL jsonb_set or `

### Steps

```
) rather than a full replacement. A full-replace bug would delete paymentTermsandndaSignedwhen onlyprogressPercentage` is sent. The assertion checks both the newly added key and the untouched existing key.
```

### Pass Criteria

  ACTIVE contract seeded; metadata pre-set to {"paymentTerms":"MILESTONE","ndaSigned":true}

---

## TC307 — Search returns contracts in amount range with matching status

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/search?minAmount=2000&maxAmount=6000&status=ACTIVE`

### What it tests

Verifies S4-F3 combined filtering: the search endpoint must respect both the amount range and the status filter simultaneously. Seeds contracts with different statuses and amounts to ensure the intersection logic works. Students commonly apply only one filter (amount or status) while ignoring the other, returning too many results. The assertion checks that at least one result is returned (the ACTIVE contract within range).

### Steps

```
1) Seed user, job via _FmM1Seed.
2) Seed three contracts with varying status and amount via _FmM1Seed.seedContract.
3) Call httpGetAuth("/api/contracts/search?minAmount=2000&maxAmount=6000&status=ACTIVE", adminToken()).
4) Call assert2xx(r, "TC307").
5) Parse list (handle paginated or flat array via arr.isArray() / arr.get("content")).
6) assertTrue(list.size() >= 1).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Search endpoint errors when both filters are applied together.
- **assertTrue(list.size() >= 1) — at least one matching contract returned**
  - *Bug it catches:* Student ignores the status filter and returns zero results from the wrong intersection, or ignores the amount filter and returns the COMPLETED contract.

---

## TC308 — Search without status filter returns all in range

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/search?minAmount=1000&maxAmount=4000`

### What it tests

Verifies S4-F3 optional status parameter: omitting the status query param must return all contracts regardless of status as long as agreedAmount falls within [minAmount, maxAmount]. Tests that the status filter is truly optional (not mandatory). Students may hard-code a default status filter or throw 400 when status is absent. The assertion checks that both the ACTIVE and COMPLETED contracts within range appear.

### Steps

```
1) Seed user, job, two contracts (ACTIVE 1500, COMPLETED 2500) via _FmM1Seed.
2) Call httpGetAuth("/api/contracts/search?minAmount=1000&maxAmount=4000", adminToken()).
3) Call assert2xx(r, "TC308").
4) Parse list from response (flat or paginated).
5) assertTrue(list.size() >= 2).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint returns 400 when status param is omitted, treating it as required.
- **assertTrue(list.size() >= 2) — both contracts returned regardless of status**
  - *Bug it catches:* Student applies a default WHERE status=ACTIVE filter even when no status param is provided.

---

## TC309 — Search results sort DESC by agreedAmount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/search?minAmount=500&maxAmount=10000`

### What it tests

Verifies S4-F3 sort order: results must be sorted descending by agreedAmount. Seeds three contracts with amounts 1000, 5000, 3000 and validates the traversal order is 5000 → 3000 → 1000. Students often omit ORDER BY entirely or sort ascending. The assertion iterates all returned items and asserts each amount is less than or equal to the previous, failing on the first violation.

### Steps

```
1) Seed user, job, three ACTIVE contracts via _FmM1Seed.seedContract.
2) Call httpGetAuth("/api/contracts/search?minAmount=500&maxAmount=10000", adminToken()).
3) Call assert2xx(r, "TC309").
4) Parse list (flat or paginated).
5) assertTrue(list.size() >= 3).
6) Iterate list, extracting agreedAmount/agreed_amount/amount; assert each <= prev.
```

### Pass Criteria

- **assertTrue(list.size() >= 3) — all seeded contracts in range returned**
  - *Bug it catches:* Range filter is too narrow and excludes valid records.
- **Descending-order iteration assertion — each amount ≤ previous**
  - *Bug it catches:* Student omits ORDER BY or sorts ascending instead of descending.

---

## TC310 — Contracts outside [min,max] are excluded

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/search?minAmount=2000&maxAmount=5000`

### What it tests

Verifies S4-F3 exclusion boundary: contracts with agreedAmount strictly outside the requested range (100 and 9999) must not appear in results. Tests that the range predicate is inclusive-bounded and correctly filters extremes. Students sometimes apply only a one-sided bound (e.g., only >= minAmount) or use > instead of >=, causing boundary leaks. The assertion iterates every returned item and validates each amount is within the range.

### Steps

```
1) Seed user, job, two contracts (100, 9999) via _FmM1Seed.seedContract.
2) Call httpGetAuth("/api/contracts/search?minAmount=2000&maxAmount=5000", adminToken()).
3) Call assert2xx(r, "TC310").
4) Parse list.
5) For each item, extract amount and assertTrue(amt >= 2000.0 && amt <= 5000.0).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint errors on an empty result set instead of returning 200 with empty list.
- **Per-item range assertion — each amount between 2000 and 5000 inclusive**
  - *Bug it catches:* Student uses > / < instead of >= / <=, or applies only one bound, including out-of-range contracts.

---

## TC311 — Batch updates 3 ACTIVE contracts to COMPLETED

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/batch-status`

### What it tests

Verifies S4-F4 happy path: sending a JSON array of {contractId, status} pairs must successfully update all three contracts to COMPLETED and return 2xx. Tests that the batch endpoint processes all items atomically in a single request. Students sometimes implement a loop of individual updates without a true batch endpoint, or fail to accept the array body format. The assertion only checks the HTTP success status for the batch call.

### Steps

```
1) Seed user, job, three ACTIVE contracts via _FmM1Seed.seedContract.
2) Build JSON array body with {contractId, status:"COMPLETED"} for each.
3) Call httpPutAuth("/api/contracts/batch-status", body, adminToken()).
4) assert2xx(r, "TC311").
```

### Pass Criteria

- **assert2xx — 2xx required for batch update of 3 contracts**
  - *Bug it catches:* Student did not implement a batch endpoint and returns 404 or 405, or fails to process multiple items in one request.

---

## TC312 — Batch with non-existent contract id returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/batch-status`

### What it tests

Verifies S4-F4 error path: if any contract ID in the batch payload does not exist, the entire batch must be rejected with 404. Tests fail-fast / all-or-nothing semantics on the batch endpoint. Students may silently skip missing IDs (updating only valid ones and returning 200) instead of failing the whole batch. The assertion checks the status code is exactly 404.

### Steps

```
1) Seed user, job, one ACTIVE contract c1.
2) Build batch body: [{contractId:c1, status:"COMPLETED"}, {contractId:9999999, status:"COMPLETED"}].
3) Call httpPutAuth("/api/contracts/batch-status", body, adminToken()).
4) assertEquals(404, r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode()) — 404 when any ID in batch is missing**
  - *Bug it catches:* Student skips the missing ID, updates only the valid contract, and returns 200 instead of failing the entire batch.

---

## TC313 — Batch with COMPLETED→ACTIVE transition returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/batch-status`

### What it tests

Verifies S4-F4 transition validation: transitioning a contract backward from COMPLETED to ACTIVE is an invalid state change and must return 400. Tests that the batch endpoint applies business-rule validation per item, not just persistence. Students often omit state-machine logic entirely and accept any status value. The assertion checks for exactly 400.

### Steps

```
1) Seed user, job, one COMPLETED contract (with endDate set) via _FmM1Seed.seedContract.
2) Build batch body: [{contractId:cid, status:"ACTIVE"}].
3) Call httpPutAuth("/api/contracts/batch-status", body, adminToken()).
4) assertEquals(400, r.statusCode()).
```

### Pass Criteria

- **assertEquals(400, r.statusCode()) — 400 for invalid COMPLETED→ACTIVE transition**
  - *Bug it catches:* Student does not implement transition validation and accepts any status update, allowing backward state changes.

---

## TC314 — Transition to COMPLETED sets endDate

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/batch-status`

### What it tests

Verifies S4-F4 side-effect: when a contract is transitioned to COMPLETED via the batch endpoint, the endDate column must be populated (set to today or the transition date). Tests that the service sets endDate as a business rule, not leaving it null. Students often update only the status field and forget to set endDate, violating the spec. The assertion queries the DB directly via JDBC to check the endDate column is non-null.

### Steps

```
1) Seed user, job, one ACTIVE contract (endDate null) via _FmM1Seed.seedContract.
2) Build batch body: [{contractId:cid, status:"COMPLETED"}].
3) Call httpPutAuth("/api/contracts/batch-status", body, adminToken()); assert2xx.
4) Query DB: jdbc.queryForObject("SELECT endCol FROM Contract WHERE id=?", Object.class, cid) using tableName("Contract") and columnByField("Contract", "endDate").
5) assertNotNull(endVal).
```

### Pass Criteria

- **assert2xx — batch succeeded**
  - *Bug it catches:* Endpoint errors during the ACTIVE→COMPLETED transition.
- **assertNotNull(endVal) — endDate set in DB after COMPLETED transition**
  - *Bug it catches:* Student updates only the status column and leaves endDate as null, violating the contract lifecycle spec.

---

## TC315 — Batch response includes a numeric count of updated contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/contracts/batch-status`

### What it tests

Verifies S4-F4 response shape: the batch endpoint must return a body containing a numeric field indicating how many contracts were updated. Tests that students don't return an empty 204 or ignore the count in the response. The assertion uses a tolerant multi-key probe (count, updated, updatedCount) to accommodate field naming variation, falling back to array size. Checks that the count equals the number of contracts actually submitted.

### Steps

```
1) Seed user, job, two ACTIVE contracts via _FmM1Seed.seedContract.
2) Build batch body for c1 and c2 targeting COMPLETED.
3) Call httpPutAuth("/api/contracts/batch-status", body, adminToken()).
4) assert2xx(r, "TC315").
5) Parse JSON; probe j.has("count"), j.has("updated"), j.isArray(), j.has("updatedCount") for value 2.
6) assertEquals(2L, count).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* Endpoint returns 204 No Content and the count cannot be parsed.
- **assertEquals(2L, count) — count matches number of updated contracts**
  - *Bug it catches:* Student hard-codes 0, omits the count field entirely, or returns a list instead of a count object.

---

## TC316 — operator=eq returns contracts with matching JSONB value

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/metadata/search?key=paymentTerms&operator=eq&value=MILESTONE`

### What it tests

Verifies S4-F5 JSONB metadata search with eq operator: the endpoint must return only contracts whose metadata JSONB has the given key set to exactly the given string value. Seeds two contracts with different paymentTerms values and checks that only the matching one appears. Students often implement a plain SQL LIKE or full-document equality instead of per-key JSONB comparison. The assertion checks at least one result is returned (the MILESTONE contract).

### Steps

```
1) Seed user, job, two ACTIVE contracts via _FmM1Seed.
2) Set c1 metadata via _FmM1Seed.setContractMetadata(this, c1, {"paymentTerms":"MILESTONE"}).
3) Set c2 metadata via _FmM1Seed.setContractMetadata(this, c2, {"paymentTerms":"FIXED"}).
4) Call httpGetAuth("/api/contracts/metadata/search?key=paymentTerms&operator=eq&value=MILESTONE", adminToken()).
5) assert2xx(r, "TC316").
6) Parse list; assertTrue(list.size() >= 1).
```

### Pass Criteria

- **assert2xx — 2xx required for eq search**
  - *Bug it catches:* Endpoint errors when performing JSONB key lookup.
- **assertTrue(list.size() >= 1) — at least one matching contract returned**
  - *Bug it catches:* Student does full-document JSONB equality or uses LIKE which fails to match the per-key eq semantics.

---

## TC317 — operator=gt returns contracts with JSONB numeric > value

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/metadata/search?key=progressPercentage&operator=gt&value=50`

### What it tests

Verifies S4-F5 JSONB numeric comparison with gt operator: the endpoint must return contracts where the JSONB key holds a number strictly greater than the threshold. Seeds one contract at 80% and one at 20%, expecting only the 80% contract to appear. Students often cast the JSONB value as text and do string comparison (which fails numerically) or fail to handle the gt operator branch. The assertion checks at least one result with progressPercentage > 50.

### Steps

```
1) Seed user, job, two ACTIVE contracts.
2) Set c1 metadata to {"progressPercentage":80} via _FmM1Seed.setContractMetadata.
3) Set c2 metadata to {"progressPercentage":20} via _FmM1Seed.setContractMetadata.
4) Call httpGetAuth("/api/contracts/metadata/search?key=progressPercentage&operator=gt&value=50", adminToken()).
5) assert2xx(r, "TC317").
6) Parse list; assertTrue(list.size() >= 1).
```

### Pass Criteria

- **assert2xx — 2xx required for gt search**
  - *Bug it catches:* Endpoint errors on numeric JSONB comparison or does not handle the gt operator.
- **assertTrue(list.size() >= 1) — contract with value > 50 returned**
  - *Bug it catches:* Student casts JSONB value as text and uses string comparison, causing "80" < "50" (lexicographic) to incorrectly exclude valid results.

---

## TC318 — operator=lt returns contracts with JSONB numeric < value

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/metadata/search?key=progressPercentage&operator=lt&value=50`

### What it tests

Verifies S4-F5 JSONB numeric comparison with lt operator: the endpoint must return contracts where the JSONB key holds a number strictly less than the threshold. Seeds one contract at 15%, expects it to appear when filtering < 50. Tests the third operator branch symmetrical to gt. Students may implement gt but omit lt, or flip the comparison direction. The assertion checks at least one result is returned.

### Steps

```
1) Seed user, job, one ACTIVE contract.
2) Set c1 metadata to {"progressPercentage":15} via _FmM1Seed.setContractMetadata.
3) Call httpGetAuth("/api/contracts/metadata/search?key=progressPercentage&operator=lt&value=50", adminToken()).
4) assert2xx(r, "TC318").
5) Parse list; assertTrue(list.size() >= 1).
```

### Pass Criteria

- **assert2xx — 2xx required for lt search**
  - *Bug it catches:* Endpoint does not handle the lt operator and returns an error or empty result.
- **assertTrue(list.size() >= 1) — contract with value < 50 returned**
  - *Bug it catches:* Student implements > for all numeric operators or reverses the comparison sign for lt.

---

## TC319 — Invalid operator returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/metadata/search?key=progressPercentage&operator=xyz&value=50`

### What it tests

Verifies S4-F5 input validation: an unrecognized operator string (xyz) must be rejected with HTTP 400. Tests that the endpoint validates the operator parameter against the allowed set (eq, gt, lt) and does not silently ignore unknown operators or throw a 500. Students often let an unknown operator fall through to a default case or produce a DB error surfaced as 500. The assertion checks the status code is exactly 400.

### Steps

```
1) Call httpGetAuth("/api/contracts/metadata/search?key=progressPercentage&operator=xyz&value=50", adminToken()).
2) assertEquals(400, r.statusCode()).
```

### Pass Criteria

- **assertEquals(400, r.statusCode()) — 400 for unrecognized operator**
  - *Bug it catches:* Student does not validate the operator param; an unknown value falls through to a DB query that either crashes (500) or silently returns all results (200).

---

## TC320 — History returns contracts in date range filtered on createdAt

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/history?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Verifies S4-F6 history happy path: the endpoint returns contracts whose createdAt (or startDate) falls within the specified date range, excluding those outside the range. Seeds three contracts: two in March 2026 (one ACTIVE, one COMPLETED) and one in February 2026, expecting only the two March contracts. Students often filter on endDate instead of createdAt, or do not filter at all and return all contracts. The assertion checks at least two results are present.

### Steps

```
1) Seed user, job via _FmM1Seed.
2) Seed three contracts with different start dates via _FmM1Seed.seedContract.
3) Call httpGetAuth("/api/contracts/history?startDate=2026-03-01&endDate=2026-03-31", adminToken()).
4) assert2xx(r, "TC320").
5) Parse list (flat or paginated).
6) assertTrue(list.size() >= 2).
```

### Pass Criteria

- **assert2xx — 2xx required**
  - *Bug it catches:* History endpoint does not exist or errors on date params.
- **assertTrue(list.size() >= 2) — both March contracts returned, February excluded**
  - *Bug it catches:* Student filters on endDate column instead of createdAt/startDate, or applies no date filter and returns all three contracts (size >= 3 with no selectivity).

---

## TC321 — History with status=COMPLETED returns only COMPLETED contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/history?startDate=...&endDate=...&status=COMPLETED`

### What it tests

Verifies that the S4-F6 contract history endpoint filters results by the status query parameter, returning only contracts whose status matches the requested value. Seeds one ACTIVE and one COMPLETED contract in the same date range, then asserts every item in the response has status COMPLETED. Catches implementations that ignore the status filter and return all contracts in range. Response is unwrapped as array or paginated content node for flexibility.

### Steps

```
1) Seed user with role FREELANCER via _FmM1Seed.seedUser.
2) Seed job via _FmM1Seed.seedJob.
3) Seed one ACTIVE and one COMPLETED contract via _FmM1Seed.seedContract.
4) Obtain adminToken(), call httpGetAuth to GET /api/contracts/history?startDate=2026-03-01&endDate=2026-03-31&status=COMPLETED.
5) Call assert2xx, parseNode, unwrap array/content, iterate and assertEquals("COMPLETED", st) for each item.
```

### Pass Criteria

- **Every item in the response list has status == "COMPLETED".**
  - *Bug it catches:* Implementation applies no status filter and returns both ACTIVE and COMPLETED contracts in the date range.
- **Response is 2xx.**
  - *Bug it catches:* Endpoint rejects optional status query parameter with 400 due to missing mapping.

---

## TC322 — History results are ordered by createdAt ascending

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/history?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F6 history endpoint returns all seeded contracts in the date range, checking the result set contains at least 3 items. Seeds three ACTIVE contracts with different start dates (Mar 25, Mar 5, Mar 15) within the range. The assertion confirms none are missing from the response, indicating the endpoint does not incorrectly filter or truncate results. A follow-on check on list size >= 3 ensures the ordering does not accidentally deduplicate rows.

### Steps

```
1) Seed FREELANCER user via _FmM1Seed.seedUser.
2) Seed job via _FmM1Seed.seedJob.
3) Seed three ACTIVE contracts with out-of-order start dates via _FmM1Seed.seedContract.
4) Call httpGetAuth to GET /api/contracts/history?startDate=2026-03-01&endDate=2026-03-31.
5) assert2xx, parseNode, unwrap list, assertTrue(list.size() >= 3).
```

### Pass Criteria

- **Response list contains at least 3 items.**
  - *Bug it catches:* Endpoint applies an unintended LIMIT or drops duplicate-start-date rows, causing some seeded contracts to be absent from results.

---

## TC323 — Empty date range returns empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/history?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

Verifies that the S4-F6 history endpoint returns an empty collection when no contracts exist within the requested date range rather than throwing an error or returning unrelated records. Uses a far-future range (year 2099) that no seeded contract can fall into. Asserts the unwrapped list size is exactly 0. Catches implementations that ignore date boundaries or return all contracts when the range yields no matches.

### Steps

```
1) Obtain adminToken().
2) Call httpGetAuth to GET /api/contracts/history?startDate=2099-01-01&endDate=2099-01-31.
3) assert2xx, parseNode, unwrap array/content node.
4) assertEquals(0, list.size()).
```

### Pass Criteria

- **Response list size is exactly 0.**
  - *Bug it catches:* Implementation ignores date filter and returns all existing contracts regardless of date range.
- **Response is 2xx (not 404 or 500).**
  - *Bug it catches:* Endpoint throws an exception on empty result instead of returning an empty list.

---

## TC324 — Purge deletes old COMPLETED contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `DELETE /api/contracts/purge?olderThanDays=30`

### What it tests

Verifies that the S4-F7 purge endpoint physically removes COMPLETED contracts whose creation date is older than the specified cutoff. Seeds a COMPLETED contract dated 2020-01-01 (well beyond 30 days), calls the purge endpoint, then queries the database directly via jdbc to confirm the row is gone. This bypasses any soft-delete mechanism, ensuring a true hard delete occurred. Catches implementations that only soft-delete or apply the date filter incorrectly.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed old COMPLETED contract (2020-01-01) via _FmM1Seed.seedContract; capture cid.
3) Obtain adminToken(), call httpDeleteAuth to DELETE /api/contracts/purge?olderThanDays=30.
4) assert2xx, then jdbc.queryForObject SELECT COUNT(*) FROM contracts WHERE id=cid.
5) assertEquals(0L, remaining).
```

### Pass Criteria

- **DB row count for the purged contract ID is 0.**
  - *Bug it catches:* Implementation uses soft-delete (sets a deleted flag) instead of physically removing the row, leaving the record in the table.
- **Response is 2xx.**
  - *Bug it catches:* Purge endpoint rejects requests when no contracts are within range instead of succeeding with a zero count.

---

## TC325 — Purge does not delete ACTIVE contracts even if old

**Tags:** `public` `features_m1`  
**Endpoint(s):** `DELETE /api/contracts/purge?olderThanDays=30`

### What it tests

Verifies that the S4-F7 purge endpoint only deletes terminal-status contracts and never removes ACTIVE ones regardless of age. Seeds an ACTIVE contract from 2020-01-01 (far older than the 30-day cutoff), runs purge, and then queries the database to confirm the row still exists. Catches implementations that delete based solely on age without checking contract status. This is a critical safety check to prevent data loss on ongoing work.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed old ACTIVE contract (2020-01-01) via _FmM1Seed.seedContract; capture cid.
3) Obtain adminToken(), call httpDeleteAuth to DELETE /api/contracts/purge?olderThanDays=30; assert2xx.
4) jdbc.queryForObject SELECT COUNT(*) FROM contracts WHERE id=cid.
5) assertEquals(1L, remaining).
```

### Pass Criteria

- **DB row count for the ACTIVE contract ID is still 1 after purge.**
  - *Bug it catches:* Implementation purges any contract older than the cutoff regardless of status, accidentally deleting active ongoing contracts.

---

## TC326 — Purge response carries a numeric deletedCount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `DELETE /api/contracts/purge?olderThanDays=30`

### What it tests

Verifies that the S4-F7 purge endpoint response body contains a numeric field indicating how many records were deleted, under flexible field names (deletedCount, deleted, count, or a bare number). Seeds two old terminal contracts (one COMPLETED, one TERMINATED) and asserts the returned count is >= 2. Multiple field name aliases are checked to accommodate common naming variations without over-constraining the response shape.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed one COMPLETED and one TERMINATED contract dated 2020-01-01 via _FmM1Seed.seedContract.
3) Obtain adminToken(), call httpDeleteAuth to DELETE /api/contracts/purge?olderThanDays=30; assert2xx.
4) parseNode, probe deletedCount / deleted / count / bare number.
5) assertTrue(count >= 2).
```

### Pass Criteria

- **Response body contains a numeric deleted-count field >= 2.**
  - *Bug it catches:* Implementation returns 200 with an empty body or a string message instead of a structured JSON response with the deletion count.
- **Count reflects both COMPLETED and TERMINATED contracts.**
  - *Bug it catches:* Purge only handles COMPLETED status and ignores TERMINATED contracts, under-reporting deletions.

---

## TC327 — Recent COMPLETED contracts (within cutoff) are not purged

**Tags:** `public` `features_m1`  
**Endpoint(s):** `DELETE /api/contracts/purge?olderThanDays=30`

### What it tests

Verifies that the S4-F7 purge cutoff is enforced: a COMPLETED contract created only 5 days ago must survive a olderThanDays=30 purge. Seeds a COMPLETED contract dated today - 5 days, runs purge, and queries the DB to assert the row still exists. Catches implementations that delete all COMPLETED contracts regardless of their age, ignoring the cutoff parameter entirely. Uses LocalDate.now() to keep the test date-independent.

### Steps

```
1) Compute recent = LocalDate.now().minusDays(5).toString().
2) Seed FREELANCER user and job via _FmM1Seed.
3) Seed COMPLETED contract at recent via _FmM1Seed.seedContract; capture cid.
4) Obtain adminToken(), call httpDeleteAuth to DELETE /api/contracts/purge?olderThanDays=30; assert2xx.
5) jdbc.queryForObject count by id; assertEquals(1L, remaining).
```

### Pass Criteria

- **DB row count for the recent COMPLETED contract is still 1 after purge.**
  - *Bug it catches:* Implementation ignores olderThanDays parameter and purges all COMPLETED contracts unconditionally, deleting recently completed work.

---

## TC328 — Returns totalContracts, totalEarnings, averageContractValue for freelancer

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/freelancer/{id}/summary?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F8 freelancer summary endpoint returns a totalContracts count matching all contracts (COMPLETED, TERMINATED, ACTIVE) within the date range for the specified freelancer. Seeds 3 contracts (2 COMPLETED, 1 TERMINATED) and asserts totalContracts == 3. Uses _FmM2.rL() helper for flexible field name resolution. Catches implementations that count only COMPLETED contracts or omit contracts of non-COMPLETED statuses from the total.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed 2 COMPLETED and 1 TERMINATED contract via _FmM1Seed.seedContract.
3) Obtain adminToken(), call httpGetAuth to GET /api/contracts/freelancer/{fr}/summary?startDate=2026-03-01&endDate=2026-03-31.
4) assert2xx, parseNode.
5) _FmM2.rL(j, "totalContracts", "total_contracts"), assertEquals(3L, total).
```

### Pass Criteria

- **totalContracts equals 3 (all statuses counted).**
  - *Bug it catches:* Implementation only counts COMPLETED contracts, missing TERMINATED and ACTIVE entries and returning 2 instead of 3.

---

## TC329 — Non-existent freelancerId returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/freelancer/9999999/summary?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F8 freelancer summary endpoint returns HTTP 404 when the requested freelancer ID does not exist in the system. Uses an obviously invalid ID (9999999). This ensures the service validates the freelancer's existence before computing statistics rather than silently returning an empty summary. Catches implementations that return 200 with zero values or fail with 500 on a missing user.

### Steps

```
1) Obtain adminToken().
2) Call httpGetAuth to GET /api/contracts/freelancer/9999999/summary?startDate=2026-03-01&endDate=2026-03-31.
3) assertEquals(404, r.statusCode()).
```

### Pass Criteria

- **Response status code is exactly 404.**
  - *Bug it catches:* Implementation returns 200 with an empty/zero summary object instead of rejecting the request for an unknown freelancer ID.
- **Response is not 500.**
  - *Bug it catches:* Service throws a NullPointerException or unhandled exception when the freelancer is not found, producing a 500 error instead of a meaningful 404.

---

## TC330 — completionRate = completed / total

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/freelancer/{id}/summary?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F8 summary endpoint computes completionRate as the ratio of COMPLETED contracts to total contracts. Seeds 4 contracts (2 COMPLETED, 1 TERMINATED, 1 ACTIVE), expecting a rate of 0.5 or 50.0 (either decimal or percentage representation is accepted). Uses _FmM2.rD() for flexible field name lookup and a tolerance check to handle both conventions. Catches implementations that compute the rate from the wrong base or exclude non-COMPLETED from the denominator.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed 2 COMPLETED, 1 TERMINATED, 1 ACTIVE contract via _FmM1Seed.seedContract.
3) Obtain adminToken(), call httpGetAuth to summary endpoint.
4) assert2xx, parseNode, _FmM2.rD(j, "completionRate", "completion_rate").
5) assertTrue rate is ~0.5 (±0.05) or ~50.0 (±1.0).
```

### Pass Criteria

- **completionRate is approximately 0.5 or 50.0 (2 of 4 contracts completed).**
  - *Bug it catches:* Implementation divides only by COMPLETED count instead of total contract count, producing a rate of 1.0 instead of 0.5.
- **Both decimal and percentage formats accepted.**
  - *Bug it catches:* Test would incorrectly fail implementations that use a valid but different numeric convention (0.5 vs 50.0).

---

## TC331 — averageDurationDays computed across COMPLETED contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/freelancer/{id}/summary?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F8 summary endpoint includes a positive averageDurationDays field computed from COMPLETED contracts. Seeds two COMPLETED contracts with durations of 10 days and 20 days (expected average 15), and asserts the returned value is > 0. Uses _FmM2.rD() with multiple field name aliases. The loose > 0 check accommodates rounding differences while still catching implementations that return zero or null for the field.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed 2 COMPLETED contracts with known start/end dates via _FmM1Seed.seedContract.
3) Obtain adminToken(), call httpGetAuth to summary endpoint.
4) assert2xx, parseNode, _FmM2.rD(j, "averageDurationDays", "average_duration_days", "avgDurationDays").
5) assertTrue(avg > 0).
```

### Pass Criteria

- **averageDurationDays is greater than 0.**
  - *Bug it catches:* Implementation returns 0 or omits the field entirely because it only considers contracts without an endDate, ignoring COMPLETED ones that have both dates populated.

---

## TC332 — totalEarnings is sum of COMPLETED contract amounts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/freelancer/{id}/summary?startDate=...&endDate=...`

### What it tests

Verifies that the S4-F8 summary endpoint computes totalEarnings by summing the amounts of only COMPLETED contracts, excluding TERMINATED ones. Seeds 2 COMPLETED contracts (amounts 1000 and 2000) and 1 TERMINATED contract (amount 500), then asserts totalEarnings >= 3000. Catches implementations that include TERMINATED amounts in earnings or that sum all contract amounts regardless of status. Uses _FmM2.rD() for flexible field resolution.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed 2 COMPLETED and 1 TERMINATED contract via _FmM1Seed.seedContract.
3) Obtain adminToken(), call httpGetAuth to summary endpoint.
4) assert2xx, parseNode, _FmM2.rD(j, "totalEarnings", "total_earnings").
5) assertTrue(earnings >= 3000.0).
```

### Pass Criteria

- **totalEarnings is at least 3000.0 (1000 + 2000).**
  - *Bug it catches:* Implementation sums amounts for all statuses, returning 3500 which passes, but if it sums only one COMPLETED it returns 1000 or 2000, failing the >= 3000 check.
- **TERMINATED amount (500) does not inflate earnings above what COMPLETED provide.**
  - *Bug it catches:* Implementation includes TERMINATED contracts in the earnings sum, indicating incorrect status filtering logic.

---

## TC333 — Returns ACTIVE contracts with progress<=max and stale lastActivityDate

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/stalled?maxProgress=50&stalledDays=7`

### What it tests

Verifies the S4-F9 stalled-contracts endpoint returns an ACTIVE contract whose metadata shows low progress (10%) and a lastActivityDate 30 days ago, well beyond the 7-day stalledDays threshold. Sets contract metadata via _FmM1Seed.setContractMetadata with JSONB containing progressPercentage and lastActivityDate. Asserts at least 1 result appears. Catches implementations that ignore the metadata fields or return an empty list for valid stalled contracts.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed ACTIVE contract dated today-60 via _FmM1Seed.seedContract; capture cid.
3) Set JSONB metadata via _FmM1Seed.setContractMetadata(this, cid, {...}).
4) Obtain adminToken(), call httpGetAuth to GET /api/contracts/stalled?maxProgress=50&stalledDays=7.
5) assert2xx, unwrap list, assertTrue(list.size() >= 1).
```

### Pass Criteria

- **Response list contains at least 1 item (the seeded stalled contract).**
  - *Bug it catches:* Implementation does not read lastActivityDate from JSONB metadata and instead uses createdAt as the activity timestamp, causing valid stalled contracts to be missed.

---

## TC334 — Stalled does not include COMPLETED contracts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/stalled?maxProgress=50&stalledDays=7`

### What it tests

Verifies that the S4-F9 stalled endpoint only considers ACTIVE contracts, explicitly excluding COMPLETED ones even when their metadata would otherwise qualify them as stalled. Seeds a COMPLETED contract with progressPercentage=10 and lastActivityDate=today-50, then asserts the contract's ID does not appear in the stalled results. Uses flexible ID field extraction (contractId, contract_id, id) for tolerance. Catches implementations that filter by metadata alone without checking contract status.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed COMPLETED contract dated today-60 via _FmM1Seed.seedContract; capture cid.
3) Set JSONB metadata with stale lastActivityDate via _FmM1Seed.setContractMetadata.
4) Obtain adminToken(), call httpGetAuth to stalled endpoint.
5) assert2xx, unwrap list, iterate and assertNotEquals(cid, id) for each item.
```

### Pass Criteria

- **The COMPLETED contract's ID does not appear in the stalled results list.**
  - *Bug it catches:* Implementation filters only on progressPercentage and lastActivityDate without requiring status=ACTIVE, returning completed contracts that happen to match the metadata thresholds.

---

## TC335 — Stalled excludes contracts with progress above maxProgress

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/stalled?maxProgress=50&stalledDays=7`

### What it tests

Verifies that the S4-F9 stalled endpoint respects the maxProgress threshold by excluding ACTIVE contracts whose progressPercentage exceeds it. Seeds an ACTIVE contract with progressPercentage=85 and lastActivityDate=today-30, then with maxProgress=50 asserts the contract does not appear in results. Catches implementations that ignore the maxProgress filter or apply it as a minimum rather than maximum. JSONB metadata is set directly via _FmM1Seed.setContractMetadata.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed ACTIVE contract dated today-60; capture highProg.
3) Set metadata progressPercentage=85 via _FmM1Seed.setContractMetadata.
4) Obtain adminToken(), call httpGetAuth to GET /api/contracts/stalled?maxProgress=50&stalledDays=7.
5) assert2xx, unwrap list, iterate and assertNotEquals(highProg, id) for each item.
```

### Pass Criteria

- **The high-progress contract ID does not appear in stalled results.**
  - *Bug it catches:* Implementation treats maxProgress as a minimum threshold or ignores it entirely, returning contracts with 85% progress when the query asks for <= 50%.

---

## TC336 — Stalled excludes contracts with recent activity

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/stalled?maxProgress=50&stalledDays=14`

### What it tests

Verifies that the S4-F9 stalled endpoint respects the stalledDays threshold by excluding contracts whose lastActivityDate is within the cutoff. Seeds an ACTIVE contract with progressPercentage=10 but lastActivityDate=today-2 (very recent), and with stalledDays=14 asserts the contract is not included in results. Catches implementations that check progress but not the activity recency. Uses assertNotEquals per item for precision.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed ACTIVE contract dated today-60; capture recent.
3) Set metadata lastActivityDate=today-2 via _FmM1Seed.setContractMetadata.
4) Obtain adminToken(), call httpGetAuth to GET /api/contracts/stalled?maxProgress=50&stalledDays=14.
5) assert2xx, unwrap list, iterate and assertNotEquals(recent, id) for each item.
```

### Pass Criteria

- **The recently-active contract ID does not appear in stalled results.**
  - *Bug it catches:* Implementation ignores lastActivityDate from metadata and uses only progressPercentage, including contracts that had activity 2 days ago as "stalled".

---

## TC337 — Stalled DTO includes daysSinceLastActivity

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/contracts/stalled?maxProgress=50&stalledDays=7`

### What it tests

Verifies that the S4-F9 stalled endpoint response DTO includes a daysSinceLastActivity field (or equivalent snake_case/shortened alias) for each returned item. Seeds a clearly stalled contract (progress=10%, lastActivity=today-40) and checks the first result in the list for the presence of the field. Catches implementations that return the correct contracts but omit the computed staleness metric from the response DTO.

### Steps

```
1) Seed FREELANCER user and job via _FmM1Seed.
2) Seed ACTIVE contract dated today-60; set metadata via _FmM1Seed.setContractMetadata.
3) Obtain adminToken(), call httpGetAuth to stalled endpoint.
4) assert2xx, unwrap list, assertTrue(list.size() >= 1).
5) Check `item.has("daysSinceLastActivity")
```

### Pass Criteria



---

# S5 — Wallet Service

## M2 Features — TC136–TC190

## TC136 — Category breakdown groups COMPLETED payouts by jobs.category

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

The S5-F10 category revenue breakdown endpoint must aggregate COMPLETED payouts by the Job's category field and return one row per distinct category. Three payouts with different categories (WEB_DEV, MOBILE, DESIGN) are seeded via _FmM2S5.fullChain(); all three category rows must appear in the response. The findByCategory() helper searches the response array for a matching category field value. This is the compound happy-path test confirming the JOIN between Payout and Job tables is correct and the GROUP BY produces one row per category.

### Steps

```
1) _FmM2S5.fullChain(this, "WEB_DEV", 200.0, "2026-03-10"), repeat for MOBILE and DESIGN.
2) GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31, assert 2xx.
3) Unwrap content if present; assert findByCategory(arr, "WEB_DEV"), "MOBILE", and "DESIGN" are all non-null.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* Payout analytics endpoint not mapped (404), or the JOIN between Payout and Job is wrong (5xx), or the date filter is unrecognized.
- **WEB_DEV, MOBILE, and DESIGN rows all present**
  - *Bug it catches:* GROUP BY uses the wrong column (e.g., groups by job id instead of job category), or the JOIN is missing and categories can't be resolved.

---

## TC137 — Category.totalRevenue = SUM(payout.amount) for that category

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Two WEB_DEV payouts of 400.0 and 250.0 are seeded; the WEB_DEV totalRevenue must equal 650.0. Tests that the SUM is performed across multiple payouts in the same category, not just reporting the last one or the first one. Tolerance is 0.5 (accepting minor floating-point differences).

### Steps

```
1) _FmM2S5.fullChain(this, "WEB_DEV", 400.0, "2026-03-10") and fullChain(this, "WEB_DEV", 250.0, "2026-03-12").
2) GET category analytics, assert 2xx.
3) Find WEB_DEV row, assert totalRevenue == 650.0 within 0.5.
```

### Pass Criteria

- **totalRevenue == 650.0 for WEB_DEV**
  - *Bug it catches:* Only the last/first payout's amount is used instead of SUM; or payouts from different WEB_DEV jobs are not aggregated together.

---

## TC138 — Category.payoutCount equals number of COMPLETED payouts for that category

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Four MOBILE payouts are seeded; payoutCount for the MOBILE row must equal 4. Tests that COUNT is applied correctly within the GROUP BY. Accepts field names payoutCount or payout_count.

### Steps

```
1) Seed 4 MOBILE payouts for March 2026.
2) GET category analytics, assert 2xx.
3) Find MOBILE row, assert payoutCount == 4.
```

### Pass Criteria

- **payoutCount == 4**
  - *Bug it catches:* COUNT(*) is off by one (cross-join artifact), or only distinct payout ids are counted (DISTINCT reduces count), or the count is from a subquery that doesn't match the outer GROUP BY.

---

## TC139 — When transactionDetails.platformFee absent, fallback 10% of amount used

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

The S5-F10 platformFeeRevenue field must fall back to 10% of amount when the transactionDetails JSONB does not contain a platformFee key. _FmM2S5.fullChainNoFee() creates a payout with transactionDetails={} (no fee); the expected platformFeeRevenue is 10% of 500 = 50.0. This tests the JSONB conditional logic: COALESCE((transactionDetails->'platformFee')::numeric, amount * 0.10).

### Steps

```
1) _FmM2S5.fullChainNoFee(this, "WEB_DEV", 500.0, "2026-03-10").
2) GET category analytics, assert 2xx.
3) Find WEB_DEV row, assert platformFeeRevenue == 50.0 within 0.5.
```

### Pass Criteria

- **platformFeeRevenue == 50.0**
  - *Bug it catches:* No JSONB fallback implemented — fee returns 0 when platformFee is absent; or the 10% formula is hardcoded to a different rate; or the COALESCE logic uses the wrong path.

---

## TC140 — When transactionDetails.platformFee=20, that value is used (not 10%)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

When transactionDetails.platformFee=20 is present in the JSONB, the endpoint must use the explicit value (20.0) instead of the 10% fallback (which would be 50.0 for a 500-amount payout). _FmM2S5.fullChainWithFee() injects {platformFee: 20.0} into the payout's JSONB. Tolerance is 0.5. Pairs with TC139 to fully test the JSONB conditional logic.

### Steps

```
1) _FmM2S5.fullChainWithFee(this, "WEB_DEV", 500.0, 20.0, "2026-03-10").
2) GET category analytics, assert 2xx.
3) Find WEB_DEV row, assert platformFeeRevenue == 20.0 within 0.5.
```

### Pass Criteria

- **platformFeeRevenue == 20.0**
  - *Bug it catches:* The JSONB value is ignored and 10% fallback is always used (returns 50 instead of 20); or the JSONB path is wrong (transactionDetails->>'platformFee' as text instead of -> for numeric).

---

## TC141 — netPayoutRevenue = totalRevenue - platformFeeRevenue

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

With amount=500 and platformFee=25, the expected values are: totalRevenue=500, platformFeeRevenue=25, netPayoutRevenue=475. Tests the arithmetic formula net = total - fee. Tolerance is 0.5. Catches: net computed as total alone (fee subtraction missing), or fee added instead of subtracted.

### Steps

```
1) _FmM2S5.fullChainWithFee(this, "WEB_DEV", 500.0, 25.0, "2026-03-10").
2) GET category analytics, assert 2xx.
3) Find WEB_DEV row, assert netPayoutRevenue == 475.0 within 0.5.
```

### Pass Criteria

- **netPayoutRevenue == 475.0**
  - *Bug it catches:* Net is computed as totalRevenue (fee subtraction omitted), returning 500; or fee is added to total, returning 525.

---

## TC142 — PENDING/REFUNDED/FAILED payouts excluded from category breakdown

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Two WEB_DEV payouts are seeded: one PENDING and one COMPLETED. Only the COMPLETED one must appear in the count (payoutCount=1). Tests that the SQL WHERE clause filters to status='COMPLETED' only. Catches: WHERE status IN ('COMPLETED', 'PENDING') or missing status filter (both counted, returns 2).

### Steps

```
1) _FmM2S5.fullChainWithStatus(this, "WEB_DEV", 200.0, "PENDING", "2026-03-10") and fullChainWithStatus(..., "COMPLETED", "2026-03-11").
2) GET category analytics, assert 2xx.
3) Find WEB_DEV row, assert payoutCount == 1.
```

### Pass Criteria

- **payoutCount == 1**
  - *Bug it catches:* PENDING payouts are included in the GROUP BY query (missing or wrong WHERE clause on status), returning 2 instead of 1.

---

## TC143 — Distinct categories produce distinct rows

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-03-01&endDate=2026-03-31`

### What it tests

Four payouts with distinct categories (WEB_DEV, MOBILE, DESIGN, WRITING) are seeded; the response must contain all four as separate rows. Tests the GROUP BY produces distinct results rather than merging all categories.

### Steps

```
1) Seed one payout each for WEB_DEV, MOBILE, DESIGN, WRITING.
2) GET category analytics, collect category values from response array, assert all 4 are present.
```

### Pass Criteria

- **All 4 categories present in response**
  - *Bug it catches:* GROUP BY aggregates all categories into one row (wrong GROUP BY column), or some categories are dropped by a HAVING clause.

---

## TC144 — Category breakdown with no payouts in range returns empty list

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

A 2099 date range guarantees no payouts. Response must be 2xx with an empty array. Tests null-safe empty-result handling.

### Steps

```
1) GET category analytics for 2099, assert 2xx.
2) Unwrap array, assert size == 0.
```

### Pass Criteria

- **Status 200..299 + empty array**
  - *Bug it catches:* Controller returns 404 for empty result; or GroupBy on empty set throws NPE (5xx).

---

## TC145 — Payout at exactly startDate is included

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC UPDATE created_at to 2026-05-01 00:00:00, GET /api/payouts/analytics/category?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

The payout date filter must be inclusive on the lower bound. A payout's created_at is set to exactly 2026-05-01 00:00:00 via JDBC; it must appear in the WEB_DEV row when querying with startDate=2026-05-01.

### Steps

```
1) _FmM2S5.fullChain(this, "WEB_DEV", 100.0, "2026-05-01"), capture payout id.
2) JDBC UPDATE created_at=Timestamp.valueOf("2026-05-01 00:00:00").
3) GET analytics for May 2026, assert WEB_DEV row non-null.
```

### Pass Criteria

- **WEB_DEV row present**
  - *Bug it catches:* Lower bound is exclusive (WHERE created_at > :startDate).

---

## TC146 — Payout at exactly endDate is included

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC UPDATE created_at to 2026-05-31 23:59:59, GET /api/payouts/analytics/category?startDate=2026-05-01&endDate=2026-05-31`

### What it tests

Upper bound must be inclusive. A payout's created_at is set to 2026-05-31 23:59:59 via JDBC; it must appear when querying with endDate=2026-05-31.

### Steps

```
1) _FmM2S5.fullChain(this, "WEB_DEV", 100.0, "2026-05-31"), JDBC UPDATE created_at=Timestamp.valueOf("2026-05-31 23:59:59").
2) GET analytics for May 2026, assert WEB_DEV row non-null.
```

### Pass Criteria

- **WEB_DEV row present**
  - *Bug it catches:* Upper bound is exclusive (WHERE created_at < :endDate).

---

## TC147 — Category breakdown with startDate > endDate returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-04-01&endDate=2026-03-01`

### What it tests

Inverted dates must return strictly 400. Same validation pattern as TC63/TC108 applied to the S5-F10 endpoint.

### Steps

```
1) GET category analytics with inverted dates, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Inverted range passed to query returns 2xx; or query exception from inverted range propagates as 5xx.

---

## TC148 — Category breakdown without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?... (no auth)`

### What it tests

S5-F10 endpoint requires authentication. Unauthenticated GET returns strictly 401.

### Steps

```
1) GET category analytics via httpGet (no Authorization header), assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Payout analytics endpoint is publicly accessible.

---

## TC149 — Category breakdown with malformed JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?... with token xxx.yyy.zzz`

### What it tests

S5-F10 endpoint rejects malformed JWT with 401. Same pattern as other JWT-rejection tests.

### Steps

```
1) GET category analytics with "xxx.yyy.zzz" as token, assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx on the payout service.

---

## TC150 — First category call writes ANALYTICS_VIEWED to payout_audit_trail

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-07-01&endDate=2026-07-31, MongoDB count on s5AuditCollection()`

### What it tests

The S5-F10 spec requires logging ANALYTICS_VIEWED to the payout audit trail (MongoDB collection from s5AuditCollection()). Count {action: "ANALYTICS_VIEWED"} before and after, assert increased.

### Steps

```
1) Count before, GET category analytics for July 2026, assert 2xx, count after, assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* ANALYTICS_VIEWED not logged; wrong collection; wrong action name.

---

## TC151 — Second category call (cache hit) still logs ANALYTICS_VIEWED

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two identical GET /api/payouts/analytics/category?...`

### What it tests

ANALYTICS_VIEWED must be logged on every call including cache hits. Same pattern as TC67/TC112 applied to S5-F10.

### Steps

```
1) Call twice, count events after each, assert after2 > after1.
```

### Pass Criteria

- **after2 > after1**
  - *Bug it catches:* Event logging inside @Cacheable method — bypassed on cache hit.

---

## TC152 — First call populates Redis under wallet-service::S5-F10::*

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-08-01&endDate=2026-08-31, redis.dbSize()`

### What it tests

The S5-F10 result must be cached in Redis. redis.dbSize() before and after confirms a new key was added.

### Steps

```
1) Record before, GET analytics for August 2026, record after, assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* @Cacheable wired to in-memory cache instead of Redis.

---

## TC153 — Two identical category requests return identical bodies (cached)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/payouts/analytics/category?startDate=2026-07-01&endDate=2026-07-31`

### What it tests

Two consecutive identical requests must return the same body. Same pattern as TC69/TC96/TC113.

### Steps

```
1) GET category analytics twice, compare bodies, both assert 2xx.
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* Cache not applied — re-aggregations produce non-deterministic ordering.

---

## TC154 — Insert payout after first call → cached body still returned

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/payouts/analytics/category?startDate=2026-12-01&endDate=2026-12-31 with _FmM2S5.fullChain() between calls`

### What it tests

Cache must not be invalidated by a new payout insertion. After the first call establishes the cache, a new payout is inserted, and the second call must return the same (pre-insert) body. Same pattern as TC114.

### Steps

```
1) GET for December 2026 (captures body r1), insert new WEB_DEV payout in December, GET again (body r2), assert r1.body() == r2.body().
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* @CacheEvict on the payout save method invalidates the analytics cache, causing re-aggregation and returning a different body.

---

## TC155 — Payout outside [startDate, endDate] is excluded from category breakdown

**Tags:** `public` `features_m2`  
**Endpoint(s):** `JDBC UPDATE created_at to 2026-08-15, GET /api/payouts/analytics/category?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

A payout with created_at=2026-08-15 must NOT appear when querying for September 2026. Response must be empty. Catches: date filter not applied — August payout leaks into September results.

### Steps

```
1) _FmM2S5.fullChain(this, "WEB_DEV", 100.0, "2026-08-15"), JDBC UPDATE created_at=Timestamp.valueOf("2026-08-15 12:00:00").
2) GET analytics for September 2026, assert 2xx, unwrap array, assert size == 0.
```

### Pass Criteria

- **arr.size() == 0**
  - *Bug it catches:* Date filter is ignored — the August payout appears in the September results.

---

## TC156 — Category with no payouts in range is not present in response

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-08-01&endDate=2026-08-31 with only WEB_DEV payout`

### What it tests

When only WEB_DEV has payouts in the range, the MOBILE category (with zero payouts) must either be absent from the response or have payoutCount=0. Tests that the GROUP BY does not produce zero-count rows unless explicitly requested.

### Steps

```
1) Seed 1 WEB_DEV payout for August.
2) GET analytics for August, find MOBILE row, assert it is null or has payoutCount=0.
```

### Pass Criteria

- **MOBILE absent or payoutCount=0**
  - *Bug it catches:* A CROSS JOIN between categories and payouts produces phantom zero-count rows for all categories, inflating the result.

---

## TC157 — Payouts for the same category across different jobs aggregate together

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-08-01&endDate=2026-08-31 with two WEB_DEV jobs`

### What it tests

Two WEB_DEV payouts from different jobs (each via _FmM2S5.fullChain()) must be aggregated into a single WEB_DEV row with totalRevenue=300. Catches: the GROUP BY groups by job id AND category instead of category only — returning two separate WEB_DEV rows.

### Steps

```
1) Call _FmM2S5.fullChain(this, "WEB_DEV", 100.0, "2026-08-10") and fullChain(this, "WEB_DEV", 200.0, "2026-08-12").
2) GET analytics for August, find WEB_DEV row, assert totalRevenue == 300.0 within 0.5.
```

### Pass Criteria

- **totalRevenue == 300.0**
  - *Bug it catches:* GROUP BY uses both job.id and job.category — two rows appear for WEB_DEV, each with 100 or 200, instead of one row with 300.

---

## TC158 — Each category row has category+netPayoutRevenue+platformFeeRevenue+totalRevenue+payoutCount

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/category?startDate=2026-09-01&endDate=2026-09-30`

### What it tests

The category breakdown DTO must include all 5 required fields: category, totalRevenue (or total_revenue), netPayoutRevenue (or net_payout_revenue), platformFeeRevenue (or platform_fee_revenue), and payoutCount (or payout_count). Per-field assertions pinpoint missing fields.

### Steps

```
1) _FmM2S5.fullChainWithFee(this, "WEB_DEV", 100.0, 10.0, "2026-09-10").
2) GET analytics for September, assert at least one row, check first row for all 5 fields.
```

### Pass Criteria

- **All 5 fields present**
  - *Bug it catches:* Any field omitted from the DTO or serialized under a different name than the two accepted camelCase/snake_case variants.

---

## TC159 — BANK_TRANSFER: success=5/failure=2/rate≈0.71/total=500; PAYPAL: 3/0/1.0/300

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

The S5-F11 payout method breakdown endpoint reads from MongoDB s5AuditCollection() (filtering action IN (COMPLETED, FAILED)) and groups by method. Seed: 5 COMPLETED BANK_TRANSFER + 2 FAILED BANK_TRANSFER + 3 COMPLETED PAYPAL. Expected: BANK_TRANSFER successCount=5, failureCount=2, successRate≈0.71, totalAmount=500; PAYPAL successCount=3, failureCount=0, successRate=1.0. Comprehensive happy-path test covering all 4 metrics for 2 methods.

### Steps

```
1) Insert 5 COMPLETED BANK_TRANSFER, 2 FAILED BANK_TRANSFER, 3 COMPLETED PAYPAL audit docs.
2) GET methods analytics for August 2026, assert 2xx.
3) Find BANK_TRANSFER and PAYPAL rows, assert all 4 metrics match expected values within tolerances.
```

### Pass Criteria

- **BANK_TRANSFER successCount=5, failureCount=2, successRate≈0.71, totalAmount=500**
  - *Bug it catches:* MongoDB aggregation pipeline uses wrong filter or group stage; or totalAmount sums FAILED amounts too; or successRate uses wrong denominator.
- **PAYPAL successCount=3, failureCount=0, successRate=1.0**
  - *Bug it catches:* Different methods share a single aggregation bucket instead of grouping independently.

---

## TC160 — successCount = number of COMPLETED events for the method

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

Four COMPLETED BANK_TRANSFER audit docs are seeded; successCount must equal 4. Isolates the count metric from TC159's compound test.

### Steps

```
1) Insert 4 COMPLETED BANK_TRANSFER docs.
2) GET methods analytics, assert 2xx.
3) Find BANK_TRANSFER row, assert successCount == 4.
```

### Pass Criteria

- **successCount == 4**
  - *Bug it catches:* MongoDB $match filter on action=COMPLETED is wrong (e.g., using $eq on wrong field), returning a different count.

---

## TC161 — failureCount = number of FAILED events for the method

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

Three FAILED BANK_TRANSFER audit docs are seeded; failureCount must equal 3.

### Steps

```
1) Insert 3 FAILED BANK_TRANSFER docs.
2) GET methods analytics, find BANK_TRANSFER, assert failureCount == 3.
```

### Pass Criteria

- **failureCount == 3**
  - *Bug it catches:* FAILED documents are counted in successCount instead of failureCount; or the $match for FAILED uses the wrong action field name.

---

## TC162 — successRate = success / (success + failure)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

Four COMPLETED + 1 FAILED BANK_TRANSFER docs give successRate = 4/5 = 0.8. Tests the denominator is success + failure (total), not just success (which would be 4/4=1.0).

### Steps

```
1) Insert 4 COMPLETED + 1 FAILED BANK_TRANSFER docs.
2) GET methods analytics, find BANK_TRANSFER, assert successRate == 0.8 within 0.01.
```

### Pass Criteria

- **successRate == 0.8**
  - *Bug it catches:* Denominator is success only (returns 1.0); or denominator is total MongoDB docs including non-COMPLETED/FAILED (returns wrong ratio).

---

## TC163 — totalAmount sums COMPLETED amounts only (failed amounts excluded)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

Two COMPLETED amounts (50+50=100) + one FAILED amount (999) are seeded; totalAmount must equal 100.0 (excluding the FAILED 999). Catches: totalAmount sums ALL documents regardless of action, returning 1099.

### Steps

```
1) Insert 2 COMPLETED 50.0 + 1 FAILED 999.0 BANK_TRANSFER docs.
2) GET methods analytics, find BANK_TRANSFER, assert totalAmount == 100.0 within 0.5.
```

### Pass Criteria

- **totalAmount == 100.0**
  - *Bug it catches:* $sum is applied to all docs in the group including FAILED (returns 1099), meaning the aggregation pipeline doesn't filter by action before computing the amount sum.

---

## TC164 — Only CREATED/REFUNDED/REFUND_DENIED/ANALYTICS_VIEWED events: empty result

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

When all audit documents have actions other than COMPLETED or FAILED (CREATED, REFUNDED, REFUND_DENIED, ANALYTICS_VIEWED), the response must be an empty list. Tests that the $match stage correctly restricts to action IN [COMPLETED, FAILED].

### Steps

```
1) Insert one each of CREATED, REFUNDED, REFUND_DENIED, ANALYTICS_VIEWED audit docs.
2) GET methods analytics, unwrap list, assert size == 0.
```

### Pass Criteria

- **size == 0**
  - *Bug it catches:* $match is missing or uses $exists instead of $in — all audit documents are included in the aggregation regardless of action.

---

## TC165 — Result has one entry per distinct method

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31`

### What it tests

One COMPLETED audit doc each for BANK_TRANSFER, PAYPAL, and CRYPTO is inserted; the response must have one row per method (3 rows). Tests that the $group stage uses the method field as the key.

### Steps

```
1) Insert 3 docs (BANK_TRANSFER, PAYPAL, CRYPTO).
2) GET methods analytics, collect method values, assert all 3 are present.
```

### Pass Criteria

- **All 3 methods in response**
  - *Bug it catches:* $group uses a fixed key instead of $method, merging all methods into one bucket.

---

## TC166 — Empty date range returns empty list

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2030-01-01&endDate=2030-01-31`

### What it tests

A future date range with no audit docs returns 2xx with an empty list. Tests the no-results case for the method breakdown.

### Steps

```
1) GET methods analytics for January 2030, assert 2xx, unwrap list, assert size == 0.
```

### Pass Criteria

- **Status 200..299 + empty list**
  - *Bug it catches:* Controller returns 404 or 5xx for empty MongoDB aggregation result.

---

## TC167 — startDate > endDate returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?startDate=2026-04-01&endDate=2026-03-01`

### What it tests

Inverted date range returns strictly 400 for the S5-F11 endpoint.

### Steps

```
1) GET methods analytics with inverted dates, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Inverted range forwarded to MongoDB aggregation returns empty 2xx, or causes 5xx.

---

## TC168 — Missing JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?... (no auth)`

### What it tests

S5-F11 endpoint requires authentication. Unauthenticated GET returns strictly 401.

### Steps

```
1) GET methods analytics via httpGet, assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Method breakdown is publicly accessible.

---

## TC169 — Bogus JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `GET /api/payouts/analytics/methods?... with xxx.yyy.zzz`

### What it tests

Malformed JWT must be rejected with 401 on the method breakdown endpoint.

### Steps

```
1) GET methods analytics with "xxx.yyy.zzz", assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx on payout service.

---

## TC170 — Cache hit: 2nd call (after Mongo mutation) returns 1st response

**Tags:** `public` `features_m2`  
**Endpoint(s):** `Two GET /api/payouts/analytics/methods?startDate=2026-08-01&endDate=2026-08-31 with _FmM2S5.audit() insert between calls`

### What it tests

After the first methods call caches the result, a new audit document is inserted; the second call must return the same pre-insert body (cache hit). Same pattern as TC114/TC154. Redis required.

### Steps

```
1) Insert 1 COMPLETED BANK_TRANSFER doc. GET methods analytics (body r1). Insert another doc. GET again (body r2). Assert r1.body() == r2.body().
```

### Pass Criteria

- **r1.body() == r2.body()**
  - *Bug it catches:* Cache is eagerly invalidated on every MongoDB insert, causing re-aggregation on the second call.

---

## TC171 — FullPayoutReversal: reversalScope=FULL, within 30 days → 100% refund

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone with {reason:"client cancelled", reversalScope:"FULL"}`

### What it tests

The S5-F12 reverse-milestone endpoint with reversalScope=FULL on a COMPLETED payout within the 30-day window must return 2xx. The payout createdAt is set to System.currentTimeMillis() (now) — well within the window. This is the happy-path smoke test for the FullPayoutReversalStrategy. Catches: endpoint not mapped (404), strategy not selected for FULL scope, or 30-day window calculated incorrectly (window computed as < 30 days).

### Steps

```
1) _FmM2S5.payoutWithCreatedAt(this, "COMPLETED", 200.0, new Timestamp(System.currentTimeMillis())) → pid.
2) POST /api/payouts/<pid>/reverse-milestone with {reason:"client cancelled", reversalScope:"FULL"}, assert 2xx.
```

### Pass Criteria

- **Status 200..299**
  - *Bug it catches:* The 30-day window check incorrectly uses > instead of >=, or the FULL scope is not wired to the FullPayoutReversalStrategy, or the endpoint is not mapped at all.

---

## TC172 — Successful reversal updates Payout.status=REFUNDED

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone, JDBC SELECT status`

### What it tests

After a successful FULL reversal, the Payout's status column in PostgreSQL must be REFUNDED. JDBC reads the status directly from the DB to verify persistence (not relying on the API response). Catches: service returns 2xx but forgets to call payoutRepository.save() after updating status, or updates a detached entity.

### Steps

```
1) Seed COMPLETED payout, POST reverse-milestone FULL, assert 2xx.
2) JDBC: SELECT <statusCol>::text FROM <payoutsTable> WHERE id=pid, assert == "REFUNDED".
```

### Pass Criteria

- **DB status == "REFUNDED"**
  - *Bug it catches:* The service updates the entity in memory but doesn't persist (save() missing or @Transactional absent), leaving the DB status as COMPLETED.

---

## TC173 — FULL reversal populates transactionDetails.refundAmount=200 (full payout)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone, JDBC SELECT transaction_details`

### What it tests

After a FULL reversal on a 200.0-amount payout, transactionDetails.refundAmount in the JSONB column must equal 200.0. JDBC reads the raw JSONB and parses it. Catches: refundAmount set to a hardcoded value, or computed as a partial amount instead of the full payout amount.

### Steps

```
1) Seed 200.0 COMPLETED payout, POST FULL reversal, assert 2xx.
2) JDBC: SELECT transaction_details::text FROM <payoutsTable> WHERE id=pid, parse JSON, assert refundAmount == 200.0 within 0.5.
```

### Pass Criteria

- **refundAmount == 200.0**
  - *Bug it catches:* FULL reversal refunds only a percentage or the MILESTONE_ONLY amount instead of the full payout amount.

---

## TC174 — transactionDetails.reversalScope = 'FULL' (echoed from request)

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone, JDBC SELECT transaction_details`

### What it tests

After a FULL reversal, transactionDetails.reversalScope in the JSONB column must equal "FULL". Tests that the request body's reversalScope is persisted to the JSONB. Catches: scope not written to JSONB, or always hardcoded to "MILESTONE_ONLY".

### Steps

```
1) Seed COMPLETED payout, POST FULL reversal, assert 2xx.
2) JDBC parse transaction_details, assert reversalScope == "FULL".
```

### Pass Criteria

- **reversalScope == "FULL"**
  - *Bug it catches:* The reversalScope request body field is not mapped to the JSONB update; or the strategy always writes MILESTONE_ONLY as the scope.

---

## TC175 — MILESTONE_ONLY: refunds only PENDING/IN_PROGRESS milestones

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone with reversalScope:"MILESTONE_ONLY", JDBC SELECT transaction_details`

### What it tests

The MilestoneReversalStrategy must sum only the amounts of milestones with status PENDING or IN_PROGRESS, excluding COMPLETED milestones. Three milestones (50 COMPLETED, 80 PENDING, 70 IN_PROGRESS) are seeded; the expected refundAmount is 150.0 (80+70). Tests the milestone status filter in the partial-reversal algorithm.

### Steps

```
1) _FmM2S5.payoutWithMilestones(this, 200.0, [50, 80, 70], ["COMPLETED","PENDING","IN_PROGRESS"], now) → pid.
2) POST MILESTONE_ONLY reversal, assert 2xx.
3) JDBC parse transaction_details, assert refundAmount == 150.0 within 0.5.
```

### Pass Criteria

- **refundAmount == 150.0**
  - *Bug it catches:* All milestones are summed (returns 200), or only PENDING is included (returns 80), or COMPLETED milestone is mistakenly included.

---

## TC176 — MILESTONE_ONLY reversal stores reversalScope='MILESTONE_ONLY'

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone with reversalScope:"MILESTONE_ONLY", JDBC SELECT transaction_details`

### What it tests

After a MILESTONE_ONLY reversal, transactionDetails.reversalScope must equal "MILESTONE_ONLY". Same verification pattern as TC174 but for the MILESTONE_ONLY scope.

### Steps

```
1) Seed payout with 1 PENDING milestone, POST MILESTONE_ONLY reversal, assert 2xx.
2) JDBC parse transaction_details, assert reversalScope == "MILESTONE_ONLY".
```

### Pass Criteria

- **reversalScope == "MILESTONE_ONLY"**
  - *Bug it catches:* Scope is always hardcoded to "FULL" in the JSONB update, regardless of the request body.

---

## TC177 — payout.createdAt > 30 days ago → NoReversalStrategy → 400 with 'reversal window expired'

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone on a payout with createdAt 31 days ago`

### What it tests

The NoReversalStrategy must activate when the payout is older than 30 days and return strictly 400. The response body must contain the text "reversal window expired" (case-insensitive). The payout createdAt is set to currentTimeMillis() - 31 days via payoutWithCreatedAt(). This catches: window calculated as > 31 days (too lenient), or window not checked at all.

### Steps

```
1) _FmM2S5.payoutWithCreatedAt(this, "COMPLETED", 100.0, new Timestamp(now - 31*24*3600*1000)) → pid.
2) POST FULL reversal, assert strictly 400.
3) Assert body contains "reversal window expired" (case-insensitive).
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Window check uses 31 days threshold instead of 30 (still within window for a 31-day-old payout); or no window check implemented (2xx returned).
- **Body contains "reversal window expired"**
  - *Bug it catches:* Error message is generic ("Bad Request") without the spec-required "reversal window expired" phrasing.

---

## TC178 — Reversal denial writes REFUND_DENIED to payout_audit_trail BEFORE 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone (denied), MongoDB count on s5AuditCollection()`

### What it tests

When a reversal is denied (NoReversalStrategy, window expired), the REFUND_DENIED event must still be logged to MongoDB before the 400 response is returned. Count {action: "REFUND_DENIED"} before and after the denied reversal call.

### Steps

```
1) Count before. POST reversal (expect 400). Count after. Assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* The service logs events only on success — the REFUND_DENIED event is skipped when the strategy returns denial.

---

## TC179 — Reversal denial invalidates wallet-service::S5-F10::* cache keys

**Tags:** `public` `features_m2`  
**Endpoint(s):** `S5-F10 cache primed then reversal denied, redis.dbSize()`

### What it tests

Even a denied reversal must invalidate the S5-F10 category analytics cache so that the next analytics call re-aggregates fresh data. The test primes the S5-F10 cache, performs a denied reversal, then asserts redis.dbSize() decreased (cache keys were evicted).

### Steps

```
1) GET category analytics to prime cache, record primed = redis.dbSize().
2) POST reversal (denied). Record afterDenial = redis.dbSize().
3) Assert `afterDenial < primed
```

### Pass Criteria



---

## TC180 — Reversal denial invalidates wallet-service::S5-F11::* cache keys

**Tags:** `public` `features_m2`  
**Endpoint(s):** `S5-F11 cache primed then reversal denied, redis.dbSize()`

### What it tests

Same as TC179 but for the S5-F11 method breakdown cache. Both caches must be invalidated even on denial.

### Steps

```
1) GET methods analytics to prime cache, record primed = redis.dbSize().
2) POST reversal (denied). Record afterDenial = redis.dbSize().
3) Assert `afterDenial < primed
```

### Pass Criteria



---

## TC181 — Reversal on PENDING payout returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone on PENDING Payout`

### What it tests

A PENDING payout cannot be reversed (only COMPLETED payouts are eligible). Must return strictly 400. Catches: status check missing — PENDING payout is reversed, corrupting the payout lifecycle.

### Steps

```
1) _FmM2S5.payoutWithCreatedAt(this, "PENDING", 100.0, now) → pid.
2) POST FULL reversal, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* No status check before reversal — PENDING payout is processed and status updated to REFUNDED, creating an invalid payout state.

---

## TC182 — Reversal on already-REFUNDED payout returns 400

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone on REFUNDED Payout`

### What it tests

A REFUNDED payout cannot be reversed again (idempotency guard). Must return strictly 400. Catches: double-refund bug where a payout can be refunded twice.

### Steps

```
1) _FmM2S5.payoutWithCreatedAt(this, "REFUNDED", 100.0, now) → pid.
2) POST FULL reversal, assert strictly 400.
```

### Pass Criteria

- **Status strictly 400**
  - *Bug it catches:* Double-refund is allowed — status check is missing and a REFUNDED payout can be reversed again, potentially issuing a second refund to the freelancer.

---

## TC183 — Reversal on non-existent payoutId returns 404

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/999999/reverse-milestone`

### What it tests

The reverse-milestone endpoint must verify the payout exists, returning 404 if not found.

### Steps

```
1) POST reverse-milestone for payout id 999999, assert strictly 404.
```

### Pass Criteria

- **Status strictly 404**
  - *Bug it catches:* repository.findById(999999).get() threw NPE (5xx), or controller returned 2xx for a non-existent payout.

---

## TC184 — Reversal without Authorization header returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone (no auth)`

### What it tests

The reverse-milestone endpoint must require authentication. Unauthenticated POST returns strictly 401.

### Steps

```
1) Seed COMPLETED payout, POST via httpPost (no auth), assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* Reversal is publicly accessible without a token.

---

## TC185 — Reversal with malformed JWT returns 401

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone with xxx.yyy.zzz`

### What it tests

Malformed JWT rejected with 401 on the reverse-milestone endpoint.

### Steps

```
1) Seed COMPLETED payout, POST with "xxx.yyy.zzz" token, assert strictly 401.
```

### Pass Criteria

- **Status strictly 401**
  - *Bug it catches:* MalformedJwtException propagated as 5xx.

---

## TC186 — Successful reversal writes REFUNDED to payout_audit_trail

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone (success), MongoDB count on s5AuditCollection()`

### What it tests

A successful reversal must log a REFUNDED event to MongoDB. Count {action: "REFUNDED"} before and after, assert increased.

### Steps

```
1) Count before. POST FULL reversal (assert 2xx). Count after. Assert after > before.
```

### Pass Criteria

- **after > before**
  - *Bug it catches:* REFUNDED event not logged on success; or the audit write is inside a try-catch that swallows failures.

---

## TC187 — transactionDetails.refundReason matches request body reason

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone with reason:"scope changed", JDBC SELECT transaction_details`

### What it tests

After a successful reversal, transactionDetails.refundReason must equal the reason string from the request body ("scope changed"). Tests that the reason is mapped from the request DTO to the JSONB update.

### Steps

```
1) POST reversal with reason="scope changed", assert 2xx.
2) JDBC parse transaction_details, assert refundReason == "scope changed".
```

### Pass Criteria

- **refundReason == "scope changed"**
  - *Bug it catches:* The reason field is not mapped to refundReason in the JSONB — field is null, empty, or uses a different key name.

---

## TC188 — transactionDetails.refundedAt is populated as ISO timestamp

**Tags:** `public` `features_m2`  
**Endpoint(s):** `POST /api/payouts/{pid}/reverse-milestone, JDBC SELECT transaction_details`

### What it tests

After a successful reversal, transactionDetails.refundedAt must be present and non-blank. Tests that the timestamp of the reversal is recorded in the JSONB. Catches: refundedAt omitted from the JSONB update.

### Steps

```
1) POST FULL reversal, assert 2xx.
2) JDBC parse transaction_details, assert refundedAt is present and !dj.get("refundedAt").asText().isBlank().
```

### Pass Criteria

- **refundedAt present and non-blank**
  - *Bug it catches:* The reversal timestamp is not stored in the JSONB — downstream audit reports cannot determine when the refund was processed.

---

## TC189 — Successful reversal invalidates wallet-service::S5-F10::* cache keys

**Tags:** `public` `features_m2`  
**Endpoint(s):** `S5-F10 cache primed, successful reversal, redis.dbSize() decreases`

### What it tests

A successful reversal must invalidate the S5-F10 category analytics cache (same as TC179 but for success path). After reversal, the analytics data has changed (one COMPLETED payout is now REFUNDED), so stale cache must be evicted.

### Steps

```
1) GET category analytics to prime cache, record primed. POST FULL reversal (assert 2xx). Record afterReversal. Assert `afterReversal < primed
```

### Pass Criteria



---

## TC190 — Successful reversal invalidates wallet-service::S5-F11::* cache keys

**Tags:** `public` `features_m2`  
**Endpoint(s):** `S5-F11 cache primed, successful reversal, redis.dbSize() decreases`

### What it tests

Same as TC189 but for the S5-F11 method breakdown cache. Both caches must be evicted on success.

### Steps

```
1) GET methods analytics to prime cache, record primed. POST FULL reversal (assert 2xx). Record afterReversal. Assert `afterReversal < primed
```

### Pass Criteria



---

## M1 Features — TC338–TC378

## TC338 — Search payouts with status filter returns matches

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/search?status=COMPLETED&startDate=...&endDate=...`

### What it tests

Verifies the S5-F1 payout search endpoint filters results by status query parameter, returning only payouts with the requested status. Seeds one COMPLETED payout and one REFUNDED payout in the date range, then asserts every item with a non-empty status field has status COMPLETED. Uses checkoutServiceUrl (S5 wallet/payout service). The flexible equality check (skips items with empty status string) accommodates responses that omit the field on some items.

### Steps

```
1) Seed FREELANCER user, job, and COMPLETED contract via _FmM1Seed.
2) Seed COMPLETED payout and REFUNDED payout via _FmM1Seed.seedPayout.
3) Set BASE_URL = checkoutServiceUrl; obtain adminToken().
4) Call httpGetAuth to GET /api/payouts/search?status=COMPLETED&startDate=2026-03-01&endDate=2026-03-31.
5) assert2xx, unwrap list, iterate and assertEquals("COMPLETED", st) for non-empty status values.
```

### Pass Criteria

- **Every item with a non-empty status field has status COMPLETED.**
  - *Bug it catches:* Implementation ignores the status query parameter and returns all payouts in the date range, including the REFUNDED payout.

---

## TC339 — Search without status filter returns all in range

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/search?startDate=...&endDate=...`

### What it tests

Verifies that the S5-F1 payout search endpoint returns a 2xx response when called without a status filter, accepting all payouts in the date range. Seeds one COMPLETED and one PENDING payout, then simply asserts the response is successful. This confirms the status parameter is truly optional and not required. Catches implementations that mandate the status parameter or throw a 400 when it is absent.

### Steps

```
1) Seed FREELANCER user, job, and COMPLETED contract via _FmM1Seed.
2) Seed COMPLETED and PENDING payouts via _FmM1Seed.seedPayout.
3) Set BASE_URL = checkoutServiceUrl; obtain adminToken().
4) Call httpGetAuth to GET /api/payouts/search?startDate=2026-03-01&endDate=2026-03-31.
5) assert2xx(r, "TC339").
```

### Pass Criteria

- **Response is 2xx (status parameter is optional).**
  - *Bug it catches:* Implementation requires status as a mandatory query parameter and returns 400 Bad Request when it is omitted.

---

## TC340 — Search results are ordered most recent first

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/search?startDate=2026-01-01&endDate=2026-12-31`

### What it tests

Verifies that the S5-F1 payout search endpoint responds with a 2xx status when called with a broad date range, confirming the endpoint is reachable and handles large ranges without error. No payouts are seeded, so this acts as a structural/smoke test for the ordering feature. Catches endpoints that crash on empty result sets or reject wide date ranges. The minimal assertion intentionally avoids seeding complexity while still exercising the endpoint path.

### Steps

```
1) Set BASE_URL = checkoutServiceUrl; obtain adminToken().
2) Call httpGetAuth to GET /api/payouts/search?startDate=2026-01-01&endDate=2026-12-31.
3) assert2xx(r, "TC340").
```

### Pass Criteria

- **Response is 2xx for the broad date range search.**
  - *Bug it catches:* Implementation crashes with 500 when the result set is empty or when the date range spans a full year, due to missing null-safety in the ordering/sorting logic.

---

## TC341 — Out-of-range payouts produce empty list

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/search?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

Verifies S5-F1 (payout date-range search) with a future date range that contains no data. The spec requires the endpoint to return an empty collection, not an error. A common bug is students returning null or omitting pagination wrapper handling, so the test tolerates both plain arrays and content-wrapped paged responses. The size assertion catches controllers that return all payouts regardless of filter parameters.

### Steps

```
1) adminToken() to get admin JWT.
2) httpGetAuth("/api/payouts/search?startDate=2099-01-01&endDate=2099-01-31", tok) to search.
3) assert2xx(r, "TC341") to verify success.
4) parseNode(r.body()) then unwrap content if paged.
5) assertEquals(0, list.size(), ...) to assert empty.
```

### Pass Criteria

- **list.size() == 0**
  - *Bug it catches:* Student ignores date filter and returns all payouts in DB.
- **HTTP 2xx status**
  - *Bug it catches:* Student throws exception instead of returning empty collection when no results match.

---

## TC342 — Refunding a COMPLETED payout returns 2xx and sets REFUNDED

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/refund`

### What it tests

Verifies S5-F2 (payout refund) happy path: a COMPLETED payout can be refunded by an admin and the response body must show status REFUNDED. Seeds a real freelancer, job, contract, and payout via _FmM1Seed to ensure realistic FK chain. The common student bug is returning 2xx but not actually updating the status field. The assertion directly reads the status from the response body to catch this.

### Steps

```
1) _FmM1Seed.seedUser(this, "Fr", "tc342@fm.io", "FREELANCER") to create freelancer.
2) _FmM1Seed.seedJob(...) then _FmM1Seed.seedContract(...) COMPLETED with amount 1000.
3) _FmM1Seed.seedPayout(this, cid, fr, 1000.0, "BANK_TRANSFER", "COMPLETED").
4) adminToken() then httpPutAuth("/api/payouts/{pid}/refund", "{\"reason\":\"contract terminated early\"}", tok).
5) assert2xx(r, "TC342") then parseNode(r.body()).path("status").asText(""); assertEquals("REFUNDED", st, ...).
```

### Pass Criteria

- **status == "REFUNDED" in response body**
  - *Bug it catches:* Student updates DB but returns stale entity or omits status in DTO.
- **HTTP 2xx**
  - *Bug it catches:* Student throws 500 due to missing transaction handling on status update.

---

## TC343 — Refunding a non-existent payout returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/9999999/refund`

### What it tests

Verifies S5-F2 error path: refund request for a payout ID that does not exist must return 404, not 500 or 200. This tests that the service layer validates existence before processing. A frequent bug is students letting the ORM throw EmptyResultDataAccessException or EntityNotFoundException without mapping it to 404. No seed data is needed — the non-existent ID guarantees the not-found path.

### Steps

```
1) adminToken() to get admin JWT.
2) httpPutAuth("/api/payouts/9999999/refund", "{\"reason\":\"x\"}", tok) to attempt refund.
3) assertEquals(404, r.statusCode(), ...) to assert correct error code.
```

### Pass Criteria

- **statusCode == 404**
  - *Bug it catches:* Student returns 500 from unhandled EntityNotFoundException instead of mapping to 404.
- **No 2xx returned**
  - *Bug it catches:* Student silently ignores missing entity and returns an empty success response.

---

## TC344 — Refunding a PENDING payout returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/refund`

### What it tests

Verifies S5-F2 business rule: only COMPLETED payouts may be refunded; attempting to refund a PENDING payout must return 400. Seeds a real PENDING payout to ensure the constraint is tested against a real DB row (not just a missing ID). The spec is explicit that status must be COMPLETED before a refund is allowed. The common mistake is students skipping the status check entirely, causing the refund to proceed on any status.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED), seedPayout (PENDING) to create pre-conditions.
2) adminToken() then httpPutAuth("/api/payouts/{pid}/refund", "{\"reason\":\"x\"}", tok).
3) assertEquals(400, r.statusCode(), ...) to assert rejection.
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student performs refund on PENDING payout without validating current status.
- **No status transition to REFUNDED**
  - *Bug it catches:* Student applies refund logic unconditionally regardless of current payout status.

---

## TC345 — Refund populates refundReason and refundedAt in transactionDetails

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/refund`

### What it tests

Verifies S5-F2 JSONB persistence: after a successful refund, the response must include transactionDetails with refundReason (and ideally refundedAt) populated. This tests that the service writes metadata into the JSONB column rather than just flipping the status enum. The test uses _FmM2.rO() to handle both camelCase and snake_case key variants, accommodating naming convention differences across student implementations. The common bug is storing the reason in a flat column instead of the JSONB field.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED), seedPayout (COMPLETED) to set up data.
2) adminToken() then httpPutAuth("/api/payouts/{pid}/refund", "{\"reason\":\"client cancelled\"}", tok).
3) assert2xx(r, "TC345"), parseNode(r.body()).
4) _FmM2.rO(j, "transactionDetails", "transaction_details") to get JSONB node.
5) assertNotNull(td, ...) and `assertTrue(td.has("refundReason")
```

### Pass Criteria



---

## TC346 — Returns totalPayouts and totalAmount for freelancer's COMPLETED payouts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/freelancer/{freelancerId}/summary`

### What it tests

Verifies S5-F3 (freelancer payout summary) happy path: the endpoint must aggregate the freelancer's COMPLETED payouts and return totalPayouts count. Seeds 4 COMPLETED payouts across different methods (BANK_TRANSFER, PAYPAL, CRYPTO) to verify the aggregation covers all method types. The common bug is students filtering by payment method or returning only the latest payout. The _FmM2.rL() helper handles both camelCase and snake_case key variants for the count field.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 4000), then 4x seedPayout (COMPLETED with BANK_TRANSFER/PAYPAL/CRYPTO).
2) adminToken() then httpGetAuth("/api/payouts/freelancer/{fr}/summary", tok).
3) assert2xx(r, "TC346"), parseNode(r.body()).
4) _FmM2.rL(j, "totalPayouts", "total_payouts") to get count.
5) assertEquals(4L, total, ...).
```

### Pass Criteria

- **totalPayouts == 4**
  - *Bug it catches:* Student counts only one payment method or filters by some other criterion.
- **HTTP 2xx**
  - *Bug it catches:* Student throws NPE during aggregation when freelancer has payouts with different methods.

---

## TC347 — methodBreakdown maps method names to total amounts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/freelancer/{freelancerId}/summary`

### What it tests

Verifies S5-F3 that the summary response includes a methodBreakdown map grouping COMPLETED payout totals by payment method. Seeds payouts with BANK_TRANSFER (×2, 3500 total) and PAYPAL (×1, 800) to confirm the map contains at least a BANK_TRANSFER key. The test uses _FmM2.rO() to tolerate camelCase or snake_case field naming. The common bug is students returning only the total amount without the per-method breakdown, or serializing the breakdown as a list instead of a map.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 4000), then seedPayout ×3 (BANK_TRANSFER 1500, BANK_TRANSFER 2000, PAYPAL 800, all COMPLETED).
2) adminToken() then httpGetAuth("/api/payouts/freelancer/{fr}/summary", tok).
3) assert2xx(r, "TC347"), parseNode(r.body()).
4) _FmM2.rO(j, "methodBreakdown", "method_breakdown") to extract map node.
5) assertNotNull(mb, ...) and assertTrue(mb.has("BANK_TRANSFER"), ...).
```

### Pass Criteria

- **methodBreakdown node is non-null**
  - *Bug it catches:* Student omits the breakdown map from the summary DTO.
- **BANK_TRANSFER key present in breakdown map**
  - *Bug it catches:* Student serializes breakdown as a list of objects instead of a {METHOD: amount} map, breaking key lookup.

---

## TC348 — Non-existent freelancer returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/freelancer/9999999/summary`

### What it tests

Verifies S5-F3 error handling: requesting a payout summary for a freelancer ID that does not exist must return 404. The spec requires the service to validate the freelancer's existence before aggregating. The common student bug is returning an empty summary object (totalPayouts=0) instead of 404 when the user does not exist in the users table. Uses a guaranteed non-existent ID so no seed data is needed.

### Steps

```
1) adminToken() to get admin JWT.
2) httpGetAuth("/api/payouts/freelancer/9999999/summary", tok) to request summary.
3) assertEquals(404, r.statusCode(), ...) to assert not-found.
```

### Pass Criteria

- **statusCode == 404**
  - *Bug it catches:* Student returns empty summary {totalPayouts:0, totalAmount:0} instead of 404 when freelancer not found.
- **No 2xx returned**
  - *Bug it catches:* Student queries payouts table without first verifying the user exists, masking the not-found condition.

---

## TC349 — Summary includes only COMPLETED payouts

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/freelancer/{freelancerId}/summary`

### What it tests

Verifies S5-F3 filter logic: the freelancer payout summary must count only COMPLETED payouts, excluding REFUNDED and FAILED ones. Seeds exactly 1 COMPLETED, 1 REFUNDED, and 1 FAILED payout for the same freelancer to isolate the status filter. The assertion expects totalPayouts=1, catching the common bug where students count all payouts regardless of status. The _FmM2.rL() helper normalizes field naming across implementations.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 3000).
2) seedPayout ×3: COMPLETED(1000/BANK_TRANSFER), REFUNDED(500/PAYPAL), FAILED(200/BANK_TRANSFER).
3) adminToken() then httpGetAuth("/api/payouts/freelancer/{fr}/summary", tok).
4) assert2xx(r, "TC349"), parseNode(r.body()).
5) _FmM2.rL(j, "totalPayouts", "total_payouts"); assertEquals(1L, total, ...).
```

### Pass Criteria

- **totalPayouts == 1**
  - *Bug it catches:* Student counts all payouts (returns 3) instead of filtering to COMPLETED status only.
- **HTTP 2xx**
  - *Bug it catches:* Student throws exception when encountering REFUNDED or FAILED statuses during aggregation.

---

## TC350 — Processing a COMPLETED contract's PENDING payout returns 201

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/contract/{contractId}`

### What it tests

Verifies S5-F4 (process payout) happy path: when a COMPLETED contract has a PENDING payout, calling the process endpoint with a payment method must succeed with 200 or 201. Seeds the full FK chain (freelancer, job, COMPLETED contract, PENDING payout) to simulate a real payment processing scenario. The test accepts both 200 and 201 to accommodate implementations that create a new payout row versus updating the existing PENDING one. The common bug is students validating the contract status incorrectly or not accepting the method payload.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 2000), seedPayout (PENDING BANK_TRANSFER 2000).
2) adminToken() then httpPostAuth("/api/payouts/contract/{cid}", "{\"method\":\"BANK_TRANSFER\",\"accountLastFour\":\"9876\"}", tok).
3) `assertTrue(r.statusCode() == 200
```

### Pass Criteria



---

## TC351 — Process payout for non-existent contract returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/contract/9999999`

### What it tests

Verifies S5-F4 error path: attempting to process a payout for a contract ID that does not exist must return 404. The spec requires the service to verify contract existence before any payout logic. A common mistake is students letting the ORM return null and proceeding with NullPointerException (500) instead of throwing a mapped 404. Uses a guaranteed non-existent ID to avoid seeding overhead.

### Steps

```
1) adminToken() to get admin JWT.
2) httpPostAuth("/api/payouts/contract/9999999", "{\"method\":\"BANK_TRANSFER\"}", tok) to attempt processing.
3) assertEquals(404, r.statusCode(), ...) to assert not-found.
```

### Pass Criteria

- **statusCode == 404**
  - *Bug it catches:* Student throws 500 from NullPointerException when contract lookup returns null instead of mapping to 404.
- **No 2xx returned**
  - *Bug it catches:* Student silently creates a dangling payout with no valid contract FK.

---

## TC352 — Processing payout for ACTIVE contract returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/contract/{contractId}`

### What it tests

Verifies S5-F4 business rule: payout can only be processed for a COMPLETED contract; an ACTIVE contract must be rejected with 400. Seeds an ACTIVE contract (no endDate) to ensure the status check is evaluated against a real DB row. The spec is explicit that the contract must be in COMPLETED state before payment. The common bug is students skipping the status validation and processing the payout regardless of contract state.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract(fr, 1L, 0L, jid, "ACTIVE", 1000.0, "2026-03-01", null) (null endDate).
2) adminToken() then httpPostAuth("/api/payouts/contract/{cid}", "{\"method\":\"BANK_TRANSFER\"}", tok).
3) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student processes payout for ACTIVE contract without checking contract status.
- **No payout created**
  - *Bug it catches:* Student inserts a COMPLETED payout row for an in-progress contract, violating business integrity.

---

## TC353 — Second payment for the same contract returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/contract/{contractId}`

### What it tests

Verifies S5-F4 idempotency guard: if a COMPLETED contract already has a COMPLETED payout, a second process request must return 400 (already paid). Seeds a COMPLETED payout on a COMPLETED contract to set up the already-paid state. The common bug is students allowing duplicate payouts for the same contract, violating the one-payout-per-contract rule. The assertion checks that the duplicate attempt is rejected rather than creating a second payout row.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 1500), seedPayout (COMPLETED BANK_TRANSFER 1500).
2) adminToken() then httpPostAuth("/api/payouts/contract/{cid}", "{\"method\":\"BANK_TRANSFER\"}", tok).
3) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student creates a second payout row without checking whether a COMPLETED payout already exists for that contract.
- **No 201 returned**
  - *Bug it catches:* Student only checks for PENDING duplicates but ignores existing COMPLETED payouts, allowing double payment.

---

## TC354 — ?simulateFailure=true short-circuits to FAILED

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/contract/{contractId}?simulateFailure=true`

### What it tests

Verifies S5-F4 failure simulation flag: when simulateFailure=true is passed as a query param, the payout processing must create/update the payout to FAILED status without charging. The test queries the DB directly via jdbc and columnByField dynamic column resolution to verify the actual persisted status, not just the HTTP response code. This catches students who set FAILED only in memory but don't persist it, or who ignore the flag entirely.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 2000), seedPayout (PENDING BANK_TRANSFER 2000).
2) adminToken() then httpPostAuth("/api/payouts/contract/{cid}?simulateFailure=true", "{\"method\":\"BANK_TRANSFER\"}", tok).
3) tableName("Payout"), columnByField("Payout", "status"), columnByField("Payout", "contract") to build dynamic query.
4) jdbc.queryForObject("SELECT ... FROM payout WHERE contract=? LIMIT 1", String.class, cid) to read persisted status.
5) assertEquals("FAILED", st, ...).
```

### Pass Criteria

- **DB row status == "FAILED" after simulateFailure**
  - *Bug it catches:* Student sets status in the response DTO but doesn't persist FAILED to the DB.
- **simulateFailure=true is recognized**
  - *Bug it catches:* Student ignores the query param and processes payment normally, leaving status as COMPLETED instead of FAILED.

---

## TC355 — Applying PERCENTAGE promo creates PayoutPromo with correct discount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 (promo application) happy path for PERCENTAGE type: applying an active, non-expired, under-limit PERCENTAGE promo to a PENDING payout must succeed with 2xx. Seeds a real promo via _FmM1Seed.seedPromo with type PERCENTAGE, 10% discount, maxUses=100, future expiry, and active=true. The nonce ensures a unique promo code per test run. The common bug is students not implementing the PERCENTAGE branch or computing the discount against the wrong base amount.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 3000), seedPayout (PENDING BANK_TRANSFER 3000).
2) _FmM1Seed.seedPromo(this, "TC355_"+nonce(), "PERCENTAGE", 10.0, 100, futureDateTime(), true) to create promo.
3) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
4) assert2xx(r, "TC355") to confirm acceptance.
```

### Pass Criteria

- **HTTP 2xx**
  - *Bug it catches:* Student only implements FIXED discount type and returns 500 or 400 for PERCENTAGE type.
- **Promo linked to payout**
  - *Bug it catches:* Student applies the discount calculation but fails to persist the PayoutPromo join record.

---

## TC356 — Applying FIXED promo records exact discountValue

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 happy path for FIXED discount type: applying an active FIXED promo (200 flat discount) to a PENDING payout must succeed with 2xx. Seeds a FIXED promo with value=200, maxUses=100, future expiry, active=true. The test confirms the FIXED type is accepted, complementing TC355 which tests PERCENTAGE. The common bug is students treating all promos as PERCENTAGE type or incorrectly applying the discount as a percentage.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 3000), seedPayout (PENDING BANK_TRANSFER 3000).
2) _FmM1Seed.seedPromo(this, "TC356_"+nonce(), "FIXED", 200.0, 100, futureDateTime(), true) to create promo.
3) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
4) assert2xx(r, "TC356") to confirm acceptance.
```

### Pass Criteria

- **HTTP 2xx**
  - *Bug it catches:* Student implements PERCENTAGE branch but returns error for FIXED type due to missing enum branch handling.
- **No type-mismatch error**
  - *Bug it catches:* Student interprets FIXED value as a percentage (applying 200% discount) and throws an arithmetic error.

---

## TC357 — Discount is capped at payout amount when FIXED value exceeds amount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 cap rule: when a FIXED promo's discount value (9999) exceeds the payout amount (1000), the applied discount must be capped at the payout amount so the net is not negative. After the promo is applied, the test queries the PayoutPromo join table directly via jdbc and columnByField to read the persisted discountApplied value and assert it is at most 1000. The common bug is students applying the raw promo value without a cap check, resulting in negative net amounts.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 1000), seedPayout (PENDING BANK_TRANSFER 1000).
2) _FmM1Seed.seedPromo(this, "TC357_"+nonce(), "FIXED", 9999.0, 100, futureDateTime(), true).
3) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
4) assert2xx(r, "TC357").
5) tableName("PayoutPromo"), columnByField("PayoutPromo", "discountApplied"), columnByField("PayoutPromo", "payout"), query MAX discountApplied for pid.
6) assertTrue(applied <= 1000.01, ...).
```

### Pass Criteria

- **discountApplied <= 1000.01 in PayoutPromo table**
  - *Bug it catches:* Student stores raw promo value (9999) as discountApplied without capping at the payout amount.
- **PayoutPromo row exists**
  - *Bug it catches:* Student applies the discount in memory for the response but does not persist the join record.

---

## TC358 — Applying an inactive promo returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 validation: an inactive promo (active=false) must be rejected with 400 even if it is otherwise valid (not expired, under maxUses). Seeds a PERCENTAGE promo with active=false to isolate the active-flag check. The common bug is students only checking expiry and maxUses but not the active boolean, allowing deactivated promos to be applied. The nonce ensures the promo code is unique per run.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 1000), seedPayout (PENDING BANK_TRANSFER 1000).
2) _FmM1Seed.seedPromo(this, "TC358_"+nonce(), "PERCENTAGE", 10.0, 100, futureDateTime(), false) with active=false.
3) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
4) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student skips the active flag check and applies inactive promos as if they were valid.
- **No 2xx returned**
  - *Bug it catches:* Student only guards against expired or maxed-out promos but ignores the explicit deactivation flag.

---

## TC359 — Applying an expired promo returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 validation: an expired promo (expiresAt in the past) must be rejected with 400 even if it is active and under maxUses. Seeds a PERCENTAGE promo using _FmM1Seed.pastDateTime() for the expiry to set up a definitively expired promo. The common bug is students not checking the expiry date at application time, accepting expired promos. The nonce ensures uniqueness per run.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 1000), seedPayout (PENDING BANK_TRANSFER 1000).
2) _FmM1Seed.seedPromo(this, "TC359_"+nonce(), "PERCENTAGE", 10.0, 100, pastDateTime(), true) with past expiry.
3) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
4) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student does not validate the expiresAt timestamp before applying the promo.
- **No 2xx returned**
  - *Bug it catches:* Student compares expiry against creation date instead of current time, accepting all promos that expire after their creation.

---

## TC360 — Applying a promo at maxUses returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `POST /api/payouts/{id}/promos/{promoId}`

### What it tests

Verifies S5-F5 usage-cap validation: a promo whose currentUses has already reached maxUses must be rejected with 400. Seeds a promo with maxUses=5, then uses _FmM1Seed.setPromoCurrentUses(this, promo, 5) to directly update currentUses to the cap before attempting to apply it. The common bug is students not checking currentUses >= maxUses at application time, allowing over-redemption. The nonce ensures the promo code is unique per run.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract (COMPLETED 1000), seedPayout (PENDING BANK_TRANSFER 1000).
2) _FmM1Seed.seedPromo(this, "TC360_"+nonce(), "PERCENTAGE", 10.0, 5, futureDateTime(), true) with maxUses=5.
3) _FmM1Seed.setPromoCurrentUses(this, promo, 5) to exhaust the usage count.
4) adminToken() then httpPostAuth("/api/payouts/{pid}/promos/{promo}", "", tok).
5) assertEquals(400, r.statusCode(), ...).
```

### Pass Criteria

- **statusCode == 400**
  - *Bug it catches:* Student does not check currentUses >= maxUses before applying, allowing over-redemption beyond the cap.
- **No 2xx returned**
  - *Bug it catches:* Student only decrements remaining uses without guarding against already-exhausted promos.

---

## TC361 — Returns totalRevenue, totalTransactions, refundedAmount, refundCount in range

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/reports/revenue?startDate=...&endDate=...`

### What it tests

Verifies S5-F6 revenue report endpoint returns all four required keys: totalRevenue, totalTransactions, refundedAmount, and refundCount within a date range. Seeds two COMPLETED payouts and one REFUNDED payout so the response must reflect mixed statuses. Common student bug is returning only totalRevenue and omitting refund-specific fields entirely. Assertion uses flexible key-name matching (camelCase or snake_case) to tolerate either JSON serialization style.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout x3 to produce mixed statuses.
2) adminToken() to get auth token.
3) httpGetAuth("/api/payouts/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok).
4) assert2xx(r, "TC361").
5) parseNode(r.body()) then assertTrue for each of: totalRevenue/total_revenue, totalTransactions/total_transactions, refundedAmount/refunded_amount, refundCount/refund_count.
```

### Pass Criteria

- **j.has("totalRevenue") or j.has("total_revenue")**
  - *Bug it catches:* Student omits totalRevenue key or names it differently without snake_case fallback.
- **j.has("totalTransactions") or j.has("total_transactions")**
  - *Bug it catches:* Student returns count under a non-standard key like "count" or "transactionCount".
- **j.has("refundedAmount") or j.has("refunded_amount")**
  - *Bug it catches:* Student excludes refund aggregation entirely from the DTO.
- **j.has("refundCount") or j.has("refund_count")**
  - *Bug it catches:* Student tracks refunded amount but not refund count as a separate field.

---

## TC362 — startDate after endDate returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/reports/revenue?startDate=2026-04-01&endDate=2026-03-01`

### What it tests

Verifies S5-F6 input validation rejects an inverted date range with HTTP 400. No seed data is needed because the validation must fire before any DB query. Common student bug is skipping date-range validation and returning 200 with an empty result set instead. The assertEquals(400) assertion is strict — a 2xx response fails immediately.

### Steps

```
1) adminToken() to get auth token.
2) httpGetAuth("/api/payouts/reports/revenue?startDate=2026-04-01&endDate=2026-03-01", tok).
3) assertEquals(400, r.statusCode(), "TC362: must be 400; got " + r.statusCode()).
```

### Pass Criteria

- **assertEquals(400, r.statusCode())**
  - *Bug it catches:* Student returns 200 with empty data instead of rejecting the inverted range as a bad request.

---

## TC363 — averagePayout reflects totalRevenue / totalTransactions

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/reports/revenue?startDate=...&endDate=...`

### What it tests

Verifies S5-F6 that the averagePayout field is computed and non-zero when COMPLETED payouts exist in range. Seeds two COMPLETED payouts (1000 and 3000) so the average must be 2000, confirming division is being performed rather than being hardcoded to zero. Common student bug is omitting averagePayout from the DTO or returning 0 unconditionally. Uses _FmM2.rD() for flexible key-name resolution.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout x2 (COMPLETED, amounts 1000 and 3000).
2) adminToken().
3) httpGetAuth("/api/payouts/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok).
4) assert2xx(r, "TC363").
5) _FmM2.rD(j, "averagePayout", "average_payout", "avgPayout") then assertTrue(avg > 0).
```

### Pass Criteria

- **avg > 0**
  - *Bug it catches:* Student hard-codes averagePayout=0 or omits it entirely from the report DTO, failing to implement the division.

---

## TC364 — refundedAmount and refundCount reflect REFUNDED payouts only

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/reports/revenue?startDate=...&endDate=...`

### What it tests

Verifies S5-F6 that refundedAmount and refundCount are scoped strictly to REFUNDED-status payouts and are not contaminated by COMPLETED payouts. Seeds one COMPLETED and two REFUNDED payouts; refundCount must be >= 2. Common student bug is counting all payouts (regardless of status) towards refundCount, or summing all amounts into refundedAmount. Uses _FmM2.rL() for long extraction with key-name fallback.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout x3 (one COMPLETED, two REFUNDED).
2) adminToken().
3) httpGetAuth("/api/payouts/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok).
4) assert2xx(r, "TC364").
5) _FmM2.rL(j, "refundCount", "refund_count") then assertTrue(rc >= 2).
```

### Pass Criteria

- **rc >= 2**
  - *Bug it catches:* Student aggregates all payouts into refundCount without filtering by REFUNDED status, or returns 0 because refunds are excluded from the query entirely.

---

## TC365 — No transactions in range returns 0 averagePayout (no div-by-zero)

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/reports/revenue?startDate=2099-01-01&endDate=2099-01-31`

### What it tests

Verifies S5-F6 division-by-zero safety: when no payouts exist in the far-future range 2099, averagePayout must be exactly 0.0 rather than NaN, Infinity, or an exception. Common student bug is dividing totalRevenue by totalTransactions without a null/zero guard, causing an ArithmeticException or returning NaN serialized as null. Uses a far-future date range to guarantee isolation from any seeded data.

### Steps

```
1) adminToken().
2) httpGetAuth("/api/payouts/reports/revenue?startDate=2099-01-01&endDate=2099-01-31", tok).
3) assert2xx(r, "TC365").
4) parseNode(r.body()) then _FmM2.rD(j, "averagePayout", "average_payout", "avgPayout").
5) assertEquals(0.0, avg, 0.001).
```

### Pass Criteria

- **assertEquals(0.0, avg, 0.001)**
  - *Bug it catches:* Student performs integer or floating-point division by zero (totalRevenue / totalTransactions with zero transactions), resulting in an exception or non-zero sentinel value.

---

## TC366 — Retry on FAILED payout transitions to COMPLETED

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/retry`

### What it tests

Verifies S5-F7 happy-path retry: a FAILED payout with details JSON is retried and the response status transitions to COMPLETED. Seeds a FAILED payout with gatewayResponse and retryAttempt in the details JSONB, then calls PUT /retry and asserts the returned status field equals "COMPLETED". Common student bug is returning 200 but not updating the payout status in the DB or DTO, leaving it as FAILED.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (FAILED).
2) _FmM1Seed.setPayoutDetails(this, pid, "{"gatewayResponse":"rejected","retryAttempt":0}").
3) adminToken().
4) httpPutAuth("/api/payouts/" + pid + "/retry", "", tok).
5) assert2xx(r, "TC366") then parseNode(r.body()).path("status").asText("") and assertEquals("COMPLETED", st).
```

### Pass Criteria

- **assertEquals("COMPLETED", st)**
  - *Bug it catches:* Student's retry endpoint returns 200 without actually changing the payout status, or updates the DB but returns the old status in the response body.

---

## TC367 — Retry on non-existent payout returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/9999999/retry`

### What it tests

Verifies S5-F7 that retrying a payout with a non-existent ID returns HTTP 404. Uses a large sentinel ID (9999999) guaranteed not to exist. Common student bug is returning 400 (treating it as a bad state) or 500 (unhandled EntityNotFoundException) instead of 404. No seed data is required because the resource must not be found.

### Steps

```
1) adminToken().
2) httpPutAuth("/api/payouts/9999999/retry", "", tok).
3) assertEquals(404, r.statusCode(), "TC367: must be 404; got " + r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode())**
  - *Bug it catches:* Student throws an unhandled exception resulting in 500, or maps EntityNotFoundException to 400 instead of 404.

---

## TC368 — Retry on COMPLETED payout returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/retry`

### What it tests

Verifies S5-F7 that retrying an already-COMPLETED payout is rejected with HTTP 400, enforcing the state-machine constraint that only FAILED payouts can be retried. Common student bug is performing the retry regardless of current status, returning 200 and potentially double-processing. Seeds a COMPLETED payout so the invalid-state guard must trigger.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (COMPLETED).
2) adminToken().
3) httpPutAuth("/api/payouts/" + pid + "/retry", "", tok).
4) assertEquals(400, r.statusCode(), "TC368: must be 400; got " + r.statusCode()).
```

### Pass Criteria

- **assertEquals(400, r.statusCode())**
  - *Bug it catches:* Student's retry handler does not check the current status and processes the retry unconditionally, returning 200 for a COMPLETED payout.

---

## TC369 — Retry on REFUNDED payout returns 400

**Tags:** `public` `features_m1`  
**Endpoint(s):** `PUT /api/payouts/{id}/retry`

### What it tests

Verifies S5-F7 that retrying a REFUNDED payout is also rejected with HTTP 400, confirming the allowed-state check covers both terminal states (COMPLETED and REFUNDED), not just COMPLETED. Common student bug is guarding only against COMPLETED but overlooking REFUNDED, allowing retry on a finalized refund. Seeds a REFUNDED payout to trigger this specific guard path.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (REFUNDED).
2) adminToken().
3) httpPutAuth("/api/payouts/" + pid + "/retry", "", tok).
4) assertEquals(400, r.statusCode(), "TC369: must be 400; got " + r.statusCode()).
```

### Pass Criteria

- **assertEquals(400, r.statusCode())**
  - *Bug it catches:* Student checks only for COMPLETED in the state guard, forgetting that REFUNDED is also a terminal state that must not be retried.

---

## TC370 — Payout details DTO includes appliedPromoCodes when promos applied

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/{id}/details`

### What it tests

Verifies S5-F8 that the payout details endpoint returns an appliedPromoCodes (or equivalent) field when at least one promo has been linked to the payout. Seeds a payout, a promo, and a payout_promo join record with a discount of 300, then asserts the response contains the promo list field. Common student bug is returning the payout fields but omitting the join-table data entirely, never populating appliedPromoCodes. Uses _FmM2.rO() for key-name-flexible object/array extraction.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (COMPLETED).
2) _FmM1Seed.seedPromo with futureDateTime, active=true.
3) _FmM1Seed.seedPayoutPromo(this, pid, promo, 300.0).
4) adminToken().
5) httpGetAuth("/api/payouts/" + pid + "/details", tok), assert2xx, parseNode, _FmM2.rO(j, "appliedPromoCodes", "applied_promo_codes", "promoCodes"), assertNotNull.
```

### Pass Criteria

- **assertNotNull(promos)**
  - *Bug it catches:* Student's details DTO does not include the applied promo codes join-table relationship, returning null or omitting the field entirely from serialization.

---

## TC371 — Payout with no promos: totalDiscount=0, finalAmount=originalAmount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/{id}/details`

### What it tests

Verifies S5-F8 that when no promo codes have been applied to a payout, the totalDiscount field is exactly 0.0. This baseline check ensures the discount aggregation defaults correctly to zero rather than null or an uninitialized value. Common student bug is returning null for totalDiscount when no promos exist, causing a NullPointerException or a JSON null that fails the assertEquals. Uses _FmM2.rD() for flexible key resolution.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (COMPLETED, amount 1500).
2) adminToken().
3) httpGetAuth("/api/payouts/" + pid + "/details", tok).
4) assert2xx, parseNode, _FmM2.rD(j, "totalDiscount", "total_discount").
5) assertEquals(0.0, totalDisc, 0.01).
```

### Pass Criteria

- **assertEquals(0.0, totalDisc, 0.01)**
  - *Bug it catches:* Student returns null or omits totalDiscount when there are no applied promos, instead of defaulting the aggregate SUM to 0.

---

## TC372 — Details for non-existent payout returns 404

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/9999999/details`

### What it tests

Verifies S5-F8 that requesting details for a payout that does not exist returns HTTP 404. Uses the sentinel ID 9999999. Common student bug is returning 500 due to an unhandled EntityNotFoundException in the service layer, or returning 200 with a null body. No seed data required.

### Steps

```
1) adminToken().
2) httpGetAuth("/api/payouts/9999999/details", tok).
3) assertEquals(404, r.statusCode(), "TC372: must be 404; got " + r.statusCode()).
```

### Pass Criteria

- **assertEquals(404, r.statusCode())**
  - *Bug it catches:* Student does not handle the missing-entity case and lets an unhandled exception propagate as a 500 response.

---

## TC373 — finalAmount = originalAmount - totalDiscount

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/{id}/details`

### What it tests

Verifies S5-F8 arithmetic correctness: finalAmount must equal originalAmount minus the sum of all applied promo discounts. Seeds a 2000-unit payout with two promos applying 200 and 100 respectively (totalDiscount=300), so finalAmount must be 1700. Common student bug is returning the raw payout amount as finalAmount without subtracting the discount, or computing discount sum incorrectly. Uses _FmM2.rD() for all three numeric fields and assertEquals with delta 0.01.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout (2000).
2) seedPromo x2 (PERCENTAGE 10% and FIXED 100), seedPayoutPromo x2 (200.0, 100.0).
3) adminToken().
4) httpGetAuth("/api/payouts/" + pid + "/details", tok), assert2xx, parseNode.
5) _FmM2.rD for originalAmount, totalDiscount, finalAmount then assertEquals(orig - td, finalAmt, 0.01).
```

### Pass Criteria

- **assertEquals(orig - td, finalAmt, 0.01)**
  - *Bug it catches:* Student returns the original payout amount as finalAmount without applying the total discount subtraction, or sums discount_applied incorrectly from the join table.

---

## TC374 — Top promos returns DESC by usage count

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/promos/top-used?limit=10`

### What it tests

Verifies S5-F9 that the top-used promos endpoint returns at least the seeded promos when ordered descending by usage count. Seeds two promos with currentUses of 5 and 1 respectively and asserts the list contains at least 2 entries. Common student bug is returning an empty list because the query uses a JOIN that excludes promos never linked to a payout, or ordering ascending instead of descending. Handles both array and paginated (content) response shapes.

### Steps

```
1) _FmM1Seed.seedPromo x2 (pa with 10% and pb with 5%), _FmM1Seed.setPromoCurrentUses(this, pa, 5) and (this, pb, 1).
2) adminToken().
3) httpGetAuth("/api/payouts/promos/top-used?limit=10", tok).
4) assert2xx, parseNode, resolve list (array or content field).
5) assertTrue(list.size() >= 2).
```

### Pass Criteria

- **list.size() >= 2**
  - *Bug it catches:* Student's query filters to only promos that appear in payout_promos join table, excluding promos that have currentUses set but no join records, returning fewer results than expected.

---

## TC375 — limit=N caps the result list at N

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/promos/top-used?limit=2`

### What it tests

Verifies S5-F9 that the limit query parameter is enforced and the response never exceeds N items. Seeds three promos with usage counts 1, 2, 3 and requests limit=2; the result must have at most 2 elements. Common student bug is ignoring the limit parameter and returning all promos, or applying it only to pagination page size but not the total result count. Handles both array and paginated response shapes.

### Steps

```
1) Loop seedPromo x3, setPromoCurrentUses(this, p, i+1) for i in 0..2.
2) adminToken().
3) httpGetAuth("/api/payouts/promos/top-used?limit=2", tok).
4) assert2xx, parseNode, resolve list.
5) assertTrue(list.size() <= 2).
```

### Pass Criteria

- **list.size() <= 2**
  - *Bug it catches:* Student ignores the limit parameter and returns all promos in the table, or wires limit only to a page-size parameter that doesn't constrain total rows returned.

---

## TC376 — DTO 'expired' is true for past expiryDate

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/promos/top-used?limit=100`

### What it tests

Verifies S5-F9 that each promo DTO includes a computed boolean 'expired' field set to true when the expiryDate is in the past. Seeds one promo with a past expiry date (via _FmM1Seed.pastDateTime()), sets its currentUses to 1, then scans the response list for that promo's ID and checks expired=true. Common student bug is not including the expired computed field in the DTO at all, or always returning false.

### Steps

```
1) _FmM1Seed.seedPromo with pastDateTime(), active=true, then setPromoCurrentUses(this, pid, 1).
2) adminToken().
3) httpGetAuth("/api/payouts/promos/top-used?limit=100", tok).
4) assert2xx, parseNode, resolve list.
5) Scan list for matching promoCodeId/promo_code_id/id, read expired boolean, assertTrue(foundExpired).
```

### Pass Criteria

- **assertTrue(foundExpired)**
  - *Bug it catches:* Student omits the expired computed field from the DTO, or always sets it to false without comparing expiryDate against the current timestamp.

---

## TC377 — timesUsed in DTO matches the promo's currentUses column

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/promos/top-used?limit=100`

### What it tests

Verifies S5-F9 that the timesUsed (or times_used) field in the top-promos DTO accurately reflects the currentUses column value set directly in the DB. Seeds a promo, sets its currentUses to 7 via setPromoCurrentUses, then asserts the found timesUsed equals exactly 7. Common student bug is computing timesUsed by counting payout_promos join rows instead of reading the currentUses column, returning 0 when no join records exist.

### Steps

```
1) _FmM1Seed.seedPromo(PERCENTAGE, futureDateTime, active), setPromoCurrentUses(this, pid, 7).
2) adminToken().
3) httpGetAuth("/api/payouts/promos/top-used?limit=100", tok).
4) assert2xx, parseNode, resolve list.
5) Scan for matching ID, read timesUsed/times_used, assertEquals(7, found).
```

### Pass Criteria

- **assertEquals(7, found)**
  - *Bug it catches:* Student derives timesUsed by counting join-table rows rather than from the currentUses column, returning 0 instead of 7 when the promo has no payout_promos records.

---

## TC378 — totalDiscountGiven equals SUM(payout_promos.discount_applied) for the promo

**Tags:** `public` `features_m1`  
**Endpoint(s):** `GET /api/payouts/promos/top-used?limit=100`

### What it tests

Verifies S5-F9 that the totalDiscountGiven (or total_discount_given) field equals the aggregate sum of discount_applied values from the payout_promos join table for that promo. Seeds two payouts each linked to the same promo with discount_applied=100, so totalDiscountGiven must be exactly 200.0. Common student bug is reporting the promo's own discountValue (100) instead of summing all join-table applications. Uses assertEquals with delta 0.01.

### Steps

```
1) _FmM1Seed.seedUser, seedJob, seedContract, seedPayout x2 (1500 each), seedPromo (FIXED, 100).
2) seedPayoutPromo(this, pid1, promo, 100.0) and (this, pid2, promo, 100.0), setPromoCurrentUses(this, promo, 2).
3) adminToken().
4) httpGetAuth("/api/payouts/promos/top-used?limit=100", tok), assert2xx, parseNode, resolve list.
5) Scan for matching promo ID, read totalDiscountGiven/total_discount_given, assertEquals(200.0, td, 0.01).
```

### Pass Criteria

- **assertEquals(200.0, td, 0.01)**
  - *Bug it catches:* Student returns the promo's discountValue field (100) as totalDiscountGiven instead of summing all discount_applied entries from payout_promos, producing 100 instead of the correct 200.

---

# Design Patterns

## TC379 — DP-1 Strategy: RefundStrategy interface exists

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.2, the Strategy pattern requires a RefundStrategy interface with exactly one abstract method named calculateRefund. This is the contract every concrete strategy must implement and the entry point the selector dispatches to at runtime. Asserting the interface shape via reflection is the cheapest way to detect a malformed Strategy implementation before exercising any HTTP path.

### Pre-conditions

Compiled wallet-service classpath available.

### Steps

```
1) Reflection-load RefundStrategy.
2) Assert it is an interface (Class.isInterface()).
3) Enumerate abstract methods; assert exactly one whose name equals calculateRefund.
```

### Pass Criteria

- **RefundStrategy is an interface**
  - *Bug it catches:* Strategy modeled as a concrete class or abstract base — defeats polymorphic dispatch.
- **Exactly one abstract method named calculateRefund**
  - *Bug it catches:* Extra abstract methods (e.g., validate, getName) bloat the contract and force every concrete strategy to override irrelevant methods.

---

## TC380 — DP-1 Strategy: 3 concrete strategies implement RefundStrategy

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.2, the spec mandates three concrete strategies named exactly FullPayoutReversalStrategy, MilestoneReversalStrategy, and NoReversalStrategy (theme.json s5StrategyFullRefund/s5StrategyFoodOnly/s5StrategyNoRefund). Each must implement RefundStrategy so the selector can return any of them through the common interface.

### Pre-conditions

TC379 passes.

### Steps

```
1) Reflection-load each of the three classes by exact name from theme.json.
2) For each, assert RefundStrategy.class.isAssignableFrom(strategyClass).
```

### Pass Criteria

- **All 3 concrete strategy classes load by name**
  - *Bug it catches:* Class missing or named differently — selector cannot return the expected type.
- **Each implements RefundStrategy**
  - *Bug it catches:* Class exists but does not implement the interface — won't compile in selector.select() return position.

---

## TC381 — DP-1 Strategy: RefundStrategySelector exists

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.2, the dispatch logic must live in a separate RefundStrategySelector (or RefundStrategyFactory) class — not in the service. The service should only call selector.select(payout, request).calculateRefund(...). A separate selector keeps the service decoupled from the strategy choice.

### Pre-conditions

None.

### Steps

```
1) Reflection-scan wallet-service classpath for a class named RefundStrategySelector or RefundStrategyFactory.
2) Assert a select-like method exists.
3) Assert its return type is RefundStrategy.
```

### Pass Criteria

- **Selector class exists**
  - *Bug it catches:* Selection logic embedded in the service (TC385 catches the consequence; this catches the structural cause).
- **select() returns RefundStrategy**
  - *Bug it catches:* Method returns a concrete strategy type — defeats polymorphic substitutability and forces the caller to know which strategy ran.

---

## TC382 — DP-1 Strategy: FullPayoutReversalStrategy audit trail

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** `POST /api/payouts/{id}/reverse-milestone (reversalScope=FULL, recent payout)`

### What it tests

Per Section 3.2 step (d), invoking S5-F12 with a recent payout (within 30 days from createdAt) and reversalScope=FULL must select FullPayoutReversalStrategy AND record the strategy class name into the payout_audit_trail audit event. The recorded class name is the runtime evidence of which branch ran — without it the test cannot distinguish a wrong strategy from the right one when both produce the same numeric refund.

### Pre-conditions

COMPLETED payout created today, amount=5000, contract reference set.

### Steps

```
1) JDBC insert COMPLETED payout with createdAt = NOW(), amount=5000, with valid contractId.
2) POST /api/payouts/{id}/reverse-milestone with body {"reversalScope":"FULL","reason":"contract terminated"}.
3) Assert 2xx with refundAmount=5000.
4) Read latest doc in payout_audit_trail for that payoutId.
5) Assert details.strategyName equals FullPayoutReversalStrategy.
```

### Pass Criteria

- **2xx with refundAmount=5000**
  - *Bug it catches:* Strategy returned wrong amount.
- **Audit event strategyName == FullPayoutReversalStrategy**
  - *Bug it catches:* Selector picked MilestoneReversal even though reversalScope=FULL — silent dispatch bug masked by similar-looking output.

---

## TC383 — DP-1 Strategy: MilestoneReversalStrategy audit trail

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** `POST /api/payouts/{id}/reverse-milestone (reversalScope=MILESTONE_ONLY, recent payout)`

### What it tests

Per Section 3.2 step (e), reversalScope=MILESTONE_ONLY within the 30-day window must select MilestoneReversalStrategy and refund only the sum of ProposalMilestone.amount values whose status is NOT in {COMPLETED, APPROVED}, resolved via the cross-service native SQL pattern (contract → proposalId → proposal_milestones). The audit event must record the strategy class name.

### Pre-conditions

COMPLETED payout created today amount=5000; contract → proposal with 3 milestones (2 COMPLETED at 1500 each, 1 IN_PROGRESS at 2000).

### Steps

```
1) JDBC insert user, job, proposal, contract, then 3 proposal_milestone rows (status COMPLETED/COMPLETED/IN_PROGRESS, amounts 1500/1500/2000), then COMPLETED payout amount=5000 createdAt=NOW().
2) POST /api/payouts/{id}/reverse-milestone with {"reversalScope":"MILESTONE_ONLY","reason":"milestone dispute"}.
3) Assert 2xx with refundAmount=2000.
4) Read latest payout_audit_trail doc.
5) Assert strategyName equals MilestoneReversalStrategy.
```

### Pass Criteria

- **2xx with refundAmount=2000**
  - *Bug it catches:* Wrong arithmetic in MilestoneReversal — refunded full amount, zero, or summed the wrong status set (e.g., included COMPLETED milestones).
- **Audit strategyName == MilestoneReversalStrategy**
  - *Bug it catches:* Selector ignores the reversalScope flag and routes everything through one strategy.

---

## TC384 — DP-1 Strategy: NoReversalStrategy 400 + audit

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** `POST /api/payouts/{id}/reverse-milestone (payout older than 30 days)`

### What it tests

Per Section 3.2 step (f), a payout older than 30 days from createdAt must select NoReversalStrategy, return 400 with reason "reversal window expired", AND emit a REFUND_DENIED event with the strategy name. The audit log must be written BEFORE the 400 is thrown (per S5-F12 step e); otherwise the denial is unobservable for downstream analytics.

### Pre-conditions

COMPLETED payout with createdAt = NOW() − 35 days.

### Steps

```
1) JDBC insert COMPLETED payout with createdAt 35 days ago.
2) POST /api/payouts/{id}/reverse-milestone with {"reversalScope":"FULL","reason":"x"}.
3) Assert HTTP 400 and body contains "reversal window expired".
4) Read latest payout_audit_trail document for that payoutId.
5) Assert action == REFUND_DENIED and strategyName == NoReversalStrategy.
```

### Pass Criteria

- **400 with "reversal window expired"**
  - *Bug it catches:* Time-based dispatch missing — old payouts still reversed.
- **Audit event REFUND_DENIED + strategyName=NoReversalStrategy**
  - *Bug it catches:* Denial path skips audit logging (commonly because it throws before the Observer fires) — analytics never see denial events.

---

## TC385 — DP-1 Strategy: refund service has no if-else on reversalScope

**Tags:** `public` `patterns` `strategy`  
**Endpoint(s):** _(source scan)_

### What it tests

Per Section 3.2 step (g), the refund service method body must NOT contain if (reversalScope == FULL), if ("FULL".equals(reversalScope)), or equivalent branching — that decision belongs in the selector, not the service. A service that still branches is a façade Strategy: pattern shape without pattern intent.

### Pre-conditions

None.

### Steps

```
1) Locate the S5-F12 service method that handles /reverse-milestone.
2) Source-scan the method body for if (reversalScope, ternary reversalScope ?, or switch on reversalScope.
3) Assert no match.
```

### Pass Criteria

- **No branching on reversalScope in the service**
  - *Bug it catches:* Strategy classes exist but the service still owns the dispatch — refactoring the strategies in isolation has no effect on behavior.

---

## TC386 — DP-2 Observer: EntityObserver interface

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.3, the GoF Observer requires an EntityObserver interface with method onEvent(String eventType, Object payload). Every concrete observer must implement this contract so subjects can register observers polymorphically without knowing their concrete type.

### Pre-conditions

None.

### Steps

```
1) Reflection-load EntityObserver.
2) Assert it is an interface.
3) Assert it declares method onEvent(String, Object) (or equivalent two-argument signature).
```

### Pass Criteria

- **EntityObserver is an interface**
  - *Bug it catches:* Observer modeled as a concrete class — multiple observer kinds cannot coexist on one subject.
- **onEvent(String, Object) declared**
  - *Bug it catches:* Method named differently or wrong arity — register/notify cannot compile against a uniform handler signature.

---

## TC387 — DP-2 Observer: MongoEventLogger implements EntityObserver

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.3, MongoEventLogger is the concrete observer that persists events into MongoDB. It must implement EntityObserver so subjects treat it polymorphically — replacing it with a stub or unregistering it must not require source changes elsewhere.

### Pre-conditions

TC386 passes.

### Steps

```
1) Reflection-load MongoEventLogger.
2) Assert EntityObserver.class.isAssignableFrom(MongoEventLogger.class).
```

### Pass Criteria

- **MongoEventLogger implements EntityObserver**
  - *Bug it catches:* Logger built without inheriting from the interface — would force services to know they're talking to MongoEventLogger specifically.

---

## TC388 — DP-2 Observer: no @EventListener writes to MongoDB (Spring vs GoF)

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** _(static analysis)_

### What it tests

Per Section 3.3, M2 mandates classical GoF Observer (explicit interface + register/notify methods). Spring's @EventListener is forbidden on the MongoDB-write path because using it would skip the learning objective of building the pattern manually. The spec calls this out explicitly: no method annotated with @EventListener may write to MongoDB across user, job, proposal, contract, and wallet services.

### Pre-conditions

None.

### Steps

```
1) Source/class-file scan all 5 services (user, job, proposal, contract, wallet).
2) Find every method annotated with @EventListener.
3) For each, assert no MongoDB write (no mongoTemplate, no Spring Data Mongo Repository.save/insert).
```

### Pass Criteria

- **Zero @EventListener methods write to MongoDB**
  - *Bug it catches:* Team implemented event logging via Spring's built-in publisher and shipped a no-op classical Observer just to satisfy reflection — defeats the M2 learning objective.

---

## TC389 — DP-2 Observer: register triggers REGISTERED in auth_events

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** `POST /api/auth/register`

### What it tests

Per Section 3.3 step (d), registration must propagate through the observer chain and produce exactly one REGISTERED document in auth_events with the new user's id. End-to-end check that the chain is wired from the controller through the subject through the MongoEventLogger.

### Pre-conditions

MongoDB reachable.

### Steps

```
1) POST /api/auth/register with a fresh nonce-based email and phone.
2) Capture the resulting user id.
3) Query auth_events filtered by userId AND action="REGISTERED".
4) Assert exactly 1 document with a recent timestamp.
```

### Pass Criteria

- **1 REGISTERED document with matching userId**
  - *Bug it catches:* Auth controller never invokes notifyObservers (most common retrofit miss); or fires it but with the wrong userId/action.

---

## TC390 — DP-2 Observer: login triggers LOGGED_IN

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** `POST /api/auth/login`

### What it tests

Per Section 3.3 step (e), login produces a LOGGED_IN document on every successful authentication. Sister test to TC389 — proves both auth controllers fire the chain, not just register.

### Pre-conditions

Registered user.

### Steps

```
1) Snapshot count of auth_events for that userId.
2) Login with valid credentials.
3) Re-snapshot count.
4) Assert grew by ≥1 with action=LOGGED_IN.
```

### Pass Criteria

- **New LOGGED_IN doc per login**
  - *Bug it catches:* Login bypasses observer chain — only register fires notifications, leaving login silent in the audit trail.

---

## TC391 — DP-2 Observer: M1 retrofit (S1-F2) emits event

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** `PUT /api/users/{id}/preferences (M1 S1-F2)`

### What it tests

Per Section 3.3 step (f) and Section 4.5, every M1 write endpoint must be retrofitted to fire the observer chain. PUT preferences (M1 S1-F2) is the canonical example: invoking the M1 endpoint must produce a MongoDB event in auth_events, proving the retrofit isn't only on M2 features.

### Pre-conditions

Registered user with valid token.

### Steps

```
1) Snapshot auth_events count for the user.
2) PUT /api/users/{id}/preferences with body {"language":"fr"}.
3) Re-snapshot.
4) Assert count grew by ≥1 (action USER_UPDATED or equivalent).
```

### Pass Criteria

- **M1 write produces a MongoDB event**
  - *Bug it catches:* M2 features fire observers but M1 retrofits skipped — cache invalidation and audit trail incomplete on legacy paths.

---

## TC392 — DP-2 Observer: unregister stops events (proves chain path)

**Tags:** `public` `patterns` `observer`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.3 step (g), removing all observers from the subject must stop MongoDB writes. This is the reverse-corner check: it proves the logging path goes through the explicit observer chain rather than via a hidden direct Mongo call inside the service.

### Pre-conditions

A unit-test-friendly subject with MongoEventLogger registered.

### Steps

```
1) Construct subject and register MongoEventLogger.
2) Unregister all observers.
3) Trigger a write that would normally fire notifyObservers.
4) Snapshot auth_events count before and after — assert NO new doc.
```

### Pass Criteria

- **No event written when observer chain is empty**
  - *Bug it catches:* Service has both notifyObservers(...) AND a direct mongoTemplate.save(...) next to it — unregistering the observer doesn't stop event writes (the chain is decorative, not load-bearing).

---

## TC393 — DP-3 CoR: AuthHandler base + setNext/handle

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.4, the JWT auth chain requires an abstract AuthHandler (or interface) with handle(AuthContext) and setNext(AuthHandler) methods. These are the canonical Chain-of-Responsibility primitives — without setNext there is no chain.

### Pre-conditions

None.

### Steps

```
1) Reflection-load AuthHandler.
2) Assert it has both setNext(AuthHandler) and handle(...) methods (any concrete signature for handle accepted, e.g., handle(AuthContext)).
```

### Pass Criteria

- **AuthHandler exists**
  - *Bug it catches:* Handler not modeled as a chain primitive — class has handle() but no setNext(), so handlers cannot be linked at runtime.
- **Both methods declared**
  - *Bug it catches:* setNext defined on each concrete handler instead of the base — inconsistent chain construction.

---

## TC394 — DP-3 CoR: ≥3 concrete AuthHandler subclasses

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.4, the chain has at least 3 concrete handlers (TokenExtractionHandler, SignatureValidationHandler, UserLoaderHandler, optionally RoleAuthorizationHandler). The minimum-3 requirement ensures the chain isn't collapsed into a fat all-in-one handler.

### Pre-conditions

TC393 passes.

### Steps

```
1) Reflection scan user-service classpath for concrete (non-abstract) subclasses of AuthHandler.
2) Assert count ≥ 3.
```

### Pass Criteria

- **≥3 concrete AuthHandler subclasses**
  - *Bug it catches:* A single fat handler does extraction + signature + role inline — the per-step responsibility separation that justifies the pattern is lost.

---

## TC395 — DP-3 CoR: missing Authorization → 401

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** `GET /api/users/1 (no Authorization header)`

### What it tests

Per Section 3.4 step (c), the first handler (TokenExtractionHandler) rejects requests without an Authorization header with 401. End-to-end behavioral evidence that the first link in the chain works.

### Pre-conditions

None.

### Steps

```
1) GET /api/users/1 with NO Authorization header.
2) Assert HTTP status 401.
```

### Pass Criteria

- **401 (TokenExtractionHandler short-circuited)**
  - *Bug it catches:* First handler missing or doesn't short-circuit — later handlers NPE on the missing token instead of returning a clean 401.

---

## TC396 — DP-3 CoR: invalid signature → 401

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** `GET /api/users/1 with Bearer xxx.yyy.zzz`

### What it tests

Per Section 3.4 step (d), a syntactically-valid but cryptographically-invalid token is rejected with 401 by the SignatureValidationHandler. This is the second-leg test: token extraction succeeded, but signature verification failed, and the chain stopped before user-load.

### Pre-conditions

None.

### Steps

```
1) GET /api/users/1 with Authorization: Bearer xxx.yyy.zzz (valid format, invalid signature).
2) Assert 401.
```

### Pass Criteria

- **401 (SignatureValidation rejected)**
  - *Bug it catches:* Signature step skipped — payload claims read directly from base64, making the role/uid forgeable.

---

## TC397 — DP-3 CoR: deleted user with valid token → 401

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** `GET /api/users/1 with token of deleted user`

### What it tests

Per Section 3.4 step (e), a structurally-valid token must be rejected when the user no longer exists in PG. The UserLoaderHandler resolves the user from the DB on every request and 401s when missing. Catches stale-token issues for deactivated/deleted accounts.

### Pre-conditions

A user, captured token, then user JDBC-deleted.

### Steps

```
1) Register user A and capture A's token.
2) JDBC DELETE FROM users WHERE id=? for A.
3) GET /api/users/{any-id} with A's token.
4) Assert 401.
```

### Pass Criteria

- **401 (UserLoader rejected)**
  - *Bug it catches:* Filter never reloads user from PG — stale tokens for deleted users still authenticate, allowing a deactivated account to act for up to the token TTL (24h).

---

## TC398 — DP-3 CoR: ADMIN-only endpoint with CLIENT token → 403

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** `PUT /api/users/{id}/role with CLIENT token`

### What it tests

Per Section 3.4 step (f), the RoleAuthorizationHandler rejects insufficient roles with 403 (not 401 — the user IS authenticated, just unauthorized). Distinguishing 401 from 403 is the spec's point: anonymous = 401, authenticated-but-forbidden = 403. CLIENT is Freelance's default role on registration (per §5.3).

### Pre-conditions

CLIENT user with valid token.

### Steps

```
1) Register a CLIENT and login.
2) PUT /api/users/{any-id}/role with body {"role":"ADMIN"}.
3) Assert 403.
```

### Pass Criteria

- **Strictly 403**
  - *Bug it catches:* Role check missing (returns 200 — privilege escalation), or returns 401 — leaks the existence of admin endpoints to unauthenticated callers vs cleanly distinguishing "forbidden".

---

## TC399 — DP-3 CoR: ADMIN-only with ADMIN token → 2xx

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** `PUT /api/users/{id}/role with ADMIN token`

### What it tests

Per Section 3.4 step (g), the chain passes through cleanly when all handlers succeed. The positive path completes the CoR test matrix.

### Pre-conditions

Seeded ADMIN, target CLIENT.

### Steps

```
1) Login as the seeded ADMIN.
2) PUT /api/users/{client.id}/role with body {"role":"FREELANCER"}.
3) Assert 2xx and the target's role is now FREELANCER.
```

### Pass Criteria

- **2xx (chain pass-through)**
  - *Bug it catches:* Chain rejects all writes (over-eager) or misclassifies ADMIN as insufficient.

---

## TC400 — DP-3 CoR: filter delegates to chain head (source scan)

**Tags:** `public` `patterns` `chain-of-responsibility`  
**Endpoint(s):** _(source scan)_

### What it tests

Per Section 3.4 step (h), JwtAuthenticationFilter.doFilterInternal() must invoke the head of the AuthHandler chain rather than duplicating extraction/validation/authorization logic inline. This proves the chain is load-bearing — changes to handler implementations actually take effect.

### Pre-conditions

None.

### Steps

```
1) Source-scan JwtAuthenticationFilter.doFilterInternal() body.
2) Assert it constructs/invokes a chain head (e.g., head.handle(...) or via injection).
3) Assert it does NOT contain inline Jwts.parser, BCrypt, or hardcoded role checks.
```

### Pass Criteria

- **Chain head invoked**
  - *Bug it catches:* Chain exists but is dead code — the filter does everything inline anyway, so handler classes are decorative.

---

## TC401 — DP-4 Builder: M2 dashboard DTOs have builder()

**Tags:** `public` `patterns` `builder`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.5, the four M2 dashboard/analytics DTOs (JobDashboardDTO from S2-F12, ProposalAnalyticsDashboardDTO from S3-F10, ContractAnalyticsDTO from S4-F10, CategoryRevenueDTO from S5-F10) each have 5+ fields and must expose a static builder() plus a fluent build().

### Pre-conditions

None.

### Steps

```
1) For each of the 4 M2 dashboard DTOs: reflection-load the class.
2) Locate static builder() method.
3) Invoke builder() to obtain a Builder instance.
4) Call any setter — assert it returns the Builder (fluent chain).
5) Call build() and assert return type matches the DTO.
```

### Pass Criteria

- **builder() / setter / build() all wired**
  - *Bug it catches:* DTO uses public constructor with 7+ positional args (defeats Builder's purpose) or builder() exists but skips fluent chaining.

---

## TC402 — DP-4 Builder: M1 in-scope DTOs have Builder

**Tags:** `public` `patterns` `builder`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.5, the M2 spec lists 18 M1 features as Builder-eligible (S1-F3/F6/F8/F9, S2-F3/F6/F9, S3-F3/F6/F9, S4-F3/F6/F8/F9, S5-F3/F6/F8/F9), but cross-checking against the M1 spec reveals an inconsistency: S1-F9 (Find Users by Language Preference with Minimum Contracts) and S4-F6 (Get Contracts in Date Range) declare no Response DTO in the M1 spec — they return List<User> and List<Contract> entities respectively. The M2 spec is internally inconsistent on these two; the grader follows M1 reality and treats them as entity-returners (parallel to how the M2 spec already excludes S2-F8 and S3-F8), so Builder is NOT required for them. The actual in-scope set is therefore 16 DTO-returning M1 features.

### Pre-conditions

None.

### Steps

```
1) For each of the 16 in-scope M1 DTOs — UserContractSummaryDTO (S1-F3), TopFreelancerDTO (S1-F6), UserProfileDTO (S1-F8), JobProposalSummaryDTO (S2-F3), TopBudgetJobDTO (S2-F6), JobAttachmentAlertDTO (S2-F9), FeeEstimateDTO (S3-F3), ProposalAnalyticsDTO (S3-F6), ProposalDetailsDTO (S3-F9), ContractSummaryDTO (S4-F3), FreelancerPerformanceDTO (S4-F8), StalledContractDTO (S4-F9), FreelancerPayoutSummaryDTO (S5-F3), RevenueReportDTO (S5-F6), PayoutDetailsDTO (S5-F8), PromoCodeUsageDTO (S5-F9): reflection-load the DTO class.
2) Assert a static builder() method exists.
3) Verify build() returns the DTO type.
4) Do NOT require Builder for S1-F9 or S4-F6 — they have no DTO to attach a Builder to per the M1 spec.
```

### Pass Criteria

- **All 16 DTO-returning M1 features have Builder**
  - *Bug it catches:* Retrofit only applied to M2 — M1 DTOs left unchanged, breaking the explicit Section 3.5 retrofit contract.
- **S1-F9 and S4-F6 are skipped (entity returns per M1 spec, parallel to S2-F8/S3-F8)**
  - *Bug it catches:* Grader requires Builder on entity-returning features — false positive against features that have no DTO in M1 to apply Builder to.

---

## TC403 — DP-4 Builder: M2 dashboard regression after Builder retrofit

**Tags:** `public` `patterns` `builder`  
**Endpoint(s):** `GET /api/jobs/{id}/dashboard (S2-F12)`

### What it tests

Regression check after the Builder retrofit on JobDashboardDTO: the dashboard endpoint must continue to return a correctly-populated response. Swapping the DTO's all-args constructor for a Builder-based construction is a behavior-preserving refactor — this test verifies the swap didn't break field population, response shape, or aggregation arithmetic.

### Pre-conditions

Job with proposals and active attachments.

### Steps

```
1) Create a job.
2) JDBC insert 5 proposals for that job (1 ACCEPTED, 4 SUBMITTED, average bid = 880) and 2 attachments with future expiryDate.
3) GET /api/jobs/{id}/dashboard with valid token.
4) Parse the response body as JSON.
5) Assert jobId, title, totalProposals, acceptedProposals, averageBidAmount, activeAttachments, rating fields are present and populated with the expected values.
```

### Pass Criteria

- **Response well-formed and fields populated correctly**
  - *Bug it catches:* The Builder retrofit broke the response shape — fields missing, mis-mapped, or wrong values after the constructor → Builder swap (e.g., setter chain wired to the wrong field, missing .averageBidAmount(...) call, build() constructing the record with arguments in the wrong order). This is the regression that the retrofit is most likely to introduce on M2 features.

---

## TC404 — DP-4 Builder: M1 retrofit doesn't break behavior

**Tags:** `public` `patterns` `builder`  
**Endpoint(s):** `GET /api/users/{id}/contract-summary (M1 S1-F3)`

### What it tests

Per Section 3.5 step (4), Builder retrofit must not change M1 response shape. Regression check on S1-F3 — same fields, same values, just constructed via Builder internally.

### Pre-conditions

User with COMPLETED/TERMINATED/ACTIVE contracts.

### Steps

```
1) Create user + 5 contracts (3 COMPLETED with agreedAmounts 500/1000/1500, 1 TERMINATED, 1 ACTIVE).
2) GET /api/users/{id}/contract-summary.
3) Assert response contains totalContracts=5, completedContracts=3, terminatedContracts=1, totalEarnings=3000, averageContractValue=1000.
```

### Pass Criteria

- **M1 endpoint returns expected fields**
  - *Bug it catches:* Retrofit changed field names or order — M1 regression that would break clients still calling the legacy endpoint.

---

## TC405 — DP-4 Builder: S2-F8 and S3-F8 do NOT use Builder

**Tags:** `public` `patterns` `builder`  
**Endpoint(s):** _(source scan)_

### What it tests

Per Section 3.5, S2-F8 (Verify Job Attachment) and S3-F8 (Add Milestones to Proposal) return entities, not DTOs, so Builder is explicitly excluded for Freelance. The grader confirms students didn't over-apply the pattern.

### Pre-conditions

None.

### Steps

```
1) Source-scan S2-F8 and S3-F8 service methods.
2) Assert no Builder is constructed for the return value (controller returns entity directly).
```

### Pass Criteria

- **No Builder used in S2-F8 or S3-F8**
  - *Bug it catches:* Student wrapped the entity in an unnecessary Builder out of pattern-thumping — over-engineered without benefit.

---

## TC406 — DP-5 Singleton: JwtConfigurationManager has private constructor

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.6, classical Singleton requires exactly one constructor with private access. Public constructors break the pattern (any caller could new JwtConfigurationManager() and bypass the singleton).

### Pre-conditions

None.

### Steps

```
1) Reflection-load JwtConfigurationManager.
2) Get declared constructors.
3) Assert length == 1.
4) Assert Modifier.isPrivate(constructor.getModifiers()).
```

### Pass Criteria

- **Single private constructor**
  - *Bug it catches:* Public default constructor exposed — callers can construct multiple instances, defeating the singleton.

---

## TC407 — DP-5 Singleton: getInstance() is public static

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.6, getInstance() is the canonical access point. Must be public + static + return the singleton type.

### Pre-conditions

None.

### Steps

```
1) Reflection-load JwtConfigurationManager.
2) Find method getInstance().
3) Assert Modifier.isPublic and Modifier.isStatic.
4) Assert return type is JwtConfigurationManager.
```

### Pass Criteria

- **getInstance() is public static returning JwtConfigurationManager**
  - *Bug it catches:* Method named differently (get, instance), non-static (per-bean), or wrong return type (returns Object).

---

## TC408 — DP-5 Singleton: same reference (==)

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** _(behavioral)_

### What it tests

Per Section 3.6 step (3), Singleton's defining property — two getInstance() calls return the SAME instance via reference equality (==), not just .equals().

### Pre-conditions

None.

### Steps

```
1) ref1 = JwtConfigurationManager.getInstance().
2) ref2 = JwtConfigurationManager.getInstance().
3) Assert ref1 == ref2.
```

### Pass Criteria

- **ref1 == ref2 (identity)**
  - *Bug it catches:* getInstance() implemented as return new JwtConfigurationManager() every call — looks like a singleton but isn't.

---

## TC409 — DP-5 Singleton: thread-safe under contention

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** _(concurrency test)_

### What it tests

Per Section 3.6 step (4), concurrent getInstance() calls must all return the SAME reference. This catches lazy initialization without synchronization, where a race produces multiple instances under load.

### Pre-conditions

None.

### Steps

```
1) Spawn 10 threads (e.g., via IntStream.range(0,10).parallel()).
2) Each thread captures JwtConfigurationManager.getInstance().
3) Collect references into an IdentityHashMap or Set keyed by identity hash.
4) Assert set.size() == 1.
```

### Pass Criteria

- **All 10 threads see the same reference**
  - *Bug it catches:* Lazy init without synchronization — concurrent first calls each see instance==null and each call new, producing multiple instances.

---

## TC410 — DP-5 Singleton: NOT a Spring bean

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.6, JwtConfigurationManager must be a classical GoF Singleton, NOT a Spring-managed bean. Annotations like @Component / @Service / @Configuration disqualify it because Spring would manage the lifecycle, defeating the pattern's lecture purpose and risking two singletons coexisting under @Component-driven init plus explicit getInstance().

### Pre-conditions

None.

### Steps

```
1) Reflection-load JwtConfigurationManager.
2) Assert NONE of the annotations: @Component, @Service, @Configuration, @Bean, @Repository are present on the class.
```

### Pass Criteria

- **No Spring stereotype annotations**
  - *Bug it catches:* Student added @Component out of habit — making it a Spring singleton (not classical GoF) and possibly causing two singletons to coexist when @Component-driven init races with explicit getInstance().

---

## TC411 — DP-5 Singleton: JwtService reads via getInstance()

**Tags:** `public` `patterns` `singleton`  
**Endpoint(s):** `POST /api/auth/login round-trip`

### What it tests

Per Section 3.6 step (6), the Spring-managed JwtService must obtain JWT config via JwtConfigurationManager.getInstance() rather than via @Autowired or @Value. Integration test: issue a token via login and validate it via a protected endpoint — proves the singleton-served secret round-trips correctly.

### Pre-conditions

Running stack.

### Steps

```
1) Register and login a user.
2) Capture JWT.
3) GET a protected endpoint with the token.
4) Assert 2xx (validation passed).
```

### Pass Criteria

- **Token round-trip works via singleton-served secret**
  - *Bug it catches:* JwtService uses @Value("${jwt.secret}") directly (bypasses singleton) — the singleton exists but isn't actually wired into the auth flow.

---

## TC412 — DP-6 Factory: MongoEvent interface

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.7 and 7.1.1, the common MongoEvent interface unifies the 5 event subtypes. It must declare getId(), getTimestamp(), getAction(), getDetails() so EventFactory.createEvent can return any subtype typed through this interface.

### Pre-conditions

None.

### Steps

```
1) Reflection-load MongoEvent.
2) Assert it is an interface.
3) Assert all 4 methods declared with the right return types: getId() → String, getTimestamp() → LocalDateTime, getAction() → String, getDetails() → Map<String,Object>.
```

### Pass Criteria

- **Interface with 4 correctly-typed methods**
  - *Bug it catches:* Each event class has its own bespoke API → factory return type can't be MongoEvent → factory loses its polymorphic value.

---

## TC413 — DP-6 Factory: 5 event classes implement MongoEvent

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.7, the five concrete event classes (AuthEvent, JobEvent, ProposalEvent, ContractEvent, PayoutAuditEvent) must all implement MongoEvent so the factory can return any of them through the common type.

### Pre-conditions

TC412 passes.

### Steps

```
1) Reflection-load each of the 5 event classes by name.
2) For each, assert MongoEvent.class.isAssignableFrom(eventClass).
```

### Pass Criteria

- **All 5 events implement MongoEvent**
  - *Bug it catches:* One or more events not retrofitted to the common interface — factory's polymorphic return position breaks.

---

## TC414 — DP-6 Factory: createEvent(EventType, Map) signature

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.7, the factory entry point is createEvent(EventType type, Map<String, Object> params) returning MongoEvent. The signature is the contract — strings or generics break dispatch correctness.

### Pre-conditions

None.

### Steps

```
1) Reflection-load EventFactory.
2) Find method createEvent taking (EventType, Map).
3) Assert its return type is MongoEvent (or assignable to it).
```

### Pass Criteria

- **createEvent(EventType, Map) → MongoEvent**
  - *Bug it catches:* Factory takes a string discriminator (untyped, error-prone) or returns a concrete subtype (loses substitutability).

---

## TC415 — DP-6 Factory: createEvent(AUTH, ...) returns AuthEvent

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.7 step (4), dispatching on EventType.AUTH must return an instance assignable to AuthEvent with fields populated from the params map. The factory is the single source of construction; this test exercises one of its branches.

### Pre-conditions

None.

### Steps

```
1) Build params = {userId:1, action:"REGISTERED", timestamp:LocalDateTime.now(), details:{}}.
2) Call EventFactory.createEvent(EventType.AUTH, params).
3) Assert instanceof AuthEvent.
4) Assert getAction().equals("REGISTERED") and userId set.
```

### Pass Criteria

- **AuthEvent returned with populated fields**
  - *Bug it catches:* Factory returns the wrong concrete class for the AUTH key, returns null on unknown keys, or doesn't apply param values into the constructed object.

---

## TC416 — DP-6 Factory: all 5 EventTypes dispatch correctly

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.7 step (5), all five EventType values (AUTH, JOB, PROPOSAL, CONTRACT, PAYOUT_AUDIT) must dispatch to their matching subclass. Catches missing branches in the factory's switch/dispatch.

### Pre-conditions

None.

### Steps

```
1) For each EventType in {AUTH, JOB, PROPOSAL, CONTRACT, PAYOUT_AUDIT}: invoke createEvent(type, params).
2) Assert instanceof matches the expected subclass (AuthEvent, JobEvent, ProposalEvent, ContractEvent, PayoutAuditEvent).
```

### Pass Criteria

- **All 5 enum values dispatch to correct subtypes**
  - *Bug it catches:* Factory switch missing a branch — e.g., only handles AUTH and JOB, falls through to null/default for the other three.

---

## TC417 — DP-6 Factory: PAYOUT_AUDIT exposes method+amount

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.7 step (6) and Section 7.1.6, PayoutAuditEvent has service-specific fields method and amount on top of the common MongoEvent interface. The factory must populate them when creating PAYOUT_AUDIT events. The method value is one of the M1 Payout.method enum values (BANK_TRANSFER/PAYPAL/CRYPTO).

### Pre-conditions

None.

### Steps

```
1) params = {payoutId:1, action:"COMPLETED", method:"BANK_TRANSFER", amount:2000.0, timestamp:LocalDateTime.now()}.
2) Call createEvent(EventType.PAYOUT_AUDIT, params).
3) Cast to PayoutAuditEvent.
4) Assert getMethod().equals("BANK_TRANSFER") and getAmount() == 2000.0.
```

### Pass Criteria

- **PayoutAuditEvent has method=BANK_TRANSFER, amount=2000.0**
  - *Bug it catches:* Factory only populates the common interface fields — service-specific fields are null, breaking downstream analytics (S5-F11 method breakdown).

---

## TC418 — DP-6 Factory: register integration matches factory output

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** `POST /api/auth/register + Mongo inspection`

### What it tests

Per Section 3.7 step (7), the actual MongoDB document written when registering must match what EventFactory.createEvent(AUTH, ...) would produce — proves services route through the factory rather than constructing events with new.

### Pre-conditions

Mongo reachable.

### Steps

```
1) POST /api/auth/register with fresh email and phone.
2) Read the latest auth_events document.
3) Assert it has action="REGISTERED", a userId, and a timestamp matching what EventFactory.createEvent(AUTH, params) would produce.
4) Compare structure (field names, types) against a factory-built reference.
```

### Pass Criteria

- **Persisted doc has AuthEvent shape**
  - *Bug it catches:* Service writes a hand-rolled Document directly (bypasses factory), with subtly different field names that won't deserialize into AuthEvent later.

---

## TC419 — DP-6 Factory: no new XEvent(...) in services

**Tags:** `public` `patterns` `factory`  
**Endpoint(s):** _(source scan)_

### What it tests

Per Section 3.7 step (8), all event construction must go through the factory. Direct new AuthEvent(...) calls in service code defeat the factory's centralization — changes in factory logic (e.g., adding tracing, adding default fields) wouldn't reach those services.

### Pre-conditions

None.

### Steps

```
1) Source-scan all 5 services (excluding the factory's own implementation).
2) Search for new AuthEvent(, new JobEvent(, new ProposalEvent(, new ContractEvent(, new PayoutAuditEvent(.
3) Assert zero matches in service classes.
```

### Pass Criteria

- **No direct event constructors in service classes**
  - *Bug it catches:* Service bypassed the factory in one or more code paths — adding tracing/defaults to the factory has no effect on those paths.

---

## TC420 — DP-7 Adapter: per-service NoSQL adapter classes

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.8, each service must have one adapter per NoSQL source it uses: MongoDocumentAdapter in all 5 services; ElasticsearchHitAdapter in job-service; Neo4jRecordAdapter in proposal-service; CassandraRowAdapter in contract-service.

### Pre-conditions

None.

### Steps

```
1) For each service: reflection-load expected adapter classes per the spec mapping (user, job, proposal, contract, wallet → MongoDocumentAdapter; job → ElasticsearchHitAdapter; proposal → Neo4jRecordAdapter; contract → CassandraRowAdapter).
2) Assert each named class exists.
```

### Pass Criteria

- **All required adapter classes present**
  - *Bug it catches:* Service does inline raw-NoSQL → DTO conversion in the controller — no Adapter encapsulation, can't be tested in isolation.

---

## TC421 — DP-7 Adapter: each adapter has adapt() returning service DTO

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(reflection — no HTTP)_

### What it tests

Per Section 3.8, each adapter has an adapt(source) → targetDto method returning the service's specific domain DTO. There is NO universal "EntityDto" base type — each adapter targets its own DTO.

### Pre-conditions

TC420 passes.

### Steps

```
1) For each adapter: locate adapt(...) method.
2) Assert it has a single parameter typed to its NoSQL source (Document, SearchHit, Record, Row).
3) Assert return type is the service's domain DTO.
```

### Pass Criteria

- **adapt() signatures correct**
  - *Bug it catches:* Adapter returns Map<String,Object> generically — defeats type safety; or takes generic Object parameter — defeats compile-time enforcement.

---

## TC422 — DP-7 Adapter: MongoDocumentAdapter.adapt(Document) → DTO

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.8 step (3), passing a mock MongoDB Document to MongoDocumentAdapter.adapt(...) must yield a populated DTO. Validates the mapping logic in isolation, before running any integration test.

### Pre-conditions

None.

### Steps

```
1) Build a mock org.bson.Document with sample fields (e.g., userId=1, action="LOGGED_IN", timestamp=...).
2) Call MongoDocumentAdapter.adapt(doc).
3) Assert returned DTO has each field populated from the document's keys.
```

### Pass Criteria

- **DTO fields match Document keys**
  - *Bug it catches:* Field-name mismatch (camelCase DTO vs snake_case stored in Mongo); or null-handling absent so adapter NPEs on missing optional keys.

---

## TC423 — DP-7 Adapter: ElasticsearchHitAdapter (job-service)

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(unit test)_

### What it tests

Per Section 3.8 step (4), the job-service ElasticsearchHitAdapter.adapt(SearchHit) converts a Search hit to the job DTO with title/description/category/budgetMin/budgetMax/rating/status populated, since these are the fields S2-F10 returns from the jobs Elasticsearch index.

### Pre-conditions

None.

### Steps

```
1) Build a mock SearchHit with sourceAsMap = {title:"React Native app", description:"build a cross-platform mobile app", category:"WEB_DEV", budgetMin:1000.0, budgetMax:5000.0, rating:4.5, status:"OPEN"}.
2) Call ElasticsearchHitAdapter.adapt(hit).
3) Assert DTO has title="React Native app", category="WEB_DEV", rating=4.5, status="OPEN".
```

### Pass Criteria

- **Job DTO populated from SearchHit**
  - *Bug it catches:* Adapter calls hit.getId() but ignores hit.getSourceAsMap() — DTO has id but no business fields; clients see empty job cards in S2-F10 search results.

---

## TC424 — DP-7 Adapter: ObjectArrayDtoAdapter for S1-F3

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(reflection + integration)_

### What it tests

Per Section 3.8 step (5), S1-F3 explicitly mandates Object[] from native SQL (per the M1 spec footnote: "Since you will use native SQL here, you can construct the DTO manually from the Object[] that will be returned from the query"). The Adapter retrofit requires an ObjectArrayDtoAdapter (or similar named class) that converts the Object[] rows into UserContractSummaryDTO. Combined check: class exists AND S1-F3 returns the correct DTO.

### Pre-conditions

User with COMPLETED/TERMINATED/ACTIVE contracts.

### Steps

```
1) Reflection-scan user-service for an adapter class converting Object[] → UserContractSummaryDTO.
2) Assert it exists.
3) Invoke S1-F3: GET /api/users/{id}/contract-summary.
4) Assert response well-formed with expected fields (userId, name, totalContracts, completedContracts, terminatedContracts, totalEarnings, averageContractValue).
```

### Pass Criteria

- **Adapter class exists**
  - *Bug it catches:* S1-F3 still mapped inline in service — Object[] → DTO logic in the service method, no adapter encapsulation.
- **S1-F3 returns correct DTO**
  - *Bug it catches:* Adapter exists but wires fields incorrectly — wrong totals, wrong field positions.

---

## TC425 — DP-7 Adapter: M1 features using JPQL/DTO projection are exempt

**Tags:** `public` `patterns` `adapter`  
**Endpoint(s):** _(source scan)_

### What it tests

Per Section 3.8 step (6), only Object[]-using M1 features need an adapter. Features implemented via JPQL constructor expressions (SELECT new com.x.SomeDTO(...)) or @Query DTO projection are exempt. This TC documents the exemption: the grader does NOT require an adapter for those features.

### Pre-conditions

None.

### Steps

```
1) For each M1 F3/F6/F9 service method: source-scan its repository's @Query annotation.
2) If native SQL with Object[] → require adapter (covered by TC424 for S1-F3 specifically).
3) If JPQL constructor expression or DTO projection → exempt; assert no failure for missing adapter.
```

### Pass Criteria

- **Adapter required only for Object[] features**
  - *Bug it catches:* Grader incorrectly fails JPQL-based features (false positive against exempt features) — this TC explicitly documents the exemption.

---

