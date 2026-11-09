import { describe, expect, it } from "vitest";
import { render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { ForecastOccurrence } from "@/api/generated/types.gen";
import { ForecastOccurrenceRow } from "./ForecastOccurrenceRow";

function occurrence(
  overrides: Partial<ForecastOccurrence> = {},
): ForecastOccurrence {
  return {
    date: "2026-07-10",
    counterpartyName: "Landlord",
    amountMinorUnits: -85000,
    type: "STANDING_ORDER",
    instrumentId: "so-1",
    ...overrides,
  };
}

function renderRow(o: ForecastOccurrence) {
  return render(
    <MemoryRouter>
      <ul>
        <ForecastOccurrenceRow occurrence={o} currency="GBP" />
      </ul>
    </MemoryRouter>,
  );
}

describe("ForecastOccurrenceRow", () => {
  it("shows the compact date, counterparty name, and signed amount", () => {
    renderRow(occurrence());

    expect(screen.getByTestId("forecast-occurrence-date")).toHaveTextContent(
      "10 Jul",
    );
    expect(screen.getByTestId("forecast-occurrence-name")).toHaveTextContent(
      "Landlord",
    );
    expect(screen.getByTestId("forecast-occurrence-amount")).toHaveTextContent(
      "−£850.00",
    );
  });

  it("tags a standing order occurrence 'Standing order'", () => {
    renderRow(occurrence({ type: "STANDING_ORDER" }));

    expect(screen.getByTestId("forecast-occurrence-type")).toHaveTextContent(
      "Standing order",
    );
  });

  it("tags a direct debit occurrence 'Direct Debit'", () => {
    renderRow(occurrence({ type: "DIRECT_DEBIT" }));

    expect(screen.getByTestId("forecast-occurrence-type")).toHaveTextContent(
      "Direct Debit",
    );
  });

  it("links the row to the instrument via forecastOccurrenceHref", () => {
    renderRow(occurrence({ type: "DIRECT_DEBIT", instrumentId: "dd-7" }));

    const row = screen.getByTestId("forecast-occurrence-row");
    expect(within(row).getByRole("link")).toHaveAttribute(
      "href",
      "/move/direct-debits#dd-7",
    );
  });
});
