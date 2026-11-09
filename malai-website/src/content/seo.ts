import { architecture } from "./architecture";
import { executive } from "./executive";
import { operations } from "./operations";
import { security } from "./security";
import { site } from "./site";

export interface PageSeo {
  title: string;
  description: string;
}

function branded(title: string): string {
  return `${title} — ${site.brand}`;
}

export const pageSeo = {
  home: {
    title: site.title,
    description:
      "A working reference platform for prospective bank operators: banking core, customer app, AI-assisted operations, executive dashboard, infrastructure as code.",
  },
  operations: {
    title: branded(`Operations — ${operations.heading}`),
    description:
      "Every AI-assisted decision on an append-only, hash-chained record: what the model saw, what it produced, and who ruled on it.",
  },
  executive: {
    title: branded(`Executive — ${executive.heading}`),
    description:
      "The boardroom's headline numbers — customers, deposits, cards issued, payments stopped — in a role that reads every figure and can rule on nothing.",
  },
  architecture: {
    title: branded(`Architecture — ${architecture.heading}`),
    description:
      "Module-fenced services, idempotent writes, three-pillar observability and infrastructure-as-code — the patterns a buyer's platform team would inherit.",
  },
  security: {
    title: branded(`Security — ${security.heading}`),
    description:
      "Standards-language security claims with the evidence beside each — identity, data, edge and operations — so a review starts from facts, not faith.",
  },
  about: {
    title: branded("About"),
    description:
      "Why Malai exists and how it's built — for teams weighing whether to commission a UK retail bank stack, and who to build it with.",
  },
  whatThisIsnt: {
    title: branded("What this isn't"),
    description:
      "Malai looks like a UK retail bank but isn't one. The four lines it does not cross, named directly — the boundary on its own page, not in fine print.",
  },
  privacy: {
    title: branded("Privacy notice"),
    description:
      "How Malai handles the small amount of information visitors send through this site. The demo runs on synthetic data and never receives real personal data.",
  },
  terms: {
    title: branded("Terms of use"),
    description:
      "Plain-English terms for the Malai marketing site and demo app — a reference platform, not a regulated product, and the terms reflect that.",
  },
  notFound: {
    title: branded("Page not found"),
    description: "The page you were looking for doesn't exist or has moved.",
  },
} satisfies Record<string, PageSeo>;
