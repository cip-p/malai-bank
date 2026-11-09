import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { AmlCaseDetailResponse } from "@/api/generated/types.gen";
import type { UseCloseCaseResult } from "@/ops/useCloseCase";

vi.mock("@/ops/useCloseCase", () => ({ useCloseCase: vi.fn() }));

const hookModule = await import("@/ops/useCloseCase");
const useCloseCaseMock = hookModule.useCloseCase as unknown as ReturnType<
  typeof vi.fn<(caseId: string) => UseCloseCaseResult>
>;

const { CloseActions } = await import("./CloseActions");

const CASE_ID = "1a2b3c4d-1111-1111-1111-111111111111";

const close = vi.fn();

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

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

function renderActions(
  detail: AmlCaseDetailResponse = makeDetail(),
  result: Partial<UseCloseCaseResult> = {},
) {
  useCloseCaseMock.mockReturnValue({
    close,
    pending: null,
    outcome: null,
    ...result,
  });
  return render(<CloseActions detail={detail} />);
}

describe("CloseActions", () => {
  beforeEach(() => {
    useCloseCaseMock.mockReset();
    close.mockReset();
  });

  it("offers both terminal actions on a case still under investigation", () => {
    renderActions();

    expect(screen.getByTestId("close-dismissed")).toBeEnabled();
    expect(screen.getByTestId("close-reported")).toBeEnabled();
  });

  it.each(["CLOSED_DISMISSED", "CLOSED_REPORTED"] as const)(
    "offers nothing on a %s case — absent, not disabled",
    (status) => {
      renderActions(makeDetail({ status }));

      expect(screen.queryByTestId("close-actions")).not.toBeInTheDocument();
      expect(screen.queryByTestId("close-dismissed")).not.toBeInTheDocument();
      expect(screen.queryByTestId("close-reported")).not.toBeInTheDocument();
    },
  );

  it("arms on the first press and closes nothing", async () => {
    renderActions();

    await userEvent.click(screen.getByTestId("close-dismissed"));

    expect(screen.getByTestId("close-dismissed")).toHaveTextContent("Confirm");
    expect(close).not.toHaveBeenCalled();
  });

  it("fires the dismissal on the second press, carrying one minted key", async () => {
    renderActions();

    const button = screen.getByTestId("close-dismissed");
    await userEvent.click(button);
    await userEvent.click(button);

    expect(close).toHaveBeenCalledTimes(1);
    expect(close.mock.calls[0][0]).toBe("dismissed");
    expect(close.mock.calls[0][1]).toMatch(UUID_PATTERN);
  });

  it("fires the report through its own action", async () => {
    renderActions();

    const button = screen.getByTestId("close-reported");
    await userEvent.click(button);
    await userEvent.click(button);

    expect(close).toHaveBeenCalledTimes(1);
    expect(close.mock.calls[0][0]).toBe("reported");
  });

  it("disarms when the officer clicks elsewhere on the case", async () => {
    renderActions();

    await userEvent.click(screen.getByTestId("close-dismissed"));
    await userEvent.click(screen.getByRole("heading", { level: 2 }));

    expect(screen.getByTestId("close-dismissed")).toHaveTextContent(
      "Close as dismissed",
    );
    expect(screen.getByTestId("close-dismissed")).not.toHaveTextContent(
      "Confirm",
    );

    await userEvent.click(screen.getByTestId("close-dismissed"));
    expect(close).not.toHaveBeenCalled();
  });

  it("arms one action at a time — reaching for the other drops the first", async () => {
    renderActions();

    await userEvent.click(screen.getByTestId("close-dismissed"));
    await userEvent.click(screen.getByTestId("close-reported"));

    expect(screen.getByTestId("close-reported")).toHaveTextContent("Confirm");
    expect(screen.getByTestId("close-dismissed")).not.toHaveTextContent(
      "Confirm",
    );
    expect(close).not.toHaveBeenCalled();
  });

  it("renders an accepted Alert standing in the way as a fact, not an error", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "accepted-alert-stands" },
    });

    const refusal = screen.getByTestId("close-refusal");
    expect(refusal).toHaveTextContent(/accept/i);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("announces the refusal politely — the press changes nothing else on screen", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "accepted-alert-stands" },
    });

    expect(screen.getByTestId("close-refusal")).toHaveAttribute(
      "role",
      "status",
    );
  });

  it("renders the missing SAR ruling as its own fact", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "no-sar-ruling-in-force" },
    });

    expect(screen.getByTestId("close-refusal")).toHaveTextContent(/SAR/);
  });

  it("renders a case another officer already closed as its own fact", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "already-closed" },
    });

    expect(screen.getByTestId("close-refusal")).toHaveTextContent(
      /already closed/i,
    );
  });

  it("falls back generic on a refusal it cannot name, still as a fact", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "unknown" },
    });

    expect(screen.getByTestId("close-refusal")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("reports a request that never reached the bank as the error it is", () => {
    renderActions(makeDetail(), { outcome: { kind: "failed" } });

    expect(screen.getByTestId("close-failed")).toHaveAttribute("role", "alert");
    expect(screen.queryByTestId("close-refusal")).not.toBeInTheDocument();
  });

  it("keeps both actions offered under a refusal — the Gate decides, not the screen", () => {
    renderActions(makeDetail(), {
      outcome: { kind: "refused", reason: "accepted-alert-stands" },
    });

    expect(screen.getByTestId("close-dismissed")).toBeEnabled();
    expect(screen.getByTestId("close-reported")).toBeEnabled();
  });

  it("never predicts a refusal from the case it can see — the Gate decides", async () => {
    renderActions(
      makeDetail({
        status: "OPEN",
        alerts: [
          {
            decisionId: "77777777-7777-7777-7777-777777777777",
            typology: "STRUCTURING",
            riskScore: 72,
            raisedAt: "2026-07-20T09:10:00Z",
            rulingInForce: "ACCEPT",
            evidence: [],
          },
        ],
      }),
    );

    const dismissed = screen.getByTestId("close-dismissed");
    expect(dismissed).toBeEnabled();
    expect(screen.getByTestId("close-reported")).toBeEnabled();

    await userEvent.click(dismissed);
    await userEvent.click(dismissed);
    expect(close).toHaveBeenCalledWith("dismissed", expect.any(String));
  });

  it("says which close is in flight and lets the other one wait", () => {
    renderActions(makeDetail(), { pending: "reported" });

    expect(screen.getByTestId("close-reported")).toBeDisabled();
    expect(screen.getByTestId("close-reported")).toHaveTextContent("Closing");
    expect(screen.getByTestId("close-dismissed")).toBeDisabled();
  });
});
