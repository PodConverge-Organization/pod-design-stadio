import { test, expect } from "@playwright/test";
import { WasmWorkspacePage } from "../pages/WasmWorkspacePage";

test.beforeEach(async ({ page }) => {
  await WasmWorkspacePage.init(page);
});

test("PodConverge hides Prototype in the workspace options sidebar", async ({
  page,
}) => {
  const workspace = new WasmWorkspacePage(page);
  await workspace.setupEmptyFile();

  await workspace.goToWorkspace();

  await expect(workspace.rightSidebar).toBeVisible();
  await expect(
    workspace.rightSidebar.getByRole("tab", { name: "Design" }),
  ).toBeVisible();
  await expect(
    workspace.rightSidebar.getByRole("tab", { name: "Prototype" }),
  ).toHaveCount(0);
  await expect(workspace.rightSidebar.getByText("Prototype")).toHaveCount(0);
  await expect(
    workspace.rightSidebar.getByText("Canvas background").first(),
  ).toBeVisible();
});
