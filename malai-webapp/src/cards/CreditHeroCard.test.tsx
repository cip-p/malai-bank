import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { UseCardsResult } from "@/cards/useCards";
import type { UseCreditSummaryResult } from "@/cards/useCreditSummary";
import { useBalanceVisibility } from "@/accounts/useBalanceVisibility";
import { CreditHeroCard } from "./CreditHeroCard";

vi.mock("@/accounts/useAccount", () => ({ useAccount: vi.fn() }));
vi.mock("./useCreditSummary", () => ({ useCreditSummary: vi.fn() }));
vi.mock("./useCards", () => ({ useCards: vi.fn() }));

const { useAccount } = await import("@/accounts/useAccount");
const { useCreditSummary } = await import("./useCreditSummary");
const { useCards } = await import("./useCards");

const useAccountMock = useAccount as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useCreditSummaryMock = useCreditSummary as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseCreditSummaryResult>
>;
const useCardsMock = useCards as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseCardsResult>
>;

const ACCOUNT_UID = "55555555-5555-5555-5555-555555555555";
const CARD_ID = "44444444-4444-4444-4444-444444444444";

function renderHero() {
  return render(
    <MemoryRouter>
      <CreditHeroCard accountUid={ACCOUNT_UID} />
    </MemoryRouter>,
  );
}

describe("CreditHeroCard", () => {
  beforeEach(() => {
    useAccountMock.mockReset();
    useCreditSummaryMock.mockReset();
    useCardsMock.mockReset();
    useBalanceVisibility.setState({ hidden: false });

    useAccountMock.mockReturnValue({
      account: {
        accountUid: ACCOUNT_UID,
        accountType: "CREDIT_CARD",
        defaultCategory: "44444444-4444-4444-4444-444444444444",
        currency: "GBP",
        createdAt: "2024-01-01T00:00:00Z",
        name: "Credit Card",
      },
      status: "ready",
    });
    useCreditSummaryMock.mockReturnValue({
      summary: {
        currency: "GBP",
        creditLimitMinorUnits: 250000,
        owedMinorUnits: 40000,
        availableCreditMinorUnits: 210000,
        statementBalanceMinorUnits: 32500,
        minimumPaymentMinorUnits: 813,
        dueDate: "2026-08-21",
      },
      isPending: false,
      isError: false,
      error: null,
    });
    useCardsMock.mockReturnValue({
      cards: [
        {
          cardId: CARD_ID,
          accountId: ACCOUNT_UID,
          maskedPan: "************9876",
          variant: "CREDIT",
          status: "ACTIVE",
          expiry: "11/30",
        },
      ],
      isPending: false,
      isError: false,
      error: null,
    });
  });

  it("shows the owed balance as negative with available credit and the limit", () => {
    renderHero();
    expect(screen.getByTestId("credit-owed")).toHaveTextContent("−£400.00");
    expect(screen.queryByText("You owe")).toBeNull();
    expect(screen.getByTestId("credit-available")).toHaveTextContent(
      "£2,100.00",
    );
    expect(screen.getByTestId("credit-limit")).toHaveTextContent("£2,500.00");
  });

  it("shows the statement figures with the due date", () => {
    renderHero();
    expect(screen.getByTestId("credit-statement-balance")).toHaveTextContent(
      "£325.00",
    );
    expect(screen.getByTestId("credit-minimum-payment")).toHaveTextContent(
      "£8.13",
    );
    expect(screen.getByTestId("credit-due-date")).toHaveTextContent(
      "21 August",
    );
  });

  it("links to the Pay card flow and the card", () => {
    renderHero();
    expect(screen.getByTestId("pay-card-cta")).toHaveAttribute(
      "href",
      `/accounts/${ACCOUNT_UID}/pay-card`,
    );
    expect(screen.getByTestId("view-card-cta")).toHaveAttribute(
      "href",
      `/cards/${CARD_ID}`,
    );
  });

  it("omits the statement block when the composed figures are unavailable", () => {
    useCreditSummaryMock.mockReturnValue({
      summary: {
        currency: "GBP",
        creditLimitMinorUnits: 250000,
        owedMinorUnits: 40000,
        availableCreditMinorUnits: 210000,
      },
      isPending: false,
      isError: false,
      error: null,
    });
    renderHero();
    expect(screen.queryByTestId("credit-statement-summary")).toBeNull();
    expect(screen.getByTestId("credit-owed")).toHaveTextContent("−£400.00");
  });

  it("shows a positive balance when the card is in credit", () => {
    useCreditSummaryMock.mockReturnValue({
      summary: {
        currency: "GBP",
        creditLimitMinorUnits: 250000,
        owedMinorUnits: -15000,
        availableCreditMinorUnits: 265000,
        statementBalanceMinorUnits: -15000,
        minimumPaymentMinorUnits: 0,
        dueDate: "2026-08-21",
      },
      isPending: false,
      isError: false,
      error: null,
    });
    renderHero();
    expect(screen.getByTestId("credit-owed")).toHaveTextContent(/^£150\.00$/);
    expect(screen.getByTestId("credit-available")).toHaveTextContent(
      "£2,650.00",
    );
  });

  it("masks the owed amount when hidden", () => {
    useBalanceVisibility.setState({ hidden: true });
    renderHero();
    expect(screen.getByTestId("credit-owed")).toHaveTextContent("£·····");
  });
});
