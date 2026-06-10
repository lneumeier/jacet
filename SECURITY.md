# Security Policy

## Reporting a Vulnerability

Please report security issues privately via GitHub's
[private vulnerability reporting](https://github.com/lneumeier/jacet/security/advisories/new).

Please do **not** open public issues, discussions, or pull requests
for suspected vulnerabilities.

## Scope

In scope: the `jacet-core` and `jacet-cli` artifacts published from this
repository, and the `plugin-gradle` build integration.

Out of scope: vulnerabilities in transitive dependencies (report these
upstream), issues that require an attacker to already control the
developer's machine, and denial-of-service via adversarial input to the
formatter (formatting untrusted source code is not a supported use case).

## Supported Versions

Only the latest released version receives security fixes. Users on older
versions are expected to upgrade.

## Response

This is a volunteer-maintained project; there is no response-time SLA.
Reports are handled on a best-effort basis. Once a fix is ready, we
coordinate disclosure with the reporter and request a CVE via GitHub's
security advisory workflow.
