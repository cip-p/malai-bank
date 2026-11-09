export type Evidence =
  | { kind: "note"; text: string }
  | { kind: "link"; text: string; href: string }
  | { kind: "placeholder"; text: string };

export type TrustCategoryId = "identity" | "data" | "edge" | "operations";

export interface TrustCategory {
  id: TrustCategoryId;
  label: string;
  blurb: string;
}

export interface TrustEntry {
  claim: string;
  category: TrustCategoryId;
  detail: string;
  evidence: Evidence;
}

export interface TrustStat {
  value: string;
  label: string;
}

export interface Security {
  heading: string;
  teaser: string;
  narrative: string;
  stats: TrustStat[];
  categories: TrustCategory[];
  trustLedger: TrustEntry[];
}

export const security: Security = {
  heading: "Built for the security review",
  teaser:
    "Standards-language claims with the evidence sitting beside each — identity, data, edge, and operations — so a security review can start from facts, not faith.",
  narrative:
    "Malai retires the table-stakes objections up front. Each row is a standards-language claim, with the evidence that supports it sitting beside it — nothing on this list is asking you to take it on faith.",
  stats: [
    { value: "TLS 1.2+", label: "minimum at every edge" },
    { value: "HSTS preload", label: "1-year max-age, includeSubdomains" },
    { value: "OWASP CRS", label: "plus IP reputation & rate limiting" },
    { value: "Per-task identity", label: "no shared service secret" },
  ],
  categories: [
    {
      id: "identity",
      label: "Identity & access",
      blurb:
        "Who can speak to the system, how their tokens are minted, and what stops a stolen credential from going anywhere.",
    },
    {
      id: "data",
      label: "Data protection",
      blurb:
        "Keys, secrets, database roles and the audit trail — what's encrypted, what's append-only, who can touch which row.",
    },
    {
      id: "edge",
      label: "Edge & network",
      blurb:
        "What a request meets before it reaches application code — TLS, WAF rules, security headers, and a database that has no public address.",
    },
    {
      id: "operations",
      label: "Operational integrity",
      blurb:
        "What stops a retried write from double-spending, and what surfaces an incident before a customer reports it.",
    },
  ],
  trustLedger: [
    {
      claim: "Authentication",
      category: "identity",
      detail:
        "Short-lived JWT bearer tokens, signed with a managed key, validated on every request. The customer-facing app never holds a long-lived credential.",
      evidence: {
        kind: "note",
        text: "Token lifetime measured in minutes; refresh is server-side.",
      },
    },
    {
      claim: "Sign-in hardening",
      category: "identity",
      detail:
        "Every sign-in runs through compromised-credentials detection and adaptive risk scoring. Optional TOTP MFA is available, and the password policy requires at least twelve characters with mixed case, numbers and symbols.",
      evidence: {
        kind: "note",
        text: "Adaptive auth is enforced, not advisory — high-risk sign-ins are blocked.",
      },
    },
    {
      claim: "Authorization",
      category: "identity",
      detail:
        "RBAC enforced at the API, with module fences enforced at build time so a call that should never cross a domain cannot compile.",
      evidence: {
        kind: "note",
        text: "Module fences fail the build, not just the test.",
      },
    },
    {
      claim: "Inter-service authentication",
      category: "identity",
      detail:
        "Services authenticate to each other with OAuth2 client credentials. A compromised internal client cannot impersonate a user, and a compromised user token cannot reach internal-only routes.",
      evidence: {
        kind: "link",
        text: "OAuth 2.0 Client Credentials Grant — RFC 6749 §4.4",
        href: "https://datatracker.ietf.org/doc/html/rfc6749#section-4.4",
      },
    },
    {
      claim: "Defence-in-depth token verification",
      category: "identity",
      detail:
        "Every JWT is verified twice — once by the edge gateway against the issuer's JWKS, and again inside the service. Bypassing the gateway does not bypass the service; bypassing the service does not bypass the gateway.",
      evidence: {
        kind: "note",
        text: "Signature, expiry, audience and issuer are checked at both layers.",
      },
    },
    {
      claim: "Encryption at rest",
      category: "data",
      detail:
        "Database, object storage, log streams and the image registry all use customer-managed keys. Plaintext is not written to disk at any layer.",
      evidence: {
        kind: "note",
        text: "Key rotation runs on a managed schedule; rotation is transparent to the app.",
      },
    },
    {
      claim: "Secrets management",
      category: "data",
      detail:
        "A managed secret store holds every credential. Each task runs under its own identity; no plaintext secret is ever baked into an image or an env file.",
      evidence: {
        kind: "note",
        text: "Per-task identity means a leaked process secret revokes one task, not the fleet.",
      },
    },
    {
      claim: "Database role separation",
      category: "data",
      detail:
        "The application connects with a least-privilege role — INSERT/SELECT only on the audit log, no DDL anywhere. Migrations run under a separate, privileged role that the runtime never holds.",
      evidence: {
        kind: "note",
        text: "The database master credential auto-rotates on a managed schedule and a redeploy picks it up without manual intervention.",
      },
    },
    {
      claim: "Append-only audit",
      category: "data",
      detail:
        "Every ledger event is appended to a hash-chained, tamper-evident log. Replaying the chain reproduces the balance.",
      evidence: {
        kind: "placeholder",
        text: "A sanitised audit row sample lands with the evidence pass.",
      },
    },
    {
      claim: "Cardholder data minimisation",
      category: "data",
      detail:
        "Card numbers are masked to the last four digits everywhere except the explicit reveal, and never reach logs or the audit chain. Authorisation records are PAN-free — the number is resolved to an internal card id at the boundary and discarded.",
      evidence: {
        kind: "note",
        text: "The PANs are fictional in any case: synthetic cards making synthetic purchases.",
      },
    },
    {
      claim: "TLS at the edge",
      category: "edge",
      detail:
        "Every origin is HTTPS-only with TLS 1.2 as the floor. HSTS is set with a one-year max-age and preload, and HTTP/2 plus HTTP/3 are negotiated where the client supports them.",
      evidence: {
        kind: "placeholder",
        text: "External TLS scan link lands with the evidence pass.",
      },
    },
    {
      claim: "Managed WAF at the edge",
      category: "edge",
      detail:
        "Public origins sit behind a managed WAF with the OWASP core rule set, the known-bad-inputs rule set and an IP reputation list. A per-IP rate-limit rule caps abusive traffic before it reaches the application.",
      evidence: {
        kind: "note",
        text: "Rule sets are vendor-maintained; the application never has to ship a patch to keep up with a new exploit family.",
      },
    },
    {
      claim: "Security headers at the cache layer",
      category: "edge",
      detail:
        "HSTS, a per-app Content-Security-Policy, X-Frame-Options: DENY and X-Content-Type-Options: nosniff are stamped on every response by the edge cache. A misconfigured application cannot strip them.",
      evidence: {
        kind: "note",
        text: "Header policy lives in infrastructure code, not application code.",
      },
    },
    {
      claim: "Private database",
      category: "edge",
      detail:
        "The database has no public network address. It is reachable only from inside the private network, and only from the application's security group on the database port — nothing else.",
      evidence: {
        kind: "note",
        text: "TLS to the database is enforced at the engine level, not just at the client.",
      },
    },
    {
      claim: "Idempotency",
      category: "operations",
      detail:
        "Synchronous and asynchronous write paths take an Idempotency-Key header. Replaying a mutation inside a 24-hour window returns the original result instead of a second side-effect.",
      evidence: {
        kind: "note",
        text: "The same key is honoured across retries from clients, queues and back-pressured workers.",
      },
    },
    {
      claim: "Encrypted, correlated, alarmed logging",
      category: "operations",
      detail:
        "Every request carries a correlation id that travels through traces, metrics and structured logs. Log streams are encrypted with managed keys, and an alarm fires when the error rate crosses a threshold.",
      evidence: {
        kind: "note",
        text: "Traces, RED-method metrics and JSON logs are emitted by every service.",
      },
    },
    {
      claim: "AI decisions on the record",
      category: "operations",
      detail:
        "Every model invocation — screening verdicts and copilot answers alike — is recorded to an append-only, hash-chained decision record before the flow acts on it: model and template versions, rendered prompt, raw output, context references, latency and cost.",
      evidence: {
        kind: "link",
        text: "Read how the decision record works",
        href: "/operations/",
      },
    },
    {
      claim: "Conservative-only enforcement",
      category: "operations",
      detail:
        "A flagged payment parks with funds held and moves only when a human ruling passes the gate. An un-ruled, rejected or unreadable verdict cannot move money — the failure mode is delay, never movement.",
      evidence: {
        kind: "note",
        text: "The customer-facing screens state the payment status and nothing more — no reasons, no tipping off.",
      },
    },
  ],
};
