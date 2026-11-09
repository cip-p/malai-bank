import { site } from "./site";

export interface ExecutivePoint {
  title: string;
  detail: string;
}

export interface Executive {
  heading: string;
  narrative: string;
  screenshot: { file: string; alt: string };
  caption: string;
  points: ExecutivePoint[];
  cta: { label: string; href: string };
}

export const executive: Executive = {
  heading: "The numbers, without the keys",
  narrative:
    "The Exec surface gives the boardroom the bank's headline numbers — new customers, deposits, cards issued, payments stopped — over today, the last seven or thirty days, or the last twelve months. It is deliberately a narrow role: a manager reads every number and can rule on nothing.",
  screenshot: {
    file: "exec-dashboard.png",
    alt: "Executive dashboard for the last 30 days showing 5 new customers, £11,145.50 in deposits, 5 cards issued and £32,300.00 in payments stopped, with a reporting-window selector and an as-of line",
  },
  caption:
    "The executive dashboard in the live demo, reporting a 30-day window.",
  points: [
    {
      title: "Honest numbers or none",
      detail:
        "A metric that cannot be computed renders as an em-dash — never a zero, never a stale figure dressed as fresh. Unavailability is part of the contract, and every reading carries its as-of moment.",
    },
    {
      title: "Payments stopped — not fraud prevented",
      detail:
        "The dashboard counts the value of payments the bank declined through the ruling gate. It deliberately does not claim they were fraud: that is a claim the record cannot substantiate, so the platform refuses to make it.",
    },
    {
      title: "Separation of duties, enforced at the API",
      detail:
        "The manager role reads the dashboard and nothing else. The decision worklist refuses that role at the API, not just in the interface — a bank can hand its CFO the numbers without handing over the power to rule on payments.",
    },
    {
      title: "A composed read that owns nothing",
      detail:
        "The dashboard holds no data of its own. It fans out to the domains that do, with a timeout per source — one slow domain costs one tile, not the page.",
    },
  ],
  cta: {
    label: "Register in the demo and open the Exec surface →",
    href: site.demoSignupUrl,
  },
};
