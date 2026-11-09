import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { BalanceForecastResponse } from "@/api/generated/types.gen";
import type { UseForecastResult } from "./useForecast";
import { ForecastCard } from "./ForecastCard";

vi.mock("./useForecast", () => ({
  useForecast: vi.fn(),
}));

const { useForecast } = await import("./useForecast");
const useForecastMock = useForecast as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseForecastResult>
>;

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";

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

function renderCard() {
  return render(
    <MemoryRouter>
      <ForecastCard accountUid={ACCOUNT_UID} />
    </MemoryRouter>,
  );
}

describe("ForecastCard", () => {
  beforeEach(() => {
    useForecastMock.mockReset();
  });

  it("shows a skeleton while pending", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    });

    renderCard();

    expect(screen.getByTestId("forecast-card-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.queryByTestId("forecast-card")).not.toBeInTheDocument();
  });

  it("shows a compact unavailable card on 503", () => {
    useForecastMock.mockReturnValue({
      forecast: null,
      isPending: false,
      isError: true,
      isUnavailable: true,
      error: null,
    });

    renderCard();

    const card = screen.getByTestId("forecast-card-unavailable");
    expect(card).toHaveTextContent("Balance forecast");
    expect(card).toHaveTextContent(/unavailable just now/i);
    expect(screen.queryByTestId("forecast-balance")).not.toBeInTheDocument();
  });

  it("renders the populated positive state with title, month-end label, and amount", () => {
    useForecastMock.mockReturnValue(ready());

    renderCard();

    const card = screen.getByTestId("forecast-card");
    expect(card).toHaveTextContent("Balance forecast");
    expect(card).toHaveTextContent("for 31 July");
    expect(screen.getByTestId("forecast-balance")).toHaveTextContent("£700.00");
    expect(card).toHaveAttribute("href", `/accounts/${ACCOUNT_UID}/forecast`);
    expect(card).toHaveTextContent(/view forecast/i);
  });

  it("shows no warning treatment on a positive forecast", () => {
    useForecastMock.mockReturnValue(ready());

    renderCard();

    expect(
      screen.queryByTestId("forecast-warning-icon"),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId("forecast-balance")).not.toHaveClass(
      "text-destructive",
    );
  });

  it("shows the destructive warning treatment and TriangleAlert on a negative forecast", () => {
    useForecastMock.mockReturnValue(
      ready({ forecastBalanceMinorUnits: -15000 }),
    );

    renderCard();

    expect(screen.getByTestId("forecast-warning-icon")).toBeInTheDocument();
    expect(screen.getByTestId("forecast-balance")).toHaveTextContent(
      "−£150.00",
    );
    const amount = screen.getByTestId("forecast-balance");
    expect(amount.className).toContain("text-destructive");
  });

  it("shows the empty state equal to the available balance with a no-payments note and no warning", () => {
    useForecastMock.mockReturnValue(
      ready({
        completed: [],
        expected: [],
        completedTotalMinorUnits: 0,
        expectedTotalMinorUnits: 0,
        availableBalanceMinorUnits: 120000,
        forecastBalanceMinorUnits: 120000,
      }),
    );

    renderCard();

    expect(screen.getByTestId("forecast-balance")).toHaveTextContent(
      "£1,200.00",
    );
    expect(screen.getByTestId("forecast-no-payments")).toHaveTextContent(
      /no payments due this month/i,
    );
    expect(
      screen.queryByTestId("forecast-warning-icon"),
    ).not.toBeInTheDocument();
  });

  it("does not show the no-payments note when there are occurrences", () => {
    useForecastMock.mockReturnValue(ready());

    renderCard();

    expect(
      screen.queryByTestId("forecast-no-payments"),
    ).not.toBeInTheDocument();
  });

  it("links the whole card to the forecast screen", () => {
    useForecastMock.mockReturnValue(ready());

    renderCard();

    const link = screen.getByTestId("forecast-card");
    expect(within(link).getByText(/view forecast/i)).toBeInTheDocument();
    expect(link).toHaveAttribute("href", `/accounts/${ACCOUNT_UID}/forecast`);
  });
});
