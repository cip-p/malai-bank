import { hero } from "./hero";
import { site } from "./site";
import type { Cta } from "./hero";

export interface NavAnchor {
  label: string;
  href: string;
}

export interface Nav {
  brand: string;
  anchors: NavAnchor[];
  primaryCta: Cta;
}

export const nav: Nav = {
  brand: site.brand,
  anchors: [
    { label: "Operations", href: "/operations/" },
    { label: "Executive", href: "/executive/" },
    { label: "Architecture", href: "/architecture/" },
    { label: "Security", href: "/security/" },
    { label: "About", href: "/about/" },
    { label: "What this isn't", href: "/what-this-isnt/" },
    { label: "Talk to us", href: `mailto:${site.contactEmail}` },
  ],
  primaryCta: hero.primaryCta,
};
