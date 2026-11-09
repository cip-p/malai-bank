import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type { CreditCardApplicationResponse } from "@/api/generated/types.gen";
import type {
  UseCreditCardApplicationOptions,
  UseCreditCardApplicationResult,
} from "@/cards/useCreditCardApplication";
import { CreditCardApplicationPage } from "./CreditCardApplicationPage";

vi.mock("@/cards/useCreditCardApplication", () => ({
  useCreditCardApplication: vi.fn(),
}));
vi.mock("@/auth/useUser", () => ({ useUser: vi.fn() }));

const { useCreditCardApplication } =
  await import("@/cards/useCreditCardApplication");
const { useUser } = await import("@/auth/useUser");

const useApplicationMock = useCreditCardApplication as unknown as ReturnType<
  typeof vi.fn<
    (
      options?: UseCreditCardApplicationOptions,
    ) => UseCreditCardApplicationResult
  >
>;
const useUserMock = useUser as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;

const APPROVED: CreditCardApplicationResponse = {
  account: {
    accountUid: "55555555-5555-5555-5555-555555555555",
    accountType: "CREDIT_CARD",
    defaultCategory: "66666666-6666-6666-6666-666666666666",
    currency: "GBP",
    createdAt: "2026-07-12T09:00:00Z",
    name: "Credit Card",
  },
  card: {
    cardId: "44444444-4444-4444-4444-444444444444",
    accountId: "55555555-5555-5555-5555-555555555555",
    maskedPan: "************9876",
    variant: "CREDIT",
    status: "ACTIVE",
    expiry: "11/30",
  },
};

const applySpy = vi.fn();
let applicationOptions: UseCreditCardApplicationOptions = {};

function renderPage() {
  return render(
    <MemoryRouter>
      <CreditCardApplicationPage />
    </MemoryRouter>,
  );
}

describe("CreditCardApplicationPage", () => {
  beforeEach(() => {
    useApplicationMock.mockReset();
    useUserMock.mockReset();
    applySpy.mockReset();
    applicationOptions = {};

    useApplicationMock.mockImplementation(
      (options?: UseCreditCardApplicationOptions) => {
        applicationOptions = options ?? {};
        return { apply: applySpy, isPending: false, alreadyExists: false };
      },
    );
    useUserMock.mockReturnValue({
      user: {
        email: "ada@example.com",
        firstName: "Ada",
        lastName: "Lovelace",
      },
      status: "authenticated",
    });
  });

  it("requires the confirmation checkbox before applying", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(screen.getByTestId("apply-submit")).toBeDisabled();
    await user.click(screen.getByTestId("apply-confirm"));
    expect(screen.getByTestId("apply-submit")).toBeEnabled();
    await user.click(screen.getByTestId("apply-submit"));
    expect(applySpy).toHaveBeenCalledTimes(1);
  });

  it("shows the approval with the new card and links to it", () => {
    renderPage();
    act(() => {
      applicationOptions.onSuccess?.(APPROVED);
    });
    expect(screen.getByTestId("apply-approved")).toBeInTheDocument();
    expect(screen.getByTestId("apply-view-card")).toHaveAttribute(
      "href",
      `/cards/${APPROVED.card.cardId}`,
    );
    expect(screen.getByTestId("apply-view-account")).toHaveAttribute(
      "href",
      `/accounts/${APPROVED.account.accountUid}`,
    );
    expect(screen.getByTestId("card-art")).toHaveAttribute(
      "data-variant",
      "CREDIT",
    );
  });

  it("explains when the holder already has a credit card", () => {
    useApplicationMock.mockImplementation(
      (options?: UseCreditCardApplicationOptions) => {
        applicationOptions = options ?? {};
        return { apply: applySpy, isPending: false, alreadyExists: true };
      },
    );
    renderPage();
    expect(screen.getByTestId("apply-already-exists")).toBeInTheDocument();
    expect(screen.getByTestId("apply-view-cards")).toHaveAttribute(
      "href",
      "/cards",
    );
    expect(screen.queryByTestId("apply-submit")).toBeNull();
  });
});
