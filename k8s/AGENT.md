# Kubernetes AGENT Guide (k8s/)

This file is the operational runbook for this `k8s/` folder.

Use it when you need a **clean redeploy**, a **quick update**, or a **basic test pass**.

---

## 0) What lives in this folder

Top-level structure:

- `k8s/namespaces/` -> namespace manifests (`freelance`, `monitoring`)
- `k8s/secrets/` -> Kubernetes secrets
- `k8s/configmaps/` -> app and gateway config
- `k8s/services/` -> internal service discovery
- `k8s/statefulsets/` -> stateful infra (databases/brokers)
- `k8s/deployments/` -> stateless app workloads
- `k8s/api-gateway/` -> gateway deployment/service
- `k8s/monitoring/` -> Loki, Grafana, Prometheus manifests
- `k8s/pvcs/` -> persistent volume claims

Important: this setup is applied in a **strict order**. Do not use recursive apply (`-R`) for this repo.

---

## 1) Clean reset (nuke and rebuild)

Run from repository root.

### Step 1: Delete both namespaces

```bash
kubectl delete namespace freelance monitoring --ignore-not-found=true
```

Wait until both are fully gone before continuing:

```bash
kubectl get namespaces
```

If either still appears as `Terminating`, wait and re-check.

### Step 2: Rebuild Docker images

Full rebuild:

```bash
docker compose build
```

Faster targeted rebuild (example):

```bash
docker compose build user-service job-service
```

### Step 3: Recreate namespaces

```bash
kubectl apply -f k8s/namespaces/namespace.yaml -f k8s/namespaces/monitoring-namespace.yaml
```

### Step 4: Apply all remaining manifests (non-recursive, fixed order)

```bash
kubectl apply \
  -f k8s/secrets \
  -f k8s/configmaps \
  -f k8s/services \
  -f k8s/statefulsets \
  -f k8s/deployments \
  -f k8s/api-gateway \
  -f k8s/monitoring/loki/ \
  -f k8s/monitoring/grafana \
  -f k8s/monitoring/prometheus \
  -f k8s/pvcs
```

Note: this command intentionally applies directories in a manual sequence. Keep this behavior.

---

## 2) Restart only one deployment (quick iteration)

For any app deployment, do not redeploy everything.

Delete and re-apply only the target manifest:

```bash
kubectl delete -f k8s/deployments/<name>-deployment.yaml
kubectl apply -f k8s/deployments/<name>-deployment.yaml
```

Example:

```bash
kubectl delete -f k8s/deployments/user-service-deployment.yaml
kubectl apply -f k8s/deployments/user-service-deployment.yaml
```

---

## 3) Testing checklist (idiot-proof)

Run these checks after any clean deploy or targeted redeploy.

### A) Namespaces exist

```bash
kubectl get ns freelance monitoring
```

Expected: both namespaces show `Active`.

### B) Pods are up

```bash
kubectl get pods -n freelance
kubectl get pods -n monitoring
```

Expected: pod status becomes `Running` or `Completed` (for short jobs).

If pods are stuck in `Pending`, inspect events:

```bash
kubectl describe pod <pod-name> -n <namespace>
```

### C) Services exist

```bash
kubectl get svc -n freelance
kubectl get svc -n monitoring
```

Expected: all expected service names are listed.

### D) PVCs are bound

```bash
kubectl get pvc -n freelance
kubectl get pvc -n monitoring
```

Expected: every claim status is `Bound`.

### E) Deployment rollout status

Check each app deployment rollout explicitly:

```bash
kubectl rollout status deployment/user-service -n freelance
kubectl rollout status deployment/job-service -n freelance
kubectl rollout status deployment/proposal-service -n freelance
kubectl rollout status deployment/contract-service -n freelance
kubectl rollout status deployment/wallet-service -n freelance
kubectl rollout status deployment/gateway -n freelance
```

Expected: `successfully rolled out`.

### F) Basic logs sanity

If any pod restarts or errors, inspect logs:

```bash
kubectl logs <pod-name> -n <namespace>
```

For continuous monitoring while debugging:

```bash
kubectl logs -f <pod-name> -n <namespace>
```

### G) Quick API smoke (through gateway)

Port-forward gateway locally:

```bash
kubectl port-forward svc/gateway-service 8080:8080 -n freelance
```

Then in another terminal:

```bash
curl -i http://localhost:8080/actuator/health
```

Expected: HTTP 200 with health payload.

### H) Monitoring smoke

Prometheus:

```bash
kubectl port-forward svc/prometheus-service 9090:9090 -n monitoring
```

Grafana:

```bash
kubectl port-forward svc/grafana-service 3000:3000 -n monitoring
```

Open:

- `http://localhost:9090` (Prometheus UI)
- `http://localhost:3000` (Grafana UI)

---

## 4) Common failure patterns

- Namespace missing errors -> apply namespace files first.
- Secret/config errors on startup -> re-apply `k8s/secrets` and `k8s/configmaps`, then restart affected deployment.
- PVC pending -> storage class / local cluster capacity issue.
- CrashLoopBackOff -> inspect pod logs and `describe` output.
- Monitoring not loading dashboards -> verify Grafana configmaps and datasource manifests were applied.

---

## 5) Golden rules

- Do not use `kubectl apply -R -f k8s` in this repo.
- Keep the manifest apply order exactly as documented.
- For small app changes, restart only the impacted deployment.
- For weird cluster state, do a clean namespace delete + full re-apply.
