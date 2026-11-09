export interface SiteMeta {
  brand: string;
  contactEmail: string;
  demoSignupUrl: string;
  title: string;
  description: string;
  ogImage: string;
  ogImageAlt: string;
  ogImageWidth: number;
  ogImageHeight: number;
  locale: string;
  language: string;
}

export const site: SiteMeta = {
  brand: "Malai",
  contactEmail: "info@malaibank.com",
  demoSignupUrl: "https://app.malaibank.com/register",
  title: "Malai — a UK retail bank you can run — without being one.",
  description:
    "Malai is a complete reference platform for prospective bank operators — a banking core, a customer app, an operations back office for AI-assisted decisions, an executive dashboard, and infrastructure-as-code. It isn't an authorised bank and holds no money, but everything is wired together so you can evaluate the architecture, walk the customer journey, and commission the people who built it to adapt it for your bank.",
  ogImage: "/og-image.png",
  ogImageAlt:
    "Malai — a modern UK retail bank, end to end. A working reference platform.",
  ogImageWidth: 1200,
  ogImageHeight: 630,
  locale: "en_GB",
  language: "en-GB",
};
