import { type ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import { DropdownMenu } from "radix-ui";
import {
  ArrowLeftRight,
  ChevronDown,
  CreditCard,
  House,
  type LucideIcon,
  UserRound,
} from "lucide-react";
import type { UserResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { SkipToMainContent } from "@/components/SkipToMainContent";
import {
  FeatureHintBanner,
  useFeatureHint,
} from "@/components/FeatureHintBanner";
import { useSignout } from "@/auth/useSignout";
import { isSurfaceOffered } from "@/surfaces/surfaceOffering";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { ThemeSwitcher } from "@/theming/ThemeSwitcher";
import { useActivePageTitle } from "@/lib/usePageTitle";
import { cn } from "@/lib/utils";
import { menuItemClassName } from "@/components/menuItem";

export function displayNameOf(user: UserResponse): string {
  const first = user.firstName?.trim();
  const last = user.lastName.trim();
  if (first && last) return `${first} ${last}`;
  if (first) return first;
  if (last) return last;
  return user.email;
}

export function firstNameOf(user: UserResponse): string {
  const first = user.firstName?.trim();
  if (first) return first;
  return displayNameOf(user);
}

type NavSurface = "desktop" | "mobile" | "both";

type NavItem = {
  to: string;
  label: string;
  desktopTestId?: string;
  mobileTestId?: string;
  surfaces: NavSurface;
  isActive: (pathname: string) => boolean;
  Icon: LucideIcon;
};

const NAV_ITEMS: NavItem[] = [
  {
    to: "/",
    label: "Accounts",
    desktopTestId: "primary-nav-link-accounts",
    mobileTestId: "bottom-tab-accounts",
    surfaces: "both",
    isActive: (p) => p === "/" || p.startsWith("/accounts"),
    Icon: House,
  },
  {
    to: "/cards",
    label: "Cards",
    desktopTestId: "primary-nav-link-cards",
    mobileTestId: "bottom-tab-cards",
    surfaces: "both",
    isActive: (p) => p.startsWith("/cards"),
    Icon: CreditCard,
  },
  {
    to: "/move",
    label: "Move money",
    desktopTestId: "primary-nav-link-move",
    mobileTestId: "bottom-tab-move",
    surfaces: "both",
    isActive: (p) => p.startsWith("/move"),
    Icon: ArrowLeftRight,
  },
  {
    to: "/profile",
    label: "Profile",
    mobileTestId: "bottom-tab-profile",
    surfaces: "mobile",
    isActive: (p) => p.startsWith("/profile"),
    Icon: UserRound,
  },
];

type Props = {
  user: UserResponse;
  children: ReactNode;
};

export function AppShell({ user, children }: Props) {
  const { visible: hintVisible, dismiss: dismissHint } = useFeatureHint();

  return (
    <div className="flex min-h-full flex-col">
      <SkipToMainContent />
      {hintVisible ? (
        <FeatureHintBanner roles={user.roles} onDismiss={dismissHint} />
      ) : null}
      <header
        data-testid="app-header"
        className="hidden border-b border-border bg-background md:block"
      >
        <div className="mx-auto flex h-12 w-full max-w-5xl items-center justify-between px-6">
          <Link
            to="/"
            aria-label="malai — home"
            data-testid="app-header-wordmark"
            className="inline-flex items-center text-primary outline-none focus-visible:underline"
          >
            <HeaderWordmark className="h-6 w-auto" />
          </Link>
          <div className="flex items-center gap-2">
            <ThemeSwitcher />
            <UserMenu user={user} highlighted={hintVisible} />
          </div>
        </div>
      </header>
      <DesktopNav />
      <MobileTopBar />
      <main
        id="main"
        tabIndex={-1}
        className="flex-1 outline-none pb-[calc(env(safe-area-inset-bottom)+72px)] md:pb-0"
      >
        {children}
      </main>
      <BottomTabBar />
    </div>
  );
}

function MobileTopBar() {
  return (
    <header
      data-testid="app-mobile-top-bar"
      className="sticky top-0 z-30 border-b border-border bg-background pt-[env(safe-area-inset-top)] pr-[env(safe-area-inset-right)] pl-[env(safe-area-inset-left)] md:hidden"
    >
      <div className="mx-auto flex h-12 w-full max-w-5xl items-center justify-between gap-3 px-4">
        <Link
          to="/"
          aria-label="malai — home"
          data-testid="mobile-top-bar-wordmark"
          className="inline-flex items-center text-primary outline-none focus-visible:underline"
        >
          <HeaderWordmark className="h-6 w-auto" />
        </Link>
        <MobileTopBarTitle />
      </div>
    </header>
  );
}

function MobileTopBarTitle() {
  const title = useActivePageTitle();
  return (
    <span
      data-testid="mobile-top-bar-title"
      className="truncate text-sm font-medium text-foreground"
    >
      {title}
    </span>
  );
}

function UserMenu({
  user,
  highlighted,
}: {
  user: UserResponse;
  highlighted: boolean;
}) {
  const { signout, isPending } = useSignout();

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="user-menu-trigger"
          data-hinted={highlighted ? "true" : undefined}
          className={cn(
            highlighted &&
              "border-accent-foreground/20 bg-accent text-accent-foreground hover:bg-accent/80",
          )}
        >
          <span data-testid="display-name" className="max-w-64 truncate">
            {displayNameOf(user)}
          </span>
          <ChevronDown aria-hidden="true" className="size-4" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          className="z-50 min-w-44 rounded-lg border border-border bg-popover text-popover-foreground p-1 shadow-md motion-safe:data-[state=open]:animate-in motion-safe:data-[state=open]:fade-in-0 motion-safe:ease-arrive motion-safe:data-[state=closed]:animate-out motion-safe:data-[state=closed]:fade-out-0"
        >
          <DropdownMenu.Item asChild className={menuItemClassName}>
            <Link to="/settings" data-testid="user-menu-item-settings">
              Settings
            </Link>
          </DropdownMenu.Item>
          <DropdownMenu.Item asChild className={menuItemClassName}>
            <Link to="/sandbox" data-testid="user-menu-item-sandbox">
              Sandbox
            </Link>
          </DropdownMenu.Item>
          {isSurfaceOffered(user.roles, "ops") ? (
            <DropdownMenu.Item asChild className={menuItemClassName}>
              <Link to="/ops" data-testid="user-menu-item-ops">
                Ops
              </Link>
            </DropdownMenu.Item>
          ) : null}
          {isSurfaceOffered(user.roles, "exec") ? (
            <DropdownMenu.Item asChild className={menuItemClassName}>
              <Link to="/exec" data-testid="user-menu-item-exec">
                Exec
              </Link>
            </DropdownMenu.Item>
          ) : null}
          <DropdownMenu.Item
            disabled={isPending}
            onSelect={() => signout()}
            data-testid="user-menu-item-signout"
            className={menuItemClassName}
          >
            Sign out
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

const desktopLinkBase =
  "inline-flex h-10 items-center border-b-2 border-transparent px-3 text-sm pointer-coarse:h-11 font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors";
const desktopLinkActive = "border-ring text-foreground";

function DesktopNav() {
  const { pathname } = useLocation();
  const items = NAV_ITEMS.filter(
    (item) => item.surfaces === "desktop" || item.surfaces === "both",
  );
  return (
    <nav
      aria-label="Primary"
      data-testid="primary-nav-desktop"
      className="hidden border-b border-border bg-background md:block"
    >
      <div className="mx-auto w-full max-w-5xl px-6">
        <ul className="flex items-center gap-2">
          {items.map((item) => {
            const active = item.isActive(pathname);
            return (
              <li key={item.to}>
                <Link
                  to={item.to}
                  data-testid={item.desktopTestId}
                  aria-current={active ? "page" : undefined}
                  className={cn(desktopLinkBase, active && desktopLinkActive)}
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

const tabBase =
  "flex flex-1 flex-col items-center justify-start gap-1 px-1 pt-2 pb-1 text-center text-xs leading-tight font-medium text-muted-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors";
const tabActive = "font-semibold text-primary";
const tabIconWrapBase =
  "inline-flex h-7 min-w-12 items-center justify-center rounded-full";
const tabIconWrapActive = "bg-accent";

function BottomTabBar() {
  const { pathname } = useLocation();
  const items = NAV_ITEMS.filter(
    (item) => item.surfaces === "mobile" || item.surfaces === "both",
  );
  return (
    <nav
      aria-label="Primary"
      data-testid="bottom-tab-bar"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-background pr-[env(safe-area-inset-right)] pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] md:hidden"
    >
      <ul className="mx-auto flex w-full max-w-5xl items-stretch">
        {items.map((item) => {
          const active = item.isActive(pathname);
          const Icon = item.Icon;
          return (
            <li key={item.to} className="flex flex-1">
              <Link
                to={item.to}
                data-testid={item.mobileTestId}
                aria-current={active ? "page" : undefined}
                className={cn(tabBase, active && tabActive)}
              >
                <span
                  className={cn(tabIconWrapBase, active && tabIconWrapActive)}
                >
                  <Icon
                    aria-hidden="true"
                    className="size-5"
                    fill={active ? "currentColor" : "none"}
                    strokeWidth={active ? 0 : 2}
                  />
                </span>
                <span>{item.label}</span>
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
