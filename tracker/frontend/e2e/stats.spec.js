import { test, expect } from '@playwright/test'
import { login } from './helpers.js'

test('Stats tab renders summary, heatmap and most-logged', async ({ page }) => {
  await login(page)
  await page.getByRole('button', { name: 'Stats' }).click()
  await expect(page).toHaveURL(/\/stats/)

  // Summary cards
  await expect(page.getByText('Entries')).toBeVisible()
  await expect(page.getByText('Streak', { exact: true })).toBeVisible()
  // Heatmap + most-logged sections
  await expect(page.getByRole('heading', { name: 'Activity' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Most logged' })).toBeVisible()
})
