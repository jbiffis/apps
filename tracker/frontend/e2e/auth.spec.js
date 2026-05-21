import { test, expect } from '@playwright/test'
import { login, USER, PASS } from './helpers.js'

test('redirects to login when unauthenticated', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
})

test('rejects a bad password', async ({ page }) => {
  await page.goto('login') // relative — keeps the /tracker/ base
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  await page.locator('#username').fill(USER)
  await page.locator('#password').fill('definitely-wrong')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText(/incorrect username or password/i)).toBeVisible()
})

test('logs in with valid credentials', async ({ page }) => {
  await login(page, USER, PASS)
  await expect(page.getByRole('heading', { name: 'All trackers' })).toBeVisible()
})
