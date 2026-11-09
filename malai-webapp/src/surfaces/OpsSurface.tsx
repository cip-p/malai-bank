import { Suspense } from "react";
import { Outlet, useOutletContext } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { OpsShell } from "@/ops/OpsShell";
import { RouteFallback } from "@/components/RouteFallback";
import { AccessPage } from "@/pages/AccessPage";
import { isSurfaceOffered } from "./surfaceOffering";

type SessionOutletContext = { user: UserResponse };

export function OpsSurface() {
  const { user } = useOutletContext<SessionOutletContext>();

  if (!isSurfaceOffered(user.roles, "ops")) {
    return <AccessPage roles={user.roles} />;
  }

  return (
    <OpsShell user={user}>
      <Suspense fallback={<RouteFallback className="max-w-6xl" />}>
        <Outlet context={{ user }} />
      </Suspense>
    </OpsShell>
  );
}
