# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

CodeBlog is a social blogging REST API for programmers (posts, comments, follows, notifications), built with Java 21 / Spring Boot 4.1.1 (Spring Framework 7, Jackson 3 — `tools.jackson.*`). Docs, comments and README are in Portuguese.

## Commands

Use `./mvnw` (or `mvnw.cmd` on Windows).

```bash
./mvnw spring-boot:run                      # run (default profile: local → application-local.yaml)
./mvnw -B verify                            # what CI runs (SPRING_PROFILES_ACTIVE=test)
./mvnw test                                 # all tests
./mvnw test -Dtest=UserServiceTest          # single class
./mvnw test -Dtest=UserServiceTest#follow*  # single method / pattern
docker compose up -d                        # local Postgres (host port 5433), Redis (6379), RabbitMQ (5672, UI 15672)
```

There is no separate linter configured.

### Test infrastructure
- Tests use the `test` profile (`src/test/resources/application-test.yaml`): H2 in-memory DB, dummy Auth0/Cloudinary/JWT values.
- Integration tests needing Redis/RabbitMQ extend `integration/support/RedisContainerSupport` or `RabbitMQContainerSupport` (which extends the Redis one). Redis resolution order: external instance via `-Dredis.test.host=… -Dredis.test.port=…` (or `REDIS_TEST_HOST`/`REDIS_TEST_PORT`) → Testcontainers if Docker is available → otherwise the class is **skipped** (via JUnit `Assumptions`), not failed. RabbitMQ follows the same order with `-Drabbitmq.test.host=… -Drabbitmq.test.port=…` (or `RABBITMQ_TEST_HOST`/`RABBITMQ_TEST_PORT`). CI (`.github/workflows/ci.yml`) runs Redis and RabbitMQ as service containers and points the tests at them via these env vars.
- Redis-backed tests only delete keys they create (`integration:test:*` and cache prefixes) — never add a `FLUSHDB`.

## Architecture

Package root: `src/main/java/blog/code/codeblog`. Note the historical typos that are real names: `AutheticationController`, `execptions/`, `Auth0ServiceIntergration`.

### Request flow: Controller → Facade → (Mapper → Command) → Service → Repository
- **Controllers** obtain the authenticated `User` via `service/provider/UserProvider.getCurrentUser()` (reads the `SecurityContext` principal) and delegate to a **Facade**.
- **Facades** (`facade/`) convert request DTOs into immutable **Command** objects (`command/`, Lombok `@Builder @Getter`) using static methods on the **Mappers** (`mapper/`, e.g. `UserMapper.toFollowCommand(...)`), then call the service.
- **Services** take Commands and return response DTOs (`dto/`). Several services implement interfaces in `service/interfaces/`. `FeedService` extends `PostService`.
- Errors are mapped to responses in `config/handlers/GlobalExceptionHandler` (plus custom entry point / access denied handlers).

### Security
`config/security/SecurityConfiguration` + `SecurityFilter` (runs before `UsernamePasswordAuthenticationFilter`) validate the app's own JWTs via `TokenService` (secret from `api.security.token.secret`). There's also an Auth0 OTP/passwordless login flow (`service/integration/Auth0ServiceIntergration`, `enums/AuthFlow`). Public routes: `/auth/login`, `/auth/register`, `/auth/otp/*`, Swagger; ASYNC dispatches are permitted (needed for SSE).

### Caching (Redis)
Cache names and per-cache TTLs are defined as constants in `config/RedisConfig` (`FEED_CACHE`, `USER_POSTS_CACHE`, `POST_COMMENTS_CACHE`, `FOLLOWERS_CACHE`, …). Services use `@Cacheable` / `@CacheEvict` with these constants; when adding a mutation, evict the affected caches. The feed cache key includes `currentTimeMillis() / feed.seed-interval-ms`, which is what makes the "balanced feed" shuffle deterministic within a time window (`feed.*` settings in `application.yaml`).

### Async messaging (RabbitMQ)
All queues/exchanges are declared in `config/RabbitMQConfig`, with names in `app.rabbitmq.*` of `application.yaml`. One topic exchange (`user.events.exchange`); each queue has a DLQ via a dead-letter exchange. Listeners retry 3× then reject without requeue (→ DLQ). Messages are JSON (`Jackson2JsonMessageConverter`).

- **Image upload**: `UserService`/`PostService` publish `ImageUploadEvent` (bytes + `FlowImageFlag` PROFILE/POST) via `CloudinaryUploadPublisher` → `CloudinaryUploadConsumer` uploads to Cloudinary → publishes a Spring in-process `ImageUploadCompletedEvent` → `ImageUploadCompletedListener` routes by flag to `UserService.onProfileImageUploaded` / `PostService.onPostImageUploaded` (which persist the URL and evict caches).
- **Follow notifications**: `UserService.follow` publishes `UserFollowedEvent` → `UserFollowConsumer` → `NotificationService` persists a `Notification` and pushes it over SSE through `SseNotificationHub` (per-user list of `SseEmitter`s, one per tab/device). Clients subscribe via `NotificationController`.

Adding a new async flow means: properties in `app.rabbitmq.*`, queue + DLQ + bindings in `RabbitMQConfig`, a publisher in `messaging/publisher`, an event in `messaging/event`, and a `@RabbitListener` in `messaging/consumer`.

## Configuration
- `application.yaml`: shared settings + `prod` profile (env vars: `DATABASE_URL`, `JWT_SECRET`, `REDIS_*`, `RABBITMQ_*`, `AUTH0_*`, `CLOUDINARY_*`).
- `application-local.yaml`: local dev against `docker compose` services (Postgres on **5433**).
- `docs/` has design notes (RabbitMQ, async image upload, SSE).
