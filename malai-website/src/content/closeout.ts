import { hero } from "./hero";
import type { Cta } from "./hero";

export interface CloseOut {
  closingLine: string;
  primaryCta: Cta;
  secondaryCta: Cta;
}

export const closeOut: CloseOut = {
  closingLine:
    "The fastest read on whether this fits your bank is to use the demo, then read how it's built.",
  primaryCta: hero.primaryCta,
  secondaryCta: hero.secondaryCta,
};
