import { test, expect } from '@playwright/test'
import { login } from './helpers.js'

// Toggling a tracker's visibility persists to the prefs API across a reload.
// Restores it to "Shown" at the end so the run stays idempotent.
test('Me tab: hide a tracker and it persists', async ({ page }) => {
  await login(page)
  // exact — otherwise "Me" substring-matches tracker tiles like "Medication"/"Meditation".
  await page.getByRole('button', { name: 'Me', exact: true }).click()
  await expect(page).toHaveURL(/\/me/)

  const row = page.getByRole('listitem').filter({ hasText: 'Coffee' }).first()
  const toggle = row.getByRole('button')

  // Ensure starting state is "Shown".
  if ((await toggle.innerText()).trim() === 'Hidden') await toggle.click()
  await expect(toggle).toHaveText('Shown')

  await toggle.click()
  await expect(toggle).toHaveText('Hidden')

  // Persisted across reload.
  await page.reload()
  const rowAfter = page.getByRole('listitem').filter({ hasText: 'Coffee' }).first()
  await expect(rowAfter.getByRole('button')).toHaveText('Hidden')

  // Restore.
  await rowAfter.getByRole('button').click()
  await expect(rowAfter.getByRole('button')).toHaveText('Shown')
})
