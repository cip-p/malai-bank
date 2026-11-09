import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  AmlCaseDetailResponse,
  AmlSarDraftResponse,
} from "@/api/generated/types.gen";
import type { SarDraftInvocation } from "@/ops/sarPanelState";
import type { UseSarDraftResult } from "@/ops/useSarDraft";

vi.mock("@/ops/useSarDraft", () => ({ useSarDraft: vi.fn() }));

const hookModule = await import("@/ops/useSarDraft");
const useSarDraftMock = hookModule.useSarDraft as unknown as ReturnType<
  typeof vi.fn<(caseId: string) => UseSarDraftResult>
>;

import { SarPanel } from "./SarPanel";

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";
const DECISION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
const NARRATIVE =
  "Between 20 and 22 July 2026 the account made three payments of £3,400 to Carol Singh.";

const requestDraft = vi.fn();

function makeDetail(
  overrides: Partial<AmlCaseDetailResponse> = {},
): AmlCaseDetailResponse {
  return {
    caseId: CASE_ID,
    accountId: "9f8e7d6c-2222-2222-2222-222222222222",
    status: "INVESTIGATING",
    openedAt: "2026-07-20T09:00:00Z",
    updatedAt: "2026-07-20T09:30:00Z",
    alerts: [],
    ...overrides,
  };
}

function served(
  overrides: Partial<AmlSarDraftResponse> = {},
): SarDraftInvocation {
  return {
    kind: "served",
    response: {
      decisionId: DECISION_ID,
      outcome: "COMPLETED",
      narrative: NARRATIVE,
      ...overrides,
    },
  };
}

function renderPanel(
  detail: AmlCaseDetailResponse = makeDetail(),
  invocation: SarDraftInvocation = { kind: "idle" },
) {
  useSarDraftMock.mockReturnValue({ invocation, requestDraft });
  return render(
    <MemoryRouter>
      <SarPanel detail={detail} />
    </MemoryRouter>,
  );
}

describe("SarPanel", () => {
  beforeEach(() => {
    useSarDraftMock.mockReset();
    requestDraft.mockReset();
  });

  it("drives the draft for the case it is shown on", () => {
    renderPanel();
    expect(useSarDraftMock).toHaveBeenLastCalledWith(CASE_ID);
  });

  it("offers the request affordance on a non-terminal case with no draft", async () => {
    renderPanel(makeDetail({ status: "OPEN" }));

    const request = screen.getByTestId("sar-request");
    await userEvent.click(request);

    expect(requestDraft).toHaveBeenCalledTimes(1);
  });

  it("says the invocation is in flight and cannot be pressed again", () => {
    renderPanel(makeDetail(), { kind: "drafting" });

    const request = screen.getByTestId("sar-request");
    expect(request).toBeDisabled();
    expect(request).toHaveTextContent("Drafting");
  });

  it("renders the grounded narrative inline with its record a link away", () => {
    renderPanel(makeDetail(), served());

    expect(screen.getByTestId("sar-narrative")).toHaveTextContent(NARRATIVE);
    const link = screen.getByTestId("sar-record-link");
    expect(link).toHaveAttribute(
      "href",
      `/ops/cases/${CASE_ID}/decisions/${DECISION_ID}`,
    );
    expect(link).toHaveTextContent("aaaaaaaa");
  });

  it("renders a grounding refusal as a recorded fact, never an error", () => {
    renderPanel(
      makeDetail(),
      served({ narrative: undefined, refusalCode: "GROUNDING_FAILED" }),
    );

    expect(screen.getByTestId("sar-refused")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByTestId("sar-record-link")).toHaveAttribute(
      "href",
      `/ops/cases/${CASE_ID}/decisions/${DECISION_ID}`,
    );
  });

  it.each(["TIMED_OUT", "ERRORED"] as const)(
    "renders a %s invocation as a recorded fact with its record linked",
    (outcome) => {
      renderPanel(makeDetail(), served({ outcome, narrative: undefined }));

      expect(screen.getByTestId("sar-no-answer")).toBeInTheDocument();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
      expect(screen.getByTestId("sar-record-link")).toHaveAttribute(
        "href",
        `/ops/cases/${CASE_ID}/decisions/${DECISION_ID}`,
      );
    },
  );

  it("reports a request that never reached a record as the error it is", () => {
    renderPanel(makeDetail(), { kind: "failed" });

    expect(screen.getByTestId("sar-request-failed")).toHaveAttribute(
      "role",
      "alert",
    );
    expect(screen.queryByTestId("sar-record-link")).not.toBeInTheDocument();
  });

  it("shows the draft on record with its ruling in force on revisit", () => {
    renderPanel(
      makeDetail({ sar: { decisionId: DECISION_ID, rulingInForce: "ACCEPT" } }),
    );

    expect(screen.getByTestId("sar-on-record")).toBeInTheDocument();
    expect(screen.getByTestId("sar-ruling")).toHaveTextContent("Accept");
    expect(screen.getByTestId("sar-record-link")).toHaveAttribute(
      "href",
      `/ops/cases/${CASE_ID}/decisions/${DECISION_ID}`,
    );
    expect(screen.queryByTestId("sar-request")).not.toBeInTheDocument();
  });

  it("says an unruled draft on record awaits its first ruling", () => {
    renderPanel(makeDetail({ sar: { decisionId: DECISION_ID } }));

    expect(screen.getByTestId("sar-ruling")).toHaveTextContent(
      "Awaiting ruling",
    );
  });

  it("renders the attached narrative verbatim once the case is closed reported", () => {
    renderPanel(
      makeDetail({
        status: "CLOSED_REPORTED",
        sar: {
          decisionId: DECISION_ID,
          rulingInForce: "ACCEPT",
          narrative: NARRATIVE,
        },
      }),
    );

    expect(screen.getByTestId("sar-attached")).toHaveTextContent(NARRATIVE);
    expect(screen.queryByTestId("sar-request")).not.toBeInTheDocument();
  });

  it("offers nothing on a case that closed without a draft", () => {
    renderPanel(makeDetail({ status: "CLOSED_DISMISSED" }));

    expect(screen.getByTestId("sar-never-drafted")).toBeInTheDocument();
    expect(screen.queryByTestId("sar-request")).not.toBeInTheDocument();
  });

  it("never rules on the draft — that happens at the record", () => {
    renderPanel(makeDetail({ sar: { decisionId: DECISION_ID } }), served());

    expect(
      screen.queryByRole("button", { name: /accept|reject|edit/i }),
    ).not.toBeInTheDocument();
  });
});
