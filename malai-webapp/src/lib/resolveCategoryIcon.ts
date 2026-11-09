import {
  ArrowLeftRight,
  Briefcase,
  Bus,
  Coffee,
  CreditCard,
  Film,
  HandCoins,
  Home,
  Plane,
  Receipt,
  Send,
  ShoppingBag,
  ShoppingCart,
  Utensils,
  Wrench,
  Zap,
  type LucideIcon,
} from "lucide-react";

export function resolveCategoryIcon(category: string | undefined): LucideIcon {
  if (!category) return Receipt;
  switch (category.toLowerCase()) {
    case "food":
    case "dining":
    case "restaurants":
      return Coffee;
    case "eating_out":
      return Utensils;
    case "groceries":
    case "supermarket":
      return ShoppingCart;
    case "shopping":
      return ShoppingBag;
    case "transport":
    case "transit":
      return Bus;
    case "travel":
      return Plane;
    case "salary":
    case "income":
      return Briefcase;
    case "cash":
    case "atm":
      return HandCoins;
    case "utilities":
    case "bills":
      return Zap;
    case "rent":
    case "housing":
    case "mortgage":
      return Home;
    case "entertainment":
      return Film;
    case "subscriptions":
      return CreditCard;
    case "services":
      return Wrench;
    case "transfers":
      return ArrowLeftRight;
    case "payments":
      return Send;
    default:
      return Receipt;
  }
}
