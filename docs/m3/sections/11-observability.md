# Section 11 — Observability

> Split from `../m3.txt`. Original file is untouched.

## 11.1 Loki4J Appender (All 5 Services)
Add to each service's pom.xml:

```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>2.0.0</version>
</dependency>
```
Per-Service MDC Fields
Each service populates only the MDC keys relevant to its domain. correlationId is shared by all five services (set from the X-Correlation-ID header forwarded by api-gateway, or from the RabbitMQ message header in consumers). The remaining entity-specific keys differ:

| Service | Entity-specific MDC keys |
| --- | --- |
| user-service | userId |
| job-service | jobId, proposalId, routingKey |
| proposal-service | proposalId, userId, jobId, contractId, payoutId, routingKey |
| contract-service | contractId, proposalId, jobId, userId, routingKey |
| wallet-service | payoutId, contractId, proposalId, userId, routingKey |
logback-spring.xml
The example below is the proposal-service template (the busiest service — its JSON includes every entity field). Each other service uses the same XML structure but drops the MDC fields it does not populate from the <message><pattern> block.

```xml
<configuration>
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://loki:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <pattern>app=freelance,service=${spring.application.name},level=%level,env=k8s</pattern>
            </label>
            <message>
                <pattern>
                    {
                      "timestamp": "%d{ISO8601}",
                      "level": "%level",
                      "service": "${spring.application.name}",
                      "thread": "%thread",
                      "logger": "%logger{36}",
                      "correlationId": "%X{correlationId:-}",
                      "userId": "%X{userId:-}",
                      "jobId": "%X{jobId:-}",
                      "proposalId": "%X{proposalId:-}",
                      "contractId": "%X{contractId:-}",
                      "payoutId": "%X{payoutId:-}",
                      "routingKey": "%X{routingKey:-}",
                      "message": "%msg"
                    }
                </pattern>
            </message>
        </format>
    </appender>
    <root level="INFO">
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```
MDC Population
correlationId — populated by a servlet filter (OncePerRequestFilter) that reads the X-Correlation-ID header set by api-gateway and calls MDC.put("correlationId", value). The filter must clear MDC in finally. RabbitMQ consumers must also read the correlationId header from the inbound Message and call MDC.put at the start of the listener method.
Entity IDs (userId, jobId, proposalId, contractId, payoutId) — populated manually by service-layer methods using MDC.put("proposalId", id.toString()) immediately before performing the operation, paired with MDC.remove(...) in a finally block to prevent leaking IDs into unrelated subsequent requests.
routingKey — set by RabbitMQ publishers and consumers to the routing key being processed (e.g., proposal.completed, payment.failed). This makes the Layer 3 RabbitMQ event audit panel (§11.3) usable.
Required Log Points
Each service must emit logs at the following points so the LogQL panels in §11.3 have data to query. Use SLF4J: private static final Logger log = LoggerFactory.getLogger(<Class>.class);.

| Log point | Level | Suggested message format |
| --- | --- | --- |
| Controller method entry | INFO | "Received {} {}" (HTTP method, path) |
| Controller method exit | INFO | "Returning {} for {} {}" (status, method, path) |
| Feign call — before request | INFO | "Calling {}.{} with args={}" (client, method, args) |
| Feign call — after success | INFO | "{}.{} returned successfully" (client, method) |
| Feign call — exception caught | WARN | "Feign call to {} failed: {}" (service, exception message) |
| RabbitMQ — event published | INFO | "Published {} for {}={}" (routingKey, entityName, id) |
| RabbitMQ — event consumed (start) | INFO | "Consuming {} for {}={}" (routingKey, entityName, id) |
| RabbitMQ — event processed (success) | INFO | "Processed {} for {}={}" (routingKey, entityName, id) |
| RabbitMQ — consumer error | ERROR | "Failed to process {}: {}" (routingKey, exception message) → DLQ |
| Saga state transition (S3 only) | INFO | "Proposal {} transitioning {} → {}" (proposalId, oldStatus, newStatus) |
| DB write success | INFO | "{} {} saved with status={}" (entityName, id, status) |
| Slow operation (> threshold) | WARN | "Slow {} took {}ms" (operationName, elapsedMs) — wrap operations expected to be slow under load (e.g., S5-F10 platform-fee analytics, S4-F3 contract enrichment, S1-F6 top-freelancers report) with a stopwatch and emit when elapsed exceeds a threshold (e.g., 1000ms). Feeds the Layer 6 LogQL panel. |
Required in application.yml:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: "prometheus,health,info"
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```
## 11.2 Dashboard per Service
Each of the 5 services has its own Grafana dashboard. Each dashboard has at minimum 3 LogQL panels and 3 PromQL panels chosen from the lists below. Five dashboard JSON files must be submitted (one per service).

## 11.3 LogQL Panel Options (choose ≥ 3 per service)
A LogQL query is built up in three layers, and every panel below uses all three:

Label — {app="freelance", service="proposal-service", level="ERROR"} — match log streams by the labels emitted by the Loki4J appender (§11.1). This narrows down which streams the rest of the query reads from.
Line — |= "search-text", != "exclude", | json, | line_format "{{...}}" — filter and parse individual log lines within the matched streams. Because messages are JSON (§11.1), | json exposes every field (correlationId, proposalId, routingKey, …) for further filtering.
Aggregator — count_over_time(...[1m]), rate(...[5m]), sum by (service) (...) — turn the matching lines into time-series numbers that Grafana can plot.
Available Panels
Error rate panel — Count of ERROR-level log lines per service per minute.
**Example purpose:** Spike detection — if proposal-service logs 50 ERRORs in one minute, something is wrong.

Correlation ID trace panel — Filter all log lines by a specific X-Correlation-ID value across all services.
**Example purpose:** Trace a single proposal-acceptance request from api-gateway through proposal-service, contract-service, and job-service.

RabbitMQ event audit panel — Lines emitted by event publishers and consumers, filtered by routing key.
**Example purpose:** Show how many proposal.completed events were published vs. how many payment.initiated events were consumed in the last hour.

Feign call outcomes panel — Log lines for successful Feign responses vs. FeignException catches.
**Example purpose:** Detect when contract-service is degraded — proposal-service Feign calls to it start throwing exceptions.

Saga state transitions panel — Log lines at each saga step filtered by proposalId.
**Example purpose:** Visualize the complete saga flow for proposal ID=42: COMPLETING → PAYMENT_PENDING → PAID.

Slow operation warnings panel — Log lines where elapsed time exceeded a threshold.
**Example purpose:** Alert when S5-F10 platform-fee analytics aggregation takes > 5 seconds.

## 11.4 PromQL Panel Options (choose ≥ 3 per service)
A PromQL query is built up in four layers, and every panel below uses all four:

Metric — the metric name itself, e.g., http_server_requests_seconds_count or jvm_memory_used_bytes. These are exposed by each service's /actuator/prometheus endpoint and scraped by Prometheus every 15s.
Label — narrow the metric down with label matchers, e.g., {service="proposal-service", uri="/api/proposals", method="GET"}. Labels come from Spring Boot's Actuator metrics and from the job_name set in prometheus.yml.
Range — append a time window in square brackets, e.g., [5m] or [1h]. This turns the instant counter into a sequence of samples over that window so the function in layer 4 has data to operate on.
Function — rate(...), increase(...), histogram_quantile(0.99, ...), sum by (uri) (...), topk(5, ...) — converts the range vector into the final per-second rate, percentile, top-N, or grouped aggregate that Grafana plots.
Available Panels
HTTP request rate panel — Requests per second per endpoint.
**Example purpose:** Which proposal-service endpoints are under the most load during peak hours?

HTTP latency percentiles panel — P50/P95/P99 latency per endpoint.
**Example purpose:** P99 latency on GET /api/contracts/freelancer/{id}/summary is 4s — Feign enrichment is slow.

JVM health panel — Heap usage, GC pause duration, thread count.
**Example purpose:** Memory pressure before OOM — wallet-service heap at 90% after processing 10,000 events.

Database connection pool panel — HikariCP active connections vs. pool size.
**Example purpose:** Pool exhaustion alert — wallet-service using 10/10 connections during saga fan-out.

Cache hit/miss ratio panel — Redis cache hits vs. misses from cache_gets_total.
**Example purpose:** S2-F12 dashboard cache hit rate — verify caching is effective.

RabbitMQ throughput panel — Messages published vs. consumed per queue.
**Example purpose:** Consumer lag on payment.saga-listener — published count > consumed count by > 100.

## 11.5 Observability Stack (K8s — monitoring namespace)
The three observability tools — Loki, Prometheus, and Grafana — run as their own pods inside the cluster, in a dedicated namespace called monitoring. Keeping them separate from the freelance namespace means observability resources (CPU, memory, restarts) are isolated from the application services, and an issue in the app does not take down the dashboards.

The data flow uses two opposite directions:

Logs (push): Each Spring Boot service runs the Loki4J appender (§11.1), which pushes log lines as JSON over HTTP to http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push. Loki itself never reaches into the services — they send to it.
Metrics (pull): Prometheus scrapes each service's /actuator/prometheus endpoint on a 15-second interval (configured below). "Scrape" here just means an HTTP GET — Prometheus pulls the current metric values from each service and stores them as time-series.
Dashboards: Grafana is configured with two datasources — Loki (for LogQL panels) and Prometheus (for PromQL panels). The 5 dashboard JSON files (one per service) are committed to the repo and provisioned into Grafana via a ConfigMap mount.
| Component | Image | Role in the stack |
| --- | --- | --- |
| Loki | grafana/loki:2.9.4 | Receives JSON log streams pushed by Loki4J from each service. |
| Prometheus | prom/prometheus:v2.51.2 | Pulls metrics from each service's /actuator/prometheus every 15s. |
| Grafana | grafana/grafana:10.4.2 | Dashboard UI; runs the LogQL/PromQL queries from §11.3 and §11.4. |
Cross-namespace DNS resolution makes this work: from monitoring, Prometheus reaches a service in freelance using the fully qualified name <service-name>.freelance.svc.cluster.local. From freelance, services push logs to loki.monitoring.svc.cluster.local.

## Two Namespaces Required
The cluster must contain two namespaces, each defined as its own YAML file under k8s/namespaces/:

| Namespace | Purpose | YAML file |
| --- | --- | --- |
| freelance | All 5 application services + their PostgreSQL + RabbitMQ + NoSQL stores. Already declared in §10.2. | k8s/namespaces/namespace.yaml |
| monitoring | Loki + Prometheus + Grafana only. Nothing application-related deploys here. | k8s/namespaces/monitoring-namespace.yaml |
```yaml
# k8s/namespaces/monitoring-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: monitoring
```
## Required Files & Directory Structure
All observability manifests live under k8s/monitoring/, separate from the application K8s tree shown in §10.1:

k8s/
├── namespaces/
│   ├── namespace.yaml                  # freelance (already in §10.1)
│   └── monitoring-namespace.yaml       # monitoring (new — see above)
└── monitoring/
├── loki/
│   ├── loki-configmap.yaml         # /etc/loki/local-config.yaml content
│   ├── loki-pvc.yaml               # storage for log chunks (≥ 5Gi)
│   ├── loki-statefulset.yaml       # image: grafana/loki:2.9.4, port 3100
│   └── loki-service.yaml           # ClusterIP, port 3100 → name "loki"
├── prometheus/
│   ├── prometheus-configmap.yaml   # contains prometheus.yml (scrape config below)
│   ├── prometheus-pvc.yaml         # storage for TSDB (≥ 5Gi)
│   ├── prometheus-deployment.yaml  # image: prom/prometheus:v2.51.2, port 9090
│   └── prometheus-service.yaml     # ClusterIP, port 9090 → name "prometheus"
└── grafana/
├── grafana-datasources.yaml    # ConfigMap — Loki + Prometheus datasource provisioning
├── grafana-dashboards.yaml     # ConfigMap — embeds 5 dashboard JSON files
├── grafana-pvc.yaml            # storage for Grafana state (≥ 1Gi)
├── grafana-deployment.yaml     # image: grafana/grafana:10.4.2, port 3000
└── grafana-service.yaml        # NodePort 30030 — browser access to dashboards
The 5 dashboard JSON files (user-dashboard.json, job-dashboard.json, proposal-dashboard.json, contract-dashboard.json, wallet-dashboard.json) are committed to k8s/monitoring/grafana/dashboards/ and embedded into the grafana-dashboards.yaml ConfigMap so Grafana auto-loads them on startup.

## Required Manifests Per Component
Loki (StatefulSet): Mount loki-configmap at /etc/loki/, attach the PVC at /loki for chunk storage. Service named loki so the Loki4J appender URL http://loki.monitoring.svc.cluster.local:3100/loki/api/v1/push resolves.

Prometheus (Deployment): Mount prometheus-configmap at /etc/prometheus/prometheus.yml. The ConfigMap holds the scrape config below. Attach the PVC at /prometheus for the TSDB.

Grafana (Deployment): Mount grafana-datasources at /etc/grafana/provisioning/datasources/ and grafana-dashboards at /etc/grafana/provisioning/dashboards/. Service is type: NodePort on port 30030 so the dashboards are reachable from the host at http://$(minikube ip):30030 (default credentials admin/admin, change on first login).

## Example Manifest — Prometheus Deployment
The full file at k8s/monitoring/prometheus/prometheus-deployment.yaml. Loki and Grafana follow the same pattern (different image, different mount paths, different ports — see "Required Manifests Per Component" above).

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
        - name: prometheus
          image: prom/prometheus:v2.51.2
          args:
            - --config.file=/etc/prometheus/prometheus.yml
            - --storage.tsdb.path=/prometheus
          ports:
            - containerPort: 9090
          volumeMounts:
            - name: config
              mountPath: /etc/prometheus
            - name: storage
              mountPath: /prometheus
          readinessProbe:
            httpGet:
              path: /-/ready
              port: 9090
            initialDelaySeconds: 30
            periodSeconds: 10
      volumes:
        - name: config
          configMap:
            name: prometheus-config       # ConfigMap.metadata.name = "prometheus-config" inside k8s/monitoring/prometheus/prometheus-configmap.yaml
        - name: storage
          persistentVolumeClaim:
            claimName: prometheus-pvc
```
The companion prometheus-service.yaml is a ClusterIP Service named prometheus exposing port 9090 — Grafana's Prometheus datasource uses http://prometheus.monitoring.svc.cluster.local:9090 to reach it.

## Prometheus Scrape Config
This is the file that goes inside prometheus-configmap.yaml under the key prometheus.yml:

```yaml
scrape_configs:
  - job_name: user-service
    static_configs:
      - targets: ['user-service.freelance.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: job-service
    static_configs:
      - targets: ['job-service.freelance.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: proposal-service
    static_configs:
      - targets: ['proposal-service.freelance.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: contract-service
    static_configs:
      - targets: ['contract-service.freelance.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
  - job_name: wallet-service
    static_configs:
      - targets: ['wallet-service.freelance.svc.cluster.local:8080']
    metrics_path: /actuator/prometheus
```
## Apply Order
```bash
kubectl apply -f k8s/namespaces/monitoring-namespace.yaml
kubectl apply -f k8s/monitoring/loki/
kubectl apply -f k8s/monitoring/prometheus/
kubectl apply -f k8s/monitoring/grafana/
kubectl wait --for=condition=ready pod -l app=loki -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=prometheus -n monitoring --timeout=120s
kubectl wait --for=condition=ready pod -l app=grafana -n monitoring --timeout=120s
```
Open Grafana at http://$(minikube ip):30030 — both datasources should be green and all 5 dashboards visible under the Freelance folder.

## Observability Deliverables
- [ ] logback-spring.xml in all 5 services with Loki4J appender (§11.1)
- [ ] management.endpoints.web.exposure.include: prometheus,health,info in all 5 services
- [ ] 5 Grafana dashboard JSON files (user-dashboard.json, job-dashboard.json, proposal-dashboard.json, contract-dashboard.json, wallet-dashboard.json) — ≥3 LogQL + ≥3 PromQL panels each, committed under k8s/monitoring/grafana/dashboards/
- [ ] k8s/namespaces/monitoring-namespace.yaml declaring the monitoring namespace
- [ ] k8s/monitoring/loki/ — ConfigMap + PVC + StatefulSet + Service
- [ ] k8s/monitoring/prometheus/ — ConfigMap (with the 5-job scrape config) + PVC + Deployment + Service
- [ ] k8s/monitoring/grafana/ — datasources ConfigMap + dashboards ConfigMap + PVC + Deployment + NodePort Service (30030)
- [ ] Verified end-to-end: trigger an HTTP request via the gateway → log line appears in Loki within ~5s; metric counter increments in Prometheus within ~15s; both render in the corresponding service dashboard
