import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import type { UserResponse } from "@/api/generated/types.gen";

vi.mock("@/exec/ExecShell", () => ({
  ExecShell: ({
    user,
    children,
  }: {
    user: UserResponse;
    children: ReactNode;
  }) => (
    <div data-testid="exec-shell" data-user-email={user.email}>
      {children}
    </div>
  ),
}));

const { ExecSurface } = await import("./ExecSurface");

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
          <Route path="/exec" element={<ExecSurface />}>
            <Route index element={<div>dashboard outlet</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("ExecSurface", () => {
  it("renders the exec shell around the outlet for an admin user", () => {
    renderAt("/exec", makeUser(["CUSTOMER", "ADMIN"]));
    expect(screen.getByTestId("exec-shell")).toBeInTheDocument();
    expect(screen.getByText("dashboard outlet")).toBeInTheDocument();
  });

  it("renders the exec shell for a manager-only user", () => {
    renderAt("/exec", makeUser(["MANAGER"]));
    expect(screen.getByTestId("exec-shell")).toBeInTheDocument();
    expect(screen.getByText("dashboard outlet")).toBeInTheDocument();
    expect(screen.queryByTestId("access-heading")).not.toBeInTheDocument();
  });

  it("renders the Access page in place, without exec chrome, when Exec is withheld", () => {
    renderAt("/exec", makeUser(["CUSTOMER"]));
    expect(screen.getByTestId("access-heading")).toBeInTheDocument();
    expect(screen.queryByTestId("exec-shell")).not.toBeInTheDocument();
    expect(screen.queryByText("dashboard outlet")).not.toBeInTheDocument();
  });

  it("offers the withheld customer-only user a link back to Banking", () => {
    renderAt("/exec", makeUser(["CUSTOMER"]));
    const link = screen.getByTestId("access-surface-link-banking");
    expect(link).toHaveAttribute("href", "/");
    expect(
      screen.queryByTestId("access-surface-link-exec"),
    ).not.toBeInTheDocument();
  });
});
