const MICROS_PER_MAJOR_UNIT = 1_000_000;

export function formatCostMicros(costMicros: number, currency: string): string {
  const major = costMicros / MICROS_PER_MAJOR_UNIT;
  try {
    return new Intl.NumberFormat("en-GB", {
      style: "currency",
      currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 6,
    }).format(major);
  } catch {
    const amount = new Intl.NumberFormat("en-GB", {
      maximumFractionDigits: 6,
    }).format(major);
    return `${amount} ${currency}`;
  }
}
