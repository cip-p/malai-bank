import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

type Props = {
  testId: string;
  title: string;
  body?: string;
  action?: ReactNode;
  role?: "alert" | "status";
  className?: string;
};

export function StatusPanel({
  testId,
  title,
  body,
  action,
  role,
  className,
}: Props) {
  return (
    <div
      data-testid={testId}
      role={role}
      className={cn(
        "rounded-2xl border border-dashed border-border px-6 py-8 text-center",
        className,
      )}
    >
      <p className="text-base font-medium">{title}</p>
      {body ? (
        <p className="mt-1 text-sm text-muted-foreground">{body}</p>
      ) : null}
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}
