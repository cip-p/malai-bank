export interface Cta {
  label: string;
  href: string;
}

export interface Hero {
  eyebrow?: string;
  headline: string;
  subhead: string;
  primaryCta: Cta;
  secondaryCta: Cta;
}

export const hero: Hero = {
  headline: "A UK retail bank you can run — without being one.",
  subhead:
    "Malai is a complete reference platform for prospective bank operators — a banking core, a customer app, an operations back office for AI-assisted decisions, an executive dashboard, and infrastructure-as-code. It isn't an authorised bank and holds no money, but everything is wired together so you can evaluate the architecture, walk the customer journey, and commission the people who built it to adapt it for your bank.",
  primaryCta: {
    label: "Try the demo →",
    href: "https://app.malaibank.com/register",
  },
  secondaryCta: {
    label: "Talk to us",
    href: "mailto:info@malaibank.com",
  },
};
