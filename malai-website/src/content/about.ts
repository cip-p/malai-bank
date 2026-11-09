export interface Principle {
  label: string;
  detail: string;
}

export interface About {
  eyebrow: string;
  heading: string;
  intro: string;
  lede: string;
  origin: {
    eyebrow: string;
    heading: string;
    paragraphs: string[];
  };
  principles: {
    eyebrow: string;
    heading: string;
    intro: string;
    items: Principle[];
    pullquote: string;
  };
}

export const about: About = {
  eyebrow: "About",
  heading: "About Malai.",
  intro:
    "Malai is a reference UK retail banking platform — built end-to-end on synthetic data, so prospective operators can read the architecture, walk the customer journey, and evaluate the commercial path before commissioning it.",
  lede: "If your team is weighing whether to commission a retail bank stack, and who to build it with, this page covers why Malai exists and how it's built.",
  origin: {
    eyebrow: "Why it exists",
    heading: "There was no honest reference.",
    paragraphs: [
      "A prospective operator evaluating a platform vendor has two usual options: a slide deck that claims production-grade architecture, or a demo behind a vendor's NDA. Neither lets the operator's engineers see how it's built. Neither shows the customer journey end-to-end. Neither names the commercial path before the contract.",
      "Malai exists to be that reference. It's a UK retail bank assembled honestly — a banking core with enforced module boundaries, a double-entry ledger with a sum-to-zero invariant, a hash-chained audit trail, a typed customer portal running the journey end-to-end, and infrastructure-as-code that stands the whole thing up on a cloud-native footprint. Same shape a real bank's platform team would inherit. Different data.",
      "It is not a bank. It holds no customer money, processes no real payments, and connects to no payment rails. The point isn't to be a bank — the point is to be the reference an operator can read, walk through, take apart, and then commission the people who built it to adapt or recreate for their own bank.",
    ],
  },
  principles: {
    eyebrow: "How we work",
    heading: "Three working principles.",
    intro:
      "Malai is built by platform engineers who would rather be judged on the work than on the pitch. These are the three commitments behind every page on this site.",
    items: [
      {
        label: "Read the build, not the deck.",
        detail:
          "Every claim on this site is either backed by something you can open — the running demo, the executed end-to-end flows — or marked as pending. Build-time fences keep module boundaries honest, and the typed API contract generates the customer portal's client code so the wire format and the UI cannot drift. There is no demonstration-purposes magic between what the site says and what the demo does.",
      },
      {
        label: "Synthetic data, by design.",
        detail:
          "Every customer, account holder, transaction, ledger entry, and audit event in Malai is generated. Sort codes sit in a Malai-reserved range; account numbers validate through the standard UK modulus check; payment rails are not wired in. The data shape matches a real UK retail bank. The data itself is reference fiction.",
      },
      {
        label: "Public, on principle.",
        detail:
          "The architecture, the security posture, and the lines Malai doesn't cross live on this site — before the walkthrough call. A prospective operator should be able to disqualify Malai (or shortlist it) without scheduling time with us first.",
      },
    ],
    pullquote:
      "Malai is a reference platform built on synthetic data. Its job is to be evaluated — read, walked through, taken apart, and either dismissed or taken further. Not to hold customer money.",
  },
};
