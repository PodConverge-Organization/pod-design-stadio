import { expect, test } from "@playwright/test";
import { buildSync } from "esbuild";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const compiledRegisterPath = resolve(
  process.cwd(),
  "resources/public/js/cljs-runtime/app.plugins.register.js",
);
const compiledRegisterMapPath = `${compiledRegisterPath}.map`;
const registerSourcePath = resolve(
  process.cwd(),
  "src/app/plugins/register.cljs",
);
const runtimeModalPath = resolve(
  process.cwd(),
  "../plugins/libs/plugins-runtime/src/lib/modal/plugin-modal.ts",
);

const navigationMessageType = "podconverge:navigate";
const productionDesignStudioOrigin = "https://design.podconverge.com";
const productionPluginOrigin = "https://plugin.podconverge.com";
const developerPluginOrigin = "https://plugin-develop.podconverge.com";
const evilPluginOrigin = "https://evil.example";
const localPluginOrigin = "http://localhost:4403";
const localDesignStudioOrigin = "http://localhost:3450";
const localSecureDesignStudioOrigin = "https://localhost:3449";
const localDestinationOrigin = "https://develop.podconverge.com";
const localSecureDestinationOrigin = "https://localhost:3002";
const bridgePath = "/bridge.html";
const pluginPath = "/plugin.html";

const destinations = {
  order: "https://app.podconverge.com/panel/orders?cart=cart-123",
  publish:
    "https://app.podconverge.com/panel/design-hub/publish-to-stores/product-123/mockups?selectStore=true",
  billing: "https://app.podconverge.com/panel/billing/plans",
};

function readCurrentRegisterSource() {
  return readFileSync(registerSourcePath, "utf8").replace(/\r\n/g, "\n");
}

function readCompiledRegisterSource() {
  if (!existsSync(compiledRegisterMapPath)) {
    throw new Error(
      `Current compiled navigation bridge cannot be proven: missing ${compiledRegisterMapPath}`,
    );
  }

  const sourceMap = JSON.parse(readFileSync(compiledRegisterMapPath, "utf8"));
  const sourceIndex = sourceMap.sources?.findIndex(
    (source) => source === "app/plugins/register.cljs",
  );
  const compiledSource =
    sourceIndex === undefined || sourceIndex < 0
      ? null
      : sourceMap.sourcesContent?.[sourceIndex];

  if (typeof compiledSource !== "string") {
    throw new Error(
      `Current compiled navigation bridge cannot be proven: ${compiledRegisterMapPath} lacks app/plugins/register.cljs sourcesContent`,
    );
  }

  return compiledSource.replace(/\r\n/g, "\n");
}

function assertCompiledBridgeIsCurrent() {
  if (readCompiledRegisterSource() !== readCurrentRegisterSource()) {
    throw new Error(
      "Current compiled navigation bridge cannot be proven: app.plugins.register.js.map does not match frontend/src/app/plugins/register.cljs",
    );
  }
}

function buildRuntimeModalSource() {
  return buildSync({
    absWorkingDir: resolve(process.cwd(), "../plugins/libs/plugins-runtime"),
    bundle: true,
    entryPoints: [runtimeModalPath],
    format: "iife",
    target: "es2022",
    write: false,
    plugins: [
      {
        name: "inline-css-query",
        setup(build) {
          build.onResolve({ filter: /\.css\?inline$/ }, (args) => ({
            path: resolve(args.resolveDir, args.path.replace(/\?inline$/, "")),
            namespace: "inline-css",
          }));
          build.onLoad({ filter: /.*/, namespace: "inline-css" }, (args) => ({
            contents: `export default ${JSON.stringify(
              readFileSync(args.path, "utf8"),
            )};`,
            loader: "js",
          }));
        },
      },
    ],
  }).outputFiles[0].text;
}

async function routeStaticFrontendModule(page, origin) {
  await page.route(`${origin}/js/cljs-runtime/**`, async (route) => {
    const url = new URL(route.request().url());
    const filename = url.pathname.replace("/js/cljs-runtime/", "");
    const localPath = resolve(
      process.cwd(),
      "resources/public/js/cljs-runtime",
      filename,
    );

    if (!existsSync(localPath)) {
      await route.fulfill({ status: 404, body: "Not found" });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: "application/javascript",
      body: readFileSync(localPath, "utf8"),
    });
  });
}

async function routeHtml(page, url, body = "ok") {
  await page.route(url, (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/html",
      body: `<!doctype html><html><body>${body}</body></html>`,
    }),
  );
}

async function setupBridgePage(page, origin = productionDesignStudioOrigin) {
  assertCompiledBridgeIsCurrent();

  await routeHtml(page, `${origin}${bridgePath}`);
  await routeHtml(page, "https://app.podconverge.com/**", "app");
  await routeHtml(page, `${localDestinationOrigin}/**`, "local app");
  await routeHtml(page, `${localSecureDestinationOrigin}/**`, "local app");
  await routeHtml(page, `${productionPluginOrigin}${pluginPath}`, "plugin");
  await routeHtml(page, `${developerPluginOrigin}${pluginPath}`, "plugin");
  await routeHtml(page, `${evilPluginOrigin}${pluginPath}`, "evil plugin");
  await routeHtml(page, `${localPluginOrigin}${pluginPath}`, "plugin");
  await routeHtml(page, `${productionPluginOrigin}/orphan.html`, "orphan");
  await routeStaticFrontendModule(page, origin);

  await page.goto(`${origin}${bridgePath}`);
  await page.addScriptTag({ content: buildRuntimeModalSource() });
  await page.addScriptTag({
    url: `${origin}/js/cljs-runtime/app.plugins.register.js`,
    type: "module",
  });
  await page.waitForFunction(
    () => window.app?.plugins?.register?.install_navigation_bridge_BANG_,
  );
  await page.evaluate(() => {
    window.app.plugins.register.install_navigation_bridge_BANG_();
  });
}

async function createPluginModal(page, origin = productionPluginOrigin) {
  await page.evaluate(
    ({ pluginOrigin, path }) => {
      const modal = document.createElement("plugin-modal");
      modal.setTheme("light");
      modal.setAttribute("title", "PodConverge");
      modal.setAttribute("iframe-src", `${pluginOrigin}${path}`);
      modal.style.setProperty("--modal-block-start", "40px");
      modal.style.setProperty("--modal-inline-start", "40px");
      document.body.appendChild(modal);
    },
    { pluginOrigin: origin, path: pluginPath },
  );

  return page.locator("plugin-modal").last();
}

async function frameForUrl(page, url) {
  await expect
    .poll(() => page.frames().some((frame) => frame.url() === url))
    .toBe(true);

  return page.frames().find((frame) => frame.url() === url);
}

async function postFromModalFrame(page, { origin, url, type }) {
  await createPluginModal(page, origin);
  const frame = await frameForUrl(page, `${origin}${pluginPath}`);

  await frame.evaluate(
    ({ messageType, destination }) => {
      window.parent.postMessage({ type: messageType, url: destination }, "*");
    },
    { messageType: type ?? navigationMessageType, destination: url },
  );
}

async function postFromOrphanFrame(page, url) {
  await page.evaluate(
    ({ origin, path }) => {
      const iframe = document.createElement("iframe");
      iframe.src = `${origin}${path}`;
      document.body.appendChild(iframe);
    },
    { origin: productionPluginOrigin, path: "/orphan.html" },
  );
  const frame = await frameForUrl(
    page,
    `${productionPluginOrigin}/orphan.html`,
  );

  await frame.evaluate(
    ({ messageType, destination }) => {
      window.parent.postMessage({ type: messageType, url: destination }, "*");
    },
    { messageType: navigationMessageType, destination: url },
  );
}

async function assertNoPopupWhile(page, action) {
  const popupPromise = page
    .waitForEvent("popup", { timeout: 500 })
    .catch(() => null);
  await action();
  expect(await popupPromise).toBeNull();
}

async function expectRejectedNavigation(page, action, origin) {
  await action();
  await page.waitForTimeout(100);
  await expect(page).toHaveURL(`${origin}${bridgePath}`);
}

for (const [name, url] of Object.entries(destinations)) {
  test(`production plugin can navigate to approved ${name} destination`, async ({
    page,
  }) => {
    await setupBridgePage(page);

    await assertNoPopupWhile(page, () =>
      postFromModalFrame(page, { origin: productionPluginOrigin, url }),
    );

    await expect(page).toHaveURL(url);
  });
}

test("approved developer plugin origin uses production destination rules", async ({
  page,
}) => {
  await setupBridgePage(page);

  await postFromModalFrame(page, {
    origin: developerPluginOrigin,
    url: destinations.billing,
  });

  await expect(page).toHaveURL(destinations.billing);
});

test("approved developer plugin origin rejects unapproved production destination", async ({
  page,
}) => {
  await setupBridgePage(page);

  await assertNoPopupWhile(page, () =>
    expectRejectedNavigation(
      page,
      () =>
        postFromModalFrame(page, {
          origin: developerPluginOrigin,
          url: "https://app.podconverge.com/panel/admin",
        }),
      productionDesignStudioOrigin,
    ),
  );
});

test("production bridge rejects untrusted senders and wrong messages", async ({
  page,
}) => {
  await setupBridgePage(page);

  await expectRejectedNavigation(
    page,
    () =>
      postFromModalFrame(page, {
        origin: evilPluginOrigin,
        url: destinations.billing,
      }),
    productionDesignStudioOrigin,
  );
  await expectRejectedNavigation(
    page,
    () => postFromOrphanFrame(page, destinations.billing),
    productionDesignStudioOrigin,
  );
  await expectRejectedNavigation(
    page,
    () =>
      postFromModalFrame(page, {
        origin: productionPluginOrigin,
        type: "podconverge:wrong",
        url: destinations.billing,
      }),
    productionDesignStudioOrigin,
  );
});

test("production bridge rejects local plugin and non-production destinations", async ({
  page,
}) => {
  await setupBridgePage(page);

  await expectRejectedNavigation(
    page,
    () =>
      postFromModalFrame(page, {
        origin: localPluginOrigin,
        url: destinations.billing,
      }),
    productionDesignStudioOrigin,
  );
  await expectRejectedNavigation(
    page,
    () =>
      postFromModalFrame(page, {
        origin: productionPluginOrigin,
        url: `${localDestinationOrigin}/panel/billing/plans`,
      }),
    productionDesignStudioOrigin,
  );
});

test("unknown Design Studio origin rejects navigation", async ({ page }) => {
  const unknownOrigin = "https://unknown-design.podconverge.com";
  await setupBridgePage(page, unknownOrigin);

  await expectRejectedNavigation(
    page,
    () =>
      postFromModalFrame(page, {
        origin: productionPluginOrigin,
        url: destinations.billing,
      }),
    unknownOrigin,
  );
});

test("production bridge rejects invalid destinations", async ({ page }) => {
  await setupBridgePage(page);

  for (const url of [
    "https://evil.example/panel/billing/plans",
    "/panel/billing/plans",
    "notaurl",
    "https://app.podconverge.com/panel/admin",
    "https://app.podconverge.com/panel/orders",
    "https://app.podconverge.com/panel/design-hub/publish-to-stores/product-123/mockups",
    "https://app.podconverge.com/panel/billing/plans/extra",
    "https://app.podconverge.com/panel/billing/plans.json",
    "https://user:pass@app.podconverge.com/panel/billing/plans",
  ]) {
    await expectRejectedNavigation(
      page,
      () =>
        postFromModalFrame(page, {
          origin: productionPluginOrigin,
          url,
        }),
      productionDesignStudioOrigin,
    );
  }
});

test("local Design Studio allows reviewed local contract", async ({ page }) => {
  const localDestination = `${localDestinationOrigin}/panel/billing/plans`;
  await setupBridgePage(page, localDesignStudioOrigin);

  await postFromModalFrame(page, {
    origin: localPluginOrigin,
    url: localDestination,
  });

  await expect(page).toHaveURL(localDestination);
});

test("secure local Design Studio allows reviewed localhost contract", async ({
  page,
}) => {
  const localDestination = `${localSecureDestinationOrigin}/panel/billing/plans`;
  await setupBridgePage(page, localSecureDesignStudioOrigin);

  await postFromModalFrame(page, {
    origin: localPluginOrigin,
    url: localDestination,
  });

  await expect(page).toHaveURL(localDestination);
});

test("active runtime modal iframe remains sandboxed without top navigation", async ({
  page,
}) => {
  await setupBridgePage(page);
  await createPluginModal(page);

  const sandbox = await page.locator("plugin-modal").evaluate((modal) => {
    const iframe = modal.shadowRoot.querySelector("iframe");
    return Array.from(iframe.sandbox);
  });

  expect(sandbox).toEqual([
    "allow-scripts",
    "allow-forms",
    "allow-modals",
    "allow-popups",
    "allow-popups-to-escape-sandbox",
    "allow-storage-access-by-user-activation",
    "allow-same-origin",
  ]);
  expect(sandbox).not.toContain("allow-top-navigation");
  expect(sandbox).not.toContain("allow-top-navigation-by-user-activation");
});
