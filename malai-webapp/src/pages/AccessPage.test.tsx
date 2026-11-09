import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AccessPage } from "./AccessPage";
import type { Role } from "@/surfaces/surfaceOffering";

function renderWithRoles(roles: readonly Role[]) {
  return render(
    <MemoryRouter>
      <AccessPage roles={roles} />
    </MemoryRouter>,
  );
}

describe("AccessPage", () => {
  it("states calmly that the area is not part of the user's access", () => {
    renderWithRoles(["CUSTOMER"]);
    expect(screen.getByTestId("access-heading")).toHaveTextContent(
      "This area isn’t part of your access",
    );
  });

  it("focuses the heading on mount", () => {
    renderWithRoles(["CUSTOMER"]);
    expect(screen.getByTestId("access-heading")).toHaveFocus();
  });

  it("links a customer-only user to Banking and nowhere else", () => {
    renderWithRoles(["CUSTOMER"]);
    const link = screen.getByTestId("access-surface-link-banking");
    expect(link).toHaveTextContent("Banking");
    expect(link).toHaveAttribute("href", "/");
    expect(
      screen.queryByTestId("access-surface-link-ops"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("access-surface-link-exec"),
    ).not.toBeInTheDocument();
  });

  it("links an admin-only user to Ops and Exec", () => {
    renderWithRoles(["ADMIN"]);
    expect(screen.getByTestId("access-surface-link-ops")).toHaveAttribute(
      "href",
      "/ops",
    );
    expect(screen.getByTestId("access-surface-link-exec")).toHaveAttribute(
      "href",
      "/exec",
    );
    expect(
      screen.queryByTestId("access-surface-link-banking"),
    ).not.toBeInTheDocument();
  });

  it("renders no surface links and a terminal line for a user with no offered surfaces", () => {
    renderWithRoles(["SUPPORT_AGENT"]);
    expect(screen.getByTestId("access-no-surfaces")).toHaveTextContent(
      "Your sign-in doesn’t include any areas yet.",
    );
    expect(
      screen.queryByTestId("access-surface-link-banking"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("access-surface-link-ops"),
    ).not.toBeInTheDocument();
  });

  it("never renders 404 or not-found language", () => {
    renderWithRoles(["CUSTOMER"]);
    expect(screen.queryByText(/404/)).not.toBeInTheDocument();
    expect(screen.queryByText(/not found/i)).not.toBeInTheDocument();
  });
});
