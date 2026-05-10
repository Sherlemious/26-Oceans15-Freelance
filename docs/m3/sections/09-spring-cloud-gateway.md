# Section 9 — Spring Cloud Gateway

> Split from `../m3.txt`. Original file is untouched.

## 9.1 New Maven Module
Add api-gateway as the 6th module in the root pom.xml:

```xml
<modules>
    <module>contracts</module>
    <module>user-service</module>
    <module>job-service</module>
    <module>proposal-service</module>
    <module>contract-service</module>
    <module>wallet-service</module>
    <module>api-gateway</module>
</modules>
```
The gateway runs on port 8080 internally, exposed as NodePort 30080 externally.

## Dependencies

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```
Spring Cloud Gateway is reactive (Project Reactor). Do NOT add spring-boot-starter-web — it conflicts with webflux. Note: the artifact was renamed from spring-cloud-starter-gateway to spring-cloud-starter-gateway-server-webflux starting with the 2025.1.x release train (the older name was retired when the servlet variant was dropped).

## 9.2 Routing Configuration
```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8080
          predicates:
            - Path=/api/users/**, /api/auth/**, /api/user-skills/**
        - id: job-service
          uri: http://job-service:8080
          predicates:
            - Path=/api/jobs/**
        - id: proposal-service
          uri: http://proposal-service:8080
          predicates:
            - Path=/api/proposals/**
        - id: contract-service
          uri: http://contract-service:8080
          predicates:
            - Path=/api/contracts/**
        - id: wallet-service
          uri: http://wallet-service:8080
          predicates:
            - Path=/api/payouts/**, /api/promo-codes/**
```
## 9.3 JWT Global Filter
Adapt the JWT filter and authentication service from M2 to run inside api-gateway. The M2 filter was a servlet OncePerRequestFilter using HttpServletRequest / HttpServletResponse — Spring Cloud Gateway is reactive (WebFlux), so the filter must be rewritten as a GlobalFilter that returns Mono<Void>. Concretely:

Replace OncePerRequestFilter with implements GlobalFilter, Ordered.
Replace HttpServletRequest / HttpServletResponse with ServerWebExchange (exchange.getRequest(), exchange.getResponse()).
Replace filterChain.doFilter(req, res) with return chain.filter(exchange); and let the rest of the pipeline complete asynchronously.
Forward parsed claims as headers via exchange.mutate().request(r -> r.header("X-User-Id", uid)).
Reject with 401 by setting exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED) then return exchange.getResponse().setComplete(); — do NOT throw exceptions for auth failures (reactive convention).
The JwtConfigurationManager Singleton from M2 stays as-is (not Spring-managed, plain Java); the gateway constructs a GlobalFilter that calls into it.

## 9.4 Gateway Deliverables
- [ ] api-gateway Maven module created and added to root pom.xml
- [ ] spring-cloud-starter-gateway-server-webflux + spring-boot-starter-webflux dependencies
- [ ] All 5 service route entries in application.yml
- [ ] JwtGatewayFilter implemented and registered as @Component
- [ ] /api/auth/** bypass (no JWT check on register/login)
- [ ] X-User-Id, X-User-Role, X-Correlation-ID headers forwarded to downstream services
- [ ] docker-compose.yml updated: per-service postgres containers + RabbitMQ container + api-gateway service
