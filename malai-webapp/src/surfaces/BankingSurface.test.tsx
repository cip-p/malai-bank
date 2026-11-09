import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import type { UserResponse } from "@/api/generated/types.gen";

vi.mock("@/components/AppShell", () => ({
  AppShell: ({
    user,
    children,
  }: {
    user: UserResponse;
    children: ReactNode;
  }) => (
    <div data-testid="app-shell" data-user-email={user.email}>
      {children}
    </div>
  ),
}));

const { BankingSurface } = await import("./BankingSurface");

function makeUser(roles: UserResponse["roles"]): UserResponse {
  return {
    firstName: "Ada",
    lastName: "Lovelace",
    email: "ada@example.com",
    roles,
  };
}

function renderAt(path: string, user: UserResponse) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Outlet context={{ user }} />}>
          <Route element={<BankingSurface />}>
            <Route path="/" element={<div>banking outlet</div>} />
            <Route path="/cards" element={<div>cards outlet</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("BankingSurface", () => {
  it("renders the customer shell around the outlet for a customer user", () => {
    renderAt("/", makeUser(["CUSTOMER"]));
    expect(screen.getByTestId("app-shell")).toBeInTheDocument();
    expect(screen.getByText("banking outlet")).toBeInTheDocument();
  });

  it("hands the user to the customer shell", () => {
    renderAt("/", makeUser(["CUSTOMER"]));
    expect(screen.getByTestId("app-shell")).toHaveAttribute(
      "data-user-email",
      "ada@example.com",
    );
  });

  it("renders the Access page in place, without customer chrome, when Banking is withheld", () => {
    renderAt("/cards", makeUser(["ADMIN"]));
    expect(screen.getByTestId("access-heading")).toBeInTheDocument();
    expect(screen.queryByTestId("app-shell")).not.toBeInTheDocument();
    expect(screen.queryByText("cards outlet")).not.toBeInTheDocument();
  });

  it("offers the withheld admin-only user links to their own surfaces", () => {
    renderAt("/", makeUser(["ADMIN"]));
    expect(screen.getByTestId("access-surface-link-ops")).toBeInTheDocument();
  });

  it("renders the Access page for a manager-only user, pointing them to Exec", () => {
    renderAt("/cards", makeUser(["MANAGER"]));
    expect(screen.getByTestId("access-heading")).toBeInTheDocument();
    expect(screen.queryByTestId("app-shell")).not.toBeInTheDocument();
    expect(screen.getByTestId("access-surface-link-exec")).toHaveAttribute(
      "href",
      "/exec",
    );
    expect(
      screen.queryByTestId("access-surface-link-ops"),
    ).not.toBeInTheDocument();
  });
});
