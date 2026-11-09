import { DropdownMenu } from "radix-ui";
import { Check, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { menuItemClassName } from "@/components/menuItem";
import { useTheme, type ThemeId } from "./useTheme";

type Props = {
  className?: string;
};

export function ThemeSwitcher({ className }: Props) {
  const { theme, setTheme, themes } = useTheme();
  const active = themes.find((t) => t.id === theme) ?? themes[0];

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="theme-switcher-trigger"
          className={cn("hidden md:inline-flex", className)}
        >
          <span
            aria-hidden="true"
            data-testid="theme-switcher-active-swatch"
            data-theme={active.id}
            className="size-4 rounded-full border border-border bg-primary"
          />
          <span data-testid="theme-switcher-active-label">{active.label}</span>
          <ChevronDown aria-hidden="true" className="size-4" />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={6}
          data-testid="theme-switcher-menu"
          className="z-50 min-w-44 rounded-lg border border-border bg-background p-1 shadow-md motion-safe:data-[state=open]:animate-in motion-safe:data-[state=open]:fade-in-0 motion-safe:ease-arrive motion-safe:data-[state=closed]:animate-out motion-safe:data-[state=closed]:fade-out-0"
        >
          <DropdownMenu.Label className="px-2.5 pt-1.5 pb-1 text-xs font-medium text-muted-foreground">
            Theme
          </DropdownMenu.Label>
          <DropdownMenu.RadioGroup
            value={theme}
            onValueChange={(next) => setTheme(next as ThemeId)}
          >
            {themes.map((t) => (
              <ThemeMenuItem
                key={t.id}
                id={t.id}
                label={t.label}
                isActive={t.id === theme}
              />
            ))}
          </DropdownMenu.RadioGroup>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}

type ThemeMenuItemProps = {
  id: ThemeId;
  label: string;
  isActive: boolean;
};

function ThemeMenuItem({ id, label, isActive }: ThemeMenuItemProps) {
  return (
    <DropdownMenu.RadioItem
      value={id}
      data-testid={`theme-switcher-item-${id}`}
      data-active={isActive ? "true" : undefined}
      className={menuItemClassName}
    >
      <span
        aria-hidden="true"
        data-theme={id}
        data-testid={`theme-switcher-swatch-${id}`}
        className="size-4 rounded-full border border-border bg-primary"
      />
      <span className="flex-1">{label}</span>
      {isActive ? (
        <Check
          aria-hidden="true"
          data-testid={`theme-switcher-check-${id}`}
          className="size-4"
        />
      ) : null}
    </DropdownMenu.RadioItem>
  );
}
