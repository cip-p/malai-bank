import { beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import type {
  ConfirmationOfPayeeRequest,
  ConfirmationOfPayeeResponse,
  PayeeRequest,
  PayeeResponse,
} from "@/api/generated/types.gen";

const confirmPayeeMock = vi.fn();
const createPayeeMock = vi.fn();
let confirmOnSuccess:
  | ((response: ConfirmationOfPayeeResponse) => void)
  | undefined;
let createPayeeOnSuccess: ((payee: PayeeResponse) => void) | undefined;
let isConfirming = false;
let isCreating = false;

vi.mock("@/move-money/useConfirmationOfPayee", () => ({
  useConfirmationOfPayee: (opts?: {
    onSuccess?: (r: ConfirmationOfPayeeResponse) => void;
  }) => {
    confirmOnSuccess = opts?.onSuccess;
    return {
      confirmPayee: (input: ConfirmationOfPayeeRequest) =>
        confirmPayeeMock(input),
      isPending: isConfirming,
    };
  },
}));

vi.mock("@/move-money/useCreatePayee", () => ({
  useCreatePayee: (opts?: { onSuccess?: (p: PayeeResponse) => void }) => {
    createPayeeOnSuccess = opts?.onSuccess;
    return {
      createPayee: (input: PayeeRequest) => createPayeeMock(input),
      isPending: isCreating,
    };
  },
}));

import { AddPayeePage } from "./AddPayeePage";

function wrap(ui: React.ReactNode) {
  return <MemoryRouter>{ui}</MemoryRouter>;
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AddPayeePage />
    </MemoryRouter>,
  );
}

async function walkToConfirm(nickname?: string) {
  const user = userEvent.setup();
  await user.type(
    screen.getByLabelText("Name on the account"),
    "Sarah Mitchell",
  );
  if (nickname) {
    await user.type(screen.getByLabelText("Nickname (optional)"), nickname);
  }
  await user.click(screen.getByRole("button", { name: "Continue" }));
  await user.type(screen.getByLabelText("Sort code"), "123456");
  await user.type(screen.getByLabelText("Account number"), "12345678");
  await user.click(screen.getByRole("button", { name: "Continue" }));
  return user;
}

describe("AddPayeePage — step flow", () => {
  beforeEach(() => {
    confirmPayeeMock.mockReset();
    createPayeeMock.mockReset();
    confirmOnSuccess = undefined;
    createPayeeOnSuccess = undefined;
    isConfirming = false;
    isCreating = false;
  });

  it("focuses the Name step's h1 on mount", () => {
    render(wrap(<AddPayeePage />));
    expect(
      screen.getByRole("heading", { level: 1, name: "Who is the payee?" }),
    ).toHaveFocus();
  });

  it("step indicator shows 'Step 1 of 3' on the first step", () => {
    render(wrap(<AddPayeePage />));
    expect(screen.getByTestId("stepflow-indicator")).toHaveTextContent(
      "Step 1 of 3",
    );
  });

  it("walks Name → Bank details → Confirm with state preserved", async () => {
    const user = userEvent.setup();
    render(wrap(<AddPayeePage />));

    await user.type(
      screen.getByLabelText("Name on the account"),
      "Sarah Mitchell",
    );
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(
      screen.getByRole("heading", { level: 1, name: "Bank details" }),
    ).toBeInTheDocument();

    await user.type(screen.getByLabelText("Sort code"), "123456");
    await user.type(screen.getByLabelText("Account number"), "12345678");
    await user.click(screen.getByRole("button", { name: "Back" }));

    expect(screen.getByLabelText("Name on the account")).toHaveValue(
      "Sarah Mitchell",
    );

    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByLabelText("Sort code")).toHaveValue("12-34-56");
    expect(screen.getByLabelText("Account number")).toHaveValue("12345678");
  });

  it("on Save, fires COPA with the entered values", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));

    expect(confirmPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
    expect(createPayeeMock).not.toHaveBeenCalled();
  });

  it("on MATCH, skips the panel and fires create with the entered values", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({ result: "MATCH" });

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
    expect(screen.queryByTestId("copa-panel")).toBeNull();
  });

  it("on CLOSE_MATCH, renders the panel; Use on-record substitutes the name and fires create", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({
      result: "CLOSE_MATCH",
      onRecordName: "Sarah Beatrice Mitchell",
    });

    const panel = await screen.findByTestId("copa-panel");
    expect(panel.getAttribute("data-verdict")).toBe("CLOSE_MATCH");
    expect(createPayeeMock).not.toHaveBeenCalled();

    await user.click(screen.getByTestId("copa-use-on-record"));

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Beatrice Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
  });

  it("on CLOSE_MATCH, Keep what I typed fires create with the entered name", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({
      result: "CLOSE_MATCH",
      onRecordName: "Sarah Beatrice Mitchell",
    });
    await screen.findByTestId("copa-panel");

    await user.click(screen.getByTestId("copa-accept"));

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
  });

  it("on NO_MATCH, renders the panel; Edit details dismisses without creating", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({ result: "NO_MATCH" });

    const panel = await screen.findByTestId("copa-panel");
    expect(panel.getAttribute("data-verdict")).toBe("NO_MATCH");

    await user.click(screen.getByTestId("copa-edit"));
    expect(screen.queryByTestId("copa-panel")).toBeNull();
    expect(createPayeeMock).not.toHaveBeenCalled();
  });

  it("on NO_MATCH, Save anyway fires create with the entered values", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({ result: "NO_MATCH" });
    await screen.findByTestId("copa-panel");

    expect(screen.getByTestId("copa-send-anyway").textContent).toBe(
      "Save anyway",
    );

    await user.click(screen.getByTestId("copa-send-anyway"));

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
    });
  });

  it("shows inline validation when the Name step is advanced with a missing field", async () => {
    const user = userEvent.setup();
    render(wrap(<AddPayeePage />));
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(
      screen.getByLabelText("Name on the account").getAttribute("aria-invalid"),
    ).toBe("true");
    expect(
      screen.getByRole("heading", { level: 1, name: "Who is the payee?" }),
    ).toBeInTheDocument();
  });

  it("includes the optional nickname in the create request when present", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm("Sis");
    await user.click(screen.getByTestId("add-payee-submit"));
    confirmOnSuccess?.({ result: "MATCH" });

    expect(createPayeeMock).toHaveBeenCalledWith({
      name: "Sarah Mitchell",
      sortCode: "12-34-56",
      accountNumber: "12345678",
      nickname: "Sis",
    });
  });

  it("pre-fills name / sortCode / accountNumber from query params", async () => {
    const user = userEvent.setup();
    renderAt(
      "/move/payees/new?sortCode=12-34-56&accountNumber=12345678&name=Sarah%20Mitchell",
    );
    expect(screen.getByLabelText("Name on the account")).toHaveValue(
      "Sarah Mitchell",
    );
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(screen.getByLabelText("Sort code")).toHaveValue("12-34-56");
    expect(screen.getByLabelText("Account number")).toHaveValue("12345678");
  });

  it("pre-filled fields remain editable", async () => {
    const user = userEvent.setup();
    renderAt(
      "/move/payees/new?sortCode=12-34-56&accountNumber=12345678&name=Sarah%20Mitchell",
    );
    const nameField = screen.getByLabelText("Name on the account");
    await user.clear(nameField);
    await user.type(nameField, "Different Person");
    expect(nameField).toHaveValue("Different Person");
  });

  it("after create succeeds, renders the success screen with SuccessTick and Back to payees CTA", async () => {
    render(wrap(<AddPayeePage />));
    const user = await walkToConfirm();
    await user.click(screen.getByTestId("add-payee-submit"));
    act(() => {
      confirmOnSuccess?.({ result: "MATCH" });
    });
    act(() => {
      createPayeeOnSuccess?.({
        payeeId: "00000000-0000-4000-8000-000000000abc",
        name: "Sarah Mitchell",
        sortCode: "12-34-56",
        accountNumber: "12345678",
        createdAt: "2026-06-05T00:00:00Z",
      });
    });

    expect(screen.getByTestId("add-payee-success")).toBeInTheDocument();
    expect(screen.getByTestId("success-tick")).toBeInTheDocument();
    const cta = screen.getByTestId("add-payee-success-back");
    expect(cta).toHaveAttribute("href", "/move/payees");
    expect(
      screen.getByRole("heading", { level: 1, name: "Payee saved" }),
    ).toHaveFocus();
  });
});
