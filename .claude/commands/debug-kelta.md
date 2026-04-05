Debug Kelta platform services running in Kubernetes.

Usage: `/debug-kelta` (all services), `/debug-kelta gateway`, `/debug-kelta worker`, `/debug-kelta auth`

Argument: $ARGUMENTS (optional: "gateway", "worker", "auth", or empty for all)

All commands use namespace `kelta`.

## Step 1: Check pod status
```bash
kubectl get pods -n kelta
```

If a specific service is requested, filter to that deployment.

## Step 2: Check for recent errors
For each service (or the specified one):
```bash
kubectl logs -n kelta deployment/kelta-<service> --since=30m --tail=100 | grep -i "ERROR\|exception\|WARN"
```

## Step 3: Check pod health
For any pod not in Running/Ready state:
```bash
kubectl describe pod -n kelta <pod-name>
```

## Step 4: Check restarts
```bash
kubectl get pods -n kelta -o wide
```

Report findings: pod status, recent errors, restart counts, any unhealthy pods.
