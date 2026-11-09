import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { UseAccountsResult } from "@/accounts/useAccounts";
import type { UseCardsResult } from "@/cards/useCards";
import type { UseCardMerchantsResult } from "@/sandbox/useCardMerchants";

vi.mock("@/accounts/useAccounts", () => ({ useAccounts: vi.fn() }));
vi.mock("@/cards/useCards", () => ({ useCards: vi.fn() }));
vi.mock("@/sandbox/useCardMerchants", () => ({ useCardMerchants: vi.fn() }));
vi.mock("@/sandbox/useCardPurchaseMutation", () => ({
  useCardPurchaseMutation: () => ({
    submit: vi.fn(),
    isPending: false,
    outcome: null,
  }),
}));
vi.mock("@/sandbox/useCardPresentmentMutation", () => ({
  useCardPresentmentMutation: () => ({ presentNow: vi.fn(), isPending: false }),
}));
vi.mock("@/sandbox/useCardRefundMutation", () => ({
  useCardRefundMutation: () => ({ refund: vi.fn(), isPending: false }),
}));

const useAccountsMock = (await import("@/accounts/useAccounts"))
  .useAccounts as unknown as ReturnType<typeof vi.fn<() => UseAccountsResult>>;
const useCardsMock = (await import("@/cards/useCards"))
  .useCards as unknown as ReturnType<typeof vi.fn<() => UseCardsResult>>;
const useCardMerchantsMock = (await import("@/sandbox/useCardMerchants"))
  .useCardMerchants as unknown as ReturnType<
  typeof vi.fn<() => UseCardMerchantsResult>
>;

import { SandboxCardsPage } from "./SandboxCardsPage";

describe("SandboxCardsPage", () => {
  beforeEach(() => {
    useAccountsMock.mockReturnValue({
      accounts: [],
      isPending: false,
      isError: false,
      error: null,
    });
    useCardMerchantsMock.mockReturnValue({
      merchants: [],
      isPending: false,
      isError: false,
      error: null,
    });
  });

  it("says the cards could not be loaded rather than claiming there are none", () => {
    useCardsMock.mockReturnValue({
      cards: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });

    render(
      <MemoryRouter>
        <SandboxCardsPage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("sandbox-cards-error")).toHaveTextContent(
      "We couldn’t load your cards",
    );
    expect(screen.queryByTestId("sandbox-cards-none")).not.toBeInTheDocument();
  });

  it("still reports an empty estate honestly when the load succeeds with no cards", () => {
    useCardsMock.mockReturnValue({
      cards: [],
      isPending: false,
      isError: false,
      error: null,
    });

    render(
      <MemoryRouter>
        <SandboxCardsPage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("sandbox-cards-none")).toBeInTheDocument();
    expect(screen.queryByTestId("sandbox-cards-error")).not.toBeInTheDocument();
  });
});
