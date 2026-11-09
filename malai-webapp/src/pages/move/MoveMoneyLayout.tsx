import { Link, Outlet, useLocation } from "react-router-dom";
import { cn } from "@/lib/utils";

type SubnavItem = {
  to: string;
  label: string;
  testId: string;
};

const SUBNAV_ITEMS: SubnavItem[] = [
  { to: "/move/send", label: "Send money", testId: "move-subnav-link-send" },
  { to: "/move/payees", label: "Payees", testId: "move-subnav-link-payees" },
  {
    to: "/move/direct-debits",
    label: "Direct Debits",
    testId: "move-subnav-link-direct-debits",
  },
  {
    to: "/move/standing-orders",
    label: "Standing Orders",
    testId: "move-subnav-link-standing-orders",
  },
];

const linkBase =
  "inline-flex h-10 items-center border-b-2 border-transparent px-3 text-sm font-medium text-muted-foreground outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50 motion-safe:transition-colors";
const linkActive = "border-ring text-foreground";

export function MoveMoneyLayout() {
  const { pathname } = useLocation();

  return (
    <div className="mx-auto w-full max-w-5xl p-6">
      <nav
        aria-label="Move money"
        data-testid="move-subnav"
        className="border-b border-border"
      >
        <ul className="flex flex-wrap items-center gap-1">
          {SUBNAV_ITEMS.map((item) => {
            const active = pathname.startsWith(item.to);
            return (
              <li key={item.to}>
                <Link
                  to={item.to}
                  data-testid={item.testId}
                  aria-current={active ? "page" : undefined}
                  className={cn(linkBase, active && linkActive)}
                >
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="mt-6">
        <Outlet />
      </div>
    </div>
  );
}
