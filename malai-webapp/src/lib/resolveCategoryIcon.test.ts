import { describe, expect, it } from "vitest";
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
} from "lucide-react";
import { resolveCategoryIcon } from "./resolveCategoryIcon";

describe("resolveCategoryIcon", () => {
  it("maps food/dining categories to Coffee", () => {
    expect(resolveCategoryIcon("Food")).toBe(Coffee);
    expect(resolveCategoryIcon("food")).toBe(Coffee);
    expect(resolveCategoryIcon("Dining")).toBe(Coffee);
    expect(resolveCategoryIcon("Restaurants")).toBe(Coffee);
  });

  it("maps groceries to ShoppingCart", () => {
    expect(resolveCategoryIcon("Groceries")).toBe(ShoppingCart);
    expect(resolveCategoryIcon("Supermarket")).toBe(ShoppingCart);
  });

  it("maps shopping to ShoppingBag", () => {
    expect(resolveCategoryIcon("Shopping")).toBe(ShoppingBag);
  });

  it("maps transport to Bus", () => {
    expect(resolveCategoryIcon("Transport")).toBe(Bus);
    expect(resolveCategoryIcon("Transit")).toBe(Bus);
  });

  it("maps travel to Plane", () => {
    expect(resolveCategoryIcon("Travel")).toBe(Plane);
  });

  it("maps salary/income to Briefcase", () => {
    expect(resolveCategoryIcon("Salary")).toBe(Briefcase);
    expect(resolveCategoryIcon("Income")).toBe(Briefcase);
  });

  it("maps cash withdrawals to HandCoins", () => {
    expect(resolveCategoryIcon("Cash")).toBe(HandCoins);
    expect(resolveCategoryIcon("ATM")).toBe(HandCoins);
  });

  it("maps bills/utilities to Zap", () => {
    expect(resolveCategoryIcon("Utilities")).toBe(Zap);
    expect(resolveCategoryIcon("Bills")).toBe(Zap);
  });

  it("maps rent/housing to Home", () => {
    expect(resolveCategoryIcon("Rent")).toBe(Home);
    expect(resolveCategoryIcon("Housing")).toBe(Home);
    expect(resolveCategoryIcon("Mortgage")).toBe(Home);
  });

  it("maps entertainment to Film", () => {
    expect(resolveCategoryIcon("Entertainment")).toBe(Film);
  });

  it("maps subscriptions to CreditCard", () => {
    expect(resolveCategoryIcon("Subscriptions")).toBe(CreditCard);
  });

  it("maps services/maintenance to Wrench", () => {
    expect(resolveCategoryIcon("Services")).toBe(Wrench);
  });

  it("maps the malai Category enum values", () => {
    expect(resolveCategoryIcon("EATING_OUT")).toBe(Utensils);
    expect(resolveCategoryIcon("GROCERIES")).toBe(ShoppingCart);
    expect(resolveCategoryIcon("TRANSPORT")).toBe(Bus);
    expect(resolveCategoryIcon("BILLS")).toBe(Zap);
    expect(resolveCategoryIcon("HOUSING")).toBe(Home);
    expect(resolveCategoryIcon("SUBSCRIPTIONS")).toBe(CreditCard);
    expect(resolveCategoryIcon("INCOME")).toBe(Briefcase);
    expect(resolveCategoryIcon("TRANSFERS")).toBe(ArrowLeftRight);
    expect(resolveCategoryIcon("PAYMENTS")).toBe(Send);
    expect(resolveCategoryIcon("GENERAL")).toBe(Receipt);
  });

  it("returns the default Receipt icon for unknown categories", () => {
    expect(resolveCategoryIcon("Wibble")).toBe(Receipt);
    expect(resolveCategoryIcon("some-category")).toBe(Receipt);
  });

  it("returns the default Receipt icon for undefined", () => {
    expect(resolveCategoryIcon(undefined)).toBe(Receipt);
  });

  it("returns the default Receipt icon for empty string", () => {
    expect(resolveCategoryIcon("")).toBe(Receipt);
  });
});
