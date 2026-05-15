# Section 10 — Kubernetes Deployment

> Split from `../m3.txt`. Original file is untouched.

## 10.1 Directory Structure
```text
KUBERNETES TREE · k8s/
Expand all
Collapse all
k8s/
namespaces/
namespace.yaml
namespace: freelance
secrets/
jwt-secret.yaml
user-postgres-secret.yaml
job-postgres-secret.yaml
proposal-postgres-secret.yaml
contract-postgres-secret.yaml
wallet-postgres-secret.yaml
configmaps/
user-service-configmap.yaml
job-service-configmap.yaml
proposal-service-configmap.yaml
contract-service-configmap.yaml
wallet-service-configmap.yaml
gateway-configmap.yaml
pvcs/
user-postgres-pvc.yaml
job-postgres-pvc.yaml
proposal-postgres-pvc.yaml
contract-postgres-pvc.yaml
wallet-postgres-pvc.yaml
rabbitmq-pvc.yaml
mongo-pvc.yaml
redis-pvc.yaml
elasticsearch-pvc.yaml
neo4j-pvc.yaml
cassandra-pvc.yaml
statefulsets/
user-postgres-statefulset.yaml
job-postgres-statefulset.yaml
proposal-postgres-statefulset.yaml
contract-postgres-statefulset.yaml
wallet-postgres-statefulset.yaml
rabbitmq-statefulset.yaml
mongo-statefulset.yaml
redis-statefulset.yaml
elasticsearch-statefulset.yaml
neo4j-statefulset.yaml
cassandra-statefulset.yaml
deployments/
user-service-deployment.yaml
job-service-deployment.yaml
proposal-service-deployment.yaml
contract-service-deployment.yaml
wallet-service-deployment.yaml
services/
user-service-svc.yaml
ClusterIP
user-postgres-svc.yaml
headless
job-service-svc.yaml
ClusterIP
job-postgres-svc.yaml
headless
proposal-service-svc.yaml
ClusterIP
proposal-postgres-svc.yaml
headless
contract-service-svc.yaml
ClusterIP
contract-postgres-svc.yaml
headless
wallet-service-svc.yaml
ClusterIP
wallet-postgres-svc.yaml
headless
rabbitmq-svc.yaml
mongo-svc.yaml
redis-svc.yaml
elasticsearch-svc.yaml
neo4j-svc.yaml
cassandra-svc.yaml
api-gateway/
gateway-deployment.yaml
gateway-service.yaml
type: NodePort (30080)
```
## 10.2 Namespace
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: freelance
```
All kubectl commands use -n freelance.

## 10.3 ConfigMap Example — Proposal Service
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: proposal-service-configmap
  namespace: freelance
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://proposal-postgres:5432/freelancedb-proposals
  SPRING_DATASOURCE_USERNAME: user
  SPRING_RABBITMQ_HOST: rabbitmq
  FEIGN_USER_SERVICE_URL: http://user-service:8080
  FEIGN_JOB_SERVICE_URL: http://job-service:8080
  FEIGN_CONTRACT_SERVICE_URL: http://contract-service:8080
  FEIGN_WALLET_SERVICE_URL: http://wallet-service:8080
```
## 10.4 StatefulSet — Per-Service PostgreSQL (Example: proposal-postgres)
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: proposal-postgres
  namespace: freelance
spec:
  serviceName: proposal-postgres
  replicas: 1
  selector:
    matchLabels:
      app: proposal-postgres
  template:
    metadata:
      labels:
        app: proposal-postgres
    spec:
      containers:
        - name: postgres
          image: postgres:17
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_PASSWORD
            - name: POSTGRES_DB
              valueFrom:
                secretKeyRef:
                  name: proposal-postgres-secret
                  key: POSTGRES_DB
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 1Gi
```
## 10.5 Deployment — Spring Boot Service (Example: proposal-service)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: proposal-service
  namespace: freelance
spec:
  replicas: 1
  selector:
    matchLabels:
      app: proposal-service
  template:
    metadata:
      labels:
        app: proposal-service
    spec:
      containers:
        - name: proposal-service
          image: <your-registry>/proposal-service:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: proposal-service-configmap
            - secretRef:
                name: proposal-postgres-secret
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: jwt-secret
                  key: jwt-secret
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 30
```
## 10.6 API Gateway NodePort Service
```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: freelance
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
      nodePort: 30080
```
Access the platform via: curl http://$(minikube ip):30080/api/proposals

All other services use type: ClusterIP. No service other than the gateway is reachable from outside the cluster.

## 10.7 Deployment Order
```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/pvcs/
kubectl apply -f k8s/statefulsets/        # all databases first
# Wait for databases ready:
kubectl wait --for=condition=ready pod -l app=proposal-postgres -n freelance --timeout=120s
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/deployments/         # services after databases
kubectl apply -f k8s/services/
kubectl apply -f k8s/api-gateway/
```
## K8s Deliverables
- [ ] k8s/namespaces/namespace.yaml — namespace freelance
- [ ] k8s/secrets/jwt-secret.yaml — shared JWT secret (base64-encoded)
- [ ] 5 PostgreSQL secrets (one per service)
- [ ] 5 PostgreSQL StatefulSets with PVC templates (postgres:17 image)
- [ ] 5 headless Services for PostgreSQL StatefulSets
- [ ] RabbitMQ StatefulSet + Service
- [ ] MongoDB, Redis, Elasticsearch, Neo4j, Cassandra StatefulSets + headless Services (carry over from M2 Docker Compose)
- [ ] 5 Spring Boot Deployments with readiness/liveness probes on /actuator/health
- [ ] 5 ClusterIP Services for Spring Boot services
- [ ] 6 ConfigMaps (one per service + gateway) with all env vars
- [ ] API Gateway Deployment + NodePort Service (port 30080)
