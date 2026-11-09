import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { UseSpendingBreakdownResult } from "@/accounts/useSpendingBreakdown";
import { SpendingPage } from "./SpendingPage";

vi.mock("@/accounts/useSpendingBreakdown", () => ({
  useSpendingBreakdown: vi.fn(),
}));
vi.mock("@/accounts/SpendingBars", () => ({
  SpendingBars: ({ totalMinorUnits }: { totalMinorUnits: number }) => (
    <div data-testid="spending-breakdown">total:{totalMinorUnits}</div>
  ),
}));

const { useSpendingBreakdown } =
  await import("@/accounts/useSpendingBreakdown");
const useSpendingBreakdownMock = useSpendingBreakdown as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseSpendingBreakdownResult>
>;

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const NOW = new Date(2026, 6, 8);

function ready(): UseSpendingBreakdownResult {
  return {
    breakdown: {
      month: "2026-07",
      currency: "GBP",
      totalMinorUnits: 15000,
      categories: [
        { category: "GROCERIES", totalMinorUnits: 15000, transactionCount: 3 },
      ],
    },
    isPending: false,
    isError: false,
    isUnavailable: false,
    error: null,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/accounts/${ACCOUNT_UID}/spending`]}>
      <Routes>
        <Route
          path="/accounts/:accountUid/spending"
          element={<SpendingPage now={NOW} />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("SpendingPage", () => {
  beforeEach(() => {
    useSpendingBreakdownMock.mockReset();
  });

  it("renders the breakdown when there is spending", () => {
    useSpendingBreakdownMock.mockReturnValue(ready());
    renderPage();
    expect(screen.getByTestId("spending-breakdown")).toHaveTextContent(
      "total:15000",
    );
  });

  it("shows the empty state when nothing was spent", () => {
    useSpendingBreakdownMock.mockReturnValue({
      breakdown: {
        month: "2026-07",
        currency: "GBP",
        totalMinorUnits: 0,
        categories: [],
      },
      isPending: false,
      isError: false,
      isUnavailable: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("spending-empty")).toBeInTheDocument();
  });

  it("shows a distinct unavailable state on a 503", () => {
    useSpendingBreakdownMock.mockReturnValue({
      breakdown: null,
      isPending: false,
      isError: true,
      isUnavailable: true,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("spending-unavailable")).toBeInTheDocument();
    expect(screen.queryByTestId("spending-error")).not.toBeInTheDocument();
  });

  it("shows a skeleton while loading", () => {
    useSpendingBreakdownMock.mockReturnValue({
      breakdown: null,
      isPending: true,
      isError: false,
      isUnavailable: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("spending-skeleton")).toBeInTheDocument();
  });

  it("disables next at the current month and requests the previous month on click", async () => {
    useSpendingBreakdownMock.mockReturnValue(ready());
    renderPage();

    expect(screen.getByTestId("spending-month-label")).toHaveTextContent(
      "July 2026",
    );
    expect(screen.getByTestId("spending-next-month")).toBeDisabled();

    await userEvent.click(screen.getByTestId("spending-prev-month"));

    expect(screen.getByTestId("spending-month-label")).toHaveTextContent(
      "June 2026",
    );
    expect(screen.getByTestId("spending-next-month")).toBeEnabled();
    const lastCall = useSpendingBreakdownMock.mock.calls.at(-1);
    expect(lastCall?.[1]).toBe("2026-06");
  });
});
