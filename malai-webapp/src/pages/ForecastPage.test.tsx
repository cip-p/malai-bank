import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { BalanceForecastResponse } from "@/api/generated/types.gen";
import type { UseForecastResult } from "@/accounts/useForecast";
import { ForecastPage } from "./ForecastPage";

vi.mock("@/accounts/useForecast", () => ({
  useForecast: vi.fn(),
}));

const { useForecast } = await import("@/accounts/useForecast");
const useForecastMock = useForecast as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseForecastResult>
>;

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const NOW = new Date(2026, 6, 9);

function baseForecast(): BalanceForecastResponse {
  return {
    accountUid: ACCOUNT_UID,
    month: "2026-07",
    windowStart: "2026-07-01",
    windowEnd: "2026-07-31",
    asOfDate: "2026-07-09",
    currency: "GBP",
    availableBalanceMinorUnits: 120000,
    completedTotalMinorUnits: -20000,
    expectedTotalMinorUnits: -50000,
    forecastBalanceMinorUnits: 70000,
    completed: [
      {
        date: "2026-07-05",
        counterpartyName: "Landlord",
        amountMinorUnits: -20000,
        type: "STANDING_ORDER",
        instrumentId: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      },
    ],
    expected: [
      {
        date: "2026-07-15",
        counterpartyName: "Energy Co",
        amountMinorUnits: -50000,
        type: "DIRECT_DEBIT",
        instrumentId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
      },
    ],
  };
}

function ready(
  overrides: Partial<BalanceForecastResponse> = {},
): UseForecastResult {
  return {
    forecast: { ...baseForecast(), ...overrides },
    isPending: false,
    isError: false,
    isUnavailable: false,
    error: null,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/accounts/${ACCOUNT_UID}/forecast`]}>
      <Routes>
        <Route
          path="/accounts/:accountUid/forecast"
          element={<ForecastPage now={NOW} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ForecastPage", () => {
  beforeEach(() => {
    useForecastMock.mockReset();
  });

  it("leads with a Balance forecast hero showing the amount, month-end label, and window range", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(
      "Balance forecast",
    );
    const hero = screen.getByTestId("forecast-hero");
    expect(hero).toHaveTextContent("for 31 July");
    expect(hero).toHaveTextContent("1 July to 31 July");
    expect(screen.getByTestId("forecast-hero-balance")).toHaveTextContent(
      "£700.00",
    );
  });

  it("shows no warning treatment on a positive forecast", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    expect(
      screen.queryByTestId("forecast-hero-warning-icon"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("forecast-hero-balance")).not.toHaveClass(
      "text-destructive",
    );
  });

  it("shows the destructive warning treatment and TriangleAlert on a negative forecast", () => {
    useForecastMock.mockReturnValue(
      ready({ forecastBalanceMinorUnits: -15000 }),
    );

    renderPage();

    expect(
      screen.getByTestId("forecast-hero-warning-icon"),
    ).toBeInTheDocument();
    const amount = screen.getByTestId("forecast-hero-balance");
    expect(amount).toHaveTextContent("−£150.00");
    expect(amount.className).toContain("text-destructive");
  });

  it("shows the breakdown figures that reconcile to the forecast headline", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    expect(screen.getByTestId("forecast-breakdown")).toBeInTheDocument();
    expect(screen.getByTestId("forecast-available-balance")).toHaveTextContent(
      "£1,200.00",
    );
    expect(screen.getByTestId("forecast-expected-total")).toHaveTextContent(
      "−£500.00",
    );
    expect(screen.getByTestId("forecast-breakdown-total")).toHaveTextContent(
      "£700.00",
    );
  });

  it("shows a skeleton while loading and no hero", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    });

    renderPage();

    expect(screen.getByTestId("forecast-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.queryByTestId("forecast-hero")).not.toBeInTheDocument();
  });

  it("shows a fail-closed unavailable state on a 503 with no partial render", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: true,
      error: null,
    });

    renderPage();

    expect(screen.getByTestId("forecast-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("forecast-hero")).not.toBeInTheDocument();
    expect(screen.queryByTestId("forecast-breakdown")).not.toBeInTheDocument();
  });

  it("shows a generic error state on a non-503 error with no partial render", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: false,
      error: new Error("boom"),
    });

    renderPage();

    expect(screen.getByTestId("forecast-error")).toBeInTheDocument();
    expect(screen.queryByTestId("forecast-hero")).not.toBeInTheDocument();
  });

  it("links back to the account detail screen", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    expect(screen.getByRole("link", { name: /account/i })).toHaveAttribute(
      "href",
      `/accounts/${ACCOUNT_UID}`,
    );
  });

  it("renders the Expected section open by default with a count in its summary", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    const expected = screen.getByTestId("forecast-expected");
    expect(expected.tagName).toBe("DETAILS");
    expect(expected).toHaveAttribute("open");
    expect(screen.getByTestId("forecast-expected-summary")).toHaveTextContent(
      "Expected (1)",
    );
  });

  it("renders the Completed section collapsed by default with a count in its summary", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    const completed = screen.getByTestId("forecast-completed");
    expect(completed.tagName).toBe("DETAILS");
    expect(completed).not.toHaveAttribute("open");
    expect(screen.getByTestId("forecast-completed-summary")).toHaveTextContent(
      "Completed (1)",
    );
  });

  it("renders each expected payment's date, counterparty, amount, type tag, and deep link", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    const expected = screen.getByTestId("forecast-expected");
    const row = within(expected).getByTestId("forecast-occurrence-row");
    expect(
      within(row).getByTestId("forecast-occurrence-date"),
    ).toHaveTextContent("15 Jul");
    expect(
      within(row).getByTestId("forecast-occurrence-name"),
    ).toHaveTextContent("Energy Co");
    expect(
      within(row).getByTestId("forecast-occurrence-amount"),
    ).toHaveTextContent("−£500.00");
    expect(
      within(row).getByTestId("forecast-occurrence-type"),
    ).toHaveTextContent("Direct Debit");
    expect(within(row).getByRole("link")).toHaveAttribute(
      "href",
      "/move/direct-debits#bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    );
  });

  it("renders completed payments in the Completed section deep-linked to their instrument", () => {
    useForecastMock.mockReturnValue(ready());

    renderPage();

    const completed = screen.getByTestId("forecast-completed");
    const row = within(completed).getByTestId("forecast-occurrence-row");
    expect(
      within(row).getByTestId("forecast-occurrence-name"),
    ).toHaveTextContent("Landlord");
    expect(within(row).getByRole("link")).toHaveAttribute(
      "href",
      "/move/standing-orders#aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    );
  });

  it("keeps expected payments in the backend's date-ascending order", () => {
    useForecastMock.mockReturnValue(
      ready({
        expected: [
          {
            date: "2026-07-10",
            counterpartyName: "Gym",
            amountMinorUnits: -3000,
            type: "STANDING_ORDER",
            instrumentId: "gym-1",
          },
          {
            date: "2026-07-15",
            counterpartyName: "Energy Co",
            amountMinorUnits: -50000,
            type: "DIRECT_DEBIT",
            instrumentId: "energy-1",
          },
          {
            date: "2026-07-28",
            counterpartyName: "Broadband",
            amountMinorUnits: -4000,
            type: "DIRECT_DEBIT",
            instrumentId: "broadband-1",
          },
        ],
      }),
    );

    renderPage();

    const expected = screen.getByTestId("forecast-expected");
    const dates = within(expected)
      .getAllByTestId("forecast-occurrence-date")
      .map((node) => node.textContent);
    expect(dates).toEqual(["10 Jul", "15 Jul", "28 Jul"]);
  });

  it("renders one row per occurrence for an instrument that pays more than once", () => {
    useForecastMock.mockReturnValue(
      ready({
        expected: [
          {
            date: "2026-07-07",
            counterpartyName: "Gym",
            amountMinorUnits: -3000,
            type: "STANDING_ORDER",
            instrumentId: "gym-1",
          },
          {
            date: "2026-07-21",
            counterpartyName: "Gym",
            amountMinorUnits: -3000,
            type: "STANDING_ORDER",
            instrumentId: "gym-1",
          },
        ],
      }),
    );

    renderPage();

    const expected = screen.getByTestId("forecast-expected");
    const rows = within(expected).getAllByTestId("forecast-occurrence-row");
    expect(rows).toHaveLength(2);
    for (const row of rows) {
      expect(within(row).getByRole("link")).toHaveAttribute(
        "href",
        "/move/standing-orders#gym-1",
      );
    }
    expect(screen.getByTestId("forecast-expected-summary")).toHaveTextContent(
      "Expected (2)",
    );
  });

  it("shows explanatory copy instead of a blank Expected section when nothing is expected", () => {
    useForecastMock.mockReturnValue(ready({ expected: [] }));

    renderPage();

    const expected = screen.getByTestId("forecast-expected");
    expect(screen.getByTestId("forecast-expected-summary")).toHaveTextContent(
      "Expected (0)",
    );
    expect(
      within(expected).queryByTestId("forecast-occurrence-row"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("forecast-expected-empty")).toHaveTextContent(
      /nothing expected/i,
    );
  });

  it("shows explanatory copy instead of a blank Completed section when nothing has completed", () => {
    useForecastMock.mockReturnValue(ready({ completed: [] }));

    renderPage();

    const completed = screen.getByTestId("forecast-completed");
    expect(screen.getByTestId("forecast-completed-summary")).toHaveTextContent(
      "Completed (0)",
    );
    expect(
      within(completed).queryByTestId("forecast-occurrence-row"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("forecast-completed-empty")).toHaveTextContent(
      /nothing completed/i,
    );
  });

  it("does not render the timeline in the unavailable state", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: true,
      error: null,
    });

    renderPage();

    expect(screen.queryByTestId("forecast-expected")).not.toBeInTheDocument();
    expect(screen.queryByTestId("forecast-completed")).not.toBeInTheDocument();
  });
});
