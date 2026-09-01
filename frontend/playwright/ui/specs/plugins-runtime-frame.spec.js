import { expect, test } from "@playwright/test";
import { buildSync } from "esbuild";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const runtimeModalPath = resolve(
  process.cwd(),
  "../plugins/libs/plugins-runtime/src/lib/modal/plugin-modal.ts",
);

async function loadPluginRuntime(page) {
  const runtimeSource = buildSync({
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

  await page.setContent("<!doctype html><html><body></body></html>");
  await page.addScriptTag({ content: runtimeSource });
  await page.waitForFunction(() => customElements.get("plugin-modal"));
}

async function createModal(page) {
  return page.evaluateHandle(() => {
    const modal = document.createElement("plugin-modal");
    modal.setTheme("light");
    modal.setAttribute("title", "PodConverge");
    modal.setAttribute("iframe-src", "about:blank");
    modal.style.setProperty("--modal-block-start", "40px");
    modal.style.setProperty("--modal-inline-start", "40px");
    document.body.appendChild(modal);

    const wrapper = modal.shadowRoot.querySelector(".wrapper");
    wrapper.style.width = "335px";
    wrapper.style.inlineSize = "335px";
    wrapper.style.height = "590px";
    wrapper.style.blockSize = "590px";
    wrapper.style.minHeight = "210px";
    wrapper.style.minBlockSize = "210px";
    wrapper.style.resize = "both";
    wrapper.style.overflow = "auto";

    return modal;
  });
}

async function modalState(modal) {
  return modal.evaluate((element) => {
    const wrapper = element.shadowRoot.querySelector(".wrapper");
    const button = element.shadowRoot.querySelector("button");
    const iframe = element.shadowRoot.querySelector("iframe");
    const sandbox = Array.from(iframe.sandbox);
    const rect = wrapper.getBoundingClientRect();
    const computedStyle = window.getComputedStyle(wrapper);
    window.__pluginFrameIframe ??= iframe;
    window.__pluginFrameContentWindow ??= iframe.contentWindow;

    return {
      loaded: customElements.get("plugin-modal") !== undefined,
      closeCount: element.shadowRoot.querySelectorAll(".close").length,
      closeButtonCount: Array.from(
        element.shadowRoot.querySelectorAll("button"),
      ).filter((node) => /close/i.test(node.textContent || node.title || ""))
        .length,
      ariaExpanded: button.getAttribute("aria-expanded"),
      label: button.getAttribute("aria-label"),
      title: button.getAttribute("title"),
      height: wrapper.style.height,
      blockSize: wrapper.style.blockSize,
      width: wrapper.style.width,
      inlineSize: wrapper.style.inlineSize,
      minHeight: wrapper.style.minHeight,
      minBlockSize: wrapper.style.minBlockSize,
      resize: wrapper.style.resize,
      overflow: wrapper.style.overflow,
      renderedWidth: `${rect.width}px`,
      renderedHeight: `${rect.height}px`,
      computedWidth: computedStyle.width,
      computedHeight: computedStyle.height,
      sameIframe: window.__pluginFrameIframe === iframe,
      sameContentWindow:
        window.__pluginFrameContentWindow === iframe.contentWindow,
      sandbox,
      focusedToggle: element.shadowRoot.activeElement === button,
    };
  });
}

async function clickToggle(modal) {
  await modal.evaluate((element) => {
    element.shadowRoot.querySelector("button").click();
  });
}

test("plugin frame minimizes without closing or replacing iframe state", async ({
  page,
}) => {
  await loadPluginRuntime(page);
  const modal = await createModal(page);

  let closeEvents = 0;
  await modal.evaluate((element) => {
    element.addEventListener("close", () => {
      window.__pluginFrameCloseEvents =
        (window.__pluginFrameCloseEvents || 0) + 1;
    });
  });

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      loaded: true,
      closeCount: 0,
      closeButtonCount: 0,
      ariaExpanded: "true",
      label: "Minimize plugin",
      title: "Minimize plugin",
    });

  await clickToggle(modal);

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "false",
      label: "Maximize plugin",
      title: "Maximize plugin",
      height: "40px",
      blockSize: "40px",
      sameIframe: true,
      sameContentWindow: true,
    });

  await clickToggle(modal);

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "true",
      label: "Minimize plugin",
      title: "Minimize plugin",
      width: "335px",
      inlineSize: "335px",
      height: "590px",
      blockSize: "590px",
      minHeight: "210px",
      minBlockSize: "210px",
      resize: "both",
      overflow: "auto",
      sameIframe: true,
      sameContentWindow: true,
    });

  await clickToggle(modal);
  await clickToggle(modal);

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "true",
      sameIframe: true,
      sameContentWindow: true,
    });

  closeEvents = await page.evaluate(() => window.__pluginFrameCloseEvents || 0);
  expect(closeEvents).toBe(0);
});

test("plugin frame preserves sandbox and focus behavior", async ({ page }) => {
  await loadPluginRuntime(page);
  const modal = await createModal(page);

  const sandbox = (await modalState(modal)).sandbox;
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

  await modal.evaluate((element) => {
    element.shadowRoot.querySelector("iframe").focus();
    element.shadowRoot.querySelector("button").click();
  });

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "false",
      focusedToggle: true,
    });
});

test("plugin frame stays minimized when runtime resize happens", async ({
  page,
}) => {
  await loadPluginRuntime(page);
  const modal = await createModal(page);

  await clickToggle(modal);
  await modal.evaluate((element) => element.resize(420, 360));

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "false",
      width: "420px",
      inlineSize: "335px",
      height: "40px",
      blockSize: "40px",
    });

  await clickToggle(modal);

  await expect
    .poll(() => modalState(modal))
    .toMatchObject({
      ariaExpanded: "true",
      width: "420px",
      inlineSize: "420px",
      height: "360px",
      blockSize: "360px",
      computedWidth: "420px",
      computedHeight: "360px",
      renderedWidth: "444px",
      renderedHeight: "384px",
      sameIframe: true,
      sameContentWindow: true,
    });
});
