import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Outlet, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";
import type { UserResponse } from "@/api/generated/types.gen";

vi.mock("@/ops/OpsShell", () => ({
  OpsShell: ({
    user,
    children,
  }: {
    user: UserResponse;
    children: ReactNode;
  }) => (
    <div data-testid="ops-shell" data-user-email={user.email}>
      {children}
    </div>
  ),
}));

const { OpsSurface } = await import("./OpsSurface");

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
          <Route path="/ops" element={<OpsSurface />}>
            <Route index element={<div>worklist outlet</div>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("OpsSurface", () => {
  it("renders the ops shell around the outlet for an admin user", () => {
    renderAt("/ops", makeUser(["CUSTOMER", "ADMIN"]));
    expect(screen.getByTestId("ops-shell")).toBeInTheDocument();
    expect(screen.getByText("worklist outlet")).toBeInTheDocument();
  });

  it("renders the Access page in place, without ops chrome, when Ops is withheld", () => {
    renderAt("/ops", makeUser(["CUSTOMER"]));
    expect(screen.getByTestId("access-heading")).toBeInTheDocument();
    expect(screen.queryByTestId("ops-shell")).not.toBeInTheDocument();
    expect(screen.queryByText("worklist outlet")).not.toBeInTheDocument();
  });

  it("offers the withheld customer-only user a link back to Banking", () => {
    renderAt("/ops", makeUser(["CUSTOMER"]));
    const link = screen.getByTestId("access-surface-link-banking");
    expect(link).toHaveAttribute("href", "/");
    expect(
      screen.queryByTestId("access-surface-link-ops"),
    ).not.toBeInTheDocument();
  });

  it("renders the Access page for a manager-only user, pointing them to Exec", () => {
    renderAt("/ops", makeUser(["MANAGER"]));
    expect(screen.getByTestId("access-heading")).toBeInTheDocument();
    expect(screen.queryByTestId("ops-shell")).not.toBeInTheDocument();
    expect(screen.getByTestId("access-surface-link-exec")).toHaveAttribute(
      "href",
      "/exec",
    );
  });
});
