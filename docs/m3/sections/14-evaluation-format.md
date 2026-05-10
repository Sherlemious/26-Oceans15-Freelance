# Section 14 — Evaluation Format

> Split from `../m3.txt`. Original file is untouched.

## 14.1 Individual Presentation (~5 minutes per member)
Each member presents the branch they implemented and you will need to answer questions about your part of work

## 14.2 Demo Requirements
The team (like one member at least) must be able to run the full project from the cluster:

```bash
kubectl get pods -n freelance                   # all pods Running
kubectl logs <your-service-pod> -n freelance    # your service logs
curl http://$(minikube ip):30080/api/<endpoint> # your feature end-to-end
```
For saga branch owners: demonstrate the Proposal Lifecycle Saga & Cancellation Cascade by triggering PUT /api/proposals/{id}/complete and showing the event ripple in contract-service and wallet-service logs, then injecting a payment.failed to demonstrate the compensation cascade.
