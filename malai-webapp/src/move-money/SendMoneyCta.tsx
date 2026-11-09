import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

type Props = {
  accountUid: string;
};

export function SendMoneyCta({ accountUid }: Props) {
  return (
    <Button asChild data-testid="send-money-cta">
      <Link to={`/move/send?from=${accountUid}`}>Send money</Link>
    </Button>
  );
}
