import type { Frequency } from "@/api/generated/types.gen";

export function formatFrequency(frequency: Frequency): string {
  switch (frequency) {
    case "WEEKLY":
      return "Weekly";
    case "MONTHLY":
      return "Monthly";
    case "QUARTERLY":
      return "Quarterly";
    case "ANNUAL":
      return "Annually";
  }
}
