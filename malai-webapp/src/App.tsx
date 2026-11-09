import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { Authenticated } from "@/auth/Authenticated";
import { Unauthenticated } from "@/auth/Unauthenticated";
import { BankingSurface } from "@/surfaces/BankingSurface";
import { OpsSurface } from "@/surfaces/OpsSurface";
import { ExecSurface } from "@/surfaces/ExecSurface";
import { AccountsPage } from "@/pages/AccountsPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { lazyNamed } from "@/routeChunks";

const LoginPage = lazyNamed(() => import("@/pages/LoginPage"), "LoginPage");
const RegisterPage = lazyNamed(
  () => import("@/pages/RegisterPage"),
  "RegisterPage",
);
const VerifyPage = lazyNamed(() => import("@/pages/VerifyPage"), "VerifyPage");
const AccountDetailPage = lazyNamed(
  () => import("@/pages/AccountDetailPage"),
  "AccountDetailPage",
);
const SpendingPage = lazyNamed(
  () => import("@/pages/SpendingPage"),
  "SpendingPage",
);
const ForecastPage = lazyNamed(
  () => import("@/pages/ForecastPage"),
  "ForecastPage",
);
const PayCardPage = lazyNamed(
  () => import("@/pages/PayCardPage"),
  "PayCardPage",
);
const CardsPage = lazyNamed(() => import("@/pages/CardsPage"), "CardsPage");
const CardDetailPage = lazyNamed(
  () => import("@/pages/CardDetailPage"),
  "CardDetailPage",
);
const CreditCardApplicationPage = lazyNamed(
  () => import("@/pages/CreditCardApplicationPage"),
  "CreditCardApplicationPage",
);
const ProfilePage = lazyNamed(
  () => import("@/pages/ProfilePage"),
  "ProfilePage",
);
const SettingsPage = lazyNamed(
  () => import("@/pages/SettingsPage"),
  "SettingsPage",
);
const MoveMoneyLayout = lazyNamed(
  () => import("@/pages/move/MoveMoneyLayout"),
  "MoveMoneyLayout",
);
const TransferPage = lazyNamed(
  () => import("@/pages/move/TransferPage"),
  "TransferPage",
);
const PayeesPage = lazyNamed(
  () => import("@/pages/move/PayeesPage"),
  "PayeesPage",
);
const AddPayeePage = lazyNamed(
  () => import("@/pages/move/AddPayeePage"),
  "AddPayeePage",
);
const DirectDebitsPage = lazyNamed(
  () => import("@/pages/move/DirectDebitsPage"),
  "DirectDebitsPage",
);
const StandingOrdersPage = lazyNamed(
  () => import("@/pages/move/StandingOrdersPage"),
  "StandingOrdersPage",
);
const NewStandingOrderPage = lazyNamed(
  () => import("@/pages/move/NewStandingOrderPage"),
  "NewStandingOrderPage",
);
const SandboxLayout = lazyNamed(
  () => import("@/pages/sandbox/SandboxLayout"),
  "SandboxLayout",
);
const CounterpartiesPage = lazyNamed(
  () => import("@/pages/sandbox/CounterpartiesPage"),
  "CounterpartiesPage",
);
const InboundPaymentsPage = lazyNamed(
  () => import("@/pages/sandbox/InboundPaymentsPage"),
  "InboundPaymentsPage",
);
const SandboxCardsPage = lazyNamed(
  () => import("@/pages/sandbox/SandboxCardsPage"),
  "SandboxCardsPage",
);
const RedesignPrototypePage = import.meta.env.DEV
  ? lazyNamed(
      () => import("@/prototype-redesign/RedesignPrototypePage"),
      "RedesignPrototypePage",
    )
  : null;
const WorklistPage = lazyNamed(
  () => import("@/pages/ops/WorklistPage"),
  "WorklistPage",
);
const DecisionPage = lazyNamed(
  () => import("@/pages/ops/DecisionPage"),
  "DecisionPage",
);
const CasesPage = lazyNamed(() => import("@/pages/ops/CasesPage"), "CasesPage");
const CasePage = lazyNamed(() => import("@/pages/ops/CasePage"), "CasePage");
const ChainPage = lazyNamed(() => import("@/pages/ops/ChainPage"), "ChainPage");
const ExecDashboardPage = lazyNamed(
  () => import("@/pages/exec/ExecDashboardPage"),
  "ExecDashboardPage",
);

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Unauthenticated />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/verify" element={<VerifyPage />} />
        </Route>
        <Route element={<Authenticated />}>
          <Route element={<BankingSurface />}>
            <Route path="/" element={<AccountsPage />} />
            <Route
              path="/accounts/:accountUid"
              element={<AccountDetailPage />}
            />
            <Route
              path="/accounts/:accountUid/spending"
              element={<SpendingPage />}
            />
            <Route
              path="/accounts/:accountUid/forecast"
              element={<ForecastPage />}
            />
            <Route
              path="/accounts/:accountUid/pay-card"
              element={<PayCardPage />}
            />
            <Route path="/cards" element={<CardsPage />} />
            <Route
              path="/cards/apply"
              element={<CreditCardApplicationPage />}
            />
            <Route path="/cards/:cardId" element={<CardDetailPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/move" element={<MoveMoneyLayout />}>
              <Route index element={<Navigate to="/move/send" replace />} />
              <Route path="send" element={<TransferPage />} />
              <Route path="payees" element={<PayeesPage />} />
              <Route path="payees/new" element={<AddPayeePage />} />
              <Route path="direct-debits" element={<DirectDebitsPage />} />
              <Route path="standing-orders" element={<StandingOrdersPage />} />
              <Route
                path="standing-orders/new"
                element={<NewStandingOrderPage />}
              />
            </Route>
            {RedesignPrototypePage ? (
              <Route
                path="/prototype/redesign"
                element={<RedesignPrototypePage />}
              />
            ) : null}
            <Route path="/sandbox" element={<SandboxLayout />}>
              <Route
                index
                element={<Navigate to="/sandbox/counterparties" replace />}
              />
              <Route path="counterparties" element={<CounterpartiesPage />} />
              <Route
                path="inbound-payments"
                element={<InboundPaymentsPage />}
              />
              <Route path="cards" element={<SandboxCardsPage />} />
            </Route>
          </Route>
          <Route element={<OpsSurface />}>
            <Route path="/ops" element={<WorklistPage />} />
            <Route
              path="/ops/decisions/:decisionId"
              element={<DecisionPage />}
            />
            <Route path="/ops/cases" element={<CasesPage />} />
            <Route path="/ops/cases/:caseId" element={<CasePage />} />
            <Route
              path="/ops/cases/:caseId/decisions/:decisionId"
              element={<DecisionPage />}
            />
            <Route path="/ops/chain" element={<ChainPage />} />
          </Route>
          <Route element={<ExecSurface />}>
            <Route path="/exec" element={<ExecDashboardPage />} />
          </Route>
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
