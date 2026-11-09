import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { AccountResponse, CardResponse } from "@/api/generated/types.gen";
import type { UseCardsResult } from "@/cards/useCards";
import { CardsPage } from "./CardsPage";

vi.mock("@/cards/useCards", () => ({ useCards: vi.fn() }));
vi.mock("@/accounts/useAccounts", () => ({ useAccounts: vi.fn() }));
vi.mock("@/auth/useUser", () => ({ useUser: vi.fn() }));

const { useCards } = await import("@/cards/useCards");
const { useAccounts } = await import("@/accounts/useAccounts");
const { useUser } = await import("@/auth/useUser");
const useCardsMock = useCards as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseCardsResult>
>;
const useAccountsMock = useAccounts as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useUserMock = useUser as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const DEBIT_CARD: CardResponse = {
  cardId: "33333333-3333-3333-3333-333333333333",
  accountId: "22222222-2222-2222-2222-222222222222",
  maskedPan: "************1234",
  variant: "DEBIT",
  status: "ACTIVE",
  expiry: "10/29",
};

const CURRENT_ACCOUNT: AccountResponse = {
  accountUid: "22222222-2222-2222-2222-222222222222",
  accountType: "CURRENT",
  defaultCategory: "44444444-4444-4444-4444-444444444444",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Everyday",
};

const CREDIT_ACCOUNT: AccountResponse = {
  ...CURRENT_ACCOUNT,
  accountUid: "55555555-5555-5555-5555-555555555555",
  accountType: "CREDIT_CARD",
  name: "Credit Card",
};

function renderPage() {
  return render(
    <MemoryRouter>
      <CardsPage />
    </MemoryRouter>,
  );
}

describe("CardsPage", () => {
  beforeEach(() => {
    useCardsMock.mockReset();
    useAccountsMock.mockReset();
    useUserMock.mockReset();
    useUserMock.mockReturnValue({
      user: {
        email: "ada@example.com",
        firstName: "Ada",
        lastName: "Lovelace",
      },
      status: "authenticated",
    });
    useAccountsMock.mockReturnValue({
      accounts: [CURRENT_ACCOUNT],
      isPending: false,
      isError: false,
      error: null,
    });
  });

  it("renders a skeleton while cards load", () => {
    useCardsMock.mockReturnValue({
      cards: null,
      isPending: true,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("cards-skeleton")).toBeInTheDocument();
  });

  it("renders a card tile linking to the card detail, with account name and status", () => {
    useCardsMock.mockReturnValue({
      cards: [DEBIT_CARD],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    const tile = screen.getByTestId("card-tile");
    expect(tile).toHaveAttribute("href", `/cards/${DEBIT_CARD.cardId}`);
    expect(screen.getByTestId("card-tile-label")).toHaveTextContent(
      "Debit card · Everyday",
    );
    expect(screen.getByTestId("card-tile-status")).toHaveTextContent("Active");
    expect(screen.getByTestId("card-art-name")).toHaveTextContent(
      "Ada Lovelace",
    );
  });

  it("offers the credit card application when the holder has no credit account", () => {
    useCardsMock.mockReturnValue({
      cards: [DEBIT_CARD],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("apply-credit-card-tile")).toHaveAttribute(
      "href",
      "/cards/apply",
    );
  });

  it("hides the application tile once a credit card account exists", () => {
    useAccountsMock.mockReturnValue({
      accounts: [CURRENT_ACCOUNT, CREDIT_ACCOUNT],
      isPending: false,
      isError: false,
      error: null,
    });
    useCardsMock.mockReturnValue({
      cards: [DEBIT_CARD],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.queryByTestId("apply-credit-card-tile")).toBeNull();
  });

  it("renders the error state when the list fails", () => {
    useCardsMock.mockReturnValue({
      cards: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });
    renderPage();
    expect(screen.getByTestId("cards-error")).toBeInTheDocument();
  });
});
