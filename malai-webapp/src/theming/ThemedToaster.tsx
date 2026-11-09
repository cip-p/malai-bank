import type { CSSProperties } from "react";
import { Toaster } from "sonner";
import { useTheme as useNextTheme } from "next-themes";

const toasterStyle = {
  fontFamily: "var(--font-sans)",
  "--normal-bg": "var(--popover)",
  "--normal-border": "var(--border)",
  "--normal-text": "var(--popover-foreground)",
  "--border-radius": "var(--radius)",
} as CSSProperties;

export function ThemedToaster() {
  const { resolvedTheme } = useNextTheme();
  const theme = resolvedTheme === "dark" ? "dark" : "light";

  return (
    <Toaster
      theme={theme}
      position="top-right"
      style={toasterStyle}
      toastOptions={{
        classNames: {
          toast: "text-sm! shadow-lg!",
          description: "text-muted-foreground!",
          success: "[&_[data-icon]]:text-positive",
          error: "[&_[data-icon]]:text-destructive",
        },
      }}
      mobileOffset={{
        bottom: "calc(env(safe-area-inset-bottom) + 72px)",
      }}
    />
  );
}
