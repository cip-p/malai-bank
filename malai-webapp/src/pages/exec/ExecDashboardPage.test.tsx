import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import type { ExecMetricsResponse } from "@/api/generated/types.gen";
import { ExecDashboardPage } from "./ExecDashboardPage";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1ExecMetrics: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getMetricsMock = sdk.getApiV1ExecMetrics as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

function makeResponse(
  overrides: Partial<ExecMetricsResponse> = {},
): ExecMetricsResponse {
  return {
    window: "TODAY",
    windowStart: "2026-07-21",
    bankingDay: "2026-07-21",
    asOf: "2026-07-21T13:30:00Z",
    newCustomers: { status: "AVAILABLE", count: 12 },
    deposits: {
      status: "AVAILABLE",
      amountMinorUnits: 1_000_000,
      currency: "GBP",
    },
    cardsIssued: { status: "AVAILABLE", count: 5 },
    paymentsStopped: {
      status: "AVAILABLE",
      amountMinorUnits: 50_000,
      currency: "GBP",
    },
    ...overrides,
  };
}

function renderPage(ui: ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

function tileByLabel(label: string): HTMLElement {
  const heading = screen.getByRole("heading", { level: 2, name: label });
  const tile = heading.closest('[data-testid="metric-tile"]');
  if (!(tile instanceof HTMLElement)) {
    throw new Error(`No metric-tile for ${label}`);
  }
  return tile;
}

describe("ExecDashboardPage", () => {
  beforeEach(() => {
    getMetricsMock.mockReset();
  });

  it("renders an h1 'Executive dashboard' focused on mount", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Executive dashboard");
    expect(heading).toHaveFocus();
  });

  it("lays out the four named metric tiles in order, retiring 'fraud prevented'", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);

    const tiles = screen.getAllByTestId("metric-tile");
    expect(
      tiles.map(
        (tile) => within(tile).getByRole("heading", { level: 2 }).textContent,
      ),
    ).toEqual([
      "New customers",
      "Deposits",
      "Cards issued",
      "Payments stopped",
    ]);

    expect(screen.queryByText(/fraud prevented/i)).toBeNull();
    expect(screen.queryByText(/metrics api pending/i)).toBeNull();
  });

  it("renders each metric's real value with the currency the contract supplies", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);

    await waitFor(() =>
      expect(
        within(tileByLabel("New customers")).getByTestId("metric-tile-value"),
      ).toHaveTextContent("12"),
    );
    expect(tileByLabel("Cards issued")).toHaveTextContent("5");
    expect(tileByLabel("Deposits")).toHaveTextContent("£10,000.00");
    expect(tileByLabel("Payments stopped")).toHaveTextContent("£500.00");
  });

  it("draws an honest em-dash for an UNAVAILABLE metric, and no numeral", async () => {
    getMetricsMock.mockResolvedValue({
      data: makeResponse({ deposits: { status: "UNAVAILABLE" } }),
    });
    renderPage(<ExecDashboardPage />);

    const value = await waitFor(() => {
      const v = within(tileByLabel("Deposits")).getByTestId(
        "metric-tile-value",
      );
      expect(v).toHaveTextContent("—");
      return v;
    });
    expect(value).toHaveTextContent("Unavailable");
    expect(value).not.toHaveTextContent(/[0-9]/);
    expect(value).not.toHaveAttribute("aria-busy", "true");
  });

  it("keeps loading visually and semantically distinct from unavailable", () => {
    getMetricsMock.mockImplementation(() => new Promise(() => {}));
    const { container } = renderPage(<ExecDashboardPage />);

    const value = within(tileByLabel("New customers")).getByTestId(
      "metric-tile-value",
    );
    expect(value).toHaveAttribute("aria-busy", "true");
    expect(value).toHaveTextContent("Loading");
    expect(value).not.toHaveTextContent("—");
    expect(value).not.toHaveTextContent("Unavailable");
    expect(container.querySelector('[class*="animate-pulse"]')).not.toBeNull();
  });

  it("states its own freshness by rendering asOf in the London day", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);

    await waitFor(() =>
      expect(screen.getByTestId("exec-as-of")).toHaveTextContent(
        "21 Jul 2026, 14:30",
      ),
    );
  });

  it("offers the four reporting windows with Today pressed by default", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);

    expect(screen.getByTestId("exec-window-today")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    for (const testId of [
      "exec-window-7d",
      "exec-window-30d",
      "exec-window-12m",
    ]) {
      expect(screen.getByTestId(testId)).toHaveAttribute(
        "aria-pressed",
        "false",
      );
    }
    expect(screen.getByTestId("exec-window-copy")).toHaveTextContent(
      "The day’s headline numbers across the bank.",
    );
    const firstArg = getMetricsMock.mock.calls[0][0] as {
      query?: { window?: string };
    };
    expect(firstArg.query).toMatchObject({ window: "TODAY" });
  });

  it("re-asks for a wider window when one is chosen, and says which days it covers", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);
    await waitFor(() =>
      expect(tileByLabel("New customers")).toHaveTextContent("12"),
    );

    getMetricsMock.mockResolvedValue({
      data: makeResponse({
        window: "LAST_7_DAYS",
        windowStart: "2026-07-15",
        newCustomers: { status: "AVAILABLE", count: 84 },
      }),
    });
    await userEvent.click(screen.getByTestId("exec-window-7d"));

    await waitFor(() =>
      expect(tileByLabel("New customers")).toHaveTextContent("84"),
    );
    const lastArg = getMetricsMock.mock.calls.at(-1)?.[0] as {
      query?: { window?: string };
    };
    expect(lastArg.query).toMatchObject({ window: "LAST_7_DAYS" });
    expect(screen.getByTestId("exec-window-7d")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByTestId("exec-window-copy")).toHaveTextContent(
      "The last 7 days’ headline numbers across the bank.",
    );
    expect(screen.getByTestId("exec-as-of")).toHaveTextContent(
      "Since 15 Jul 2026 · As of 21 Jul 2026, 14:30",
    );
  });

  it("keeps the freshness line free of a Since while reporting the day itself", async () => {
    getMetricsMock.mockResolvedValue({ data: makeResponse() });
    renderPage(<ExecDashboardPage />);

    await waitFor(() => expect(screen.getByTestId("exec-as-of")).toBeVisible());
    expect(screen.getByTestId("exec-as-of")).not.toHaveTextContent("Since");
  });

  it("refetches on demand when the refresh control is pressed", async () => {
    getMetricsMock.mockResolvedValue({
      data: makeResponse({ newCustomers: { status: "AVAILABLE", count: 12 } }),
    });
    renderPage(<ExecDashboardPage />);

    await waitFor(() =>
      expect(tileByLabel("New customers")).toHaveTextContent("12"),
    );
    expect(getMetricsMock).toHaveBeenCalledTimes(1);

    getMetricsMock.mockResolvedValue({
      data: makeResponse({ newCustomers: { status: "AVAILABLE", count: 13 } }),
    });
    await userEvent.click(screen.getByRole("button", { name: /refresh/i }));

    await waitFor(() =>
      expect(tileByLabel("New customers")).toHaveTextContent("13"),
    );
    expect(getMetricsMock).toHaveBeenCalledTimes(2);
  });
});
