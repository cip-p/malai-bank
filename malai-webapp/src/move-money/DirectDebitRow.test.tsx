import { describe, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { DirectDebitResponse } from "@/api/generated/types.gen";
import { DirectDebitRow } from "./DirectDebitRow";

const BASE: DirectDebitResponse = {
  directDebitId: "dd-1",
  accountId: "acc-1",
  creditorName: "Thames Water",
  creditorSortCode: "12-34-56",
  creditorAccountNumber: "12345678",
  amountMinorUnits: 4500,
  currency: "GBP",
  frequency: "MONTHLY",
  status: "ACTIVE",
  startDate: "2026-01-15",
  nextCollectionAt: "2026-05-23T12:00:00Z",
};

const CANCELLED: DirectDebitResponse = {
  ...BASE,
  status: "CANCELLED",
  cancelledAt: "2026-05-01T09:00:00Z",
};

const NOW = new Date("2026-05-20T12:00:00Z");

describe("DirectDebitRow", () => {
  it("renders the creditor name as the primary label", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.getByTestId("dd-primary")).toHaveTextContent("Thames Water");
  });

  it("renders a fixed amount via Money with signed=never", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.getByTestId("dd-amount")).toHaveTextContent("£45.00");
  });

  it("renders the literal word 'Variable' (muted) when amountMinorUnits is undefined", () => {
    render(
      <ul>
        <DirectDebitRow
          dd={{ ...BASE, amountMinorUnits: undefined }}
          now={NOW}
        />
      </ul>,
    );
    const amount = screen.getByTestId("dd-amount");
    expect(amount).toHaveTextContent("Variable");
    expect(amount.className).toMatch(/text-muted-foreground/);
  });

  it("renders the relative next-date caption for a date within 7 days", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.getByTestId("dd-next")).toHaveTextContent("Next: in 3 days");
  });

  it("renders 'Awaiting first collection' when nextCollectionAt is undefined", () => {
    render(
      <ul>
        <DirectDebitRow
          dd={{ ...BASE, nextCollectionAt: undefined }}
          now={NOW}
        />
      </ul>,
    );
    expect(screen.getByTestId("dd-next")).toHaveTextContent(
      "Awaiting first collection",
    );
  });

  it("renders the account chip when accountLabel is provided", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} accountLabel="Main" />
      </ul>,
    );
    expect(screen.getByTestId("dd-account-chip")).toHaveTextContent("Main");
  });

  it("omits the account chip when accountLabel is undefined", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.queryByTestId("dd-account-chip")).toBeNull();
  });

  it("renders as a list item", () => {
    render(
      <ul data-testid="list">
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    const list = screen.getByTestId("list");
    expect(within(list).getAllByRole("listitem")).toHaveLength(1);
  });

  it("fires onCancel with the mandate when the cancel button is clicked", async () => {
    const onCancel = vi.fn();
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} onCancel={onCancel} />
      </ul>,
    );
    await userEvent.click(
      screen.getByRole("button", {
        name: "Cancel direct debit to Thames Water",
      }),
    );
    expect(onCancel).toHaveBeenCalledWith(BASE);
  });

  it("omits the cancel button when onCancel is not provided", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.queryByTestId("dd-cancel")).toBeNull();
  });

  it("omits the cancel button on a CANCELLED mandate", () => {
    render(
      <ul>
        <DirectDebitRow dd={CANCELLED} now={NOW} onCancel={() => {}} />
      </ul>,
    );
    expect(screen.queryByTestId("dd-cancel")).toBeNull();
  });

  it("marks a CANCELLED mandate with a badge, greys it, and drops the next-collection caption", () => {
    render(
      <ul>
        <DirectDebitRow dd={CANCELLED} now={NOW} />
      </ul>,
    );
    expect(screen.getByTestId("dd-cancelled-badge")).toHaveTextContent(
      "Cancelled",
    );
    expect(screen.getByTestId("dd-primary").className).toMatch(
      /text-muted-foreground/,
    );
    expect(screen.queryByTestId("dd-next")).toBeNull();
  });

  it("shows no cancelled badge on an ACTIVE mandate", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.queryByTestId("dd-cancelled-badge")).toBeNull();
  });

  it("carries the directDebitId as an id anchor for deep links", () => {
    render(
      <ul>
        <DirectDebitRow dd={BASE} now={NOW} />
      </ul>,
    );
    expect(screen.getByTestId("dd-row")).toHaveAttribute("id", "dd-1");
  });
});
