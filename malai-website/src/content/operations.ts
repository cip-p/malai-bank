import { site } from "./site";

export interface OperationsScreenshot {
  file: string;
  alt: string;
}

export interface OperationsStat {
  value: string;
  label: string;
}

export interface OperationsStep {
  title: string;
  text: string;
  screenshot: OperationsScreenshot;
}

export interface OperationsPanel {
  heading: string;
  body: string;
  points: string[];
}

export interface OperationsAml {
  heading: string;
  body: string;
  steps: OperationsStep[];
  points: string[];
}

export interface Operations {
  heading: string;
  narrative: string;
  stats: OperationsStat[];
  walkthroughHeading: string;
  steps: OperationsStep[];
  copilot: OperationsPanel & { screenshot: OperationsScreenshot };
  aml: OperationsAml;
  seam: OperationsPanel;
  cta: { label: string; href: string };
  evidence: { text: string; linkLabel: string; href: string };
}

export const operations: Operations = {
  heading: "Every AI decision on the record",
  narrative:
    "Malai assumes a bank will put models to work — and that a regulator, an auditor or a customer will one day ask exactly what a model was shown, what it produced, and who ruled on it. The answer is the Model Decision Record: an append-only, hash-chained record of every model invocation and every human ruling appended to it. The Ops surface below is the live demo reading that record — not a mock-up.",
  stats: [
    { value: "Append-only", label: "nothing updates — rulings append" },
    {
      value: "Hash-chained",
      label: "every entry sealed against the one before",
    },
    {
      value: "Record first",
      label: "decisions land on the record before the flow acts",
    },
    { value: "Human-ruled", label: "money moves only through the ruling gate" },
  ],
  walkthroughHeading: "A payment through the decision plane",
  steps: [
    {
      title: "A payment is screened",
      text: "Every outbound payment is screened before it reaches a rail. A flagged payment parks with the funds set aside, and the customer sees a calm holding state that names no reason — screening that explains itself to the person it screens has already failed.",
      screenshot: {
        file: "payment-referred.png",
        alt: "Customer screen reading We're checking this payment, for £250.00 to Carol Singh, with the money set aside and no reason given",
      },
    },
    {
      title: "The decision joins the worklist",
      text: "The screening verdict is recorded as a model decision — model and version, verdict, latency, cost — and queued for a human ruling. Filter chips carry live counts by ruling state, and advisory records stay out of the queue without leaving the record.",
      screenshot: {
        file: "ops-worklist.png",
        alt: "Ops worklist filtered to decisions awaiting a ruling, showing a screening decision beside decisions from a frontier model, each with verdict, outcome, model id, latency and cost",
      },
    },
    {
      title: "A reviewer reads the full record",
      text: "The decision page replays the invocation: model and prompt-template versions, the rendered prompt, the raw output, the retrieved context pinned by content hash, and the chain sequence it landed at — enough to reconstruct the decision long after the model that made it has been retired.",
      screenshot: {
        file: "ops-decision.png",
        alt: "A decision record for a screened payment: a DECLINE verdict with a watchlist reason and risk score, model and prompt-template versions, chain entry sequence, raw JSON output and retrieved context pinned by content hash",
      },
    },
    {
      title: "A ruling is appended — never edited in",
      text: "Accept, edit or reject. An edit carries its full replacement output, and the difference from the raw output is derived at read time, never stored. The gate then serves exactly what the ruling allows: the raw output under accept, the replacement under edit, nothing otherwise.",
      screenshot: {
        file: "ops-ruling-diff.png",
        alt: "An edit ruling appended to a decision, showing the reviewer's replacement output approving the payment and a line diff against the model's raw DECLINE output",
      },
    },
    {
      title: "The chain proves itself",
      text: "On-demand verification walks the record from the first entry to the head and reports whether every link holds. Tampering is not a matter of argument — a broken chain names the sequence where it broke.",
      screenshot: {
        file: "ops-chain.png",
        alt: "Chain verification reporting the record is intact after walking 30 entries from the first entry to the chain head",
      },
    },
  ],
  copilot: {
    heading: "A copilot that must cite its sources",
    body: "On a screened payment, the reviewer can ask for an explanation. The Copilot assembles a timeline from records the bank actually holds — the payment, the ledger hold, the screening decision, the audit events — and answers as ordered claims, each carrying the id of the source record it rests on.",
    points: [
      "Grounding is machine-checked and all-or-nothing: if one claim cites a record outside the timeline, the whole answer is withheld and the refusal is recorded. A fabricated citation cannot reach the reader.",
      "Every invocation — answers, refusals and failures alike — appends its own advisory decision to the same record, with its rendered prompt, output, latency and cost.",
      "It is structurally non-authoritative: no service in the bank can consume an explanation as input to an action, and the absence of that pathway is enforced at build time.",
    ],
    screenshot: {
      file: "copilot-explanation.png",
      alt: "The Explanation panel on a decision record: cited claims describing the payment, the ledger hold and the screening decision, each carrying the identifier of the source record it rests on",
    },
  },
  aml: {
    heading: "After the money moves: AML on the same record",
    body: "Screening judges one payment before it reaches a rail. Transaction monitoring reads across settled payments after the money has moved, on a sweep that re-evaluates the last rolling hour every few seconds. What it finds lands on the same decision record as everything else: a pattern found is an Alert, an Alert opens a Case, and a Case closes only through the same ruling gate that governs every model output.",
    steps: [
      {
        title: "A pattern becomes an Alert",
        text: "Five payments, each honestly under the reporting threshold, jointly at it. No single payment is wrong — the pattern is, and only something reading across settled payments can see it. The Alert is recorded as a model decision correlated to the account, naming its typology and risk score and pinning the exact payments it fired over by content hash. The same pattern re-found is the same Alert: the worklist reflects patterns, not polling cadence.",
        screenshot: {
          file: "ops-aml-cases.png",
          alt: "The AML cases list showing two investigations: one case closed as reported with one alert, and one under investigation carrying two alerts, each with its account and status",
        },
      },
      {
        title: "A Case is the account's single investigation home",
        text: "An Alert opens a Case for its account or joins the one already open — at most one live investigation per account, enforced by the database, so the officer is never reconciling two files about the same customer. Every alert's typology, risk score and evidence payments read in one place; each alert is ruled at its own decision record; every case transition lands on the audit chain.",
        screenshot: {
          file: "ops-aml-case.png",
          alt: "An AML case under investigation: a structuring alert awaiting ruling with risk score 70, the settled payments it fired over listed beneath it, each pinned by content hash",
        },
      },
      {
        title: "The SAR narrative is drafted, grounded and ruled",
        text: "On an open Case the officer requests a Suspicious Activity Report draft. Every statement must cite a record in the Case's assembled timeline; one citation outside it and the whole draft is withheld, with the refusal recorded. A human ruling on the draft's own record is what makes the narrative filable — and only that ruling lets the Case close as reported.",
        screenshot: {
          file: "ops-aml-sar.png",
          alt: "A case closed as reported: the accepted Suspicious Activity Report narrative filed with the case, every statement drawn from the settled payments and alert on the case timeline, with the decision it was ruled under linked beneath",
        },
      },
    ],
    points: [
      "Tipping off is a criminal offence, so here it is an architectural invariant rather than a guideline. A monitored customer's app is indistinguishable from anyone else's — no flag, no payment-state difference, no altered error — and no customer-facing module can even depend on the monitoring service. The absence of that pathway is enforced at build time and asserted by tests that compare customer responses with a case open and without.",
      "The SAR drafter is a model seat like every other. A deterministic template drafter ships as the default, and a provider adapter speaking the OpenAI-compatible chat surface is selected by configuration — a local model in the demo, any conforming provider in production, no rebuild either way.",
      "A drafter that runs over budget or fails records TIMED_OUT or ERRORED on its own Model Decision, with latency and cost. The failure mode is a truthful record and a case that waits for a human — never an invisible retry, never movement.",
    ],
  },
  seam: {
    heading: "Ready for the model you choose",
    body: "Screening today runs a deterministic rules engine, and the platform invokes it exactly as it would invoke a model: versioned prompt template, rendered prompt, context references, timed outcome, structured verdict — all chained into the record. Swapping in an AI fraud model is a configuration-selected implementation of the same seam, not a rebuild.",
    points: [
      "The record is provider-agnostic. The demo worklist shows a rules engine and a frontier model side by side, in the same columns, on the same chain.",
      "Provider adapters are already built behind two seams — Anthropic's Claude models behind the Copilot, and any OpenAI-compatible endpoint behind the SAR drafter — each selected by configuration and off by default, so the demo runs without any provider key.",
      "Un-ruled or unreadable verdicts move the bank conservative-only: a referred payment stays parked until a human rules. The failure mode is delay, never movement.",
    ],
  },
  cta: {
    label: "Register in the demo and open the Ops surface →",
    href: site.demoSignupUrl,
  },
  evidence: {
    text: "The screening loop and the Copilot's grounding run as end-to-end tests on every merge, published as executed sequence diagrams.",
    linkLabel: "Browse the executed flows →",
    href: "/end-to-end-flows/",
  },
};
