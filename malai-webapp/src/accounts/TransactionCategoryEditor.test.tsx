import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { TransactionCategoryEditor } from "./TransactionCategoryEditor";

const setCategoryMock = vi.fn();
const clearCategoryMock = vi.fn();
vi.mock("./useSetTransactionCategory", () => ({
  useSetTransactionCategory: () => ({
    setCategory: setCategoryMock,
    clearCategory: clearCategoryMock,
    isPending: false,
  }),
}));

const ACCOUNT_UID = "22222222-2222-2222-2222-222222222222";
const ENTRY_ID = "99999999-9999-9999-9999-999999999999";

function renderEditor(category: string | undefined = "GROCERIES") {
  return render(
    <dl>
      <TransactionCategoryEditor
        accountUid={ACCOUNT_UID}
        entryId={ENTRY_ID}
        category={category}
      />
    </dl>,
  );
}

describe("TransactionCategoryEditor", () => {
  beforeEach(() => {
    setCategoryMock.mockReset();
    clearCategoryMock.mockReset();
  });

  it("shows the current category on the trigger", () => {
    renderEditor("EATING_OUT");
    expect(
      screen.getByTestId("transaction-category-trigger"),
    ).toHaveTextContent("Eating out");
  });

  it("pins a newly chosen category and reflects it on the trigger", async () => {
    const user = userEvent.setup();
    renderEditor("GROCERIES");

    await user.click(screen.getByTestId("transaction-category-trigger"));
    await user.click(
      await screen.findByRole("menuitem", { name: "Eating out" }),
    );

    expect(setCategoryMock).toHaveBeenCalledWith(ENTRY_ID, "EATING_OUT");
    expect(
      screen.getByTestId("transaction-category-trigger"),
    ).toHaveTextContent("Eating out");
  });

  it("does not call setCategory when the current category is re-selected", async () => {
    const user = userEvent.setup();
    renderEditor("GROCERIES");

    await user.click(screen.getByTestId("transaction-category-trigger"));
    await user.click(
      await screen.findByRole("menuitem", { name: "Groceries" }),
    );

    expect(setCategoryMock).not.toHaveBeenCalled();
  });

  it("reverts to automatic categorisation", async () => {
    const user = userEvent.setup();
    renderEditor("GROCERIES");

    await user.click(screen.getByTestId("transaction-category-trigger"));
    await user.click(
      await screen.findByRole("menuitem", { name: "Revert to automatic" }),
    );

    expect(clearCategoryMock).toHaveBeenCalledWith(ENTRY_ID);
  });
});
