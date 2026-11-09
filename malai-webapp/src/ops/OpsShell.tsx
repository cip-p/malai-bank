import { type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { SkipToMainContent } from "@/components/SkipToMainContent";
import { MalaiWordmark } from "@/theming/wordmarks";
import { usePinnedDefaultTheme } from "@/theming/usePinnedDefaultTheme";
import { StaffUserMenu } from "@/surfaces/StaffUserMenu";
import { cn } from "@/lib/utils";

type NavItem = {
  to: string;
  label: string;
  testId: string;
  isActive: (pathname: string) => boolean;
};

const NAV_ITEMS: NavItem[] = [
  {
    to: "/ops",
    label: "Worklist",
    testId: "ops-nav-link-worklist",
    isActive: (p) => p === "/ops" || p.startsWith("/ops/decisions"),
  },
  {
    to: "/ops/cases",
    label: "Cases",
    testId: "ops-nav-link-cases",
    isActive: (p) => p === "/ops/cases" || p.startsWith("/ops/cases/"),
  },
  {
    to: "/ops/chain",
    label: "Chain",
    testId: "ops-nav-link-chain",
    isActive: (p) => p === "/ops/chain",
  },
];

type Props = {
  user: UserResponse;
  children: ReactNode;
};

export function OpsShell({ user, children }: Props) {
  usePinnedDefaultTheme();

  return (
    <div className="flex min-h-full flex-col">
      <SkipToMainContent />
      <header
        data-testid="ops-header"
        className="border-b border-border bg-background pr-[env(safe-area-inset-right)] pl-[env(safe-area-inset-left)]"
      >
        <div className="mx-auto flex h-12 w-full max-w-6xl items-center justify-between gap-4 px-6">
          <div className="flex items-center gap-2">
            <Link
              to="/ops"
              aria-label="malai Ops — worklist"
              data-testid="ops-header-wordmark"
              className="inline-flex items-center gap-2 text-primary outline-none focus-visible:underline"
            >
              <MalaiWordmark className="h-6 w-auto" />
            </Link>
            <span className="rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium tracking-wider text-muted-foreground uppercase">
              Ops
            </span>
          </div>
          <StaffUserMenu user={user} currentSurface="ops" />
        </div>
      </header>
      <OpsNav />
      <main id="main" tabIndex={-1} className="flex-1 outline-none">
        {children}
      </main>
    </div>
  );
}

const navLinkBase =
  "inline-flex h-10 items-center border-b-2 border-transparent px-3 text-sm pointer-coarse:h-11 font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors";
const navLinkActive = "border-ring text-foreground";

function OpsNav() {
  const { pathname } = useLocation();
  return (
    <nav
      aria-label="Ops"
      data-testid="ops-nav"
      className="border-b border-border bg-background"
    >
      <div className="mx-auto w-full max-w-6xl px-6">
        <ul className="flex items-center gap-2">
          {NAV_ITEMS.map((item) => {
            const active = item.isActive(pathname);
            return (
              <li key={item.to}>
                <Link
                  to={item.to}
                  data-testid={item.testId}
                  aria-current={active ? "page" : undefined}
                  className={cn(navLinkBase, active && navLinkActive)}
                >
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </div>
    </nav>
  );
}
