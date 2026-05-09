# Section 16 — Critical Rules

> Split from `../m3.txt`. Original file is untouched.

No cross-service JDBC. After M3, no service opens a JDBC connection to another service's database. Zero tolerance.
Feign for reads. RabbitMQ for side-effects. Use Feign when you need data to continue processing. Use RabbitMQ when triggering a state change in another service.
Auto ACK with DLQ routing. Use Spring's default acknowledge-mode: auto with default-requeue-rejected: false. Spring ACKs the message when the listener method returns normally and rejects when it throws — after retries are exhausted, rejected messages flow to the DLQ via the queue's x-dead-letter-exchange argument (no manual basicAck/basicNack calls).
DLQ for every queue. Every consumer queue has a dead-letter queue. Failed messages are never silently dropped.
StatefulSet for all databases. Never use plain Deployment for a stateful database.
Explicit constructor injection. Consistent with M1/M2 — no Lombok.
JWT validation at gateway. Individual services retain their M2 JWT filter for defense-in-depth, but the gateway is the public-facing validator.
PostgreSQL 17. Not PostgreSQL 18 — PG18 breaks Hibernate native query implicit cast operator resolution.
No new tests added during grading. Like M1/M2 the grader-provided test suite is the source of truth.
15 deliverables, contract-first parallel work. No slice waits for another slice's implementation; the contracts/ module is the only Day-0 dependency.
