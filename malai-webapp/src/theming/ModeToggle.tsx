import { Check, Monitor, Moon, Sun, type LucideIcon } from "lucide-react";
import { useTheme as useNextTheme } from "next-themes";
import { cn } from "@/lib/utils";

type Props = {
  className?: string;
};

type ModeOption = {
  id: "light" | "dark" | "system";
  label: string;
  Icon: LucideIcon;
};

const OPTIONS: ModeOption[] = [
  { id: "light", label: "Light", Icon: Sun },
  { id: "dark", label: "Dark", Icon: Moon },
  { id: "system", label: "System", Icon: Monitor },
];

const itemBase =
  "flex w-full items-center gap-3 rounded-lg border border-border bg-background px-4 py-3 text-left text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors";
const itemActive = "border-ring";

export function ModeToggle({ className }: Props) {
  const { theme, setTheme } = useNextTheme();
  const active = theme ?? "system";

  return (
    <div
      role="radiogroup"
      aria-label="Mode"
      data-testid="mode-toggle"
      className={cn("flex flex-col gap-2", className)}
    >
      {OPTIONS.map(({ id, label, Icon }) => {
        const isActive = active === id;
        return (
          <button
            key={id}
            type="button"
            role="radio"
            aria-checked={isActive}
            data-testid={`mode-toggle-item-${id}`}
            data-active={isActive ? "true" : undefined}
            onClick={() => setTheme(id)}
            className={cn(itemBase, isActive && itemActive)}
          >
            <Icon aria-hidden="true" className="size-5 text-muted-foreground" />
            <span className="flex-1">{label}</span>
            {isActive ? (
              <Check
                aria-hidden="true"
                data-testid={`mode-toggle-check-${id}`}
                className="size-5 text-primary"
              />
            ) : null}
          </button>
        );
      })}
    </div>
  );
}
