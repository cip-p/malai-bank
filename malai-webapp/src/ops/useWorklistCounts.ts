import { useQueries } from "@tanstack/react-query";
import { mdrDecisionsQueryOptions } from "./useMdrDecisions";
import {
  WORKLIST_VIEWS,
  worklistFilter,
  type WorklistView,
} from "./worklistViews";

export type WorklistCounts = Partial<Record<WorklistView, number>>;

export function useWorklistCounts(): WorklistCounts {
  const results = useQueries({
    queries: WORKLIST_VIEWS.map(({ view }) =>
      mdrDecisionsQueryOptions(worklistFilter(view)),
    ),
  });

  const counts: WorklistCounts = {};
  results.forEach((result, index) => {
    if (result.data) {
      counts[WORKLIST_VIEWS[index].view] = result.data.decisions.length;
    }
  });
  return counts;
}
