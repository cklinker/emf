# EMF Autopilot — Phase 3 Observability

Foundations for shipping dispatcher logs + per-task cost telemetry to the homelab Loki + Grafana, plus a ready-to-import dashboard and alert rules.

Per the plan, deployment was deferred until **2 weeks of real data**. This PR ships the wiring so the deploy is one short ops task when the time comes.

## What lands here

| File | Purpose |
|---|---|
| `lib/parse-usage.sh` (in `..`) | Aggregates `claude -p` stream-json into a one-line cost summary per task, written to `/var/log/emf-dispatcher/cost-<task-id>.json` |
| `worker.sh` integration (in `..`) | Calls parse-usage at the end of every task; injects `duration_sec` into the `task_done` event |
| `promtail/promtail-config.yml` | Promtail scrape config for `worker-01` (3 jobs: per-task jsonl, cost summaries, dispatcher journal) |
| `promtail/promtail.service` | systemd unit |
| `grafana/emf-autopilot-dashboard.json` | Importable dashboard: throughput, cost per task, top 10 expensive, retry rate, recent events |
| `alerts/emf-autopilot-alerts.yml` | Prometheus/Mimir alert rules: failed-task count, host down, dispatcher flap, CI slow, daily spend, single-task runaway |

## Deploy steps (when ready)

### 1. Expose Loki externally

Loki currently runs as ClusterIP only (`10.152.183.127:3100`). worker-01 lives outside the cluster so it can't reach that. Add a Traefik IngressRoute:

```yaml
# homelab-argo/observability/loki-ingressroute.yaml
apiVersion: traefik.io/v1alpha1
kind: IngressRoute
metadata:
  name: loki
  namespace: observability
spec:
  entryPoints: [websecure]
  routes:
    - match: Host(`loki.rzware.com`) && PathPrefix(`/loki/api`)
      kind: Rule
      services:
        - name: loki
          port: 3100
      middlewares:
        - name: loki-basic-auth
  tls:
    secretName: loki-tls
```

Add a basic-auth Middleware + Secret (see existing `grafana` IngressRoute for pattern). Add `loki.rzware.com` to ddns-updater.

### 2. Install Promtail on worker-01

```sh
ssh worker-01

# Binary
sudo wget -O /tmp/promtail.zip https://github.com/grafana/loki/releases/download/v3.0.0/promtail-linux-amd64.zip
sudo unzip /tmp/promtail.zip -d /usr/local/bin/
sudo mv /usr/local/bin/promtail-linux-amd64 /usr/local/bin/promtail
sudo chmod +x /usr/local/bin/promtail

# Config
sudo install -d -o root -g root /etc/promtail
sudo install -d -o promtail -g promtail /var/lib/promtail
sudo cp ~/GitHub/emf/.claude/dispatcher/observability/promtail/promtail-config.yml /etc/promtail/config.yml
# Edit /etc/promtail/config.yml: set the right loki url, basic-auth password file
sudo install -m 600 -o root -g root <(echo 'YOUR_BASIC_AUTH_PASSWORD') /etc/promtail/.loki-password

# systemd
sudo cp ~/GitHub/emf/.claude/dispatcher/observability/promtail/promtail.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now promtail

# Verify
journalctl -fu promtail
```

### 3. Import the Grafana dashboard

In Grafana UI:
- Dashboards → New → Import
- Paste `grafana/emf-autopilot-dashboard.json`
- Pick the Loki datasource

### 4. Wire alerts into Mimir/Prometheus

Place `alerts/emf-autopilot-alerts.yml` next to your other Mimir alert rules, reload the rule loader. **Tune thresholds first** based on the first week of data (`EMFAutopilotDailySpendHigh` defaults to $50/day; adjust).

## Verifying

After deployment, first signal:

```sh
ssh worker-01
cat /var/log/emf-dispatcher/cost-*.json | jq -c .
```

You should see one line per completed task with token counts + estimated cost. Once Promtail is shipping, the dashboard's "Daily $ spend" stat will start populating within a minute.

## Cost estimation accuracy

`parse-usage.sh` uses Claude Opus 4.7 list pricing baked in:

| Tier | Per token |
|---|---|
| Input | $0.000015 (= $15/M) |
| Cache creation | $0.00001875 (= $18.75/M, 1.25× input) |
| Cache read | $0.0000015 (= $1.50/M, 0.10× input) |
| Output | $0.000075 (= $75/M) |

Override via env: `PRICE_INPUT`, `PRICE_CACHE_CREATION`, `PRICE_CACHE_READ`, `PRICE_OUTPUT`. For exact accounting, reconcile against the Anthropic console's billing page periodically.
