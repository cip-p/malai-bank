import { site } from "./site";

export interface Screenshot {
  file: string;
  alt: string;
}

export interface CapabilityCard {
  title: string;
  claim: string;
  proof: string;
  href?: string;
  screenshot?: Screenshot;
}

export interface WalkthroughStep {
  step: string;
  text: string;
  href?: string;
  screenshot?: Screenshot;
}

export interface Walkthrough {
  name: string;
  steps: WalkthroughStep[];
  cta: { label: string; href: string };
  evidence: { text: string; linkLabel: string; href: string };
}

export interface FeaturedScreenshot extends Screenshot {
  caption: string;
}

export interface Functionality {
  heading: string;
  narrative: string;
  featured: FeaturedScreenshot;
  cards: CapabilityCard[];
  walkthrough: Walkthrough;
}

const webapp = (path: string): string =>
  new URL(path, site.demoSignupUrl).toString();

export const functionality: Functionality = {
  heading: "What this bank does",
  narrative:
    "Malai runs as a working retail bank on synthetic money: customers open accounts, fund them, send and receive Faster Payments, set up standing orders and direct debits, freeze and unfreeze their cards, see where their money went and where their balance is heading, and read an auditable history of every move. The capabilities below are not screenshots of a roadmap — each one is live in the demo, behind the same signup the hero links to.",
  featured: {
    file: "accounts-overview.png",
    alt: "Malai customer app signed in, showing a current account at £14,884.32, a savings account at £8,400.00 and a credit card at −£350.00, with quick actions to send money and manage payees",
    caption: "The customer app, signed in to the live demo.",
  },
  cards: [
    {
      title: "Real-time payments (FPS)",
      claim:
        "Send and receive Faster Payments end-to-end, with same-second settlement to the recipient.",
      proof: "Step 2 of the walkthrough below sends one end-to-end.",
    },
    {
      title: "Direct debits & standing orders",
      claim:
        "Set up a standing order to push money on a schedule; direct-debit mandates arrive from the biller and can be cancelled anytime — both run automatically.",
      proof:
        "A locked scheduler fires each run exactly once, even across scaled-out instances, settling through the same ledger as one-off payments.",
      screenshot: {
        file: "direct-debits.png",
        alt: "Direct debit mandates for Octopus Energy, Spotify Premium and Tesco Mobile, each showing its amount and next collection date",
      },
    },
    {
      title: "Confirmation of Payee",
      claim:
        "Every new payee is name-checked against the receiving bank before any money leaves the account.",
      proof: "Watch the name-match resolve in step 3 of the walkthrough.",
    },
    {
      title: "Spending insights",
      claim:
        "Every transaction is sorted into a spending category automatically, with a month-by-month breakdown of where the money went.",
      proof:
        "Categories are rule-based and explainable, and any transaction can be re-tagged by hand.",
      screenshot: {
        file: "spending-insights.png",
        alt: "Spending screen for July 2026 showing £852.90 spent across bills, subscriptions, general, groceries, transport and payments, with a donut chart breakdown",
      },
    },
    {
      title: "Balance forecast",
      claim:
        "Projects the end-of-month balance from live standing orders and direct debits, flagging early if scheduled payments will leave the account short.",
      proof:
        "It runs on the same scheduling engine that executes the payments, so the dates it predicts are the dates that fire.",
      screenshot: {
        file: "balance-forecast.png",
        alt: "Balance forecast of £13,805.58 for 31 July: available balance minus seven expected payments, listing an upcoming Octopus Energy direct debit and an Eve Robinson standing order",
      },
    },
    {
      title: "Debit & credit cards",
      claim:
        "Every account comes with a card: freeze and unfreeze it instantly, report it lost or stolen, and pay the credit card from any account.",
      proof:
        "Debit cards are issued with the account; credit cards go through an application and settle into their own account on the same ledger.",
      screenshot: {
        file: "cards.png",
        alt: "Cards screen showing an active Malai debit card on the current account and an active credit card, each with a masked number and expiry date",
      },
    },
    {
      title: "Multi-rail settlement (BACS, SWIFT)",
      claim:
        "Real-time rails for retail-speed payments; batch rails for direct debits; cross-border rails for international payments.",
      proof:
        "The same payment domain drives each rail; one mental model, three settlement paths.",
    },
    {
      title: "Auditable double-entry ledger",
      claim:
        "Every transaction debits one account and credits another, and every entry is append-only and hash-chained — money never appears, vanishes, or edits after the fact.",
      proof: "Step 6 of the walkthrough reads the hash-chained ledger record.",
    },
    {
      title: "Modular, themeable platform",
      claim:
        "The customer app is mobile-first, and each banking capability is a discrete module that plugs in without rewiring the rest; visual theming reskins the whole app for a new brand without a rebuild.",
      proof:
        "The newest capabilities on this page plugged in as their own modules, with no core changes.",
    },
  ],
  walkthrough: {
    name: "Follow the money",
    cta: {
      label: "Run this flow yourself — register in the demo →",
      href: site.demoSignupUrl,
    },
    evidence: {
      text: "This flow also runs as an automated end-to-end test on every merge, recorded as sequence diagrams of the calls the system actually made.",
      linkLabel: "Browse the executed flows →",
      href: "/end-to-end-flows/",
    },
    steps: [
      {
        step: "Fund the account",
        text: "Register in the demo, then credit your sandbox balance from the inbound-payments simulator so the rest of the flow has money to move.",
        href: webapp("/sandbox/inbound-payments"),
        screenshot: {
          file: "fund-account.png",
          alt: "Inbound payments simulator crediting £250.00 to the current account from a catalogue counterparty over FPS, with reference Salary advance",
        },
      },
      {
        step: "Make a Faster Payment",
        text: "Send a real-time payment to a saved payee and review it before it goes.",
        href: webapp("/move/send"),
        screenshot: {
          file: "send-payment.png",
          alt: "Review-and-send step of a transfer: £120.00 from the current account to a saved payee with reference Window repair",
        },
      },
      {
        step: "Run a Confirmation of Payee check",
        text: "Add a new payee and watch the name-match resolve before the payment is allowed.",
        href: webapp("/move/payees/new"),
        screenshot: {
          file: "cop-check.png",
          alt: "Confirmation of Payee verdict while adding a payee: on record as Transport for London, you entered Transport for London UK, with the option to use the on-record name",
        },
      },
      {
        step: "Put payments on a schedule",
        text: "Set up a standing order and let the scheduler run it — direct debits collect on the same engine.",
        href: webapp("/move/standing-orders"),
        screenshot: {
          file: "standing-orders.png",
          alt: "Standing orders list with a monthly £850.00 order to Eve Robinson and a weekly £30.00 order to Transport for London, each showing its next run date",
        },
      },
      {
        step: "Spend on the card",
        text: "Authorise a purchase at a fictional merchant in the demo card scheme and watch it settle onto the account.",
        href: webapp("/sandbox/cards"),
        screenshot: {
          file: "card-purchase.png",
          alt: "Card purchases simulator authorising a £4.60 debit-card purchase at Fictional Coffee Co, with controls to settle pending captures or send a merchant refund",
        },
      },
      {
        step: "Read the audit entry",
        text: "Open the account ledger and see the hash-chained record of every move.",
        href: webapp("/"),
        screenshot: {
          file: "account-ledger.png",
          alt: "Account detail screen with the current balance, a balance forecast card and a day-grouped transaction history of payments and card spend",
        },
      },
    ],
  },
};
