import { useOutletContext, useSearchParams } from "react-router-dom";
import type { UserResponse } from "@/api/generated/types.gen";
import { PrototypeSwitcher } from "./PrototypeSwitcher";
import { VariantLedger } from "./VariantLedger";
import { VariantPocket } from "./VariantPocket";
import { VariantAtrium } from "./VariantAtrium";

const VARIANTS = [
  { key: "a", name: "Ledger workspace" },
  { key: "b", name: "Pocket card deck" },
  { key: "c", name: "Atrium split-screen" },
];

type AuthenticatedOutletContext = { user: UserResponse };

export function RedesignPrototypePage() {
  const { user } = useOutletContext<AuthenticatedOutletContext>();
  const [searchParams] = useSearchParams();
  const variant = searchParams.get("variant") ?? "a";

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-background text-foreground">
      {variant === "a" ? <VariantLedger user={user} /> : null}
      {variant === "b" ? <VariantPocket user={user} /> : null}
      {variant === "c" ? <VariantAtrium user={user} /> : null}
      <PrototypeSwitcher variants={VARIANTS} current={variant} />
    </div>
  );
}
