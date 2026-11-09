import { cn } from "@/lib/utils";

type Props = {
  label: string;
  className?: string;
};

export function InitialAvatar({ label, className }: Props) {
  const initial = firstInitial(label);
  return (
    <span
      data-testid="initial-avatar"
      aria-hidden="true"
      className={cn(
        "flex h-10 w-10 flex-none items-center justify-center rounded-full bg-accent text-sm font-semibold text-accent-foreground",
        className,
      )}
    >
      {initial}
    </span>
  );
}

function firstInitial(label: string): string {
  const trimmed = label.trim();
  if (trimmed.length === 0) return "?";
  return trimmed.charAt(0).toUpperCase();
}
