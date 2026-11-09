import { useQuery } from "@tanstack/react-query";
import { getApiV1ExecMetricsOptions } from "@/api/generated/@tanstack/react-query.gen";
import type {
  CountMetric,
  Currency,
  ExecMetricsResponse,
  MoneyMetric,
} from "@/api/generated/types.gen";

export type ExecWindow = ExecMetricsResponse["window"];

export type MetricTileState =
  | { kind: "loading" }
  | { kind: "unavailable" }
  | { kind: "count"; count: number }
  | { kind: "money"; amountMinorUnits: number; currency: Currency };

export type ExecMetricsView = {
  isPending: boolean;
  isError: boolean;
  bankingDay: string | null;
  windowStart: string | null;
  asOf: string | null;
  newCustomers: MetricTileState;
  deposits: MetricTileState;
  cardsIssued: MetricTileState;
  paymentsStopped: MetricTileState;
  refetch: () => void;
};

export function execMetricsQueryOptions(window: ExecWindow = "TODAY") {
  return {
    ...getApiV1ExecMetricsOptions({ query: { window } }),
    refetchOnWindowFocus: true,
  };
}

function countState(metric: CountMetric): MetricTileState {
  if (metric.status === "AVAILABLE" && metric.count !== undefined) {
    return { kind: "count", count: metric.count };
  }
  return { kind: "unavailable" };
}

function moneyState(metric: MoneyMetric): MetricTileState {
  if (
    metric.status === "AVAILABLE" &&
    metric.amountMinorUnits !== undefined &&
    metric.currency !== undefined
  ) {
    return {
      kind: "money",
      amountMinorUnits: metric.amountMinorUnits,
      currency: metric.currency,
    };
  }
  return { kind: "unavailable" };
}

export function useExecMetrics(window: ExecWindow = "TODAY"): ExecMetricsView {
  const query = useQuery(execMetricsQueryOptions(window));
  const refetch = () => void query.refetch();

  if (query.isPending) {
    return {
      isPending: true,
      isError: false,
      bankingDay: null,
      windowStart: null,
      asOf: null,
      newCustomers: { kind: "loading" },
      deposits: { kind: "loading" },
      cardsIssued: { kind: "loading" },
      paymentsStopped: { kind: "loading" },
      refetch,
    };
  }

  if (query.isError || query.data === undefined) {
    return {
      isPending: false,
      isError: true,
      bankingDay: null,
      windowStart: null,
      asOf: null,
      newCustomers: { kind: "unavailable" },
      deposits: { kind: "unavailable" },
      cardsIssued: { kind: "unavailable" },
      paymentsStopped: { kind: "unavailable" },
      refetch,
    };
  }

  const data = query.data;
  return {
    isPending: false,
    isError: false,
    bankingDay: data.bankingDay,
    windowStart: data.windowStart,
    asOf: data.asOf,
    newCustomers: countState(data.newCustomers),
    deposits: moneyState(data.deposits),
    cardsIssued: countState(data.cardsIssued),
    paymentsStopped: moneyState(data.paymentsStopped),
    refetch,
  };
}
