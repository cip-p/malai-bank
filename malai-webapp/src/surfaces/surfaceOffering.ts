import type { UserResponse } from "@/api/generated/types.gen";

export type Role = UserResponse["roles"][number];

export type SurfaceId = "banking" | "ops" | "exec";

export type Surface = {
  id: SurfaceId;
  label: string;
  path: string;
};

const GATES: Record<SurfaceId, readonly Role[]> = {
  banking: ["CUSTOMER"],
  ops: ["ADMIN"],
  exec: ["MANAGER", "ADMIN"],
};

const SURFACES_IN_PRECEDENCE: readonly Surface[] = [
  { id: "banking", label: "Banking", path: "/" },
  { id: "ops", label: "Ops", path: "/ops" },
  { id: "exec", label: "Exec", path: "/exec" },
];

export function offeredSurfaces(roles: readonly Role[]): readonly Surface[] {
  return SURFACES_IN_PRECEDENCE.filter((surface) =>
    isSurfaceOffered(roles, surface.id),
  );
}

export function isSurfaceOffered(
  roles: readonly Role[],
  id: SurfaceId,
): boolean {
  return GATES[id].some((role) => roles.includes(role));
}

export function landingPath(roles: readonly Role[]): string {
  const [first] = offeredSurfaces(roles);
  return first ? first.path : "/";
}
