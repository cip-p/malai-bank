import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import type {
  AccountResponse,
  AccountHoldersResponse,
  BalanceResponse,
  AccountIdentifiersResponse,
} from "@/api/generated/types.gen";
import { getApiV1AccountsQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { useActiveHolderOverrideStore } from "@/account-holder/useActiveHolderOverrideStore";
import { AccountHeroCard } from "./AccountHeroCard";
import { useBalanceVisibility } from "./useBalanceVisibility";

vi.mock("@/api/generated/sdk.gen", () => ({
  getApiV1AccountHolderAll: vi.fn(),
  getApiV1Accounts: vi.fn(),
  getApiV1AccountsByAccountUidBalance: vi.fn(),
  getApiV1AccountsByAccountUidIdentifiers: vi.fn(),
}));

const sdk = await import("@/api/generated/sdk.gen");
const getApiV1AccountHolderAllMock =
  sdk.getApiV1AccountHolderAll as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidBalanceMock =
  sdk.getApiV1AccountsByAccountUidBalance as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;
const getApiV1AccountsByAccountUidIdentifiersMock =
  sdk.getApiV1AccountsByAccountUidIdentifiers as unknown as ReturnType<
    typeof vi.fn<(...args: unknown[]) => unknown>
  >;

const INDIVIDUAL_UID = "11111111-1111-1111-1111-111111111111";
const ACCOUNT_UID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

const HOLDERS: AccountHoldersResponse = {
  accountHolders: [
    {
      accountHolderUid: INDIVIDUAL_UID,
      accountHolderName: "Ada Lovelace",
      accountHolderType: "INDIVIDUAL",
    },
  ],
};

const ACCOUNT: AccountResponse = {
  accountUid: ACCOUNT_UID,
  accountType: "CURRENT",
  defaultCategory: "PERSONAL",
  currency: "GBP",
  createdAt: "2024-01-01T00:00:00Z",
  name: "Everyday",
};

const BALANCE: BalanceResponse = {
  currency: "GBP",
  amountMinorUnits: 123456,
  availableBalanceMinorUnits: 123456,
  heldAmountMinorUnits: 0,
};

const IDENTIFIERS: AccountIdentifiersResponse = {
  accountIdentifier: "12345678",
  bankIdentifier: "12-34-56",
  accountIdentifiers: [
    {
      identifierType: "UK_DOMESTIC",
      bankIdentifier: "12-34-56",
      accountIdentifier: "12345678",
    },
  ],
};

function makeClient(): QueryClient {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  client.setQueryData(
    getApiV1AccountsQueryKey({
      headers: { "MAL-Account-Holder-Id": INDIVIDUAL_UID },
    }),
    { accounts: [ACCOUNT] },
  );
  return client;
}

function Wrapper({
  children,
  client,
}: {
  children: ReactNode;
  client: QueryClient;
}) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

describe("AccountHeroCard", () => {
  beforeEach(() => {
    getApiV1AccountHolderAllMock.mockReset();
    getApiV1AccountsByAccountUidBalanceMock.mockReset();
    getApiV1AccountsByAccountUidIdentifiersMock.mockReset();
    useActiveHolderOverrideStore.setState({ override: null });
    useBalanceVisibility.setState({ hidden: false });
    getApiV1AccountHolderAllMock.mockResolvedValue({ data: HOLDERS });
  });

  it("renders the cache-primed name and type before balance/identifiers resolve", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockImplementation(
      () => new Promise(() => {}),
    );
    getApiV1AccountsByAccountUidIdentifiersMock.mockImplementation(
      () => new Promise(() => {}),
    );

    const client = makeClient();
    render(
      <Wrapper client={client}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("account-detail-name")).toHaveTextContent(
        "Everyday",
      ),
    );
    expect(screen.getByText("CURRENT · GBP")).toBeInTheDocument();
    expect(screen.getByTestId("hero-balance-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(screen.getByTestId("hero-identifiers-skeleton")).toHaveAttribute(
      "aria-busy",
      "true",
    );
  });

  it("renders the formatted balance and identifiers when both resolve", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£1,234.56"),
    );
    expect(screen.getByTestId("hero-sort-code")).toHaveTextContent("12-34-56");
    expect(screen.getByTestId("hero-account-number")).toHaveTextContent(
      "12345678",
    );
  });

  it("leads with the available balance and names the held amount when money is reserved", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: {
        currency: "GBP",
        amountMinorUnits: 123456,
        availableBalanceMinorUnits: 23456,
        heldAmountMinorUnits: 100000,
      },
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£234.56"),
    );
    expect(screen.getByTestId("hero-balance")).not.toHaveTextContent(
      "£1,234.56",
    );
    expect(screen.getByTestId("hero-balance-held")).toHaveTextContent(
      "£1,000.00 on hold",
    );
  });

  it("omits the held line when nothing is reserved", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£1,234.56"),
    );
    expect(screen.queryByTestId("hero-balance-held")).toBeNull();
  });

  it("masks the held amount along with the balance", async () => {
    useBalanceVisibility.setState({ hidden: true });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: {
        currency: "GBP",
        amountMinorUnits: 123456,
        availableBalanceMinorUnits: 23456,
        heldAmountMinorUnits: 100000,
      },
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£·····"),
    );
    expect(screen.getByTestId("hero-balance-held")).not.toHaveTextContent(
      "£1,000.00",
    );
  });

  it("renders the balance via the static Money primitive with no live-region role", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    const heroBalance = await screen.findByTestId("hero-balance");
    expect(heroBalance).toHaveTextContent("£1,234.56");
    expect(heroBalance).toHaveAttribute("aria-label", "£1,234.56");
    expect(heroBalance).not.toHaveAttribute("role", "status");
    expect(heroBalance.querySelector('[data-testid="money"]')).not.toBeNull();
  });

  it("renders an inline em-dash when the balance query errors", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockRejectedValue(
      new Error("boom"),
    );
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance-error")).toHaveTextContent("—"),
    );
  });

  it("renders an inline em-dash when the identifiers query errors", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockRejectedValue(
      new Error("boom"),
    );

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-identifiers-error")).toHaveTextContent(
        "—",
      ),
    );
  });

  it("masks the hero balance by default when useBalanceVisibility.hidden is true", async () => {
    useBalanceVisibility.setState({ hidden: true });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£·····"),
    );
  });

  it("reveals the hero balance locally without flipping the master store", async () => {
    useBalanceVisibility.setState({ hidden: true });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    const user = userEvent.setup();
    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£·····"),
    );

    await user.click(screen.getByTestId("hero-balance-visibility-toggle"));

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£1,234.56"),
    );
    expect(useBalanceVisibility.getState().hidden).toBe(true);
  });

  it("hides the hero balance locally without flipping the master store when master is visible", async () => {
    useBalanceVisibility.setState({ hidden: false });
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    const user = userEvent.setup();
    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£1,234.56"),
    );

    await user.click(screen.getByTestId("hero-balance-visibility-toggle"));

    await waitFor(() =>
      expect(screen.getByTestId("hero-balance")).toHaveTextContent("£·····"),
    );
    expect(useBalanceVisibility.getState().hidden).toBe(false);
  });

  it("copies the sort-code to the clipboard when the copy button is clicked", async () => {
    getApiV1AccountsByAccountUidBalanceMock.mockResolvedValue({
      data: BALANCE,
    });
    getApiV1AccountsByAccountUidIdentifiersMock.mockResolvedValue({
      data: IDENTIFIERS,
    });

    const writeText = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText },
    });

    render(
      <Wrapper client={makeClient()}>
        <AccountHeroCard accountUid={ACCOUNT_UID} />
      </Wrapper>,
    );

    const button = await screen.findByTestId("copy-sort-code");
    await user.click(button);

    await waitFor(() => expect(writeText).toHaveBeenCalledWith("12-34-56"));
  });
});
