const WEBSITE_BASE_URL = "https://malaibank.com";

const LINKS = [
  { href: `${WEBSITE_BASE_URL}/privacy`, label: "Privacy" },
  { href: `${WEBSITE_BASE_URL}/terms`, label: "Terms" },
];

export function LegalLinks() {
  return (
    <p className="text-center text-xs text-muted-foreground">
      {LINKS.map((link, index) => (
        <span key={link.href}>
          {index > 0 ? <span aria-hidden="true"> · </span> : null}
          <a
            href={link.href}
            target="_blank"
            rel="noopener noreferrer"
            className="hover:underline focus-visible:underline pointer-coarse:py-3.75"
          >
            {link.label}
          </a>
        </span>
      ))}
    </p>
  );
}
