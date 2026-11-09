import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { HeaderWordmark } from "@/theming/HeaderWordmark";
import { useFocusOnMount } from "@/lib/useFocusOnMount";
import { cn } from "@/lib/utils";

type AuthShellProps = {
  title: string;
  description?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
};

export function AuthShell({
  title,
  description,
  children,
  footer,
  className,
}: AuthShellProps) {
  const headingRef = useFocusOnMount<HTMLHeadingElement>();

  return (
    <main
      id="main"
      className="flex min-h-full justify-center px-6 py-12 sm:py-16"
    >
      <div className={cn("w-full max-w-md space-y-8", className)}>
        <div className="flex justify-center">
          <Link
            to="/login"
            aria-label="malai — sign in"
            data-testid="auth-shell-wordmark"
            className="inline-flex items-center text-primary outline-none focus-visible:underline"
          >
            <HeaderWordmark className="h-7 w-auto" />
          </Link>
        </div>

        <header className="space-y-2 text-center">
          <h1
            ref={headingRef}
            tabIndex={-1}
            className="text-3xl font-semibold tracking-tight outline-none"
          >
            {title}
          </h1>
          {description ? (
            <p className="text-base text-muted-foreground">{description}</p>
          ) : null}
        </header>

        <div className="space-y-6">{children}</div>

        {footer ? (
          <div className="border-t border-border pt-6">{footer}</div>
        ) : null}
      </div>
    </main>
  );
}
