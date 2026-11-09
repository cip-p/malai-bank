import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type {
  CardReplacementResponse,
  CardResponse,
} from "@/api/generated/types.gen";
import type { UseCardsResult } from "@/cards/useCards";
import type { UseCardRevealResult } from "@/cards/useCardReveal";
import type {
  UseReplaceCardOptions,
  UseReplaceCardResult,
} from "@/cards/useReplaceCard";
import { CardDetailPage } from "./CardDetailPage";

vi.mock("@/cards/useCards", () => ({ useCards: vi.fn() }));
vi.mock("@/cards/useCardReveal", () => ({ useCardReveal: vi.fn() }));
vi.mock("@/cards/useFreezeCard", () => ({ useFreezeCard: vi.fn() }));
vi.mock("@/cards/useUnfreezeCard", () => ({ useUnfreezeCard: vi.fn() }));
vi.mock("@/cards/useReplaceCard", () => ({ useReplaceCard: vi.fn() }));
vi.mock("@/accounts/useAccounts", () => ({ useAccounts: vi.fn() }));
vi.mock("@/auth/useUser", () => ({ useUser: vi.fn() }));

const { useCards } = await import("@/cards/useCards");
const { useCardReveal } = await import("@/cards/useCardReveal");
const { useFreezeCard } = await import("@/cards/useFreezeCard");
const { useUnfreezeCard } = await import("@/cards/useUnfreezeCard");
const { useReplaceCard } = await import("@/cards/useReplaceCard");
const { useAccounts } = await import("@/accounts/useAccounts");
const { useUser } = await import("@/auth/useUser");

const useCardsMock = useCards as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseCardsResult>
>;
const useCardRevealMock = useCardReveal as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => UseCardRevealResult>
>;
const useFreezeCardMock = useFreezeCard as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useUnfreezeCardMock = useUnfreezeCard as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useReplaceCardMock = useReplaceCard as unknown as ReturnType<
  typeof vi.fn<(options?: UseReplaceCardOptions) => UseReplaceCardResult>
>;
const useAccountsMock = useAccounts as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useUserMock = useUser as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const CARD_ID = "33333333-3333-3333-3333-333333333333";

function card(overrides: Partial<CardResponse> = {}): CardResponse {
  return {
    cardId: CARD_ID,
    accountId: "22222222-2222-2222-2222-222222222222",
    maskedPan: "************1234",
    variant: "DEBIT",
    status: "ACTIVE",
    expiry: "10/29",
    ...overrides,
  };
}

const REPLACEMENT: CardReplacementResponse = {
  cancelledCardId: CARD_ID,
  replacementCard: card({
    cardId: "44444444-4444-4444-4444-444444444444",
    maskedPan: "************9876",
    expiry: "11/30",
  }),
};

const freezeSpy = vi.fn();
const unfreezeSpy = vi.fn();
const replaceSpy = vi.fn();
let replaceOptions: UseReplaceCardOptions = {};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/cards/${CARD_ID}`]}>
      <Routes>
        <Route path="/cards/:cardId" element={<CardDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CardDetailPage", () => {
  beforeEach(() => {
    useCardsMock.mockReset();
    useCardRevealMock.mockReset();
    useFreezeCardMock.mockReset();
    useUnfreezeCardMock.mockReset();
    useReplaceCardMock.mockReset();
    useAccountsMock.mockReset();
    useUserMock.mockReset();
    freezeSpy.mockReset();
    unfreezeSpy.mockReset();
    replaceSpy.mockReset();
    replaceOptions = {};

    useCardsMock.mockReturnValue({
      cards: [card()],
      isPending: false,
      isError: false,
      error: null,
    });
    useCardRevealMock.mockReturnValue({
      detail: null,
      isPending: false,
      isError: false,
      error: null,
    });
    useFreezeCardMock.mockReturnValue({ freeze: freezeSpy, isPending: false });
    useUnfreezeCardMock.mockReturnValue({
      unfreeze: unfreezeSpy,
      isPending: false,
    });
    useReplaceCardMock.mockImplementation((options?: UseReplaceCardOptions) => {
      replaceOptions = options ?? {};
      return { replace: replaceSpy, isPending: false };
    });
    useAccountsMock.mockReturnValue({
      accounts: [
        {
          accountUid: "22222222-2222-2222-2222-222222222222",
          accountType: "CURRENT",
          defaultCategory: "44444444-4444-4444-4444-444444444444",
          currency: "GBP",
          createdAt: "2024-01-01T00:00:00Z",
          name: "Everyday",
        },
      ],
      isPending: false,
      isError: false,
      error: null,
    });
    useUserMock.mockReturnValue({
      user: {
        email: "ada@example.com",
        firstName: "Ada",
        lastName: "Lovelace",
      },
      status: "authenticated",
    });
  });

  it("renders the masked card art and a link to the linked account", () => {
    renderPage();
    expect(screen.getByTestId("card-detail-heading")).toHaveTextContent(
      "Debit card",
    );
    expect(screen.getByTestId("card-detail-account-link")).toHaveAttribute(
      "href",
      "/accounts/22222222-2222-2222-2222-222222222222",
    );
    expect(screen.queryByTestId("card-art-cvv")).toBeNull();
  });

  it("reveals the full details when toggled", async () => {
    const user = userEvent.setup();
    useCardRevealMock.mockImplementation((_cardId, revealed) =>
      revealed
        ? {
            detail: {
              cardId: CARD_ID,
              accountId: "22222222-2222-2222-2222-222222222222",
              cardholderName: "Ada Lovelace",
              pan: "9904320000012341",
              expiry: "10/29",
              cvv: "123",
              variant: "DEBIT",
              status: "ACTIVE",
            },
            isPending: false,
            isError: false,
            error: null,
          }
        : { detail: null, isPending: false, isError: false, error: null },
    );
    renderPage();

    await user.click(screen.getByTestId("card-reveal-toggle"));

    expect(screen.getByTestId("card-art-cvv")).toHaveTextContent("123");
    const groups = Array.from(screen.getByTestId("card-art-pan").children).map(
      (c) => c.textContent,
    );
    expect(groups).toEqual(["9904", "3200", "0001", "2341"]);
  });

  it("freezes an active card", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("card-freeze"));
    expect(freezeSpy).toHaveBeenCalledWith(CARD_ID);
  });

  it("unfreezes a frozen card and explains the freeze", () => {
    useCardsMock.mockReturnValue({
      cards: [card({ status: "FROZEN" })],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("card-unfreeze")).toBeInTheDocument();
    expect(screen.getByTestId("card-frozen-note")).toBeInTheDocument();
    expect(screen.getByTestId("card-art-frozen-overlay")).toBeInTheDocument();
  });

  it("confirms before reporting lost or stolen, then replaces", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("card-report-lost"));
    await user.click(await screen.findByTestId("report-lost-confirm"));
    expect(replaceSpy).toHaveBeenCalledWith(CARD_ID);
  });

  it("shows the replacement card once issued", async () => {
    renderPage();
    await waitFor(() => expect(replaceOptions.onSuccess).toBeDefined());
    act(() => {
      replaceOptions.onSuccess?.(REPLACEMENT);
    });
    expect(
      await screen.findByTestId("card-replacement-issued"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("card-replacement-view")).toHaveAttribute(
      "href",
      `/cards/${REPLACEMENT.replacementCard.cardId}`,
    );
  });

  it("offers no actions on a cancelled card", () => {
    useCardsMock.mockReturnValue({
      cards: [card({ status: "CANCELLED" })],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("card-cancelled-note")).toBeInTheDocument();
    expect(screen.queryByTestId("card-freeze")).toBeNull();
    expect(screen.queryByTestId("card-reveal-toggle")).toBeNull();
    expect(screen.queryByTestId("card-report-lost")).toBeNull();
  });

  it("renders a not-found state for an unknown card", () => {
    useCardsMock.mockReturnValue({
      cards: [],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("card-not-found")).toBeInTheDocument();
  });
});
