import { Link, Navigate, useOutletContext } from "react-router-dom";
import {
  ChevronRight,
  FlaskConical,
  LogOut,
  Settings as SettingsIcon,
  TrendingUp,
  Wrench,
} from "lucide-react";
import type { UserResponse } from "@/api/generated/types.gen";
import { displayNameOf } from "@/components/AppShell";
import { useSignout } from "@/auth/useSignout";
import { isSurfaceOffered } from "@/surfaces/surfaceOffering";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { useIsDesktop } from "@/lib/useIsDesktop";
import { usePageTitle } from "@/lib/usePageTitle";
import { InlineThemeSwitcher } from "@/theming/InlineThemeSwitcher";
import { ModeToggle } from "@/theming/ModeToggle";

type AuthenticatedOutletContext = { user: UserResponse };

export function ProfilePage() {
  const isDesktop = useIsDesktop();
  if (isDesktop) {
    return <Navigate to="/settings" replace />;
  }
  return <ProfileMobile />;
}

function ProfileMobile() {
  usePageTitle("Profile");
  const { user } = useOutletContext<AuthenticatedOutletContext>();
  const headingRef = useFocusOnMount<HTMLHeadingElement>();
  const { signout, isPending } = useSignout();

  return (
    <div className="mx-auto w-full max-w-md space-y-8 p-6">
      <header className="space-y-1">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="text-3xl font-semibold tracking-tight outline-none"
        >
          Profile
        </h1>
      </header>

      <section
        aria-labelledby="profile-identity-heading"
        data-testid="profile-identity"
        className="rounded-xl border border-border bg-card p-5 shadow-xs"
      >
        <h2 id="profile-identity-heading" className="sr-only">
          Account
        </h2>
        <p
          data-testid="profile-display-name"
          className="text-lg font-medium text-foreground"
        >
          {displayNameOf(user)}
        </p>
        <p
          data-testid="profile-email"
          className="mt-0.5 text-sm text-muted-foreground"
        >
          {user.email}
        </p>
      </section>

      <AppearanceSection />

      <section aria-labelledby="profile-actions-heading" className="space-y-2">
        <h2 id="profile-actions-heading" className="sr-only">
          Actions
        </h2>
        <Link
          to="/settings"
          data-testid="profile-settings-link"
          className="flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 shadow-xs text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors"
        >
          <SettingsIcon
            aria-hidden="true"
            className="size-5 text-muted-foreground"
          />
          <span className="flex-1">Account settings</span>
          <ChevronRight
            aria-hidden="true"
            className="size-5 text-muted-foreground"
          />
        </Link>
        <Link
          to="/sandbox"
          data-testid="profile-sandbox-link"
          className="flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 shadow-xs text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors"
        >
          <FlaskConical
            aria-hidden="true"
            className="size-5 text-muted-foreground"
          />
          <span className="flex-1">Sandbox</span>
          <ChevronRight
            aria-hidden="true"
            className="size-5 text-muted-foreground"
          />
        </Link>
        {isSurfaceOffered(user.roles, "ops") ? (
          <Link
            to="/ops"
            data-testid="profile-ops-link"
            className="flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 shadow-xs text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors"
          >
            <Wrench
              aria-hidden="true"
              className="size-5 text-muted-foreground"
            />
            <span className="flex-1">Ops</span>
            <ChevronRight
              aria-hidden="true"
              className="size-5 text-muted-foreground"
            />
          </Link>
        ) : null}
        {isSurfaceOffered(user.roles, "exec") ? (
          <Link
            to="/exec"
            data-testid="profile-exec-link"
            className="flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 shadow-xs text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors"
          >
            <TrendingUp
              aria-hidden="true"
              className="size-5 text-muted-foreground"
            />
            <span className="flex-1">Exec</span>
            <ChevronRight
              aria-hidden="true"
              className="size-5 text-muted-foreground"
            />
          </Link>
        ) : null}
        <button
          type="button"
          disabled={isPending}
          onClick={() => signout()}
          data-testid="profile-signout"
          className="flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 shadow-xs text-sm font-medium text-destructive outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors disabled:opacity-50"
        >
          <LogOut aria-hidden="true" className="size-5" />
          <span className="flex-1 text-left">Sign out</span>
        </button>
      </section>
    </div>
  );
}

export function AppearanceSection() {
  return (
    <section
      aria-labelledby="appearance-heading"
      data-testid="appearance-section"
      className="space-y-6"
    >
      <h2 id="appearance-heading" className="text-lg font-semibold">
        Appearance
      </h2>
      <div className="space-y-3">
        <h3 className="text-sm font-medium text-muted-foreground">Theme</h3>
        <InlineThemeSwitcher />
      </div>
      <div className="space-y-3">
        <h3 className="text-sm font-medium text-muted-foreground">Mode</h3>
        <ModeToggle />
      </div>
    </section>
  );
}
