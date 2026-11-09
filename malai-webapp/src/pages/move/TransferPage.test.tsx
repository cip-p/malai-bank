import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AccountIdentifiersResponse,
  AccountResponse,
  ConfirmationOfPayeeRequest,
  ConfirmationOfPayeeResponse,
  PayeeRequest,
  PayeeResponse,
  PaymentResponse,
} from "@/api/generated/types.gen";
import type { TransferInput } from "@/move-money/useTransfer";

const accountsState: {
  accounts: AccountResponse[] | null;
  isPending: boolean;
  isError: boolean;
} = { accounts: null, isPending: false, isError: false };

const identifiersByUid = new Map<string, AccountIdentifiersResponse>();

const payeesState: {
  payees: PayeeResponse[] | null;
  isPending: boolean;
  isError: boolean;
} = { payees: null, isPending: false, isError: false };

vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: () => accountsState,
}));

vi.mock("@/move-money/usePayees", () => ({
  usePayees: () => payeesState,
}));

vi.mock("@/accounts/useIdentifiers", () => ({
  useIdentifiers: (accountUid: string) => {
    if (!accountUid) {
      return {
        identifiers: null,
        isPending: false,
        isError: false,
        error: null,
      };
    }
    const found = identifiersByUid.get(accountUid);
    if (!found) {
      return {
        identifiers: null,
        isPending: true,
        isError: false,
        error: null,
      };
    }
    return {
      identifiers: found,
      isPending: false,
      isError: false,
      error: null,
    };
  },
}));

let transferMock = vi.fn<(input: TransferInput) => void>();
let transferOnSuccess: ((r: PaymentResponse) => void) | undefined;
let transferState = { isPending: false, insufficientFunds: false };
const resetMock = vi.fn();

vi.mock("@/move-money/useTransfer", () => ({
  useTransfer: (opts?: { onSuccess?: (r: PaymentResponse) => void }) => {
    transferOnSuccess = opts?.onSuccess;
    return {
      transfer: (input: TransferInput) => transferMock(input),
      isPending: transferState.isPending,
      insufficientFunds: transferState.insufficientFunds,
      reset: resetMock,
    };
  },
}));

let paymentStatusOverride: PaymentResponse | null = null;

vi.mock("@/move-money/usePaymentStatus", () => ({
  usePaymentStatus: (initial: PaymentResponse) => ({
    payment: paymentStatusOverride ?? initial,
  }),
}));

let confirmPayeeMock = vi.fn<(input: ConfirmationOfPayeeRequest) => void>();
let confirmOnSuccess:
  | ((response: ConfirmationOfPayeeResponse) => void)
  | undefined;
let confirmState = { isPending: false };

vi.mock("@/move-money/useConfirmationOfPayee", () => ({
  useConfirmationOfPayee: (opts?: {
    onSuccess?: (r: ConfirmationOfPayeeResponse) => void;
  }) => {
    confirmOnSuccess = opts?.onSuccess;
    return {
      confirmPayee: (input: ConfirmationOfPayeeRequest) =>
        confirmPayeeMock(input),
      isPending: confirmState.isPending,
    };
  },
}));

let createPayeeMock = vi.fn<(input: PayeeRequest) => void>();
let createPayeeOnSuccess: ((payee: PayeeResponse) => void) | undefined;
let createPayeeState = { isPending: false };
let createPayeeNavigateOnSuccess: boolean | undefined;

vi.mock("@/move-money/useCreatePayee", () => ({
  useCreatePayee: (opts?: {
    onSuccess?: (p: PayeeResponse) => void;
    navigateOnSuccess?: boolean;
  }) => {
    createPayeeOnSuccess = opts?.onSuccess;
    createPayeeNavigateOnSuccess = opts?.navigateOnSuccess;
    return {
      createPayee: (input: PayeeRequest) => createPayeeMock(input),
      isPending: createPayeeState.isPending,
    };
  },
}));

import { TransferPage } from "./TransferPage";

const HOLDER_ACCOUNTS: AccountResponse[] = [
  {
    accountUid: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    accountType: "CURRENT",
    defaultCategory: "PERSONAL",
    currency: "GBP",
    createdAt: "2026-01-01T00:00:00Z",
    name: "Main",
  },
  {
    accountUid: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    accountType: "SAVINGS",
    defaultCategory: "PERSONAL",
    currency: "GBP",
    createdAt: "2026-01-01T00:00:00Z",
    name: "Savings",
  },
];

const SAVINGS_IDENT: AccountIdentifiersResponse = {
  accountIdentifier: "87654321",
  bankIdentifier: "98-76-54",
  accountIdentifiers: [],
};

function paymentResponseOf(
  overrides: Partial<PaymentResponse> = {},
): PaymentResponse {
  return {
    paymentId: "44444444-4444-4444-4444-444444444444",
    state: "SETTLED",
    rail: "BOOK_TRANSFER",
    direction: "OUTBOUND",
    createdAt: "2026-05-20T00:00:00Z",
    endToEndId: "55555555-5555-5555-5555-555555555555",
    fromAccountId: HOLDER_ACCOUNTS[0]!.accountUid,
    toAccountId: HOLDER_ACCOUNTS[1]!.accountUid,
    amountMinorUnits: 1245,
    currency: "GBP",
    ...overrides,
  };
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <TransferPage />
    </MemoryRouter>,
  );
}

async function advanceFromSource(fromName: string = "Main") {
  const user = userEvent.setup();
  await user.click(screen.getByRole("radio", { name: fromName }));
  return user;
}

async function walkToDestination(fromName: string = "Main") {
  return advanceFromSource(fromName);
}

async function walkToAmountViaMyAccounts(toName: string) {
  const user = await walkToDestination();
  await user.click(screen.getByRole("radio", { name: toName }));
  return user;
}

async function walkToConfirmViaMyAccounts(toName: string, amount: string) {
  const user = await walkToAmountViaMyAccounts(toName);
  await user.type(screen.getByLabelText("Amount"), amount);
  await user.click(screen.getByRole("button", { name: "Review" }));
  return user;
}

function resetState() {
  transferMock = vi.fn<(input: TransferInput) => void>();
  transferOnSuccess = undefined;
  transferState = { isPending: false, insufficientFunds: false };
  resetMock.mockReset();
  confirmPayeeMock = vi.fn<(input: ConfirmationOfPayeeRequest) => void>();
  confirmOnSuccess = undefined;
  confirmState = { isPending: false };
  createPayeeMock = vi.fn<(input: PayeeRequest) => void>();
  createPayeeOnSuccess = undefined;
  createPayeeState = { isPending: false };
  createPayeeNavigateOnSuccess = undefined;
  paymentStatusOverride = null;
  accountsState.accounts = HOLDER_ACCOUNTS;
  accountsState.isPending = false;
  accountsState.isError = false;
  identifiersByUid.clear();
  identifiersByUid.set("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", SAVINGS_IDENT);
  payeesState.payees = [];
  payeesState.isPending = false;
  payeesState.isError = false;
}

describe("TransferPage — step flow", () => {
  beforeEach(resetState);

  it("renders the source step h1 and focuses it on mount", () => {
    renderAt("/move/send");
    const heading = screen.getByRole("heading", {
      level: 1,
      name: "Where from?",
    });
    expect(heading).toHaveFocus();
  });

  it("step indicator shows 'Step 1 of 4' on the source step", () => {
    renderAt("/move/send");
    expect(screen.getByTestId("stepflow-indicator")).toHaveTextContent(
      "Step 1 of 4",
    );
  });

  it("no Back affordance on step 1", () => {
    renderAt("/move/send");
    expect(screen.queryByTestId("stepflow-back")).toBeNull();
  });

  it("Back affordance is present on step 2", async () => {
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.getByTestId("stepflow-back")).toBeInTheDocument();
  });

  it("pre-selects the source account from ?from=<accountUid> when it matches", () => {
    renderAt(`/move/send?from=${HOLDER_ACCOUNTS[1]!.accountUid}`);
    const savings = screen.getByRole("radio", { name: "Savings" });
    expect(savings).toBeChecked();
  });

  it("falls back to the first account when ?from= is missing", () => {
    renderAt("/move/send");
    const main = screen.getByRole("radio", { name: "Main" });
    expect(main).toBeChecked();
  });

  it("falls back to the first account when ?from= does not match any account", () => {
    renderAt("/move/send?from=does-not-exist");
    const main = screen.getByRole("radio", { name: "Main" });
    expect(main).toBeChecked();
  });

  it("Choose source on source step is disabled when no source is selected", () => {
    accountsState.accounts = [];
    renderAt("/move/send");
    expect(
      screen.getByRole("button", { name: "Choose source" }),
    ).toBeDisabled();
  });

  it("destination step renders all three destination tabs as enabled", async () => {
    renderAt("/move/send");
    await advanceFromSource();
    expect(
      screen.getByRole("radio", { name: /My accounts/ }),
    ).not.toBeDisabled();
    expect(
      screen.getByRole("radio", { name: /Saved payee/ }),
    ).not.toBeDisabled();
    expect(screen.getByRole("radio", { name: /New payee/ })).not.toBeDisabled();
  });

  it("no 'Coming soon' labels remain anywhere in the flow", async () => {
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.queryByText(/Coming soon/)).toBeNull();
  });

  it("My-accounts mode on destination step lists the holder's other accounts and not the source", async () => {
    renderAt("/move/send");
    await advanceFromSource();
    const list = screen.getByTestId("my-accounts-list");
    expect(within(list).queryByText("Main")).toBeNull();
    expect(within(list).getByText("Savings")).toBeInTheDocument();
  });

  it("walks source → destination → amount → confirm and fires transfer on Send", async () => {
    renderAt("/move/send");
    const user = await walkToConfirmViaMyAccounts("Savings", "12.45");

    const summary = screen.getByTestId("transfer-confirmation");
    expect(within(summary).getByText(/Main/)).toBeInTheDocument();
    expect(within(summary).getByText(/Savings/)).toBeInTheDocument();
    expect(within(summary).getByText(/£12\.45/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledTimes(1);
    expect(transferMock).toHaveBeenCalledWith({
      fromAccountUid: HOLDER_ACCOUNTS[0]!.accountUid,
      toSortCode: "98-76-54",
      toAccountNumber: "87654321",
      amountMinorUnits: 1245,
      description: undefined,
      copVerdictOverride: false,
      toName: "Savings",
    });
  });

  it("includes the reference on the confirm summary when entered on the amount step", async () => {
    renderAt("/move/send");
    const user = await walkToAmountViaMyAccounts("Savings");
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.type(screen.getByLabelText(/Reference/), "Lunch");
    await user.click(screen.getByRole("button", { name: "Review" }));

    const summary = screen.getByTestId("transfer-confirmation");
    expect(within(summary).getByText(/Lunch/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Send money" }));
    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ description: "Lunch" }),
    );
  });

  it("Back from confirm preserves the entered amount, reference, and picked destination", async () => {
    renderAt("/move/send");
    const user = await walkToAmountViaMyAccounts("Savings");
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.type(screen.getByLabelText(/Reference/), "Lunch");
    await user.click(screen.getByRole("button", { name: "Review" }));

    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByLabelText("Amount")).toHaveValue("12.45");
    expect(screen.getByLabelText(/Reference/)).toHaveValue("Lunch");

    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByRole("radio", { name: "Savings" })).toBeChecked();
  });

  it("Review on amount step is disabled when amount is invalid", async () => {
    renderAt("/move/send");
    const user = await walkToAmountViaMyAccounts("Savings");
    expect(screen.getByRole("button", { name: "Review" })).toBeDisabled();
    await user.type(screen.getByLabelText("Amount"), "0");
    expect(screen.getByRole("button", { name: "Review" })).toBeDisabled();
  });

  it("renders the inline insufficient-funds error on the confirm step when the hook flags it", async () => {
    transferState = { isPending: false, insufficientFunds: true };
    renderAt("/move/send");
    await walkToConfirmViaMyAccounts("Savings", "12.45");
    expect(
      screen.getByText("Not enough money in this account."),
    ).toBeInTheDocument();
  });

  it("Send is disabled on the confirm step while the mutation is pending", async () => {
    transferState = { isPending: true, insufficientFunds: false };
    renderAt("/move/send");
    await walkToConfirmViaMyAccounts("Savings", "12.45");
    expect(screen.getByTestId("transfer-send")).toBeDisabled();
  });

  it("shows the reference counter only once over 400 chars", async () => {
    renderAt("/move/send");
    const user = await walkToAmountViaMyAccounts("Savings");
    expect(screen.queryByTestId("reference-counter")).toBeNull();
    const reference = screen.getByLabelText(/Reference/) as HTMLInputElement;
    await user.click(reference);
    await user.paste("a".repeat(401));
    expect(screen.getByTestId("reference-counter")).toHaveTextContent(
      "401 / 500",
    );
  });

  it("on a SETTLED payment, renders the success screen with SuccessTick and Back-to-source CTA", async () => {
    renderAt("/move/send");
    const user = await walkToConfirmViaMyAccounts("Savings", "12.45");
    await user.click(screen.getByRole("button", { name: "Send money" }));

    act(() => {
      transferOnSuccess?.(paymentResponseOf());
    });

    const success = screen.getByTestId("transfer-success");
    expect(success).toBeInTheDocument();
    expect(screen.getByTestId("success-tick")).toBeInTheDocument();
    const cta = screen.getByTestId("transfer-success-back");
    expect(cta).toHaveAttribute(
      "href",
      `/accounts/${HOLDER_ACCOUNTS[0]!.accountUid}`,
    );
    expect(cta).toHaveTextContent("Back to Main");
    expect(
      screen.getByRole("heading", { level: 1, name: "Transfer sent" }),
    ).toHaveFocus();
  });

  it("on a payment born PENDING, renders the pending screen", async () => {
    renderAt("/move/send");
    const user = await walkToConfirmViaMyAccounts("Savings", "12.45");
    await user.click(screen.getByRole("button", { name: "Send money" }));

    act(() => {
      transferOnSuccess?.(paymentResponseOf({ state: "PENDING", rail: "FPS" }));
    });

    expect(screen.getByTestId("payment-pending")).toBeInTheDocument();
    expect(screen.queryByTestId("transfer-success")).toBeNull();
  });

  it("when the payment status resolves to RETURNED, renders the returned screen with the reason", async () => {
    paymentStatusOverride = paymentResponseOf({
      state: "RETURNED",
      rail: "FPS",
      returnReason: "ACCOUNT_CLOSED",
    });
    renderAt("/move/send");
    const user = await walkToConfirmViaMyAccounts("Savings", "12.45");
    await user.click(screen.getByRole("button", { name: "Send money" }));

    act(() => {
      transferOnSuccess?.(paymentResponseOf({ state: "PENDING", rail: "FPS" }));
    });

    expect(screen.getByTestId("payment-returned")).toBeInTheDocument();
    expect(screen.getByTestId("payment-returned-reason")).toHaveTextContent(
      "The recipient's account has been closed.",
    );
  });
});

const SAVED_PAYEES: PayeeResponse[] = [
  {
    payeeId: "11111111-1111-1111-1111-111111111aaa",
    name: "Zoe Painter",
    sortCode: "11-11-11",
    accountNumber: "11111111",
    createdAt: "2026-04-01T00:00:00Z",
  },
  {
    payeeId: "22222222-2222-2222-2222-222222222bbb",
    name: "Amara Smith",
    sortCode: "22-22-22",
    accountNumber: "22222222",
    nickname: "Best friend",
    createdAt: "2026-04-02T00:00:00Z",
  },
];

describe("TransferPage — Saved payee mode", () => {
  beforeEach(() => {
    resetState();
    payeesState.payees = SAVED_PAYEES;
  });

  it("renders the saved-payee picker when the Saved payee tab is selected", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    expect(screen.getByTestId("saved-payee-list")).toBeInTheDocument();
  });

  it("sorts saved payees alphabetically by nickname ?? name", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    const list = screen.getByTestId("saved-payee-list");
    const rows = within(list).getAllByRole("radio");
    expect(rows[0]).toHaveAccessibleName(/Best friend/);
    expect(rows[1]).toHaveAccessibleName(/Zoe Painter/);
  });

  it("sends the payee's name of record for screening, never the customer's nickname", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Best friend/ }));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ toName: "Amara Smith" }),
    );
  });

  it("clicking a saved payee auto-advances to the amount step", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Best friend/ }));
    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });

  it("confirm summary uses the selected payee's display name", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Best friend/ }));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    const summary = screen.getByTestId("transfer-confirmation");
    expect(within(summary).getByText(/Best friend/)).toBeInTheDocument();
    expect(within(summary).getByText(/£12\.45/)).toBeInTheDocument();
  });

  it("Send fires transfer with the saved payee's sortCode + accountNumber", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Zoe Painter/ }));
    await user.type(screen.getByLabelText("Amount"), "5.00");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));
    expect(transferMock).toHaveBeenCalledTimes(1);
    expect(transferMock).toHaveBeenCalledWith({
      fromAccountUid: HOLDER_ACCOUNTS[0]!.accountUid,
      toSortCode: "11-11-11",
      toAccountNumber: "11111111",
      amountMinorUnits: 500,
      description: undefined,
      copVerdictOverride: false,
      toName: "Zoe Painter",
    });
  });

  it("renders the empty state with an Add-payee link when no payees are saved", async () => {
    payeesState.payees = [];
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    expect(screen.getByTestId("saved-payee-empty")).toHaveTextContent(
      "No saved payees yet.",
    );
    const link = screen.getByRole("link", { name: /Add a payee/ });
    expect(link).toHaveAttribute("href", "/move/payees/new");
  });

  it("renders a skeleton while saved payees are loading", async () => {
    payeesState.payees = null;
    payeesState.isPending = true;
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    expect(screen.getByTestId("saved-payee-skeleton")).toBeInTheDocument();
  });

  it("switching back to My accounts clears the picked payee", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Best friend/ }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    await user.click(screen.getByRole("radio", { name: /My accounts/ }));
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    const rows = within(screen.getByTestId("saved-payee-list")).getAllByRole(
      "radio",
    );
    for (const row of rows) expect(row).not.toBeChecked();
  });
});

describe("TransferPage — New payee mode", () => {
  beforeEach(() => {
    resetState();
  });

  async function fillNewPayeeOnDestinationStep() {
    const user = userEvent.setup();
    await user.click(screen.getByRole("radio", { name: "Main" }));
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    await user.type(screen.getByLabelText("Name on the account"), "Jane Doe");
    await user.type(screen.getByLabelText("Sort code"), "123456");
    await user.type(screen.getByLabelText("Account number"), "12345678");
    return user;
  }

  it("renders the new-payee form when the New payee tab is selected on destination step", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    expect(screen.getByTestId("new-payee-body")).toBeInTheDocument();
    expect(screen.getByLabelText("Name on the account")).toBeInTheDocument();
    expect(screen.getByLabelText("Sort code")).toBeInTheDocument();
    expect(screen.getByLabelText("Account number")).toBeInTheDocument();
    expect(screen.getByTestId("save-as-payee")).not.toBeChecked();
  });

  it("Verify payee on destination step is disabled until name, sort code and account number are valid", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeDisabled();
    await user.type(screen.getByLabelText("Name on the account"), "Jane Doe");
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeDisabled();
    await user.type(screen.getByLabelText("Sort code"), "1234");
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeDisabled();
    await user.type(screen.getByLabelText("Sort code"), "56");
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeDisabled();
    await user.type(screen.getByLabelText("Account number"), "12345678");
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeEnabled();
  });

  it("Verify payee from destination step fires confirmPayee with the entered values", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    expect(confirmPayeeMock).toHaveBeenCalledWith({
      name: "Jane Doe",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
  });

  it("on MATCH, advances to the amount step without showing the COPA panel", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "MATCH" });
    });
    expect(screen.queryByTestId("copa-panel")).toBeNull();
    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });

  it("on CLOSE_MATCH, renders the panel; Keep what I typed advances to the amount step", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({
        result: "CLOSE_MATCH",
        onRecordName: "Jane A Doe",
      });
    });
    const panel = await screen.findByTestId("copa-panel");
    expect(panel.getAttribute("data-verdict")).toBe("CLOSE_MATCH");

    await user.click(screen.getByTestId("copa-accept"));

    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });

  it("on CLOSE_MATCH, Use on-record swaps the name and propagates to the confirm summary", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({
        result: "CLOSE_MATCH",
        onRecordName: "Jane A Doe",
      });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-use-on-record"));

    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));

    const summary = screen.getByTestId("transfer-confirmation");
    expect(within(summary).getByText(/Jane A Doe/)).toBeInTheDocument();
  });

  it("on NO_MATCH, Edit details dismisses the panel and stays on destination step", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(screen.getByTestId("save-as-payee"));
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "NO_MATCH" });
    });
    const panel = await screen.findByTestId("copa-panel");
    expect(panel.getAttribute("data-verdict")).toBe("NO_MATCH");

    await user.click(screen.getByTestId("copa-edit"));

    expect(screen.queryByTestId("copa-panel")).toBeNull();
    expect(screen.queryByRole("heading", { name: "How much?" })).toBeNull();
    expect(screen.getByLabelText("Name on the account")).toHaveValue(
      "Jane Doe",
    );
    expect(screen.getByLabelText("Sort code")).toHaveValue("12-34-56");
    expect(screen.getByLabelText("Account number")).toHaveValue("12345678");
    expect(screen.getByTestId("save-as-payee")).toBeChecked();
  });

  it("on NO_MATCH, Send anyway advances to the amount step", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "NO_MATCH" });
    });
    await screen.findByTestId("copa-panel");
    expect(screen.getByTestId("copa-send-anyway").textContent).toBe(
      "Send anyway",
    );

    await user.click(screen.getByTestId("copa-send-anyway"));

    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });

  it("NO_MATCH pressed through with Send anyway fires transfer with copVerdictOverride: true", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "NO_MATCH" });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-send-anyway"));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ copVerdictOverride: true }),
    );
  });

  it("CLOSE_MATCH pressed through with Keep what I typed fires transfer with copVerdictOverride: true", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "CLOSE_MATCH", onRecordName: "Jane A Doe" });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-accept"));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ copVerdictOverride: true }),
    );
  });

  it("CLOSE_MATCH resolved with the on-record name fires transfer with copVerdictOverride: false", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "CLOSE_MATCH", onRecordName: "Jane A Doe" });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-use-on-record"));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ copVerdictOverride: false }),
    );
  });

  it("re-verifying after a press-through resets copVerdictOverride", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "NO_MATCH" });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-send-anyway"));

    await user.click(screen.getByRole("button", { name: "Back" }));
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "MATCH" });
    });
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(transferMock).toHaveBeenCalledWith(
      expect.objectContaining({ copVerdictOverride: false }),
    );
  });

  it("Send WITHOUT save-as-payee: fires transfer only, never create-payee", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "MATCH" });
    });
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(createPayeeMock).not.toHaveBeenCalled();
    expect(transferMock).toHaveBeenCalledTimes(1);
    expect(transferMock).toHaveBeenCalledWith({
      fromAccountUid: HOLDER_ACCOUNTS[0]!.accountUid,
      toSortCode: "12-34-56",
      toAccountNumber: "12345678",
      amountMinorUnits: 1245,
      description: undefined,
      copVerdictOverride: false,
      toName: "Jane Doe",
    });
  });

  it("Send WITH save-as-payee: fires create-payee first; on success, fires transfer", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(screen.getByTestId("save-as-payee"));
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({ result: "MATCH" });
    });
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(createPayeeMock).toHaveBeenCalledTimes(1);
    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Jane Doe",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
    expect(transferMock).not.toHaveBeenCalled();

    act(() => {
      createPayeeOnSuccess?.({
        payeeId: "00000000-0000-4000-8000-000000000abc",
        name: "Jane Doe",
        sortCode: "12-34-56",
        accountNumber: "12345678",
        createdAt: "2026-05-20T00:00:00Z",
      });
    });

    expect(transferMock).toHaveBeenCalledTimes(1);
    expect(transferMock).toHaveBeenCalledWith({
      fromAccountUid: HOLDER_ACCOUNTS[0]!.accountUid,
      toSortCode: "12-34-56",
      toAccountNumber: "12345678",
      amountMinorUnits: 1245,
      description: undefined,
      copVerdictOverride: false,
      toName: "Jane Doe",
    });
  });

  it("CLOSE_MATCH on-record name swap propagates to the create-payee request body", async () => {
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();
    await user.click(screen.getByTestId("save-as-payee"));
    await user.click(
      screen.getByRole("button", { name: /Verify payee|Verifying/ }),
    );
    act(() => {
      confirmOnSuccess?.({
        result: "CLOSE_MATCH",
        onRecordName: "Jane A Doe",
      });
    });
    await screen.findByTestId("copa-panel");
    await user.click(screen.getByTestId("copa-use-on-record"));
    await user.type(screen.getByLabelText("Amount"), "12.45");
    await user.click(screen.getByRole("button", { name: "Review" }));
    await user.click(screen.getByRole("button", { name: "Send money" }));

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Jane A Doe",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
  });

  it("useCreatePayee is constructed with navigateOnSuccess: false", () => {
    renderAt("/move/send");
    expect(createPayeeNavigateOnSuccess).toBe(false);
  });

  it("blocks Verify payee and warns when save-as-payee is ticked and the (sortCode, accountNumber) already exists", async () => {
    payeesState.payees = [
      {
        payeeId: "00000000-0000-4000-8000-0000000000d1",
        name: "Existing Jane",
        nickname: "Jane (rent)",
        sortCode: "12-34-56",
        accountNumber: "12345678",
        createdAt: "2026-05-01T00:00:00Z",
      },
    ];
    renderAt("/move/send");
    const user = await fillNewPayeeOnDestinationStep();

    expect(screen.queryByTestId("new-payee-duplicate")).toBeNull();
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeEnabled();

    await user.click(screen.getByTestId("save-as-payee"));

    const warning = screen.getByTestId("new-payee-duplicate");
    expect(warning).toHaveTextContent(/Jane \(rent\)/);
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeDisabled();
    expect(confirmPayeeMock).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("save-as-payee"));
    expect(screen.queryByTestId("new-payee-duplicate")).toBeNull();
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeEnabled();
  });
});

describe("TransferPage — auto-advance on confirmed selection", () => {
  beforeEach(() => {
    resetState();
    payeesState.payees = SAVED_PAYEES;
  });

  it("Source step: Choose source button is hidden when a source is pre-selected", () => {
    renderAt("/move/send");
    expect(screen.queryByRole("button", { name: "Choose source" })).toBeNull();
  });

  it("Source step: Choose source button is visible disabled when there are no accounts", () => {
    accountsState.accounts = [];
    renderAt("/move/send");
    const cont = screen.getByRole("button", { name: "Choose source" });
    expect(cont).toBeInTheDocument();
    expect(cont).toBeDisabled();
  });

  it("Source step: clicking a source radio auto-advances to the destination step", async () => {
    const user = userEvent.setup();
    renderAt("/move/send");
    await user.click(screen.getByRole("radio", { name: "Main" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
  });

  it("Source step: pressing Space on a focused source radio auto-advances", async () => {
    const user = userEvent.setup();
    renderAt("/move/send");
    const radio = screen.getByRole("radio", {
      name: "Main",
    }) as HTMLInputElement;
    radio.focus();
    await user.keyboard(" ");
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
  });

  it("Source step: pressing Enter on a focused pre-selected source radio auto-advances", async () => {
    const user = userEvent.setup();
    renderAt("/move/send");
    const radio = screen.getByRole("radio", {
      name: "Main",
    }) as HTMLInputElement;
    expect(radio).toBeChecked();
    radio.focus();
    await user.keyboard("{Enter}");
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
  });

  it("Destination My accounts mode: clicking a destination radio auto-advances to amount", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: "Savings" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });

  it("Destination My accounts mode: selection is preserved after Back from amount step", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: "Savings" }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Savings" })).toBeChecked();
  });

  it("Destination Saved payee mode: selection is preserved after Back from amount step", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    await user.click(screen.getByRole("radio", { name: /Best friend/ }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /Best friend/ })).toBeChecked();
  });

  it("Destination New payee mode: Verify payee button remains visible and does NOT auto-advance on segmented-control or form input", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    await user.type(screen.getByLabelText("Name on the account"), "Jane Doe");
    await user.type(screen.getByLabelText("Sort code"), "123456");
    await user.type(screen.getByLabelText("Account number"), "12345678");
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Verify payee" })).toBeEnabled();
  });

  it("Destination My accounts mode: arrow keys on a focused radio do NOT auto-advance", async () => {
    const user = userEvent.setup();
    renderAt("/move/send");
    await user.click(screen.getByRole("radio", { name: "Main" }));
    const list = screen.getByTestId("my-accounts-list");
    const firstRadio = within(list).getAllByRole(
      "radio",
    )[0]! as HTMLInputElement;
    firstRadio.focus();
    await user.keyboard("{ArrowDown}");
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
  });

  it("Destination segmented-control radios (My accounts / Saved payee / New payee) do NOT auto-advance", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /Saved payee/ }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("radio", { name: /My accounts/ }));
    expect(
      screen.getByRole("heading", { level: 1, name: "Where to?" }),
    ).toBeInTheDocument();
  });

  it("Amount step does NOT auto-advance on input", async () => {
    renderAt("/move/send");
    const user = await walkToAmountViaMyAccounts("Savings");
    await user.type(screen.getByLabelText("Amount"), "12.45");
    expect(
      screen.getByRole("heading", { level: 1, name: "How much?" }),
    ).toBeInTheDocument();
  });
});

describe("TransferPage — verb-led CTA labels", () => {
  beforeEach(() => {
    resetState();
    payeesState.payees = SAVED_PAYEES;
  });

  it("Source step CTA reads 'Choose source' when visible", () => {
    accountsState.accounts = [];
    renderAt("/move/send");
    const cta = screen.getByRole("button", { name: "Choose source" });
    expect(cta).toBeInTheDocument();
    expect(cta).toBeDisabled();
  });

  it("Destination step New-payee CTA reads 'Verify payee'", async () => {
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    expect(
      screen.getByRole("button", { name: "Verify payee" }),
    ).toBeInTheDocument();
  });

  it("Destination step New-payee CTA reads 'Verifying…' while CoPa is pending", async () => {
    confirmState = { isPending: true };
    renderAt("/move/send");
    const user = await advanceFromSource();
    await user.click(screen.getByRole("radio", { name: /New payee/ }));
    await user.type(screen.getByLabelText("Name on the account"), "Jane Doe");
    await user.type(screen.getByLabelText("Sort code"), "123456");
    await user.type(screen.getByLabelText("Account number"), "12345678");
    expect(
      screen.getByRole("button", { name: "Verifying…" }),
    ).toBeInTheDocument();
  });

  it("Amount step CTA reads 'Review'", async () => {
    renderAt("/move/send");
    await walkToAmountViaMyAccounts("Savings");
    expect(screen.getByRole("button", { name: "Review" })).toBeInTheDocument();
  });

  it("Review step CTA reads 'Send money'", async () => {
    renderAt("/move/send");
    await walkToConfirmViaMyAccounts("Savings", "12.45");
    expect(
      screen.getByRole("button", { name: "Send money" }),
    ).toBeInTheDocument();
  });

  it("Review step CTA reads 'Sending…' while the transfer mutation is pending", async () => {
    transferState = { isPending: true, insufficientFunds: false };
    renderAt("/move/send");
    await walkToConfirmViaMyAccounts("Savings", "12.45");
    expect(
      screen.getByRole("button", { name: "Sending…" }),
    ).toBeInTheDocument();
  });

  it("The Transfer flow contains no buttons labelled 'Continue' or 'Send'", async () => {
    renderAt("/move/send");
    expect(screen.queryByRole("button", { name: "Continue" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Send" })).toBeNull();
  });
});

describe("TransferPage — destination prefill via query params", () => {
  beforeEach(() => {
    resetState();
    payeesState.payees = SAVED_PAYEES;
  });

  it("pre-selects New payee mode and pre-fills the three fields from destSortCode / destAccountNumber / destName", async () => {
    renderAt(
      "/move/send?destSortCode=12-34-56&destAccountNumber=12345678&destName=Sarah%20Mitchell",
    );
    await advanceFromSource();
    expect(screen.getByRole("radio", { name: /New payee/ })).toBeChecked();
    expect(screen.getByLabelText("Name on the account")).toHaveValue(
      "Sarah Mitchell",
    );
    expect(screen.getByLabelText("Sort code")).toHaveValue("12-34-56");
    expect(screen.getByLabelText("Account number")).toHaveValue("12345678");
  });

  it("pre-filled new-payee fields remain editable", async () => {
    renderAt(
      "/move/send?destSortCode=12-34-56&destAccountNumber=12345678&destName=Sarah%20Mitchell",
    );
    const user = await advanceFromSource();
    const nameField = screen.getByLabelText("Name on the account");
    await user.clear(nameField);
    await user.type(nameField, "Different Person");
    expect(nameField).toHaveValue("Different Person");
  });

  it("ignores partial destination params and keeps default mode", async () => {
    renderAt("/move/send?destSortCode=12-34-56&destAccountNumber=12345678");
    await advanceFromSource();
    expect(screen.getByRole("radio", { name: /My accounts/ })).toBeChecked();
  });

  it("source ?from= and destination params co-exist", async () => {
    renderAt(
      `/move/send?from=${HOLDER_ACCOUNTS[1]!.accountUid}&destSortCode=12-34-56&destAccountNumber=12345678&destName=Sarah%20Mitchell`,
    );
    expect(screen.getByRole("radio", { name: "Savings" })).toBeChecked();
    await advanceFromSource("Savings");
    expect(screen.getByRole("radio", { name: /New payee/ })).toBeChecked();
    expect(screen.getByLabelText("Name on the account")).toHaveValue(
      "Sarah Mitchell",
    );
  });
});

describe("TransferPage — single-account holder destination UX", () => {
  const ONE_ACCOUNT: AccountResponse[] = [HOLDER_ACCOUNTS[0]!];

  beforeEach(resetState);

  it("when the holder has 1 account, segmented control hides the 'My accounts' option", async () => {
    accountsState.accounts = ONE_ACCOUNT;
    payeesState.payees = SAVED_PAYEES;
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.queryByRole("radio", { name: /My accounts/ })).toBeNull();
    expect(
      screen.getByRole("radio", { name: /Saved payee/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /New payee/ }),
    ).toBeInTheDocument();
  });

  it("when the holder has 1 account AND saved payees exist, initial mode is Saved payee", async () => {
    accountsState.accounts = ONE_ACCOUNT;
    payeesState.payees = SAVED_PAYEES;
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.getByRole("radio", { name: /Saved payee/ })).toBeChecked();
    expect(screen.getByTestId("saved-payee-list")).toBeInTheDocument();
  });

  it("when the holder has 1 account AND no saved payees, initial mode is New payee", async () => {
    accountsState.accounts = ONE_ACCOUNT;
    payeesState.payees = [];
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.getByRole("radio", { name: /New payee/ })).toBeChecked();
    expect(screen.getByTestId("new-payee-body")).toBeInTheDocument();
  });

  it("when the holder has 1 account, the single-account empty-state copy is never rendered", async () => {
    accountsState.accounts = ONE_ACCOUNT;
    payeesState.payees = SAVED_PAYEES;
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.queryByText(/You only have one account/)).toBeNull();
    expect(screen.queryByTestId("my-accounts-empty")).toBeNull();
  });

  it("when the holder has 2+ accounts, segmented control shows all three options", async () => {
    accountsState.accounts = HOLDER_ACCOUNTS;
    renderAt("/move/send");
    await advanceFromSource();
    expect(
      screen.getByRole("radio", { name: /My accounts/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /Saved payee/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("radio", { name: /New payee/ }),
    ).toBeInTheDocument();
  });

  it("when the holder has 2+ accounts, initial mode is My accounts as before", async () => {
    accountsState.accounts = HOLDER_ACCOUNTS;
    payeesState.payees = SAVED_PAYEES;
    renderAt("/move/send");
    await advanceFromSource();
    expect(screen.getByRole("radio", { name: /My accounts/ })).toBeChecked();
    expect(screen.getByTestId("my-accounts-list")).toBeInTheDocument();
  });
});
