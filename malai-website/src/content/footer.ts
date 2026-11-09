import { site } from "./site";

export interface FooterLink {
  href: string;
  label: string;
  external?: boolean;
}

export interface FooterColumn {
  title: string;
  links: FooterLink[];
}

export interface Footer {
  description: string;
  columns: FooterColumn[];
  disclosure: string;
}

export const footer: Footer = {
  description:
    "A reference UK retail banking platform — built so prospective bank operators can evaluate the architecture before commissioning it.",
  columns: [
    {
      title: "Platform",
      links: [
        { href: "/operations/", label: "Operations" },
        { href: "/executive/", label: "Executive" },
        { href: "/architecture/", label: "Architecture" },
        { href: "/security/", label: "Security" },
        {
          href: "/end-to-end-flows/",
          label: "End-to-end flows",
          external: true,
        },
      ],
    },
    {
      title: "Project",
      links: [
        { href: "/about/", label: "About" },
        { href: `mailto:${site.contactEmail}`, label: "Talk to us" },
      ],
    },
    {
      title: "Legal",
      links: [
        { href: "/what-this-isnt/", label: "What this isn't" },
        { href: "/privacy/", label: "Privacy" },
        { href: "/terms/", label: "Terms" },
      ],
    },
  ],
  disclosure:
    "Malai is a demonstration platform. Not a bank, does not accept deposits, and is not authorised by the FCA or PRA. All accounts and transactions shown are simulated.",
};
