import { test, expect } from "@playwright/test";
import DashboardPage from "../pages/DashboardPage";

test.beforeEach(async ({ page }) => {
  await DashboardPage.init(page);
  await DashboardPage.mockConfigFlags(page, [
    "disable-onboarding",
    "enable-mcp",
    "enable-access-tokens",
  ]);
});

test("Dashboard profile menu opens the integrations account surface", async ({
  page,
}) => {
  const dashboardPage = new DashboardPage(page);
  await dashboardPage.setupAccessTokensEmpty();
  await dashboardPage.goToDashboard();

  await dashboardPage.openProfileMenu();
  await expect(
    dashboardPage.sidebarMenu.getByText("Your account", { exact: true }),
  ).toBeVisible();
  await expect(
    dashboardPage.sidebarMenu.getByText("Logout", { exact: true }),
  ).toBeVisible();
  await expect(
    dashboardPage.sidebarMenu.getByText("Help & Learning", { exact: true }),
  ).toBeHidden();
  await expect(
    dashboardPage.sidebarMenu.getByText("Community & Contributions", {
      exact: true,
    }),
  ).toBeHidden();
  await expect(
    dashboardPage.sidebarMenu.getByText("About Penpot", { exact: true }),
  ).toBeHidden();

  await dashboardPage.clickProfileMenuItem("Your account");

  await expect(page).toHaveURL(/\/#\/settings\/integrations$/);
  await expect(
    page.getByRole("heading", { name: "Integrations" }),
  ).toBeVisible();
  const settingsNavigation = page
    .getByTestId("settings-integrations")
    .locator("xpath=..");
  await expect(settingsNavigation.getByRole("listitem")).toHaveCount(1);
  await expect(
    settingsNavigation.getByText("Integrations", { exact: true }),
  ).toBeVisible();
  await expect(
    settingsNavigation.getByText("Profile", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Password", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Notifications", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Settings", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Subscription", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Release Notes", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Contact us", { exact: true }),
  ).toBeHidden();

  await expect(page.getByRole("heading", { name: "MCP Server" })).toBeVisible();
  await expect(
    page.getByRole("link", { name: /How to configure MCP clients/ }),
  ).toHaveAttribute("href", "https://podconverge.com/help/mcp");
});

test("Legacy settings profile route converges to integrations", async ({
  page,
}) => {
  const dashboardPage = new DashboardPage(page);
  await dashboardPage.setupAccessTokensEmpty();

  await page.goto("#/settings/profile");

  await expect(
    page.getByRole("heading", { name: "Integrations" }),
  ).toBeVisible();
  const settingsNavigation = page
    .getByTestId("settings-integrations")
    .locator("xpath=..");
  await expect(settingsNavigation.getByRole("listitem")).toHaveCount(1);
  await expect(
    settingsNavigation.getByText("Integrations", { exact: true }),
  ).toBeVisible();
  await expect(
    settingsNavigation.getByText("Profile", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Password", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Notifications", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Settings", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Subscription", { exact: true }),
  ).toBeHidden();
  await expect(
    settingsNavigation.getByText("Contact us", { exact: true }),
  ).toBeHidden();
});
