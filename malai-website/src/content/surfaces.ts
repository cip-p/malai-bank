export interface SurfaceScreenshot {
  file: string;
  alt: string;
}

export interface SurfaceCard {
  name: string;
  audience: string;
  claim: string;
  href: string;
  linkLabel: string;
  screenshot: SurfaceScreenshot;
}

export interface Surfaces {
  heading: string;
  narrative: string;
  cards: SurfaceCard[];
}

export const surfaces: Surfaces = {
  heading: "One bank, three surfaces",
  narrative:
    "A bank is more than its customer app. Malai ships three surfaces of one platform — the front of house, the back office and the boardroom — behind one sign-in, with role separation enforced at the API rather than hidden in the interface. Register once in the demo and walk all three.",
  cards: [
    {
      name: "Banking",
      audience: "For the customer",
      claim:
        "Accounts, cards, Faster Payments, direct debits, insights and forecasts — the retail app a customer would hold, mobile-first and themeable per brand.",
      href: "#functionality",
      linkLabel: "See what the bank does ↓",
      screenshot: {
        file: "accounts-overview.png",
        alt: "Malai customer app showing a current account, a savings account and a credit card, with quick actions to send money and manage payees",
      },
    },
    {
      name: "Ops",
      audience: "For the reviewer",
      claim:
        "A worklist of AI-assisted decisions, the full replayable record behind each one, and human rulings that move money only through an auditable gate.",
      href: "/operations/",
      linkLabel: "Inside the decision record →",
      screenshot: {
        file: "ops-worklist.png",
        alt: "Ops worklist of model decisions awaiting a ruling, with verdict, outcome, rulings, model, latency and cost columns",
      },
    },
    {
      name: "Exec",
      audience: "For the executive",
      claim:
        "The bank's headline numbers over a chosen window — readable by a role that can rule on nothing, so the dashboard never doubles as a control panel.",
      href: "/executive/",
      linkLabel: "See the executive view →",
      screenshot: {
        file: "exec-dashboard.png",
        alt: "Executive dashboard showing new customers, deposits, cards issued and payments stopped for the last 30 days",
      },
    },
  ],
};
