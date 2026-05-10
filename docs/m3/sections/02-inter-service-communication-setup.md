# Section 2 — Inter-Service Communication Setup

> Split from `../m3.txt`. Original file is untouched.

## 2.1 OpenFeign Dependency
Add to every service that makes Feign calls:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```
Add Spring Cloud BOM to dependencyManagement:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
Enable on @SpringBootApplication:

```java
@SpringBootApplication
@EnableFeignClients
public class UserServiceApplication { }
```
## 2.2 Feign Client Pattern (Example)
```java
@FeignClient(name = "contract-service", url = "${feign.contract-service.url}")
public interface ContractServiceClient {

    @GetMapping("/api/contracts/user/{userId}/summary")
    UserContractSummaryDTO getUserContractSummary(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/active-count")
    int getActiveContractCount(@PathVariable Long userId);

    @GetMapping("/api/contracts/user/{userId}/count")
    long getTotalContractCount(@PathVariable Long userId);
}
```
In application.yml, add each service to the one that requires it:

```yaml
feign:
  user-service:
    url: http://user-service:8080
  job-service:
    url: http://job-service:8080
  proposal-service:
    url: http://proposal-service:8080
  contract-service:
    url: http://contract-service:8080
  wallet-service:
    url: http://wallet-service:8080
```
**Important:** the url attribute is mandatory in our K8s setup. When @FeignClient is given an explicit url, Spring Cloud Feign skips its load-balancer logic and calls that URL directly — Kubernetes' built-in DNS + Service load balancing then balances across pods. If you omit url, Feign falls back to Spring Cloud LoadBalancer, which expects a service registry (Eureka/Consul) — we do not deploy one, so the Feign call will fail at startup with LoadBalancer for service-name not available. Always declare both name and url.

## 2.3 Correlation ID Propagation
Every service must forward X-Correlation-ID on all outgoing Feign calls:

```java
@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
        };
    }
}
```
## 2.4 Error Handling
Wrap every Feign call in try-catch. Never let a downstream failure crash the calling service, for example:

```java
try {
    UserContractSummaryDTO summary = contractServiceClient.getUserContractSummary(userId);
    return buildDTO(user, summary);
} catch (FeignException.NotFound e) {
    return buildDTO(user, UserContractSummaryDTO.empty());
} catch (FeignException e) {
    log.warn("contract-service unavailable for user {}: {}", userId, e.getMessage());
    throw new ServiceUnavailableException("Contract service temporarily unavailable");
}
```
## 2.5 RabbitMQ Dependency
Add to every service that publishes or consumes events:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
## 2.6 RabbitMQ Connection Configuration
Add to every service's application.yml:

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```
## 2.7 Topology — What Each Service Must Declare
Each service declares its own RabbitMQ topology as Spring @Beans in a @Configuration class. The responsibilities are split:

Producer service declares only the TopicExchange it publishes to.
Consumer service declares the Queue, the DLQ, another TopicExchange reference (same name — Spring deduplicates), and the Binding that connects them.
Every consumer queue must have a dead-letter queue. The DLQ is wired by declaring the consumer queue with the x-dead-letter-exchange and x-dead-letter-routing-key arguments pointing at a separate dead-letter exchange + DLQ. Combined with default-requeue-rejected: false (§2.6), this means: when a listener method throws and Spring's retry exhausts (max-attempts: 3), the message is automatically routed to the DLQ — no manual basicAck/basicNack code in the consumer.

The exchange type for all Freelance Marketplace events is TopicExchange. This allows routing key wildcards so future events can be added without declaring a new exchange.

## 2.8 Event Payload Records
Event payloads are plain Java record classes, serialized to JSON by Jackson. They live in an events package inside each service:

```java
// proposal-service
public record ProposalCompletedEvent(Long proposalId, Long jobId, Long freelancerId, Long contractId, BigDecimal agreedAmount) {}
public record ProposalCancelledEvent(Long proposalId, Long jobId, Long freelancerId, String reason) {}
public record ProposalAcceptedEvent(Long proposalId, Long jobId, Long freelancerId, BigDecimal bidAmount) {}
public record ProposalWithdrawnEvent(Long proposalId, Long jobId, Long freelancerId) {}

// contract-service
public record ContractCreatedEvent(Long contractId, Long proposalId, Long jobId, Long freelancerId, BigDecimal agreedAmount) {}
public record ContractStatusChangedEvent(Long contractId, String oldStatus, String newStatus) {}
public record ContractCancelledEvent(Long contractId, Long proposalId) {}

// wallet-service
public record PaymentInitiatedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal amount) {}
public record PaymentCompletedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal amount) {}
public record PaymentFailedEvent(Long payoutId, Long proposalId, Long contractId, String reason) {}
public record PaymentRefundedEvent(Long payoutId, Long proposalId, Long contractId, BigDecimal refundAmount) {}

// user-service
public record UserRegisteredEvent(Long userId, String email, String role) {}
public record UserDeactivatedEvent(Long userId) {}

// job-service
public record JobStatusChangedEvent(Long jobId, String oldStatus, String newStatus) {}
public record JobRatedEvent(Long jobId, Long contractId, Double rating, Long ratedBy) {}
public record JobClosedEvent(Long jobId, Long clientId) {}
```
## 2.9 Full Event Map (Freelance Marketplace)
| Producer | Exchange | Routing key | Payload record | Consumers |
| --- | --- | --- | --- | --- |
| user-service | user.events | user.registered | UserRegisteredEvent | proposal-service |
| user-service | user.events | user.deactivated | UserDeactivatedEvent | proposal-service, contract-service |
| job-service | job.events | job.status-changed | JobStatusChangedEvent | proposal-service, contract-service |
| job-service | job.events | job.rated | JobRatedEvent | proposal-service |
| job-service | job.events | job.closed | JobClosedEvent | proposal-service |
| proposal-service | proposal.events | proposal.accepted | ProposalAcceptedEvent | job-service, contract-service |
| proposal-service | proposal.events | proposal.completed | ProposalCompletedEvent | user-service, job-service, contract-service, wallet-service |
| proposal-service | proposal.events | proposal.cancelled | ProposalCancelledEvent | user-service, job-service, contract-service, wallet-service |
| proposal-service | proposal.events | proposal.withdrawn | ProposalWithdrawnEvent | job-service |
| contract-service | contract.events | contract.created | ContractCreatedEvent | proposal-service |
| contract-service | contract.events | contract.status-changed | ContractStatusChangedEvent | proposal-service |
| contract-service | contract.events | contract.cancelled | ContractCancelledEvent | proposal-service |
| wallet-service | payment.events | payment.initiated | PaymentInitiatedEvent | proposal-service |
| wallet-service | payment.events | payment.completed | PaymentCompletedEvent | proposal-service |
| wallet-service | payment.events | payment.failed | PaymentFailedEvent | proposal-service |
| wallet-service | payment.events | payment.refunded | PaymentRefundedEvent | proposal-service |
