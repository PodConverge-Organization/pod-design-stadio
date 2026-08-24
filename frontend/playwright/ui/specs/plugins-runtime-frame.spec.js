import { fileURLToPath } from "node:url";
import { expect, test } from "@playwright/test";

const runtimePath = fileURLToPath(
  import.meta.resolve("@penpot/plugins-runtime/index.js"),
);

test("patched active plugin runtime preserves iframe state while toggling", async ({
  page,
}) => {
  await page.setContent("<!doctype html><html><body></body></html>");
  await page.addScriptTag({ path: runtimePath, type: "module" });
  await page.waitForFunction(() => customElements.get("plugin-modal"));

  await page.evaluate(() => {
    const modal = document.createElement("plugin-modal");
    modal.setAttribute("title", "PodConverge");
    modal.setAttribute("iframe-src", "about:blank");
    modal.addEventListener("close", () => {
      window.pluginFrameCloseEvents += 1;
    });
    window.pluginFrameCloseEvents = 0;
    document.body.appendChild(modal);

    modal.wrapper.style.width = "437px";
    modal.wrapper.style.height = "517px";
    modal.wrapper.style.minHeight = "205px";
    modal.wrapper.style.resize = "both";
    window.pluginFrameIframe = modal.shadowRoot.querySelector("iframe");
    window.pluginFrameContentWindow = window.pluginFrameIframe.contentWindow;
  });

  const modal = page.locator("plugin-modal");
  const iframe = modal.locator("iframe");
  const minimize = page.getByRole("button", { name: "Minimize plugin" });

  await expect(minimize).toHaveAttribute("aria-expanded", "true");
  await expect(modal.locator(".close")).toHaveCount(0);
  await expect(page.getByRole("button", { name: /close/i })).toHaveCount(0);

  await minimize.focus();
  await page.keyboard.press("Enter");

  const maximize = page.getByRole("button", { name: "Maximize plugin" });
  await expect(maximize).toHaveAttribute("aria-expanded", "false");
  await expect(modal.locator(".wrapper")).toHaveCSS("height", "40px");
  await expect(iframe).toHaveCount(1);

  await expect
    .poll(() =>
      page.evaluate(
        () =>
          window.pluginFrameIframe ===
            document
              .querySelector("plugin-modal")
              .shadowRoot.querySelector("iframe") &&
          window.pluginFrameContentWindow ===
            window.pluginFrameIframe.contentWindow,
      ),
    )
    .toBe(true);

  await maximize.focus();
  await page.keyboard.press("Enter");

  await expect(
    page.getByRole("button", { name: "Minimize plugin" }),
  ).toHaveAttribute("aria-expanded", "true");
  await expect(modal.locator(".wrapper")).toHaveCSS("width", "437px");
  await expect(modal.locator(".wrapper")).toHaveCSS("height", "517px");
  await expect(iframe).toHaveCount(1);

  await page.getByRole("button", { name: "Minimize plugin" }).click();
  await page.getByRole("button", { name: "Maximize plugin" }).click();

  const state = await page.evaluate(() => {
    const currentIframe = document
      .querySelector("plugin-modal")
      .shadowRoot.querySelector("iframe");
    return {
      closeEvents: window.pluginFrameCloseEvents,
      sameIframe: currentIframe === window.pluginFrameIframe,
      sameContentWindow:
        currentIframe.contentWindow === window.pluginFrameContentWindow,
      sandbox: [...currentIframe.sandbox].sort(),
    };
  });

  expect(state).toEqual({
    closeEvents: 0,
    sameIframe: true,
    sameContentWindow: true,
    sandbox: [
      "allow-forms",
      "allow-modals",
      "allow-popups",
      "allow-popups-to-escape-sandbox",
      "allow-same-origin",
      "allow-scripts",
      "allow-storage-access-by-user-activation",
    ],
  });

  await iframe.focus();
  await page.evaluate(() => {
    document
      .querySelector("plugin-modal")
      .shadowRoot.querySelector('button[aria-label="Minimize plugin"]')
      .click();
  });
  await expect(
    page.getByRole("button", { name: "Maximize plugin" }),
  ).toBeFocused();
});
