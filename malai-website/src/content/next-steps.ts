import { hero } from "./hero";
import type { Cta } from "./hero";

export interface NextStep {
  title: string;
  body: string;
  cta: Cta;
  external: boolean;
}

export interface NextSteps {
  eyebrow: string;
  heading: string;
  intro: string;
  steps: [NextStep, NextStep];
}

export const nextSteps: NextSteps = {
  eyebrow: "Next steps",
  heading: "Use the demo first. Then talk to us.",
  intro:
    "Everything this site claims is checkable in the running demo, so the demo is the right first step. A conversation is the second.",
  steps: [
    {
      title: "Register and explore",
      body: "Self-register in the demo and you're granted every role, so you can walk the Banking, Ops and Exec surfaces behind one sign-in, on synthetic data.",
      cta: hero.primaryCta,
      external: true,
    },
    {
      title: "Then talk to us",
      body: "Once you've formed a view, email the team. We'll answer the questions the demo can't, or set up a thirty-minute walkthrough over video call.",
      cta: hero.secondaryCta,
      external: false,
    },
  ],
};
