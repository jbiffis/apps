import { expect } from '@playwright/test'

export const USER = process.env.E2E_USER || 'carley'
export const PASS = process.env.E2E_PASS || 'test123'

// Log in and land on Home. baseURL ends in /tracker/, so '/' hits the SPA root
// which RequireAuth bounces to /login until authenticated.
export async function login(page, user = USER, pass = PASS) {
  await page.goto('/')
  await expect(page).toHaveURL(/\/login/)
  await page.locator('#username').fill(user)
  await page.locator('#password').fill(pass)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: 'All trackers' })).toBeVisible()
}
