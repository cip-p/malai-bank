import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { AmlCaseStatus, AmlCaseSummary } from "@/api/generated/types.gen";
import type { UseAmlCasesResult } from "@/ops/useAmlCases";

vi.mock("@/ops/useAmlCases", async () => {
  const actual =
    await vi.importActual<typeof import("@/ops/useAmlCases")>(
      "@/ops/useAmlCases",
    );
  return { ...actual, useAmlCases: vi.fn() };
});

const hookModule = await import("@/ops/useAmlCases");
const useAmlCasesMock = hookModule.useAmlCases as unknown as ReturnType<
  typeof vi.fn<(status: AmlCaseStatus | null) => UseAmlCasesResult>
>;

import { CasesPage } from "./CasesPage";

function makeCase(overrides: Partial<AmlCaseSummary>): AmlCaseSummary {
  return {
    caseId: "1a2b3c4d-1111-1111-1111-111111111111",
    accountId: "9f8e7d6c-2222-2222-2222-222222222222",
    status: "OPEN",
    alertCount: 1,
    openedAt: "2026-07-20T09:00:00Z",
    updatedAt: "2026-07-20T09:30:00Z",
    ...overrides,
  };
}

const NO_DEPTHS = {
  OPEN: 0,
  INVESTIGATING: 0,
  CLOSED_DISMISSED: 0,
  CLOSED_REPORTED: 0,
};

function served(
  cases: AmlCaseSummary[],
  depths: Partial<UseAmlCasesResult["depths"]> = {},
): UseAmlCasesResult {
  return {
    cases,
    depths: { ...NO_DEPTHS, ...depths },
    total: cases.length,
    isPending: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/ops/cases"]}>
      <Routes>
        <Route path="/ops/cases" element={<CasesPage />} />
        <Route
          path="/ops/cases/:caseId"
          element={<p data-testid="case-probe" />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CasesPage", () => {
  beforeEach(() => {
    useAmlCasesMock.mockReset();
    useAmlCasesMock.mockReturnValue(served([]));
  });

  it("renders an h1 'Cases' focused on mount", () => {
    renderPage();
    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toHaveTextContent("Cases");
    expect(heading).toHaveFocus();
  });

  it("lands on every investigation, narrowed by no status", () => {
    renderPage();
    expect(useAmlCasesMock).toHaveBeenLastCalledWith(null);
  });

  it("shows a row per Case in the order the bank opened them", () => {
    useAmlCasesMock.mockReturnValue(
      served([
        makeCase({ caseId: "newest-11-1111-1111-1111-111111111111" }),
        makeCase({ caseId: "oldest-22-2222-2222-2222-222222222222" }),
      ]),
    );
    renderPage();
    const rows = screen.getAllByTestId("case-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("newest-1");
    expect(rows[1]).toHaveTextContent("oldest-2");
  });

  it("carries the shortened case id with the full id a hover away", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    const id = screen.getByTestId("case-id");
    expect(id).toHaveTextContent("1a2b3c4d");
    expect(id).not.toHaveTextContent("1a2b3c4d-1111");
    expect(id).toHaveAttribute("title", "1a2b3c4d-1111-1111-1111-111111111111");
  });

  it("names the subject account under investigation", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    const account = screen.getByTestId("case-account");
    expect(account).toHaveTextContent("9f8e7d6c");
    expect(account).toHaveAttribute(
      "title",
      "9f8e7d6c-2222-2222-2222-222222222222",
    );
  });

  it("wears the Case status as a tag", () => {
    useAmlCasesMock.mockReturnValue(
      served([makeCase({ status: "CLOSED_REPORTED" })]),
    );
    renderPage();
    expect(screen.getByTestId("case-status")).toHaveTextContent(
      "Closed reported",
    );
  });

  it("counts the Alerts linked to each Case", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({ alertCount: 3 })]));
    renderPage();
    expect(screen.getByTestId("case-alert-count")).toHaveTextContent("3");
  });

  it("reads opened and updated as recency, the absolute moment kept nearby", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    expect(screen.getByTestId("case-opened")).not.toBeEmptyDOMElement();
    expect(screen.getByTestId("case-opened")).toHaveTextContent(/ago|just now/);
    expect(screen.getByTestId("case-updated")).toHaveTextContent(
      /ago|just now/,
    );
    expect(screen.getByTestId("case-opened")).toHaveTextContent(/2026/);
  });

  it("offers four status chips, each carrying its depth", () => {
    useAmlCasesMock.mockReturnValue(
      served([], {
        OPEN: 2,
        INVESTIGATING: 1,
        CLOSED_DISMISSED: 0,
        CLOSED_REPORTED: 4,
      }),
    );
    renderPage();
    expect(screen.getByTestId("cases-filter-open")).toHaveTextContent("Open2");
    expect(screen.getByTestId("cases-filter-investigating")).toHaveTextContent(
      "Investigating1",
    );
    expect(screen.getByTestId("cases-filter-dismissed")).toHaveTextContent(
      "Closed dismissed0",
    );
    expect(screen.getByTestId("cases-filter-reported")).toHaveTextContent(
      "Closed reported4",
    );
  });

  it("narrows to a lifecycle state when its chip is pressed", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("cases-filter-investigating"));
    expect(useAmlCasesMock).toHaveBeenLastCalledWith("INVESTIGATING");
    expect(screen.getByTestId("cases-filter-investigating")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByTestId("cases-filter-open")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("widens back to every Case from an explicit Show all, offered only while narrowed", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(screen.queryByTestId("cases-filter-all")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("cases-filter-open"));
    expect(screen.getByTestId("cases-filter-all")).toHaveTextContent(
      "Show all cases",
    );

    await user.click(screen.getByTestId("cases-filter-all"));

    expect(useAmlCasesMock).toHaveBeenLastCalledWith(null);
    expect(screen.queryByTestId("cases-filter-all")).not.toBeInTheDocument();
  });

  it("says how many investigations the estate holds on the Show all affordance", async () => {
    const user = userEvent.setup();
    useAmlCasesMock.mockReturnValue({
      ...served([]),
      total: 7,
      depths: { ...NO_DEPTHS, OPEN: 3 },
    });
    renderPage();
    await user.click(screen.getByTestId("cases-filter-open"));
    expect(screen.getByTestId("cases-filter-all")).toHaveTextContent(
      "Show all cases7",
    );
  });

  it("un-narrows when the chip already in force is pressed again", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByTestId("cases-filter-open"));
    await user.click(screen.getByTestId("cases-filter-open"));
    expect(useAmlCasesMock).toHaveBeenLastCalledWith(null);
  });

  it("opens the investigation from its row", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    expect(screen.getByTestId("case-link")).toHaveAttribute(
      "href",
      "/ops/cases/1a2b3c4d-1111-1111-1111-111111111111",
    );
  });

  it("takes a click anywhere on the row to the Case view", async () => {
    const user = userEvent.setup();
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    await user.click(screen.getByTestId("case-alert-count"));
    expect(screen.getByTestId("case-probe")).toBeInTheDocument();
  });

  it("designs an empty state for a bank with no investigations", () => {
    renderPage();
    expect(screen.getByTestId("cases-empty")).toHaveTextContent(
      "No investigations",
    );
    expect(screen.queryByTestId("cases-table")).not.toBeInTheDocument();
  });

  it("says the narrowing is what emptied the list, not the bank", async () => {
    const user = userEvent.setup();
    useAmlCasesMock.mockReturnValue(served([], { OPEN: 2 }));
    renderPage();
    await user.click(screen.getByTestId("cases-filter-reported"));
    expect(screen.getByTestId("cases-empty")).toHaveTextContent(
      "No cases closed reported.",
    );
  });

  it("shows a skeleton while the listing is in flight", () => {
    useAmlCasesMock.mockReturnValue({
      ...served([]),
      cases: null,
      isPending: true,
    });
    renderPage();
    expect(screen.getByTestId("cases-skeleton")).toBeInTheDocument();
    expect(screen.queryByTestId("cases-empty")).not.toBeInTheDocument();
  });

  it("offers a retry when the listing fails, without a reload", async () => {
    const user = userEvent.setup();
    const refetch = vi.fn();
    useAmlCasesMock.mockReturnValue({
      ...served([]),
      cases: null,
      isError: true,
      error: new Error("boom"),
      refetch,
    });
    renderPage();
    expect(screen.getByTestId("cases-error")).toBeInTheDocument();
    await user.click(screen.getByTestId("cases-retry"));
    expect(refetch).toHaveBeenCalledTimes(1);
  });

  it("keeps the chips reachable while the listing is failing", () => {
    useAmlCasesMock.mockReturnValue({
      ...served([]),
      cases: null,
      isError: true,
      refetch: vi.fn(),
    });
    renderPage();
    expect(screen.getByTestId("cases-filter-open")).toBeInTheDocument();
  });

  it("gives the table a caption naming what a row is", () => {
    useAmlCasesMock.mockReturnValue(served([makeCase({})]));
    renderPage();
    const table = screen.getByTestId("cases-table");
    expect(
      within(table).getByRole("columnheader", { name: "Case" }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("columnheader", { name: "Account" }),
    ).toBeInTheDocument();
    expect(
      within(table).getByRole("columnheader", { name: "Alerts" }),
    ).toBeInTheDocument();
  });
});
