import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@/theming/useTheme";
import { CONTACT_EMAIL, ServicePausedPage } from "./ServicePausedPage";

function renderPage(
  props: Partial<Parameters<typeof ServicePausedPage>[0]> = {},
) {
  const onRetry = props.onRetry ?? vi.fn();
  render(
    <ThemeProvider>
      <ServicePausedPage
        rechecking={props.rechecking ?? false}
        onRetry={onRetry}
      />
    </ThemeProvider>,
  );
  return { onRetry };
}

beforeEach(() => {
  window.localStorage.clear();
});

afterEach(() => {
  window.localStorage.clear();
});

describe("ServicePausedPage", () => {
  it("says the application is paused rather than showing a failure", () => {
    renderPage();
    expect(screen.getByTestId("service-paused-heading")).toHaveTextContent(
      "The application is paused at the moment",
    );
  });

  it("focuses the heading on mount", () => {
    renderPage();
    expect(screen.getByTestId("service-paused-heading")).toHaveFocus();
  });

  it("shows the bank mark at hero size", () => {
    renderPage();
    const wordmark = screen.getByTestId("header-wordmark");
    expect(wordmark).toHaveAttribute("aria-label", "malai");
    expect(wordmark.getAttribute("class")).toContain("h-14");
  });

  it("offers the contact address as a mailto link", () => {
    renderPage();
    const link = screen.getByTestId("service-paused-email");
    expect(link).toHaveTextContent(CONTACT_EMAIL);
    expect(link.getAttribute("href")).toContain(`mailto:${CONTACT_EMAIL}`);
  });

  it("names the brand the visitor is actually looking at, not always malai", () => {
    window.localStorage.setItem("malai-theme", "halcyon");
    renderPage();

    expect(screen.getByTestId("service-paused-body")).toHaveTextContent(
      "Halcyon\u2019s services are switched off",
    );
    expect(screen.getByTestId("service-paused-status")).toHaveTextContent(
      "when Halcyon is back",
    );
  });

  it("lets the visitor ask for another check", async () => {
    const { onRetry } = renderPage();
    await userEvent.click(screen.getByTestId("service-paused-retry"));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("reports that a check is under way and refuses a second one meanwhile", () => {
    renderPage({ rechecking: true });
    const retry = screen.getByTestId("service-paused-retry");
    expect(retry).toBeDisabled();
    expect(screen.getByTestId("service-paused-status")).toHaveTextContent(
      "Checking",
    );
  });
});
