import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type {
  AmlAlertView,
  AmlCaseDetailResponse,
  AmlEvidencePayment,
} from "@/api/generated/types.gen";
import type { UseAmlCaseResult } from "@/ops/useAmlCase";

vi.mock("@/ops/useAmlCase", async () => {
  const actual =
    await vi.importActual<typeof import("@/ops/useAmlCase")>(
      "@/ops/useAmlCase",
    );
  return { ...actual, useAmlCase: vi.fn() };
});

vi.mock("@/ops/useSarDraft", () => ({
  useSarDraft: vi.fn(() => ({
    invocation: { kind: "idle" },
    requestDraft: vi.fn(),
  })),
}));

vi.mock("@/ops/useCloseCase", () => ({
  useCloseCase: vi.fn(() => ({
    close: vi.fn(),
    pending: null,
    outcome: null,
  })),
}));

const hookModule = await import("@/ops/useAmlCase");
const useAmlCaseMock = hookModule.useAmlCase as unknown as ReturnType<
  typeof vi.fn<(caseId: string) => UseAmlCaseResult>
>;

import { CasePage } from "./CasePage";

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";

function makeEvidence(
  overrides: Partial<AmlEvidencePayment>,
): AmlEvidencePayment {
  return {
    paymentId: "44444444-4444-4444-4444-444444444444",
    contentHash: "ab12cd34ef567890ab12cd34ef567890",
    rail: "FPS",
    direction: "OUTBOUND",
    amountMinorUnits: 340000,
    currency: "GBP",
    createdAt: "2026-07-20T09:05:00Z",
    beneficiaryName: "Carol Singh",
    beneficiarySortCode: "30-00-03",
    beneficiaryAccountNo: "11223344",
    ...overrides,
  };
}

function makeAlert(overrides: Partial<AmlAlertView>): AmlAlertView {
  return {
    decisionId: "77777777-7777-7777-7777-777777777777",
    typology: "STRUCTURING",
    riskScore: 72,
    raisedAt: "2026-07-20T09:10:00Z",
    evidence: [makeEvidence({})],
    ...overrides,
  };
}

function makeDetail(
  overrides: Partial<AmlCaseDetailResponse>,
): AmlCaseDetailResponse {
  return {
    caseId: CASE_ID,
    accountId: "9f8e7d6c-2222-2222-2222-222222222222",
    status: "INVESTIGATING",
    openedAt: "2026-07-20T09:00:00Z",
    updatedAt: "2026-07-20T09:30:00Z",
    alerts: [makeAlert({})],
    ...overrides,
  };
}

function served(detail: AmlCaseDetailResponse): UseAmlCaseResult {
  return {
    detail,
    isPending: false,
    isNotFound: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

function renderPage(caseId = CASE_ID) {
  return render(
    <MemoryRouter initialEntries={[`/ops/cases/${caseId}`]}>
      <Routes>
        <Route path="/ops/cases/:caseId" element={<CasePage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CasePage", () => {
  beforeEach(() => {
    useAmlCaseMock.mockReset();
    useAmlCaseMock.mockReturnValue(served(makeDetail({})));
  });

  it("reads the investigation named by the route", () => {
    renderPage();
    expect(useAmlCaseMock).toHaveBeenLastCalledWith(CASE_ID);
  });

  it("renders an h1 'Case' focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Case");
    expect(heading).toHaveFocus();
  });

  it("offers the way back to the Case list", () => {
    renderPage();
    expect(screen.getByTestId("case-back-link")).toHaveAttribute(
      "href",
      "/ops/cases",
    );
  });

  it("carries the shortened case id with the full id a copy away", () => {
    renderPage();
    const id = screen.getByTestId("case-id");
    expect(id).toHaveTextContent("1a2b3c4d");
    expect(id).not.toHaveTextContent("1a2b3c4d-1111");
    expect(id).toHaveAttribute("title", CASE_ID);
    expect(
      within(id).getByRole("button", { name: "Copy case id" }),
    ).toBeInTheDocument();
  });

  it("names the subject account under investigation", () => {
    renderPage();
    const account = screen.getByTestId("case-account");
    expect(account).toHaveTextContent("9f8e7d6c");
    expect(account).toHaveAttribute(
      "title",
      "9f8e7d6c-2222-2222-2222-222222222222",
    );
  });

  it("wears the Case status as a tag", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ status: "CLOSED_REPORTED" })),
    );
    renderPage();
    expect(screen.getByTestId("case-status")).toHaveTextContent(
      "Closed reported",
    );
  });

  it("reads opened and updated as recency, the absolute moment kept nearby", () => {
    renderPage();
    expect(screen.getByTestId("case-opened")).toHaveTextContent(/ago|just now/);
    expect(screen.getByTestId("case-opened")).toHaveTextContent(/2026/);
    expect(screen.getByTestId("case-updated")).toHaveTextContent(
      /ago|just now/,
    );
  });

  it("assembles every linked Alert in the order the bank raised them", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({
              decisionId: "oldest-77-7777-7777-7777-777777777777",
              typology: "STRUCTURING",
            }),
            makeAlert({
              decisionId: "newest-88-8888-8888-8888-888888888888",
              typology: "RAPID_IN_OUT",
            }),
          ],
        }),
      ),
    );
    renderPage();
    const alerts = screen.getAllByTestId("case-alert");
    expect(alerts).toHaveLength(2);
    expect(alerts[0]).toHaveTextContent("Structuring");
    expect(alerts[1]).toHaveTextContent("Rapid in-out");
  });

  it("says what monitoring concluded — typology, risk score, and when it was raised", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({ typology: "HIGH_RISK_CORRIDOR", riskScore: 88 }),
          ],
        }),
      ),
    );
    renderPage();
    expect(screen.getByTestId("alert-typology")).toHaveTextContent(
      "High-risk corridor",
    );
    expect(screen.getByTestId("alert-risk-score")).toHaveTextContent("88");
    expect(screen.getByTestId("alert-raised")).toHaveTextContent(/2026/);
  });

  it("wears the ruling in force on the Alert", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ alerts: [makeAlert({ rulingInForce: "ACCEPT" })] })),
    );
    renderPage();
    expect(screen.getByTestId("alert-ruling")).toHaveTextContent("Accept");
  });

  it("says an unruled Alert awaits its ruling rather than saying nothing", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ alerts: [makeAlert({ rulingInForce: undefined })] })),
    );
    renderPage();
    expect(screen.getByTestId("alert-ruling")).toHaveTextContent(
      "Awaiting ruling",
    );
  });

  it("sends every Alert to its Decision view, where the ruling lives", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({ decisionId: "77777777-7777-7777-7777-777777777777" }),
          ],
        }),
      ),
    );
    renderPage();
    expect(screen.getByTestId("alert-decision-link")).toHaveAttribute(
      "href",
      `/ops/cases/${CASE_ID}/decisions/77777777-7777-7777-7777-777777777777`,
    );
  });

  it("rules on nothing itself — no ruling affordance on the Case view", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ alerts: [makeAlert({ rulingInForce: undefined })] })),
    );
    renderPage();
    expect(
      screen.queryByRole("button", { name: /accept|reject|rule/i }),
    ).not.toBeInTheDocument();
  });

  it("lists each Alert's evidence in the order the payments settled", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({
              evidence: [
                makeEvidence({ amountMinorUnits: 340000 }),
                makeEvidence({ amountMinorUnits: 350000 }),
              ],
            }),
          ],
        }),
      ),
    );
    renderPage();
    const rows = screen.getAllByTestId("evidence-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("£3,400.00");
    expect(rows[1]).toHaveTextContent("£3,500.00");
  });

  it("names each evidence payment's rail, direction, amount, and moment", () => {
    renderPage();
    const row = screen.getByTestId("evidence-row");
    expect(within(row).getByTestId("evidence-rail")).toHaveTextContent(
      "Faster Payments",
    );
    expect(within(row).getByTestId("evidence-direction")).toHaveTextContent(
      "Outbound",
    );
    expect(within(row).getByTestId("evidence-amount")).toHaveTextContent(
      "£3,400.00",
    );
    expect(within(row).getByTestId("evidence-created")).toHaveTextContent(
      /2026/,
    );
  });

  it("carries the UK counterparty identifiers the payment was made against", () => {
    renderPage();
    const counterparty = screen.getByTestId("evidence-counterparty");
    expect(counterparty).toHaveTextContent("Carol Singh");
    expect(counterparty).toHaveTextContent("30-00-03");
    expect(counterparty).toHaveTextContent("11223344");
  });

  it("carries IBAN and BIC instead when the rail is SWIFT", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({
              evidence: [
                makeEvidence({
                  rail: "SWIFT",
                  beneficiarySortCode: undefined,
                  beneficiaryAccountNo: undefined,
                  beneficiaryIban: "IR820540102680020817909002",
                  beneficiaryBic: "BKMTIRTH",
                }),
              ],
            }),
          ],
        }),
      ),
    );
    renderPage();
    const counterparty = screen.getByTestId("evidence-counterparty");
    expect(counterparty).toHaveTextContent("IR820540102680020817909002");
    expect(counterparty).toHaveTextContent("BKMTIRTH");
    expect(screen.getByTestId("evidence-rail")).toHaveTextContent("SWIFT");
  });

  it("says nothing it does not know about a counterparty", () => {
    useAmlCaseMock.mockReturnValue(
      served(
        makeDetail({
          alerts: [
            makeAlert({
              evidence: [
                makeEvidence({
                  rail: "BOOK_TRANSFER",
                  beneficiaryName: undefined,
                  beneficiarySortCode: undefined,
                  beneficiaryAccountNo: undefined,
                }),
              ],
            }),
          ],
        }),
      ),
    );
    renderPage();
    expect(screen.getByTestId("evidence-counterparty")).toHaveTextContent("—");
  });

  it("pins each evidence payment by the content hash the Decider saw, shortened and copyable", () => {
    renderPage();
    const hash = screen.getByTestId("evidence-hash");
    expect(hash).toHaveTextContent("ab12cd34");
    expect(hash).not.toHaveTextContent("ab12cd34ef567890ab12cd34ef567890");
    expect(hash).toHaveAttribute("title", "ab12cd34ef567890ab12cd34ef567890");
    expect(
      within(hash.parentElement as HTMLElement).getByRole("button", {
        name: /content hash/i,
      }),
    ).toBeInTheDocument();
  });

  it("says an Alert carries no evidence rather than rendering an empty table", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ alerts: [makeAlert({ evidence: [] })] })),
    );
    renderPage();
    expect(screen.getByTestId("evidence-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("evidence-row")).not.toBeInTheDocument();
  });

  it("says an investigation with no Alerts is exactly that", () => {
    useAmlCaseMock.mockReturnValue(served(makeDetail({ alerts: [] })));
    renderPage();
    expect(screen.getByTestId("case-alerts-empty")).toBeInTheDocument();
    expect(screen.queryByTestId("case-alert")).not.toBeInTheDocument();
  });

  it("designs a not-found state for an unknown case id", () => {
    useAmlCaseMock.mockReturnValue({
      detail: null,
      isPending: false,
      isNotFound: true,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage("00000000-0000-0000-0000-000000000000");
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Case not found");
    expect(heading).toHaveFocus();
    expect(screen.getByTestId("case-not-found")).toBeInTheDocument();
    expect(screen.getByTestId("case-back-link")).toHaveAttribute(
      "href",
      "/ops/cases",
    );
  });

  it("shows a skeleton while the investigation is in flight", () => {
    useAmlCaseMock.mockReturnValue({
      detail: null,
      isPending: true,
      isNotFound: false,
      isError: false,
      error: null,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("case-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("case-alert")).not.toBeInTheDocument();
    expect(screen.queryByTestId("sar-panel")).not.toBeInTheDocument();
  });

  it("carries the SAR panel beneath the alerts, on the case it reads", () => {
    renderPage();
    const panel = screen.getByTestId("sar-panel");
    expect(panel).toBeInTheDocument();
    expect(
      screen
        .getByRole("heading", { level: 2, name: "Alerts" })
        .compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("carries the Close actions last, beneath the SAR panel", () => {
    renderPage();
    const actions = screen.getByTestId("close-actions");
    expect(
      screen.getByTestId("sar-panel").compareDocumentPosition(actions) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("offers no way to close a case that is already closed", () => {
    useAmlCaseMock.mockReturnValue(
      served(makeDetail({ status: "CLOSED_DISMISSED" })),
    );
    renderPage();
    expect(screen.queryByTestId("close-actions")).not.toBeInTheDocument();
  });

  it("offers a retry when the read fails, without a reload", async () => {
    const user = userEvent.setup();
    const refetch = vi.fn();
    useAmlCaseMock.mockReturnValue({
      detail: null,
      isPending: false,
      isNotFound: false,
      isError: true,
      error: new Error("boom"),
      refetch,
    });
    renderPage();
    expect(screen.getByTestId("case-error")).toBeInTheDocument();
    await user.click(screen.getByTestId("case-retry"));
    expect(refetch).toHaveBeenCalledTimes(1);
  });
});
