import { test, expect } from '@playwright/test'
import { login } from './helpers.js'

test('FAB → picker → Journal → save shows an undo toast', async ({ page }) => {
  await login(page)

  await page.getByRole('button', { name: 'Log something' }).click()
  // Picker dialog open; Journal is a top-level leaf.
  const picker = page.getByRole('dialog', { name: 'Log something' })
  await expect(picker).toBeVisible()
  await picker.getByText('Journal', { exact: true }).click()

  // Entry screen for Journal. "Note" is a required text property — fill it,
  // otherwise Save is blocked by validation (no POST, no toast).
  await expect(page.getByRole('heading', { name: /Journal/ })).toBeVisible()
  await page.locator('textarea').first().fill('e2e smoke entry')
  await page.getByRole('button', { name: 'Save', exact: true }).click()

  // Save navigates back to Home (leaves the /log/ route) and shows the toast.
  await expect(page).toHaveURL(/\/tracker\/$|\/tracker$|\/$/)
  await expect(page.getByText('Logged Journal')).toBeVisible()
})
