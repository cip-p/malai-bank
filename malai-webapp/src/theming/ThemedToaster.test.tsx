import { act, render, waitFor } from "@testing-library/react";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import { toast } from "sonner";
import { describe, expect, it } from "vitest";
import { ThemedToaster } from "./ThemedToaster";

async function toasterIn(mode: "light" | "dark") {
  const { container } = render(
    <NextThemesProvider
      attribute="class"
      defaultTheme={mode}
      enableSystem={false}
      storageKey={`themed-toaster-${mode}`}
    >
      <ThemedToaster />
    </NextThemesProvider>,
  );
  act(() => {
    toast("Saved");
  });
  await waitFor(() =>
    expect(container.querySelector("[data-sonner-toaster]")).not.toBeNull(),
  );
  return container.querySelector("[data-sonner-toaster]");
}

describe("ThemedToaster", () => {
  it("renders toasts in dark mode when the app is in dark mode", async () => {
    expect(await toasterIn("dark")).toHaveAttribute(
      "data-sonner-theme",
      "dark",
    );
  });

  it("renders toasts in light mode when the app is in light mode", async () => {
    expect(await toasterIn("light")).toHaveAttribute(
      "data-sonner-theme",
      "light",
    );
  });
});
