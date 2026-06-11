# Deployment Topology

The application runs as one codebase in three runtime roles, selected by Spring profile. In
production these should be **separate containers/instances**, each with a single role profile.

| Profile     | Role                          | HTTP server | RabbitMQ                          | Notes |
|-------------|-------------------------------|-------------|-----------------------------------|-------|
| `chat`      | Chat API (SSE) + admin        | yes         | not required (no `RabbitMQConfig`)| Hosts `/api/v1/chat/**` and `/api/v1/admin/**`. |
| `ingestion` | ThingsBoard webhook receiver  | yes         | publishes events                  | Hosts `/webhooks/thingsboard`. |
| `consumer`  | Background AMQP worker         | none        | consumes `iot.events` + DLQ       | `spring.main.web-application-type=none`. |

`dev` activates all three at once for local development only.

## Profile guards (audit #13)

- `RabbitMQConfig` is annotated `@Profile({"ingestion","consumer"})`, so a `chat`-only node does
  not declare queues or reach the broker at startup.
- `EventConsumerService` (the `@RabbitListener`) is `@Profile("consumer")`.
- `WebhookController` is `@Profile("ingestion")`; `ChatController` is `@Profile("chat")`.

## Required configuration per role (production)

All three need the database, Redis, and the security env vars
(`IOTCHATBOT_SECURITY_REQUIRE_JWT_VERIFICATION=true`, `IOTCHATBOT_JWT_SIGNING_KEY`,
`IOTCHATBOT_SECURITY_STRICT_CUSTOMER_MAPPING=true`, CORS allow-list).

- `ingestion`: `IOTCHATBOT_SECURITY_REQUIRE_WEBHOOK_HMAC=true` + `..._WEBHOOK_HMAC_SECRET`.
- `chat`/admin: `IOTCHATBOT_SECURITY_REQUIRE_ADMIN_TOKEN=true` + `..._ADMIN_TOKEN`.
- `consumer`: RabbitMQ credentials; scale horizontally with HikariCP pool sized so
  `instances × pool ≤ db max_connections` (see audit #21).

## Startup ordering

`consumer` should start after RabbitMQ is reachable; `chat` after Redis is warm. With external
managed brokers (CloudAMQP/Upstash) the connections are lazy and tolerant, but a readiness probe
that verifies Redis/RabbitMQ connectivity before serving traffic is recommended.
