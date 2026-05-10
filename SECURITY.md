# Security Policy

Kelta Platform is a homelab / single-user project. Security is taken seriously
but this repository is not backed by a dedicated security team — response times
are best-effort.

## Reporting a Vulnerability

Please report suspected vulnerabilities privately by email to
**cklinker@rzware.com**. Do not open a public GitHub issue for security
reports.

Include:
- A description of the issue and its impact
- Steps to reproduce (or a proof-of-concept)
- Affected component(s) (e.g. `kelta-gateway`, `kelta-worker`, `kelta-ui`)

You should expect an acknowledgement within a few days. Triage, fix, and
disclosure timelines are best-effort and depend on severity.

## Known Issues

A running list of known security risks, fragile areas, and tech debt is kept
in [`.claude/docs/concerns.md`](.claude/docs/concerns.md). Check there before
reporting to avoid duplicates.

## Autopilot and Security-Typed Changes

This repository uses an autopilot loop that can open and auto-merge pull
requests for routine work. Tasks of `type: security` are gated to manual
review and are **never** auto-merged — a human approves every security
change before it lands on `main`.
