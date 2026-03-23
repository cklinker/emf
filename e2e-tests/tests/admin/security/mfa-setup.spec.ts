import { test, expect } from '@playwright/test'

test.describe('MFA Setup', () => {
  test('should display MFA status on user detail security tab', async ({ page }) => {
    // Navigate to a user's detail page
    await page.goto('/admin/users')
    await page.waitForSelector('[data-testid="users-table"]', { timeout: 10000 })

    // Click on first user
    const firstRow = page.locator('table tbody tr').first()
    await firstRow.click()

    // Switch to Security tab
    await page.click('text=Security')

    // Verify MFA section is visible
    await expect(page.locator('text=Multi-Factor Authentication')).toBeVisible()
    await expect(page.locator('text=MFA Status:')).toBeVisible()
  })
})
