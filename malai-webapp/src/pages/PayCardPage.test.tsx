import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type {
  AccountResponse,
  PaymentResponse,
} from "@/api/generated/types.gen";
import type {
  TransferInput,
  UseTransferOptions,
  UseTransferResult,
} from "@/move-money/useTransfer";
import { PayCardPage } from "./PayCardPage";

vi.mock("@/account-holder/useActiveAccountHolder", () => ({
  useActiveAccountHolder: vi.fn(),
}));
vi.mock("@/accounts/useAccount", () => ({ useAccount: vi.fn() }));
vi.mock("@/accounts/useAccounts", () => ({ useAccounts: vi.fn() }));
vi.mock("@/accounts/useIdentifiers", () => ({ useIdentifiers: vi.fn() }));
vi.mock("@/cards/useCreditSummary", () => ({ useCreditSummary: vi.fn() }));
vi.mock("@/move-money/useTransfer", () => ({ useTransfer: vi.fn() }));
vi.mock("@/move-money/PaymentOutcome", () => ({
  PaymentOutcome: () => <div data-testid="payment-outcome-stub" />,
}));

const { useActiveAccountHolder } =
  await import("@/account-holder/useActiveAccountHolder");
const { useAccount } = await import("@/accounts/useAccount");
const { useAccounts } = await import("@/accounts/useAccounts");
const { useIdentifiers } = await import("@/accounts/useIdentifiers");
const { useCreditSummary } = await import("@/cards/useCreditSummary");
const { useTransfer } = await import("@/move-money/useTransfer");

const useActiveAccountHolderMock =
  useActiveAccountHolder as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const useAccountMock = useAccount as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useAccountsMock = useAccounts as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useIdentifiersMock = useIdentifiers as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useCreditSummaryMock = useCreditSummary as unknown as ReturnType<
  typeof vi.fn<(...args: unknown[]) => unknown>
>;
const useTransferMock = useTransfer as unknown as ReturnType<
  typeof vi.fn<(options?: UseTransferOptions) => UseTransferResult>
>;

const CREDIT_UID = "55555555-5555-5555-5555-555555555555";
const SOURCE_UID = "22222222-2222-2222-2222-222222222222";

const CREDIT_ACCOUNT: AccountResponse = {
  accountUid: CREDIT_UID,
  accountType: "CREDIT_CARD",
  defaultCategory: "44444444-4444-4444-4444-444444444444",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Credit Card",
};

const SOURCE_ACCOUNT: AccountResponse = {
  ...CREDIT_ACCOUNT,
  accountUid: SOURCE_UID,
  accountType: "CURRENT",
  name: "Everyday",
};

const transferSpy = vi.fn<(input: TransferInput) => void>();
let transferOptions: UseTransferOptions = {};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/accounts/${CREDIT_UID}/pay-card`]}>
        <Routes>
          <Route
            path="/accounts/:accountUid/pay-card"
            element={<PayCardPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("PayCardPage", () => {
  beforeEach(() => {
    useActiveAccountHolderMock.mockReset();
    useAccountMock.mockReset();
    useAccountsMock.mockReset();
    useIdentifiersMock.mockReset();
    useCreditSummaryMock.mockReset();
    useTransferMock.mockReset();
    transferSpy.mockReset();
    transferOptions = {};

    useActiveAccountHolderMock.mockReturnValue({
      status: "ready",
      activeHolder: {
        accountHolderUid: "11111111-1111-1111-1111-111111111111",
        accountHolderType: "INDIVIDUAL",
      },
      error: null,
    });
    useAccountMock.mockReturnValue({
      account: CREDIT_ACCOUNT,
      status: "ready",
    });
    useAccountsMock.mockReturnValue({
      accounts: [SOURCE_ACCOUNT, CREDIT_ACCOUNT],
      isPending: false,
      isError: false,
      error: null,
    });
    useIdentifiersMock.mockReturnValue({
      identifiers: {
        accountIdentifier: "87654321",
        bankIdentifier: "65-43-21",
        accountIdentifiers: [
          {
            identifierType: "UK_DOMESTIC",
            bankIdentifier: "65-43-21",
            accountIdentifier: "87654321",
          },
        ],
      },
      isPending: false,
      isError: false,
      error: null,
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
    useTransferMock.mockImplementation((options?: UseTransferOptions) => {
      transferOptions = options ?? {};
      return {
        transfer: transferSpy,
        isPending: false,
        insufficientFunds: false,
        reset: vi.fn(),
      };
    });
  });

  it("defaults to the minimum payment preset and shows all three options", () => {
    renderPage();
    expect(screen.getByTestId("pay-card-preset-minimum")).toBeChecked();
    expect(screen.getByTestId("pay-card-preset-statement")).not.toBeChecked();
    expect(screen.getByTestId("pay-card-preset-custom")).not.toBeChecked();
    expect(screen.getByTestId("pay-card-submit")).toHaveTextContent(
      "Pay £8.13",
    );
  });

  it("pays the statement balance to the credit account's identifiers", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("pay-card-preset-statement"));
    await user.click(screen.getByTestId("pay-card-submit"));

    expect(transferSpy).toHaveBeenCalledWith({
      fromAccountUid: SOURCE_UID,
      toSortCode: "65-43-21",
      toAccountNumber: "87654321",
      amountMinorUnits: 32500,
      description: "Credit card payment",
    });
  });

  it("pays a custom amount", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("pay-card-preset-custom"));
    await user.type(screen.getByTestId("pay-card-custom-amount"), "25.00");
    await user.click(screen.getByTestId("pay-card-submit"));

    expect(transferSpy).toHaveBeenCalledWith(
      expect.objectContaining({ amountMinorUnits: 2500 }),
    );
  });

  it("renders the payment outcome once the transfer succeeds", async () => {
    renderPage();
    const payment: PaymentResponse = {
      paymentId: "99999999-9999-9999-9999-999999999999",
      fromAccountId: SOURCE_UID,
      amountMinorUnits: 813,
      currency: "GBP",
      rail: "BOOK_TRANSFER",
      direction: "OUTBOUND",
      state: "SETTLED",
      createdAt: "2026-07-12T10:00:00Z",
      endToEndId: "e2e-1",
    };
    const { act } = await import("@testing-library/react");
    act(() => {
      transferOptions.onSuccess?.(payment);
    });
    expect(screen.getByTestId("payment-outcome-stub")).toBeInTheDocument();
  });

  it("describes an in-credit card and offers only the custom amount", () => {
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
    renderPage();
    expect(screen.getByText(/in credit\./)).toBeInTheDocument();
    expect(screen.queryByText(/You owe/)).toBeNull();
    expect(screen.queryByTestId("pay-card-preset-minimum")).toBeNull();
    expect(screen.queryByTestId("pay-card-preset-statement")).toBeNull();
    expect(screen.getByTestId("pay-card-preset-custom")).toBeChecked();
  });

  it("explains when there is no source account to pay from", () => {
    useAccountsMock.mockReturnValue({
      accounts: [CREDIT_ACCOUNT],
      isPending: false,
      isError: false,
      error: null,
    });
    renderPage();
    expect(screen.getByTestId("pay-card-no-source")).toBeInTheDocument();
  });

  it("rejects a non-credit account", () => {
    useAccountMock.mockReturnValue({
      account: SOURCE_ACCOUNT,
      status: "ready",
    });
    renderPage();
    expect(screen.getByTestId("pay-card-not-credit")).toBeInTheDocument();
  });
});
