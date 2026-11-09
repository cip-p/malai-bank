import { Link } from "react-router-dom";
import { DropdownMenu } from "radix-ui";
import { Check, ChevronDown } from "lucide-react";
import { useTheme as useNextTheme } from "next-themes";
import type { UserResponse } from "@/api/generated/types.gen";
import { Button } from "@/components/ui/button";
import { displayNameOf } from "@/components/AppShell";
import { useSignout } from "@/auth/useSignout";
import { offeredSurfaces, type SurfaceId } from "./surfaceOffering";
import { menuItemClassName } from "@/components/menuItem";

const MODES = [
  { id: "light", label: "Light" },
  { id: "dark", label: "Dark" },
  { id: "system", label: "System" },
] as const;

type Props = {
  user: UserResponse;
  currentSurface: SurfaceId;
};

export function StaffUserMenu({ user, currentSurface }: Props) {
  const { signout, isPending } = useSignout();
  const { theme: mode, setTheme: setMode } = useNextTheme();
  const otherSurfaces = offeredSurfaces(user.roles).filter(
    (surface) => surface.id !== currentSurface,
  );

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid={`${currentSurface}-user-menu-trigger`}
          className="min-w-0 shrink max-w-[45vw] md:max-w-none"
        >
          <span
            data-testid={`${currentSurface}-display-name`}
            className="truncate"
          >
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
          {otherSurfaces.map((surface) => (
            <DropdownMenu.Item
              key={surface.id}
              asChild
              className={menuItemClassName}
            >
              <Link
                to={surface.path}
                data-testid={`${currentSurface}-user-menu-item-${surface.id}`}
              >
                {surface.label}
              </Link>
            </DropdownMenu.Item>
          ))}
          <DropdownMenu.Separator className="my-1 h-px bg-border" />
          <DropdownMenu.Label className="px-2.5 pt-1.5 pb-1 text-xs font-medium text-muted-foreground">
            Mode
          </DropdownMenu.Label>
          <DropdownMenu.RadioGroup
            value={mode ?? "system"}
            onValueChange={setMode}
          >
            {MODES.map(({ id, label }) => (
              <DropdownMenu.RadioItem
                key={id}
                value={id}
                data-testid={`${currentSurface}-mode-item-${id}`}
                className={menuItemClassName}
              >
                <span className="flex-1">{label}</span>
                <DropdownMenu.ItemIndicator>
                  <Check aria-hidden="true" className="size-4" />
                </DropdownMenu.ItemIndicator>
              </DropdownMenu.RadioItem>
            ))}
          </DropdownMenu.RadioGroup>
          <DropdownMenu.Separator className="my-1 h-px bg-border" />
          <DropdownMenu.Item
            disabled={isPending}
            onSelect={() => signout()}
            data-testid={`${currentSurface}-user-menu-item-signout`}
            className={menuItemClassName}
          >
            Sign out
          </DropdownMenu.Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
