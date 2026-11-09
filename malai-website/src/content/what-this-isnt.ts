import { footer } from "./footer";

export interface AntiClaim {
  label: string;
  detail: string;
}

export interface WhatThisIsnt {
  eyebrow: string;
  heading: string;
  intro: string;
  override: string;
  claims: {
    eyebrow: string;
    heading: string;
    intro: string;
    items: AntiClaim[];
  };
  disclaimer: string;
}

export const whatThisIsnt: WhatThisIsnt = {
  eyebrow: "Disclosure",
  heading: "What Malai isn't.",
  intro:
    "Malai looks like a UK retail bank because banks are what it references. It isn't one. This page names the four lines it does not cross — directly, without ornamentation — so prospective operators, regulators, and visitors can read the boundary on its own page rather than in fine print.",
  override:
    "If anything on the rest of the site implies otherwise, this page overrides it.",
  claims: {
    eyebrow: "Four lines we don't cross",
    heading: "The boundary, in plain English.",
    intro:
      "Each claim below is binding across every page on this site and every screen in the demo webapp. If you spot a contradiction, that's a defect — please tell us through the Talk to us link.",
    items: [
      {
        label: "Malai is not a bank.",
        detail:
          "It holds no customer deposits, issues no real accounts, and operates no banking licence in any jurisdiction. The interfaces resemble a UK retail bank because that is the system being referenced — not the system being run.",
      },
      {
        label: "Malai is not FCA-authorised.",
        detail:
          "Malai is not authorised by the Financial Conduct Authority or the Prudential Regulation Authority. It has no FRN, no permissions, and no entry on the Financial Services Register. Deposits with Malai are not protected by the FSCS, because there are no deposits.",
      },
      {
        label: "All data is synthetic.",
        detail:
          "Every customer, account holder, balance, transaction, ledger entry, and audit event you can see is generated. Names, addresses, sort codes, and account numbers are drawn from reserved or fictional ranges. No real person's banking data is, has been, or will be loaded into Malai.",
      },
      {
        label: "No real payment rails.",
        detail:
          "Malai is not connected to Faster Payments, BACS, CHAPS, SEPA, SWIFT, card schemes, or any other settlement network: there are no real payment rails behind any screen. Transfers visible inside the demo move synthetic balances between synthetic accounts. No money leaves, enters, or moves through any real-world system.",
      },
    ],
  },
  disclaimer: footer.disclosure,
};
