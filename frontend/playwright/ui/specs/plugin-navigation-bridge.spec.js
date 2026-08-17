import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";

const frontendRoot = fileURLToPath(new URL("../../../", import.meta.url));
const sharedBundlePath = `${frontendRoot}resources/public/js/shared.js`;
const runtimePath = fileURLToPath(
  import.meta.resolve("@penpot/plugins-runtime/index.js"),
);
const productionDesignOrigin = "https://design.podconverge.com";
const productionPluginOrigin = "https://plugin.podconverge.com";
const developerPluginOrigin = "https://plugin-develop.podconverge.com";
const localPluginOrigin = "http://localhost:4403";
const destinationOrigins = [
  "https://app.podconverge.com",
  "https://pod-frontend-21ef7100c347.herokuapp.com",
  "https://localhost:3002",
];

async function installBridge(page, designOrigin = productionDesignOrigin) {
  await page.route(`${designOrigin}/**`, async (route) => {
    const url = new URL(route.request().url());

    if (url.pathname === "/bridge-test") {
      await route.fulfill({
        contentType: "text/html",
        body: "<!doctype html><html><body></body></html>",
      });
      return;
    }

    const response = await route.fetch({
      url: `http://localhost:3000${url.pathname}${url.search}`,
    });
    await route.fulfill({ response });
  });
  for (const destinationOrigin of destinationOrigins) {
    await page.route(`${destinationOrigin}/**`, (route) =>
      route.fulfill({
        contentType: "text/html",
        body: "<!doctype html><html><body>Destination</body></html>",
      }),
    );
  }

  await page.goto(`${designOrigin}/bridge-test`);
  await page.addScriptTag({
    content: await readFile(sharedBundlePath, "utf8"),
    type: "module",
  });
  await page.waitForFunction(() => window.app?.plugins?.register);
  await page.evaluate(() => {
    window.app.plugins.register.install_navigation_bridge_BANG_();
  });
  await page.addScriptTag({ path: runtimePath, type: "module" });
  await page.waitForFunction(() => customElements.get("plugin-modal"));
}

async function frameForUrl(page, url) {
  await expect
    .poll(() => page.frames().some((frame) => frame.url() === url))
    .toBe(true);
  return page.frames().find((frame) => frame.url() === url);
}

async function addPluginModal(page, pluginOrigin = productionPluginOrigin) {
  await page.route(`${pluginOrigin}/**`, (route) =>
    route.fulfill({
      contentType: "text/html",
      body: "<!doctype html><html><body>Plugin</body></html>",
    }),
  );

  await page.evaluate((iframeSrc) => {
    const modal = document.createElement("plugin-modal");
    modal.setAttribute("title", "PodConverge");
    modal.setAttribute("iframe-src", iframeSrc);
    document.body.appendChild(modal);
  }, `${pluginOrigin}/plugin.html`);

  const iframe = page.locator("plugin-modal").locator("iframe");
  await expect(iframe).toHaveAttribute("src", `${pluginOrigin}/plugin.html`);
  return frameForUrl(page, `${pluginOrigin}/plugin.html`);
}

async function sendNavigation(frame, url) {
  await frame.evaluate((destination) => {
    window.parent.postMessage(
      { type: "podconverge:navigate", url: destination },
      "*",
    );
  }, url);
}

async function expectIgnored(page, action) {
  const originalUrl = page.url();
  let popupCreated = false;
  page.once("popup", () => {
    popupCreated = true;
  });

  await action();
  await page.waitForTimeout(200);

  expect(page.url()).toBe(originalUrl);
  expect(popupCreated).toBe(false);
}

for (const [name, destination] of [
  [
    "trusted plugin navigates Direct Order Continue in the same tab",
    "https://app.podconverge.com/panel/orders?cart",
  ],
  [
    "trusted plugin navigates Mockup Publish in the same tab",
    "https://app.podconverge.com/panel/design-hub/publish-to-stores/product-123/mockups?selectStore=true",
  ],
  [
    "trusted plugin navigates AI Upgrade in the same tab",
    "https://app.podconverge.com/panel/billing/plans",
  ],
]) {
  test(name, async ({ page }) => {
    await installBridge(page);
    const pluginFrame = await addPluginModal(page);
    const popupPromise = page
      .waitForEvent("popup", { timeout: 500 })
      .then(() => true)
      .catch(() => false);

    await sendNavigation(pluginFrame, destination);
    await expect(page).toHaveURL(destination);
    expect(await popupPromise).toBe(false);
  });
}

test("developer plugin navigates an approved production destination", async ({
  page,
}) => {
  await installBridge(page);
  const pluginFrame = await addPluginModal(page, developerPluginOrigin);

  await sendNavigation(
    pluginFrame,
    "https://app.podconverge.com/panel/orders?cart",
  );
  await expect(page).toHaveURL("https://app.podconverge.com/panel/orders?cart");
});

test("developer plugin cannot bypass production destination validation", async ({
  page,
}) => {
  await installBridge(page);
  const pluginFrame = await addPluginModal(page, developerPluginOrigin);

  await expectIgnored(page, () =>
    sendNavigation(pluginFrame, "https://app.podconverge.com/panel/admin"),
  );
});

test("navigation bridge rejects untrusted senders", async ({ page }) => {
  await installBridge(page);
  const trustedFrame = await addPluginModal(page);

  await page.route("https://evil.example/**", (route) =>
    route.fulfill({ contentType: "text/html", body: "<!doctype html>" }),
  );
  await page.evaluate(() => {
    const iframe = document.createElement("iframe");
    iframe.src = "https://evil.example/plugin.html";
    document.body.appendChild(iframe);
  });
  const evilFrame = await frameForUrl(page, "https://evil.example/plugin.html");

  await page.evaluate((iframeSrc) => {
    const iframe = document.createElement("iframe");
    iframe.src = iframeSrc;
    document.body.appendChild(iframe);
  }, `${productionPluginOrigin}/orphan.html`);
  const orphanTrustedFrame = await frameForUrl(
    page,
    `${productionPluginOrigin}/orphan.html`,
  );

  await expectIgnored(page, () =>
    sendNavigation(evilFrame, "https://app.podconverge.com/panel/orders?cart"),
  );
  await expectIgnored(page, () =>
    sendNavigation(
      orphanTrustedFrame,
      "https://app.podconverge.com/panel/orders?cart",
    ),
  );
  await expectIgnored(page, () =>
    trustedFrame.evaluate(() => {
      window.parent.postMessage(
        {
          type: "not-podconverge:navigate",
          url: "https://app.podconverge.com/panel/orders?cart",
        },
        "*",
      );
    }),
  );
});

test("production Design Studio rejects the local plugin origin", async ({
  page,
}) => {
  await installBridge(page);
  const pluginFrame = await addPluginModal(page, localPluginOrigin);

  await expectIgnored(page, () =>
    sendNavigation(
      pluginFrame,
      "https://app.podconverge.com/panel/orders?cart",
    ),
  );
});

test("production Design Studio rejects test and local app origins", async ({
  page,
}) => {
  await installBridge(page);
  const pluginFrame = await addPluginModal(page);

  await expectIgnored(page, () =>
    sendNavigation(
      pluginFrame,
      "https://pod-frontend-21ef7100c347.herokuapp.com/panel/orders?cart",
    ),
  );
  await expectIgnored(page, () =>
    sendNavigation(pluginFrame, "https://localhost:3002/panel/billing/plans"),
  );
});

test("unknown Design Studio origin rejects valid navigation", async ({
  page,
}) => {
  await installBridge(page, "https://studio.example");
  const pluginFrame = await addPluginModal(page);

  await expectIgnored(page, () =>
    sendNavigation(
      pluginFrame,
      "https://app.podconverge.com/panel/orders?cart",
    ),
  );
});

test("navigation bridge rejects invalid destinations", async ({ page }) => {
  await installBridge(page);
  const pluginFrame = await addPluginModal(page);
  const invalidDestinations = [
    "https://evil.example/panel/orders?cart",
    "/panel/orders?cart",
    "not a URL",
    "https://app.podconverge.com/panel/admin",
    "https://app.podconverge.com/panel/orders",
    "https://app.podconverge.com/panel/orders/history?cart",
    "https://app.podconverge.com/panel/design-hub/publish-to-stores/product-123/mockups",
    "https://app.podconverge.com/panel/design-hub/publish-to-stores/product-123/mockups/extra?selectStore=true",
    "https://app.podconverge.com/panel/billing/plans/extra",
  ];

  for (const destination of invalidDestinations) {
    await expectIgnored(page, () => sendNavigation(pluginFrame, destination));
  }
});

for (const [designOrigin, destination] of [
  [
    "http://localhost:3450",
    "https://pod-frontend-21ef7100c347.herokuapp.com/panel/orders?cart",
  ],
  ["https://localhost:3449", "https://localhost:3002/panel/billing/plans"],
]) {
  test(`local Design Studio ${designOrigin} accepts only the exact local plugin origin`, async ({
    page,
  }) => {
    await installBridge(page, designOrigin);
    const pluginFrame = await addPluginModal(page, localPluginOrigin);

    await sendNavigation(pluginFrame, destination);
    await expect(page).toHaveURL(destination);
  });
}

test("active plugin runtime keeps top-level navigation sandboxed", async ({
  page,
}) => {
  await installBridge(page);
  await addPluginModal(page);

  const sandbox = await page
    .locator("plugin-modal")
    .locator("iframe")
    .evaluate((iframe) => [...iframe.sandbox]);

  expect(sandbox).not.toContain("allow-top-navigation");
  expect(sandbox).not.toContain("allow-top-navigation-by-user-activation");
});
