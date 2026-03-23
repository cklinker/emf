import { test, expect } from '@playwright/test'

test.describe('MFA Setup', () => {
  test('should display security tab on user detail page', async ({ page }) => {
    await page.goto('/admin/users')
    await page.waitForSelector('[data-testid="users-page"]', { timeout: 10000 })

    // Click on first user row
    const firstRow = page.locator('table tbody tr').first()
    await firstRow.click()

    // Verify Security tab exists
    await expect(page.locator('text=Security')).toBeVisible()
  })
})
