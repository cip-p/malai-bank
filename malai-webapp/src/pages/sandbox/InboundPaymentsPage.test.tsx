import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AccountResponse,
  CounterpartyResponse,
} from "@/api/generated/types.gen";
import type { UseCounterpartiesResult } from "@/sandbox/useCounterparties";
import type { UseAccountsResult } from "@/accounts/useAccounts";
import type {
  InboundPaymentInput,
  UseInboundPaymentMutationOptions,
  UseInboundPaymentMutationResult,
} from "@/sandbox/useInboundPaymentMutation";

vi.mock("@/sandbox/useCounterparties", () => ({
  useCounterparties: vi.fn(),
}));
vi.mock("@/accounts/useAccounts", () => ({
  useAccounts: vi.fn(),
}));
vi.mock("@/sandbox/useInboundPaymentMutation", () => ({
  useInboundPaymentMutation: vi.fn(),
}));

const useCounterpartiesHook = await import("@/sandbox/useCounterparties");
const useAccountsHook = await import("@/accounts/useAccounts");
const useInboundPaymentMutationHook =
  await import("@/sandbox/useInboundPaymentMutation");

const useCounterpartiesMock =
  useCounterpartiesHook.useCounterparties as unknown as ReturnType<
    typeof vi.fn<() => UseCounterpartiesResult>
  >;
const useAccountsMock = useAccountsHook.useAccounts as unknown as ReturnType<
  typeof vi.fn<() => UseAccountsResult>
>;
const useInboundPaymentMutationMock =
  useInboundPaymentMutationHook.useInboundPaymentMutation as unknown as ReturnType<
    typeof vi.fn<
      (
        options?: UseInboundPaymentMutationOptions,
      ) => UseInboundPaymentMutationResult
    >
  >;

import { InboundPaymentsPage } from "./InboundPaymentsPage";

function wrap(ui: React.ReactNode, initialEntries: string[] = ["/"]) {
  return <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>;
}

const SARAH: CounterpartyResponse = {
  bank: "Stub Bank",
  nameOnRecord: "Sarah Mitchell",
  sortCode: "12-34-56",
  accountNumber: "12345678",
  outboundOutcome: "SETTLES",
};

const BOB: CounterpartyResponse = {
  bank: "Returning Bank",
  nameOnRecord: "Bouncy Bob",
  sortCode: "20-00-00",
  accountNumber: "20000000",
  outboundOutcome: "RETURNS",
  returnReason: "ACCOUNT_NAME_MISMATCH",
};

const CURRENT: AccountResponse = {
  accountUid: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  accountType: "CURRENT",
  defaultCategory: "GENERAL",
  currency: "GBP",
  createdAt: "2026-05-01T10:00:00Z",
  name: "Current",
};

const SAVINGS: AccountResponse = {
  accountUid: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  accountType: "SAVINGS",
  defaultCategory: "SAVINGS",
  currency: "GBP",
  createdAt: "2026-05-02T10:00:00Z",
  name: "Savings",
};

function readyCounterparties(rows: CounterpartyResponse[]): void {
  useCounterpartiesMock.mockReturnValue({
    counterparties: rows,
    isPending: false,
    isError: false,
    error: null,
  });
}

function readyAccounts(rows: AccountResponse[]): void {
  useAccountsMock.mockReturnValue({
    accounts: rows,
    isPending: false,
    isError: false,
    error: null,
  });
}

type MutationSpy = {
  submit: ReturnType<typeof vi.fn>;
  getCapturedOnSuccess: () => (() => void) | null;
  setPending: (pending: boolean) => void;
};

function withMutationSpy(): MutationSpy {
  const submit = vi.fn();
  const state = { captured: null as (() => void) | null, pending: false };
  const install = () => {
    useInboundPaymentMutationMock.mockImplementation((options) => {
      state.captured = options?.onSuccess ?? null;
      return { submit, isPending: state.pending };
    });
  };
  install();
  return {
    submit,
    getCapturedOnSuccess: () => state.captured,
    setPending: (next: boolean) => {
      state.pending = next;
      install();
    },
  };
}

describe("InboundPaymentsPage", () => {
  beforeEach(() => {
    useCounterpartiesMock.mockReset();
    useAccountsMock.mockReset();
    useInboundPaymentMutationMock.mockReset();
  });

  it("renders defaults: form heading, rail=FPS, first counterparty + first account selected", () => {
    readyCounterparties([SARAH, BOB]);
    readyAccounts([CURRENT, SAVINGS]);
    withMutationSpy();

    render(wrap(<InboundPaymentsPage />));

    expect(
      screen.getByRole("heading", { name: "Inbound payments" }),
    ).toBeInTheDocument();
    const originator = screen.getByTestId(
      "inbound-originator",
    ) as HTMLSelectElement;
    expect(originator.value).toBe(`${SARAH.sortCode}|${SARAH.accountNumber}`);
    const credited = screen.getByTestId(
      "inbound-credited-account",
    ) as HTMLSelectElement;
    expect(credited.value).toBe(CURRENT.accountUid);
    const fps = screen.getByLabelText("FPS") as HTMLInputElement;
    expect(fps.checked).toBe(true);
  });

  it("pre-selects the matching counterparty when deep-link params point at it", () => {
    readyCounterparties([SARAH, BOB]);
    readyAccounts([CURRENT]);
    withMutationSpy();

    render(
      wrap(<InboundPaymentsPage />, [
        `/sandbox/inbound-payments?originatorSortCode=${encodeURIComponent(BOB.sortCode)}&originatorAccountNumber=${BOB.accountNumber}&originatorName=${encodeURIComponent(BOB.nameOnRecord)}`,
      ]),
    );

    const originator = screen.getByTestId(
      "inbound-originator",
    ) as HTMLSelectElement;
    expect(originator.value).toBe(`${BOB.sortCode}|${BOB.accountNumber}`);
  });

  it("falls back to the default counterparty when deep-link params do not match a catalogue row", () => {
    readyCounterparties([SARAH, BOB]);
    readyAccounts([CURRENT]);
    withMutationSpy();

    render(
      wrap(<InboundPaymentsPage />, [
        `/sandbox/inbound-payments?originatorSortCode=99-99-99&originatorAccountNumber=99999999&originatorName=Nobody`,
      ]),
    );

    const originator = screen.getByTestId(
      "inbound-originator",
    ) as HTMLSelectElement;
    expect(originator.value).toBe(`${SARAH.sortCode}|${SARAH.accountNumber}`);
  });

  it("with a single Account, pre-selects it and renders no credited-account picker", () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    withMutationSpy();

    render(wrap(<InboundPaymentsPage />));

    expect(
      screen.queryByTestId("inbound-credited-account"),
    ).not.toBeInTheDocument();
  });

  it("submits the request with the expected body when all fields are valid", async () => {
    readyCounterparties([SARAH, BOB]);
    readyAccounts([CURRENT, SAVINGS]);
    const spy = withMutationSpy();
    const user = userEvent.setup();

    render(wrap(<InboundPaymentsPage />));

    await user.type(screen.getByLabelText("Amount"), "25.00");
    await user.type(
      screen.getByLabelText("Reference (optional)"),
      "RENT MARCH",
    );
    await user.click(screen.getByTestId("inbound-submit"));

    expect(spy.submit).toHaveBeenCalledTimes(1);
    expect(spy.submit).toHaveBeenCalledWith({
      rail: "FPS",
      creditedAccountId: CURRENT.accountUid,
      originatorSortCode: SARAH.sortCode,
      originatorAccountNumber: SARAH.accountNumber,
      originatorName: SARAH.nameOnRecord,
      amountMinorUnits: 2500,
      reference: "RENT MARCH",
    } satisfies InboundPaymentInput);
  });

  it("respects rail = BACS when chosen", async () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    const spy = withMutationSpy();
    const user = userEvent.setup();

    render(wrap(<InboundPaymentsPage />));

    await user.click(screen.getByLabelText("BACS"));
    await user.type(screen.getByLabelText("Amount"), "10.00");
    await user.click(screen.getByTestId("inbound-submit"));

    expect(spy.submit).toHaveBeenCalledWith(
      expect.objectContaining({ rail: "BACS" }),
    );
  });

  it("disables the submit button while pending", () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    const spy = withMutationSpy();
    spy.setPending(true);

    render(wrap(<InboundPaymentsPage />));

    expect(screen.getByTestId("inbound-submit")).toBeDisabled();
  });

  it("double-click does not fire two requests", async () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    const spy = withMutationSpy();
    spy.setPending(true);
    const user = userEvent.setup();

    render(wrap(<InboundPaymentsPage />));

    const button = screen.getByTestId("inbound-submit");
    await user.click(button);
    await user.click(button);

    expect(spy.submit).not.toHaveBeenCalled();
  });

  it("rejects negative, zero, > Â£999,999.99 and > 2 decimals", async () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    const spy = withMutationSpy();
    const user = userEvent.setup();

    render(wrap(<InboundPaymentsPage />));

    const submit = screen.getByTestId("inbound-submit");
    const amount = screen.getByLabelText("Amount") as HTMLInputElement;

    await user.type(amount, "-10");
    await user.click(submit);
    expect(spy.submit).not.toHaveBeenCalled();
    await user.clear(amount);

    await user.type(amount, "0");
    await user.click(submit);
    expect(spy.submit).not.toHaveBeenCalled();
    await user.clear(amount);

    await user.type(amount, "1000000.00");
    await user.click(submit);
    expect(spy.submit).not.toHaveBeenCalled();
    await user.clear(amount);

    await user.type(amount, "10.123");
    await user.click(submit);
    expect(spy.submit).not.toHaveBeenCalled();
  });

  it("on success resets the form (amount cleared, reference cleared)", async () => {
    readyCounterparties([SARAH]);
    readyAccounts([CURRENT]);
    const spy = withMutationSpy();
    const user = userEvent.setup();

    render(wrap(<InboundPaymentsPage />));

    const amount = screen.getByLabelText("Amount") as HTMLInputElement;
    const reference = screen.getByLabelText(
      "Reference (optional)",
    ) as HTMLInputElement;
    await user.type(amount, "12.34");
    await user.type(reference, "Hello");

    await user.click(screen.getByTestId("inbound-submit"));
    expect(spy.submit).toHaveBeenCalled();

    act(() => {
      spy.getCapturedOnSuccess()?.();
    });

    expect((screen.getByLabelText("Amount") as HTMLInputElement).value).toBe(
      "",
    );
    expect(
      (screen.getByLabelText("Reference (optional)") as HTMLInputElement).value,
    ).toBe("");
  });

  it("replaces the form with a failure fact when the directory or accounts cannot load", () => {
    useCounterpartiesMock.mockReturnValue({
      counterparties: null,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    });
    readyAccounts([CURRENT]);
    withMutationSpy();

    render(wrap(<InboundPaymentsPage />));

    expect(screen.getByTestId("inbound-error")).toHaveTextContent(
      "We couldn’t load the counterparties and accounts this form needs.",
    );
    expect(screen.queryByTestId("inbound-submit")).not.toBeInTheDocument();
    expect(screen.queryByTestId("inbound-originator")).not.toBeInTheDocument();
  });
});
