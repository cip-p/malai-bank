import { describe, expect, it } from "vitest";
import {
  isSurfaceOffered,
  landingPath,
  offeredSurfaces,
  type Role,
} from "./surfaceOffering";

function idsOf(roles: readonly Role[]): string[] {
  return offeredSurfaces(roles).map((s) => s.id);
}

describe("offeredSurfaces", () => {
  it("offers Banking alone to a customer-only user", () => {
    expect(idsOf(["CUSTOMER"])).toEqual(["banking"]);
  });

  it("offers Ops and Exec to an admin-only user", () => {
    expect(idsOf(["ADMIN"])).toEqual(["ops", "exec"]);
  });

  it("offers Exec alone to a manager-only user", () => {
    expect(idsOf(["MANAGER"])).toEqual(["exec"]);
  });

  it("offers nothing to a support-agent-only user", () => {
    expect(idsOf(["SUPPORT_AGENT"])).toEqual([]);
  });

  it("offers nothing to a user with no roles", () => {
    expect(idsOf([])).toEqual([]);
  });

  it("offers all three surfaces to a customer+admin user, Banking first", () => {
    expect(idsOf(["CUSTOMER", "ADMIN"])).toEqual(["banking", "ops", "exec"]);
  });

  it("offers Banking and Exec to a customer+manager user, but not Ops", () => {
    expect(idsOf(["CUSTOMER", "MANAGER"])).toEqual(["banking", "exec"]);
  });

  it("offers Ops and Exec unchanged to a manager+admin user", () => {
    expect(idsOf(["MANAGER", "ADMIN"])).toEqual(["ops", "exec"]);
  });

  it("ignores the support-agent role alongside customer", () => {
    expect(idsOf(["CUSTOMER", "SUPPORT_AGENT"])).toEqual(["banking"]);
  });

  it("ignores the support-agent role alongside admin", () => {
    expect(idsOf(["SUPPORT_AGENT", "ADMIN"])).toEqual(["ops", "exec"]);
  });

  it("offers all three surfaces to a user holding every role", () => {
    expect(idsOf(["CUSTOMER", "SUPPORT_AGENT", "ADMIN"])).toEqual([
      "banking",
      "ops",
      "exec",
    ]);
  });

  it("exposes each surface's path and label", () => {
    expect(offeredSurfaces(["CUSTOMER", "ADMIN"])).toEqual([
      { id: "banking", label: "Banking", path: "/" },
      { id: "ops", label: "Ops", path: "/ops" },
      { id: "exec", label: "Exec", path: "/exec" },
    ]);
  });
});

describe("isSurfaceOffered", () => {
  it("offers Banking on the customer role", () => {
    expect(isSurfaceOffered(["CUSTOMER"], "banking")).toBe(true);
    expect(isSurfaceOffered(["ADMIN"], "banking")).toBe(false);
  });

  it("offers Ops and Exec on the admin role", () => {
    expect(isSurfaceOffered(["ADMIN"], "ops")).toBe(true);
    expect(isSurfaceOffered(["ADMIN"], "exec")).toBe(true);
    expect(isSurfaceOffered(["CUSTOMER"], "ops")).toBe(false);
    expect(isSurfaceOffered(["CUSTOMER"], "exec")).toBe(false);
  });

  it("offers no surface on the support-agent role", () => {
    expect(isSurfaceOffered(["SUPPORT_AGENT"], "banking")).toBe(false);
    expect(isSurfaceOffered(["SUPPORT_AGENT"], "ops")).toBe(false);
    expect(isSurfaceOffered(["SUPPORT_AGENT"], "exec")).toBe(false);
  });

  it("offers Exec alone on the manager role", () => {
    expect(isSurfaceOffered(["MANAGER"], "exec")).toBe(true);
    expect(isSurfaceOffered(["MANAGER"], "ops")).toBe(false);
    expect(isSurfaceOffered(["MANAGER"], "banking")).toBe(false);
  });
});

describe("landingPath", () => {
  it("lands a customer-only user on Banking", () => {
    expect(landingPath(["CUSTOMER"])).toBe("/");
  });

  it("lands a customer+admin user on Banking, not Ops", () => {
    expect(landingPath(["CUSTOMER", "ADMIN"])).toBe("/");
  });

  it("falls through a withheld Banking to Ops for an admin-only user", () => {
    expect(landingPath(["ADMIN"])).toBe("/ops");
  });

  it("prefers Ops over Exec when both are offered without Banking", () => {
    expect(landingPath(["SUPPORT_AGENT", "ADMIN"])).toBe("/ops");
  });

  it("lands a manager-only user on Exec", () => {
    expect(landingPath(["MANAGER"])).toBe("/exec");
  });

  it("lands a customer+manager user on Banking, not Exec", () => {
    expect(landingPath(["CUSTOMER", "MANAGER"])).toBe("/");
  });

  it("lands a manager+admin user on Ops, unchanged from admin alone", () => {
    expect(landingPath(["MANAGER", "ADMIN"])).toBe("/ops");
  });

  it("lands a support-agent-only user on the Banking root where the Access page renders", () => {
    expect(landingPath(["SUPPORT_AGENT"])).toBe("/");
  });

  it("lands a user with no roles on the Banking root where the Access page renders", () => {
    expect(landingPath([])).toBe("/");
  });
});
