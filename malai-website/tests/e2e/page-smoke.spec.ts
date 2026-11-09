import { test, expect } from "./fixtures";
import { hero } from "../../src/content/hero";
import { site } from "../../src/content/site";
import { functionality } from "../../src/content/functionality";
import { security } from "../../src/content/security";
import { architecture } from "../../src/content/architecture";
import { closeOut } from "../../src/content/closeout";
import { footer } from "../../src/content/footer";
import { surfaces } from "../../src/content/surfaces";
import { operations } from "../../src/content/operations";
import { executive } from "../../src/content/executive";
import { nextSteps } from "../../src/content/next-steps";

const expectedColumns = [
  {
    title: "Platform",
    links: [
      "Operations",
      "Executive",
      "Architecture",
      "Security",
      "End-to-end flows",
    ],
  },
  { title: "Project", links: ["About", "Talk to us"] },
  { title: "Legal", links: ["What this isn't", "Privacy", "Terms"] },
];

test.describe("home page — walking skeleton", () => {
  test("renders the canonical headline as the H1", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText(
      hero.headline,
    );
  });

  test("renders the canonical subhead verbatim", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText(hero.subhead, { exact: true })).toBeVisible();
  });

  test("primary CTA links to the webapp signup", async ({ page }) => {
    await page.goto("/");
    const heroEl = page.getByTestId("hero");
    const cta = heroEl.getByRole("link", { name: hero.primaryCta.label });
    await expect(cta).toHaveAttribute("href", hero.primaryCta.href);
  });

  test("secondary CTA links to the team mailto", async ({ page }) => {
    await page.goto("/");
    const heroEl = page.getByTestId("hero");
    const cta = heroEl.getByRole("link", { name: hero.secondaryCta.label });
    await expect(cta).toHaveAttribute("href", hero.secondaryCta.href);
  });

  test("rendered HTML never names 'MalaiBank'", async ({ page }) => {
    await page.goto("/");
    const html = await page.content();
    expect(html).not.toContain("MalaiBank");
  });

  test("document title is the canonical site title", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(site.title);
  });
});

test.describe("home page — surfaces section", () => {
  test("mounts under the #surfaces anchor with heading and narrative", async ({
    page,
  }) => {
    await page.goto("/");
    const section = page.locator("section#surfaces");
    await expect(section).toBeVisible();
    await expect(
      section.getByRole("heading", { level: 2, name: surfaces.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(surfaces.narrative, { exact: true }),
    ).toBeVisible();
  });

  test("renders one card per surface with claim, screenshot and link", async ({
    page,
  }) => {
    await page.goto("/");
    const section = page.locator("section#surfaces");
    const cards = section.getByTestId("surface-card");
    await expect(cards).toHaveCount(surfaces.cards.length);
    expect(surfaces.cards.length).toBe(3);

    for (const card of surfaces.cards) {
      const node = section.getByTestId("surface-card").filter({
        hasText: card.claim,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByTestId("screenshot")).toBeVisible();
      const link = node.getByTestId("surface-card-link");
      await expect(link).toHaveText(card.linkLabel);
      await expect(link).toHaveAttribute("href", card.href);
    }
  });
});

test.describe("home page — functionality section", () => {
  test("mounts under the #functionality anchor", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#functionality");
    await expect(section).toBeVisible();
  });

  test("renders the narrative paragraph and section heading", async ({
    page,
  }) => {
    await page.goto("/");
    const section = page.locator("section#functionality");
    await expect(
      section.getByRole("heading", { level: 2, name: functionality.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(functionality.narrative, { exact: true }),
    ).toBeVisible();
  });

  test("renders one card per capability with title, claim and proof", async ({
    page,
  }) => {
    await page.goto("/");
    const section = page.locator("section#functionality");
    const cards = section.getByTestId("capability-card");
    await expect(cards).toHaveCount(functionality.cards.length);
    expect(functionality.cards.length).toBeGreaterThanOrEqual(6);
    expect(functionality.cards.length).toBeLessThanOrEqual(9);

    for (const card of functionality.cards) {
      const node = section.getByTestId("capability-card").filter({
        hasText: card.title,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(card.claim, { exact: true })).toBeVisible();
      await expect(node.getByText(card.proof, { exact: true })).toBeVisible();
      if (card.href) {
        const link = node.getByRole("link", { name: card.proof });
        await expect(link).toHaveAttribute("href", card.href);
      }
    }
  });

  test("renders the named follow-the-money walkthrough with all steps", async ({
    page,
  }) => {
    await page.goto("/");
    const section = page.locator("section#functionality");
    const walkthrough = section.getByTestId("walkthrough");
    await expect(walkthrough).toBeVisible();
    await expect(
      walkthrough.getByRole("heading", {
        level: 3,
        name: functionality.walkthrough.name,
      }),
    ).toBeVisible();

    const steps = walkthrough.getByTestId("walkthrough-step");
    await expect(steps).toHaveCount(functionality.walkthrough.steps.length);

    for (const step of functionality.walkthrough.steps) {
      const node = walkthrough.getByTestId("walkthrough-step").filter({
        hasText: step.step,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(step.text, { exact: true })).toBeVisible();
    }
  });

  test("walkthrough links to the executed end-to-end flows in a new tab", async ({
    page,
  }) => {
    await page.goto("/");
    const evidence = page.getByTestId("walkthrough-evidence");
    await expect(evidence).toBeVisible();
    await expect(
      evidence.getByText(functionality.walkthrough.evidence.text),
    ).toBeVisible();
    const link = evidence.getByTestId("walkthrough-evidence-link");
    await expect(link).toHaveText(functionality.walkthrough.evidence.linkLabel);
    await expect(link).toHaveAttribute(
      "href",
      functionality.walkthrough.evidence.href,
    );
    await expect(link).toHaveAttribute("target", "_blank");
    await expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(functionality.walkthrough.evidence.href).toBe("/end-to-end-flows/");
  });
});

test.describe("home page — security teaser", () => {
  test("mounts under the #security anchor", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#security");
    await expect(section).toBeVisible();
  });

  test("renders the heading, teaser body, and a link to /security", async ({
    page,
  }) => {
    await page.goto("/");
    const teaser = page.getByTestId("security-teaser");
    await expect(teaser).toBeVisible();
    await expect(
      teaser.getByRole("heading", { level: 2, name: security.heading }),
    ).toBeVisible();
    await expect(
      teaser.getByText(security.teaser, { exact: true }),
    ).toBeVisible();
    const link = teaser.getByTestId("security-teaser-link");
    await expect(link).toHaveAttribute("href", "/security/");
  });

  test("does not render the full trust ledger on the home page", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByTestId("trust-ledger")).toHaveCount(0);
  });
});

test.describe("home page — architecture teaser", () => {
  test("mounts under the #architecture anchor", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#architecture");
    await expect(section).toBeVisible();
  });

  test("renders the heading, teaser body, and a link to /architecture", async ({
    page,
  }) => {
    await page.goto("/");
    const teaser = page.getByTestId("architecture-teaser");
    await expect(teaser).toBeVisible();
    await expect(
      teaser.getByRole("heading", { level: 2, name: architecture.heading }),
    ).toBeVisible();
    await expect(
      teaser.getByText(architecture.teaser, { exact: true }),
    ).toBeVisible();
    const link = teaser.getByTestId("architecture-teaser-link");
    await expect(link).toHaveAttribute("href", "/architecture/");
  });

  test("does not render the full architecture diagram on the home page", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page.getByTestId("architecture-diagram")).toHaveCount(0);
  });
});

test.describe("/security page — full posture", () => {
  test("renders the section heading and narrative paragraph", async ({
    page,
  }) => {
    await page.goto("/security");
    const section = page.locator("section#security");
    await expect(
      section.getByRole("heading", { level: 1, name: security.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(security.narrative, { exact: true }),
    ).toBeVisible();
  });

  test("renders one trust-ledger row per entry with claim, detail and evidence", async ({
    page,
  }) => {
    await page.goto("/security");
    const section = page.locator("section#security");
    const entries = section.getByTestId("trust-ledger-entry");
    await expect(entries).toHaveCount(security.trustLedger.length);
    expect(security.trustLedger.length).toBe(18);

    for (let i = 0; i < security.trustLedger.length; i++) {
      const entry = security.trustLedger[i];
      const node = entries.nth(i);
      await expect(
        node.getByTestId("trust-claim").getByText(entry.claim, { exact: true }),
      ).toBeVisible();
      await expect(
        node.getByTestId("trust-detail").getByText(entry.detail, {
          exact: true,
        }),
      ).toBeVisible();

      const evidence = node.getByTestId("trust-evidence");
      await expect(evidence).toHaveCount(1);
      await expect(evidence).toHaveAttribute(
        "data-evidence-kind",
        entry.evidence.kind,
      );
      await expect(evidence).toHaveText(entry.evidence.text);
      if (entry.evidence.kind === "link") {
        await expect(evidence).toHaveAttribute("href", entry.evidence.href);
      }
    }
  });
});

test.describe("/architecture page — full posture", () => {
  test("renders the section heading and narrative paragraph", async ({
    page,
  }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    await expect(
      section.getByRole("heading", { level: 1, name: architecture.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(architecture.narrative, { exact: true }),
    ).toBeVisible();
  });

  test("renders the architecture diagram with title and description", async ({
    page,
  }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    const diagram = section.getByTestId("architecture-diagram");
    await expect(diagram).toBeVisible();
    await expect(diagram).toHaveAttribute("data-diagram-variant", "v1");
    await expect(diagram.locator("title")).toHaveText(
      architecture.diagram.title,
    );
    await expect(diagram.locator("desc")).toHaveText(
      architecture.diagram.description,
    );
  });

  test("figure container carries an aria-label naming the diagram", async ({
    page,
  }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    const figure = section.getByTestId("architecture-diagram-figure");
    await expect(figure).toHaveAttribute(
      "aria-label",
      architecture.diagram.title,
    );
  });

  test("diagram includes a sync/async legend", async ({ page }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    const diagram = section.getByTestId("architecture-diagram");
    await expect(diagram.getByText("Synchronous request")).toBeVisible();
    await expect(diagram.getByText("Asynchronous event")).toBeVisible();
  });

  test("renders the end-to-end evidence panel linking to the published Kensa report", async ({
    page,
  }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    const evidence = section.getByTestId("architecture-evidence");
    await expect(evidence).toBeVisible();
    await expect(
      evidence.getByRole("heading", {
        level: 2,
        name: architecture.evidence.heading,
      }),
    ).toBeVisible();
    await expect(
      evidence.getByText(architecture.evidence.body, { exact: true }),
    ).toBeVisible();
    const link = evidence.getByTestId("architecture-evidence-link");
    await expect(link).toHaveText(architecture.evidence.linkLabel);
    await expect(link).toHaveAttribute("href", architecture.evidence.href);
    await expect(link).toHaveAttribute("target", "_blank");
    await expect(link).toHaveAttribute("rel", "noopener noreferrer");
    expect(architecture.evidence.href).toBe("/end-to-end-flows/");
  });

  test("renders one annotation per pattern with title and detail", async ({
    page,
  }) => {
    await page.goto("/architecture");
    const section = page.locator("section#architecture");
    const annotations = section.getByTestId("architecture-annotation");
    await expect(annotations).toHaveCount(architecture.annotations.length);

    for (let i = 0; i < architecture.annotations.length; i++) {
      const annotation = architecture.annotations[i];
      const node = annotations.nth(i);
      await expect(
        node
          .getByTestId("architecture-annotation-title")
          .getByText(annotation.title, { exact: true }),
      ).toBeVisible();
      await expect(
        node
          .getByTestId("architecture-annotation-detail")
          .getByText(annotation.detail, { exact: true }),
      ).toBeVisible();
    }
  });
});

test.describe("/operations page — the decision record", () => {
  test("renders the section heading and narrative", async ({ page }) => {
    await page.goto("/operations");
    const section = page.locator("section#operations");
    await expect(
      section.getByRole("heading", { level: 1, name: operations.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(operations.narrative, { exact: true }),
    ).toBeVisible();
  });

  test("renders the four record stats", async ({ page }) => {
    await page.goto("/operations");
    const section = page.locator("section#operations");
    const stats = section.getByTestId("operations-stat");
    await expect(stats).toHaveCount(operations.stats.length);
    for (const stat of operations.stats) {
      const node = section.getByTestId("operations-stat").filter({
        hasText: stat.label,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(stat.value, { exact: true })).toBeVisible();
    }
  });

  test("renders one step per decision-plane stage with title, text and screenshot", async ({
    page,
  }) => {
    await page.goto("/operations");
    const section = page.locator("section#operations");
    const steps = section.getByTestId("operations-step");
    await expect(steps).toHaveCount(operations.steps.length);

    for (const step of operations.steps) {
      const node = section.getByTestId("operations-step").filter({
        hasText: step.title,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(step.text, { exact: true })).toBeVisible();
      await expect(node.getByTestId("screenshot")).toBeVisible();
    }
  });

  test("renders the copilot panel with every point and its screenshot", async ({
    page,
  }) => {
    await page.goto("/operations");
    const panel = page.getByTestId("operations-copilot");
    await expect(panel).toBeVisible();
    await expect(
      panel.getByRole("heading", {
        level: 2,
        name: operations.copilot.heading,
      }),
    ).toBeVisible();
    await expect(
      panel.getByText(operations.copilot.body, { exact: true }),
    ).toBeVisible();
    const points = panel.getByTestId("operations-copilot-point");
    await expect(points).toHaveCount(operations.copilot.points.length);
    await expect(panel.getByTestId("screenshot")).toBeVisible();
  });

  test("renders the AML block with its steps, screenshots and points", async ({
    page,
  }) => {
    await page.goto("/operations");
    const block = page.getByTestId("operations-aml");
    await expect(block).toBeVisible();
    await expect(
      block.getByRole("heading", { level: 2, name: operations.aml.heading }),
    ).toBeVisible();
    await expect(
      block.getByText(operations.aml.body, { exact: true }),
    ).toBeVisible();

    const steps = block.getByTestId("operations-aml-step");
    await expect(steps).toHaveCount(operations.aml.steps.length);
    for (const step of operations.aml.steps) {
      const node = block.getByTestId("operations-aml-step").filter({
        hasText: step.title,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(step.text, { exact: true })).toBeVisible();
      await expect(node.getByTestId("screenshot")).toBeVisible();
    }

    const points = block.getByTestId("operations-aml-point");
    await expect(points).toHaveCount(operations.aml.points.length);
  });

  test("renders the model-seam panel with every point", async ({ page }) => {
    await page.goto("/operations");
    const panel = page.getByTestId("operations-seam");
    await expect(panel).toBeVisible();
    await expect(
      panel.getByRole("heading", { level: 2, name: operations.seam.heading }),
    ).toBeVisible();
    await expect(
      panel.getByText(operations.seam.body, { exact: true }),
    ).toBeVisible();
    const points = panel.getByTestId("operations-seam-point");
    await expect(points).toHaveCount(operations.seam.points.length);
  });

  test("CTA opens the demo signup and evidence links to the executed flows", async ({
    page,
  }) => {
    await page.goto("/operations");
    const cta = page.getByTestId("operations-cta");
    await expect(cta).toHaveText(operations.cta.label);
    await expect(cta).toHaveAttribute("href", operations.cta.href);
    const evidence = page.getByTestId("operations-evidence-link");
    await expect(evidence).toHaveAttribute("href", operations.evidence.href);
    await expect(evidence).toHaveAttribute("target", "_blank");
    await expect(evidence).toHaveAttribute("rel", "noopener noreferrer");
    expect(operations.evidence.href).toBe("/end-to-end-flows/");
  });
});

test.describe("/executive page — the dashboard", () => {
  test("renders the section heading, narrative and dashboard screenshot", async ({
    page,
  }) => {
    await page.goto("/executive");
    const section = page.locator("section#executive");
    await expect(
      section.getByRole("heading", { level: 1, name: executive.heading }),
    ).toBeVisible();
    await expect(
      section.getByText(executive.narrative, { exact: true }),
    ).toBeVisible();
    await expect(
      section.getByTestId("executive-screenshot").getByTestId("screenshot"),
    ).toBeVisible();
  });

  test("renders one card per point with title and detail", async ({ page }) => {
    await page.goto("/executive");
    const section = page.locator("section#executive");
    const points = section.getByTestId("executive-point");
    await expect(points).toHaveCount(executive.points.length);

    for (const point of executive.points) {
      const node = section.getByTestId("executive-point").filter({
        hasText: point.title,
      });
      await expect(node).toHaveCount(1);
      await expect(node.getByText(point.detail, { exact: true })).toBeVisible();
    }
  });

  test("CTA opens the demo signup", async ({ page }) => {
    await page.goto("/executive");
    const cta = page.getByTestId("executive-cta");
    await expect(cta).toHaveText(executive.cta.label);
    await expect(cta).toHaveAttribute("href", executive.cta.href);
  });
});

test.describe("home page — close-out section", () => {
  test("mounts under the #close-out anchor", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#close-out");
    await expect(section).toBeVisible();
  });

  test("renders the closing line verbatim", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#close-out");
    await expect(section.getByTestId("closeout-line")).toHaveText(
      closeOut.closingLine,
    );
  });

  test("primary CTA matches the hero's href and label", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#close-out");
    const cta = section.getByTestId("closeout-primary-cta");
    await expect(cta).toHaveText(closeOut.primaryCta.label);
    await expect(cta).toHaveAttribute("href", closeOut.primaryCta.href);
    expect(closeOut.primaryCta.href).toBe(hero.primaryCta.href);
  });

  test("secondary CTA matches the hero's href and label", async ({ page }) => {
    await page.goto("/");
    const section = page.locator("section#close-out");
    const cta = section.getByTestId("closeout-secondary-cta");
    await expect(cta).toHaveText(closeOut.secondaryCta.label);
    await expect(cta).toHaveAttribute("href", closeOut.secondaryCta.href);
    expect(closeOut.secondaryCta.href).toBe(hero.secondaryCta.href);
  });
});

test.describe("home page — external links", () => {
  test("every new-tab link is described as opening in a new tab", async ({
    page,
  }) => {
    await page.goto("/");
    const hint = page.locator("#new-tab-hint");
    await expect(hint).toHaveText("Opens in a new tab");
    const external = page.locator('a[target="_blank"]');
    expect(await external.count()).toBeGreaterThan(0);
    for (const link of await external.all()) {
      await expect(link).toHaveAttribute("aria-describedby", "new-tab-hint");
      await expect(link).toHaveAccessibleDescription("Opens in a new tab");
    }
  });
});

test.describe("next-steps panel — about and what-this-isnt", () => {
  for (const path of ["/about/", "/what-this-isnt/"]) {
    test(`${path} closes with the demo first and talk to us second`, async ({
      page,
    }) => {
      await page.goto(path);
      const panel = page.getByTestId("next-steps");
      await expect(
        panel.getByRole("heading", { level: 2, name: nextSteps.heading }),
      ).toBeVisible();
      const steps = panel.getByTestId("next-step");
      await expect(steps).toHaveCount(2);

      const first = steps.nth(0).getByRole("link");
      await expect(first).toHaveText(hero.primaryCta.label);
      await expect(first).toHaveAttribute("href", hero.primaryCta.href);
      await expect(first).toHaveAttribute("target", "_blank");

      const second = steps.nth(1).getByRole("link");
      await expect(second).toHaveText(hero.secondaryCta.label);
      await expect(second).toHaveAttribute("href", hero.secondaryCta.href);
      await expect(second).not.toHaveAttribute("target", "_blank");
    });
  }
});

test.describe("home page — footer", () => {
  test("mounts a single <footer> element", async ({ page }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    await expect(footerEl).toBeVisible();
  });

  test("renders the brand mark and description", async ({ page }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    await expect(
      footerEl.getByText("Malai", { exact: true }).first(),
    ).toBeVisible();
    await expect(
      footerEl.getByText(footer.description, { exact: true }),
    ).toBeVisible();
  });

  test("renders three navigation columns with the expected links", async ({
    page,
  }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    for (const column of expectedColumns) {
      const nav = footerEl.getByRole("navigation", { name: column.title });
      await expect(nav).toBeVisible();
      for (const label of column.links) {
        await expect(nav.getByRole("link", { name: label })).toBeVisible();
      }
    }
  });

  test("End-to-end flows link points at the published report in a new tab", async ({
    page,
  }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    const platformNav = footerEl.getByRole("navigation", { name: "Platform" });
    const flows = platformNav.getByRole("link", { name: "End-to-end flows" });
    await expect(flows).toHaveAttribute("href", "/end-to-end-flows/");
    await expect(flows).toHaveAttribute("target", "_blank");
    await expect(flows).toHaveAttribute("rel", "noopener noreferrer");
  });

  test("Talk to us link is a mailto to the site contact email", async ({
    page,
  }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    const projectNav = footerEl.getByRole("navigation", { name: "Project" });
    const contact = projectNav.getByRole("link", { name: "Talk to us" });
    await expect(contact).toHaveAttribute(
      "href",
      `mailto:${site.contactEmail}`,
    );
    expect(site.contactEmail).toBe("info@malaibank.com");
  });

  test("renders the disclosure string verbatim", async ({ page }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    await expect(footerEl.getByTestId("footer-disclosure")).toHaveText(
      footer.disclosure,
    );
  });

  test("renders the current copyright year computed at build time", async ({
    page,
  }) => {
    await page.goto("/");
    const footerEl = page.getByTestId("footer");
    const year = new Date().getFullYear();
    await expect(footerEl.getByTestId("footer-copyright")).toHaveText(
      `© ${year} Malai. Demonstration platform — not a regulated entity.`,
    );
  });
});
