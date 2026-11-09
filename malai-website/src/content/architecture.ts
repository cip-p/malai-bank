export type ArchitectureCategoryId =
  | "topology"
  | "services"
  | "edge"
  | "operations";

export interface ArchitectureCategory {
  id: ArchitectureCategoryId;
  label: string;
  blurb: string;
}

export interface ArchitectureAnnotation {
  title: string;
  category: ArchitectureCategoryId;
  detail: string;
}

export interface ArchitectureStat {
  value: string;
  label: string;
}

export interface DiagramMeta {
  title: string;
  description: string;
}

export interface ArchitectureEvidence {
  heading: string;
  body: string;
  linkLabel: string;
  href: string;
}

export interface Architecture {
  heading: string;
  teaser: string;
  narrative: string;
  stats: ArchitectureStat[];
  categories: ArchitectureCategory[];
  annotations: ArchitectureAnnotation[];
  diagram: DiagramMeta;
  evidence: ArchitectureEvidence;
}

export const architecture: Architecture = {
  heading: "How it's built",
  teaser:
    "Module-fenced services, idempotent writes on sync and async paths, three-pillar observability, and infrastructure-as-code per environment — the patterns a buyer's platform team would inherit.",
  narrative:
    "One diagram, then the patterns that hold it up. The shape below is the system as it actually runs — abstracted away from any specific vendor so you can read the design, not the marketing.",
  stats: [
    { value: "Multi-AZ", label: "across availability zones" },
    { value: "OpenAPI", label: "single contract, generated clients" },
    { value: "Idempotency-Key", label: "on every write path" },
    { value: "IaC per env", label: "no hand-rolled drift" },
  ],
  categories: [
    {
      id: "topology",
      label: "Topology & isolation",
      blurb:
        "Where the system lives — replicated across availability zones, split across cloud accounts, and fenced at the module boundary so a stray call cannot cross a domain it shouldn't.",
    },
    {
      id: "services",
      label: "Services & contracts",
      blurb:
        "How services talk to each other and to the database — synchronous where the caller needs an answer, asynchronous where coupling would force them to wait, and idempotent on every write path.",
    },
    {
      id: "edge",
      label: "Edge & delivery",
      blurb:
        "What a request meets before it reaches application code, and how credentials, certificates and headers are kept fresh without a maintenance window.",
    },
    {
      id: "operations",
      label: "Operations & change",
      blurb:
        "How the platform is observed, reproduced and promoted — three pillars of telemetry, infrastructure expressed as code, and smoke tests against the deployed surface before promotion.",
    },
  ],
  annotations: [
    {
      title: "Cloud-native, multi-AZ",
      category: "topology",
      detail:
        "Every primitive is a managed service the cloud operator runs across multiple availability zones. No single host owns state.",
    },
    {
      title: "Environment isolation by account boundary",
      category: "topology",
      detail:
        "Dev and prod live in separate cloud accounts with their own state, keys and credentials. A mistake in dev cannot reach prod data, and a dev-only credential cannot describe a prod resource.",
    },
    {
      title: "Microservices with module fences",
      category: "topology",
      detail:
        "Each banking capability is a discrete service with build-time fences that fail compilation when a call crosses a domain boundary it shouldn't.",
    },
    {
      title: "Structurally non-authoritative AI reads",
      category: "topology",
      detail:
        "The explanation capability deliberately has no internal client, so no service can consume its output as input to an action. The boundary is enforced at build time and recorded by an architecture test.",
    },
    {
      title: "Event-driven where it matters",
      category: "services",
      detail:
        "Synchronous calls where the caller needs an answer; asynchronous events where coupling would otherwise force services to wait on each other.",
    },
    {
      title: "Idempotency on sync and async paths",
      category: "services",
      detail:
        "Every write — request or event — carries an Idempotency-Key. Retries from clients, queues, and back-pressured workers all converge on the same result.",
    },
    {
      title: "Two-tier database access",
      category: "services",
      detail:
        "The application runs under a least-privilege database role — INSERT/SELECT only on the audit log, no DDL anywhere. Migrations run under a separate, privileged role that the runtime never holds.",
    },
    {
      title: "One chain core, two tenants",
      category: "services",
      detail:
        "The hash-chain mechanics behind the audit log are a shared core with a second tenant: the record of AI-assisted decisions. Same canonical serialisation, same verification, separate scopes.",
    },
    {
      title: "Composed reads own no domain",
      category: "services",
      detail:
        "The executive dashboard is a fan-out over the domains that own the data, with a timeout per source and unavailability as a first-class state — it holds no tables of its own.",
    },
    {
      title: "Edge cache layer with stamped security headers",
      category: "edge",
      detail:
        "Every public origin sits behind an edge cache that enforces HSTS, a per-app Content-Security-Policy, X-Frame-Options and X-Content-Type-Options. A misconfigured application cannot strip them.",
    },
    {
      title: "Automated credential rotation",
      category: "edge",
      detail:
        "The database master credential rotates on a managed schedule. An event-driven redeploy picks up the new value without manual intervention and without a maintenance window.",
    },
    {
      title: "Three-pillar observability",
      category: "operations",
      detail:
        "Distributed traces, RED-method metrics, and structured logs are emitted from every service and correlated by a single request id.",
    },
    {
      title: "Infrastructure-as-code per environment",
      category: "operations",
      detail:
        "A fresh environment — networking, compute, database, secrets, DNS — stands up from code in minutes. Drift between environments is a build break, not a guessing game.",
    },
    {
      title: "CI/CD with smoke tests post-deploy",
      category: "operations",
      detail:
        "Every merge to main flows through automated tests, builds an artefact, ships to dev, and runs a smoke pack against the deployed surface before promoting.",
    },
  ],
  diagram: {
    title: "Malai system architecture, abstracted",
    description:
      "Boxes-and-arrows view of the platform: external rails at the top connect through edge TLS termination and an API gateway, past an identity service, into four banking domains — account, cards, payments, audit — backed by a managed database, an event bus, and a managed object store.",
  },
  evidence: {
    heading: "End-to-end evidence",
    body: "The full end-to-end suite runs on every merge to main against a real, running stack, and a passing run publishes its Kensa report here. Every scenario reads in plain given/whenever/then language and carries the sequence diagram of the calls the system actually made — generated from execution, not drawn for a deck.",
    linkLabel: "Browse the end-to-end test report →",
    href: "/end-to-end-flows/",
  },
};
