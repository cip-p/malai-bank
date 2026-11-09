import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { SkipToMainContent } from "@/components/SkipToMainContent";
import { MalaiWordmark } from "@/theming/wordmarks";
import { usePinnedDefaultTheme } from "@/theming/usePinnedDefaultTheme";
import { StaffUserMenu } from "@/surfaces/StaffUserMenu";

type Props = {
  user: UserResponse;
  children: ReactNode;
};

export function ExecShell({ user, children }: Props) {
  usePinnedDefaultTheme();

  return (
    <div className="flex min-h-full flex-col">
      <SkipToMainContent />
      <header
        data-testid="exec-header"
        className="border-b border-border bg-background pr-[env(safe-area-inset-right)] pl-[env(safe-area-inset-left)]"
      >
        <div className="mx-auto flex h-14 w-full max-w-6xl items-center justify-between gap-4 px-6">
          <div className="flex items-center gap-2">
            <Link
              to="/exec"
              aria-label="malai Exec — dashboard"
              data-testid="exec-header-wordmark"
              className="inline-flex items-center gap-2 text-primary outline-none focus-visible:underline"
            >
              <MalaiWordmark className="h-6 w-auto" />
            </Link>
            <span className="rounded-sm border border-border px-1.5 py-0.5 text-xs font-medium tracking-wider text-muted-foreground uppercase">
              Exec
            </span>
          </div>
          <StaffUserMenu user={user} currentSurface="exec" />
        </div>
      </header>
      <main id="main" tabIndex={-1} className="flex-1 outline-none">
        {children}
      </main>
    </div>
  );
}
