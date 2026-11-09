import { Check } from "lucide-react";
import { cn } from "@/lib/utils";
import { useTheme, type ThemeId } from "./useTheme";

type Props = {
  className?: string;
};

const itemBase =
  "flex w-full items-center gap-3 rounded-lg border border-border bg-background px-4 py-3 text-left text-sm font-medium text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring/50 hover:bg-muted motion-safe:transition-colors";
const itemActive = "border-ring";

export function InlineThemeSwitcher({ className }: Props) {
  const { theme, setTheme, themes } = useTheme();

  return (
    <div
      role="radiogroup"
      aria-label="Theme"
      data-testid="inline-theme-switcher"
      className={cn("flex flex-col gap-2", className)}
    >
      {themes.map((t) => {
        const isActive = t.id === theme;
        return (
          <InlineThemeItem
            key={t.id}
            id={t.id}
            label={t.label}
            isActive={isActive}
            onSelect={() => setTheme(t.id)}
          />
        );
      })}
    </div>
  );
}

type ItemProps = {
  id: ThemeId;
  label: string;
  isActive: boolean;
  onSelect: () => void;
};

function InlineThemeItem({ id, label, isActive, onSelect }: ItemProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={isActive}
      data-testid={`inline-theme-item-${id}`}
      data-active={isActive ? "true" : undefined}
      onClick={onSelect}
      className={cn(itemBase, isActive && itemActive)}
    >
      <span
        aria-hidden="true"
        data-theme={id}
        data-testid={`inline-theme-swatch-${id}`}
        className="size-6 rounded-full border border-border bg-primary"
      />
      <span className="flex-1">{label}</span>
      {isActive ? (
        <Check
          aria-hidden="true"
          data-testid={`inline-theme-check-${id}`}
          className="size-5 text-primary"
        />
      ) : null}
    </button>
  );
}
