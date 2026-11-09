import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AccountResponse,
  PayeeResponse,
  StandingOrderCreateRequest,
  StandingOrderResponse,
} from "@/api/generated/types.gen";

let payeesResult: {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
};
let accountsResult: {
  accounts: AccountResponse[] | null;
  isPending: boolean;
  isError: boolean;
};
const createStandingOrderMock = vi.fn();
let createOnSuccess: ((order: StandingOrderResponse) => void) | undefined;
let isCreating = false;

vi.mock("@/move-money/usePayees", () => ({
  usePayees: () => payeesResult,
}));

vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: () => accountsResult,
}));

vi.mock("@/move-money/useCreateStandingOrder", () => ({
  useCreateStandingOrder: (opts?: {
    onSuccess?: (order: StandingOrderResponse) => void;
  }) => {
    createOnSuccess = opts?.onSuccess;
    return {
      createStandingOrder: (input: StandingOrderCreateRequest) =>
        createStandingOrderMock(input),
      isPending: isCreating,
    };
  },
}));

import { NewStandingOrderPage } from "./NewStandingOrderPage";

const PAYEE: PayeeResponse = {
  payeeId: "44444444-4444-4444-4444-444444444444",
  name: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  createdAt: "2026-05-19T10:00:00Z",
};

const ACCOUNT_A: AccountResponse = {
  accountUid: "33333333-3333-3333-3333-333333333333",
  accountType: "CURRENT",
  defaultCategory: "GENERAL",
  currency: "GBP",
  createdAt: "2026-05-19T10:00:00Z",
  name: "Everyday",
};

const ACCOUNT_B: AccountResponse = {
  accountUid: "99999999-9999-9999-9999-999999999999",
  accountType: "SAVINGS",
  defaultCategory: "GENERAL",
  currency: "GBP",
  createdAt: "2026-05-19T10:00:00Z",
  name: "Savings",
};

const FIXED_NOW = new Date("2026-07-08T09:00:00Z");

function renderPage() {
  return render(
    <MemoryRouter>
      <NewStandingOrderPage now={FIXED_NOW} />
    </MemoryRouter>,
  );
}

async function walkToReview(
  user: ReturnType<typeof userEvent.setup>,
  { amount = "25.00", reference }: { amount?: string; reference?: string } = {},
) {
  await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.type(screen.getByLabelText("Amount"), amount);
  if (reference) {
    await user.type(screen.getByLabelText("Reference (optional)"), reference);
  }
  await user.click(screen.getByRole("button", { name: "Review" }));
}

describe("NewStandingOrderPage — wizard", () => {
  beforeEach(() => {
    payeesResult = { payees: [PAYEE], isPending: false, isError: false };
    accountsResult = {
      accounts: [ACCOUNT_A, ACCOUNT_B],
      isPending: false,
      isError: false,
    };
    createStandingOrderMock.mockReset();
    createOnSuccess = undefined;
    isCreating = false;
  });

  it("focuses the payee step's h1 on mount", () => {
    renderPage();
    expect(
      screen.getByRole("heading", {
        level: 1,
        name: "Who is the standing order to?",
      }),
    ).toHaveFocus();
  });

  it("Continue on the payee step is disabled until a payee is chosen", async () => {
    const user = userEvent.setup();
    renderPage();
    const continueBtn = screen.getByRole("button", { name: "Continue" });
    expect(continueBtn).toBeDisabled();
    await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
    expect(continueBtn).toBeEnabled();
  });

  it("shows an add-payee path and no list when there are no payees", () => {
    payeesResult = { payees: [], isPending: false, isError: false };
    renderPage();
    expect(screen.getByTestId("so-payee-empty")).toBeInTheDocument();
    expect(screen.getByTestId("so-payee-add")).toHaveAttribute(
      "href",
      "/move/payees/new",
    );
    expect(screen.queryByTestId("so-payee-list")).toBeNull();
  });

  it("defaults the source account to the first account", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Which account pays it?" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Everyday" })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Savings" })).not.toBeChecked();
  });

  it("Review is disabled until an amount is entered and enabled once valid", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    const reviewBtn = screen.getByRole("button", { name: "Review" });
    expect(reviewBtn).toBeDisabled();
    await user.type(screen.getByLabelText("Amount"), "25.00");
    expect(reviewBtn).toBeEnabled();
  });

  it("defaults the first payment date to today and rejects earlier dates", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    const dateField = screen.getByLabelText("First payment date");
    expect(dateField).toHaveValue("2026-07-08");
    expect(dateField).toHaveAttribute("min", "2026-07-08");

    await user.type(screen.getByLabelText("Amount"), "25.00");
    await user.clear(dateField);
    await user.type(dateField, "2026-07-01");
    expect(screen.getByRole("button", { name: "Review" })).toBeDisabled();
    expect(dateField).toHaveAttribute("aria-invalid", "true");
  });

  it("fires create with the snapshotted request when confirmed", async () => {
    const user = userEvent.setup();
    renderPage();
    await walkToReview(user, { amount: "25.00", reference: "Rent" });

    expect(screen.getByTestId("so-confirmation")).toBeInTheDocument();
    await user.click(screen.getByTestId("so-submit"));

    expect(createStandingOrderMock).toHaveBeenCalledWith({
      accountId: ACCOUNT_A.accountUid,
      payeeId: PAYEE.payeeId,
      amountMinorUnits: 2500,
      currency: "GBP",
      frequency: "MONTHLY",
      startDate: "2026-07-08",
      reference: "Rent",
    });
  });

  it("omits the reference from the request when left blank", async () => {
    const user = userEvent.setup();
    renderPage();
    await walkToReview(user, { amount: "10.50" });
    await user.click(screen.getByTestId("so-submit"));

    expect(createStandingOrderMock).toHaveBeenCalledWith({
      accountId: ACCOUNT_A.accountUid,
      payeeId: PAYEE.payeeId,
      amountMinorUnits: 1050,
      currency: "GBP",
      frequency: "MONTHLY",
      startDate: "2026-07-08",
    });
  });

  it("sends the chosen source account and frequency", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("radio", { name: /Sarah Mitchell/ }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.click(screen.getByRole("radio", { name: "Savings" }));
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await user.type(screen.getByLabelText("Amount"), "25.00");
    await user.click(screen.getByRole("radio", { name: "Quarterly" }));
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByTestId("so-submit"));

    expect(createStandingOrderMock).toHaveBeenCalledWith(
      expect.objectContaining({
        accountId: ACCOUNT_B.accountUid,
        frequency: "QUARTERLY",
      }),
    );
  });

  it("after create succeeds, shows the success screen with a Back to standing orders CTA", async () => {
    const user = userEvent.setup();
    renderPage();
    await walkToReview(user, { amount: "25.00" });
    await user.click(screen.getByTestId("so-submit"));

    act(() => {
      createOnSuccess?.({
        standingOrderId: "55555555-5555-5555-5555-555555555555",
        accountId: ACCOUNT_A.accountUid,
        payeeName: "Sarah Mitchell",
        payeeSortCode: "12-34-56",
        payeeAccountNumber: "12345678",
        amountMinorUnits: 2500,
        currency: "GBP",
        frequency: "MONTHLY",
        status: "ACTIVE",
        startDate: "2026-07-08",
      });
    });

    expect(screen.getByTestId("so-success")).toBeInTheDocument();
    expect(screen.getByTestId("success-tick")).toBeInTheDocument();
    expect(screen.getByTestId("so-success-back")).toHaveAttribute(
      "href",
      "/move/standing-orders",
    );
    expect(
      screen.getByRole("heading", { level: 1, name: "Standing order set up" }),
    ).toHaveFocus();
  });

  it("preserves state when stepping back from review", async () => {
    const user = userEvent.setup();
    renderPage();
    await walkToReview(user, { amount: "25.00", reference: "Rent" });
    await user.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByLabelText("Amount")).toHaveValue("25.00");
    expect(screen.getByLabelText("Reference (optional)")).toHaveValue("Rent");
  });
});
