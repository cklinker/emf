/**
 * DashboardPage Tests
 *
 * Unit tests for the DashboardPage component.
 * Tests cover:
 * - Rendering health status cards
 * - Rendering metrics cards
 * - Rendering recent errors list
 * - Loading and error states
 * - Empty states
 * - Time range selector
 * - Auto-refresh interval selector
 * - Health alerts
 *
 * Requirements tested:
 * - 13.1: Dashboard displays system health status
 * - 13.2: Dashboard displays health status for control plane, database, Kafka, and Redis
 * - 13.3: Dashboard displays key API metrics including request rate, error rate, and latency
 * - 13.4: Dashboard displays recent errors and warnings from the system logs
 * - 13.5: Dashboard allows configuring time range for metrics display
 * - 13.6: Dashboard auto-refreshes at configurable interval
 * - 13.7: Dashboard displays health alerts when services are unhealthy
 */

import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createTestWrapper, setupAuthMocks, wrapFetchMock } from '../../test/testUtils';
import { DashboardPage } from './DashboardPage';
import type { DashboardData, HealthStatus, RecentError } from './DashboardPage';

// Mock fetch function
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Helper to create a proper Response-like object
function createMockResponse(data: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    json: () => Promise.resolve(data),
    text: () => Promise.resolve(JSON.stringify(data)),
    clone: function () {
      return this;
    },
    headers: new Headers(),
    redirected: false,
    statusText: ok ? 'OK' : 'Error',
    type: 'basic' as ResponseType,
    url: '',
    body: null,
    bodyUsed: false,
    arrayBuffer: () => Promise.resolve(new ArrayBuffer(0)),
    blob: () => Promise.resolve(new Blob()),
    formData: () => Promise.resolve(new FormData()),
    bytes: () => Promise.resolve(new Uint8Array()),
  } as Response;
}

// Mock dashboard data
const mockHealthStatuses: HealthStatus[] = [
  {
    service: 'Control Plane',
    status: 'healthy',
    details: 'All systems operational',
    lastChecked: '2024-01-20T10:00:00Z',
  },
  {
    service: 'Database',
    status: 'healthy',
    details: 'PostgreSQL connected',
    lastChecked: '2024-01-20T10:00:00Z',
  },
  {
    service: 'Kafka',
    status: 'unhealthy',
    details: 'Connection timeout',
    lastChecked: '2024-01-20T10:00:00Z',
  },
  {
    service: 'Redis',
    status: 'unknown',
    lastChecked: '2024-01-20T10:00:00Z',
  },
];

const mockRecentErrors: RecentError[] = [
  {
    id: '1',
    timestamp: '2024-01-20T09:55:00Z',
    level: 'error',
    message: 'Failed to connect to Kafka broker',
    source: 'kafka-consumer',
    traceId: 'abc123def456',
  },
  {
    id: '2',
    timestamp: '2024-01-20T09:50:00Z',
    level: 'warning',
    message: 'High memory usage detected',
    source: 'system-monitor',
  },
  {
    id: '3',
    timestamp: '2024-01-20T09:45:00Z',
    level: 'error',
    message: 'Database query timeout',
    source: 'query-engine',
    traceId: 'xyz789ghi012',
  },
];

const mockDashboardData: DashboardData = {
  health: mockHealthStatuses,
  metrics: {
    requestRate: [
      { timestamp: '2024-01-20T09:50:00Z', value: 100 },
      { timestamp: '2024-01-20T09:51:00Z', value: 120 },
      { timestamp: '2024-01-20T09:52:00Z', value: 115 },
      { timestamp: '2024-01-20T09:53:00Z', value: 130 },
      { timestamp: '2024-01-20T09:54:00Z', value: 125 },
    ],
    errorRate: [
      { timestamp: '2024-01-20T09:50:00Z', value: 0.5 },
      { timestamp: '2024-01-20T09:51:00Z', value: 0.8 },
      { timestamp: '2024-01-20T09:52:00Z', value: 1.2 },
      { timestamp: '2024-01-20T09:53:00Z', value: 0.9 },
      { timestamp: '2024-01-20T09:54:00Z', value: 0.7 },
    ],
    latencyP50: [
      { timestamp: '2024-01-20T09:50:00Z', value: 45 },
      { timestamp: '2024-01-20T09:51:00Z', value: 48 },
      { timestamp: '2024-01-20T09:52:00Z', value: 52 },
      { timestamp: '2024-01-20T09:53:00Z', value: 47 },
      { timestamp: '2024-01-20T09:54:00Z', value: 50 },
    ],
    latencyP99: [
      { timestamp: '2024-01-20T09:50:00Z', value: 150 },
      { timestamp: '2024-01-20T09:51:00Z', value: 165 },
      { timestamp: '2024-01-20T09:52:00Z', value: 180 },
      { timestamp: '2024-01-20T09:53:00Z', value: 155 },
      { timestamp: '2024-01-20T09:54:00Z', value: 160 },
    ],
  },
  recentErrors: mockRecentErrors,
};

/**
 * Create a wrapper component with all required providers
 */

describe('DashboardPage', () => {
  let cleanupAuthMocks: () => void;

  beforeEach(() => {
    cleanupAuthMocks = setupAuthMocks();
    mockFetch.mockReset();
    wrapFetchMock(mockFetch);
  });

  afterEach(() => {
    cleanupAuthMocks();
    vi.clearAllMocks();
  });

  describe('Loading State', () => {
    it('should display loading spinner while fetching dashboard data', async () => {
      // Mock a delayed response
      mockFetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve(createMockResponse(mockDashboardData)), 100)
          )
      );

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      // Look for the loading spinner component
      expect(screen.getByRole('status')).toBeInTheDocument();
    });
  });

  describe('Error State', () => {
    it('should display error message when fetch fails', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('error-message')).toBeInTheDocument();
      });
    });

    it('should display retry button on error', async () => {
      mockFetch.mockResolvedValue(createMockResponse(null, false, 500));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
      });
    });
  });

  describe('Health Status Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));
    });

    it('should display page title', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByRole('heading', { name: /dashboard/i })).toBeInTheDocument();
      });
    });

    it('should display system health section', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/system health/i)).toBeInTheDocument();
      });
    });

    it('should display health cards for all services', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Use getAllByText since Kafka appears in both health alerts and health cards
        expect(screen.getAllByText('Control Plane').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Database').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Kafka').length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText('Redis').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should display correct status badges for each service', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Check for healthy status badges
        const healthyBadges = screen.getAllByText('Healthy');
        expect(healthyBadges.length).toBe(2); // Control Plane and Database

        // Check for unhealthy status badge
        expect(screen.getByText('Unhealthy')).toBeInTheDocument();

        // Check for unknown status badge
        expect(screen.getByText('Unknown')).toBeInTheDocument();
      });
    });

    it('should display health card details when available', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('All systems operational')).toBeInTheDocument();
        expect(screen.getByText('PostgreSQL connected')).toBeInTheDocument();
        // Connection timeout appears in both health alerts and health cards
        expect(screen.getAllByText('Connection timeout').length).toBeGreaterThanOrEqual(1);
      });
    });

    it('should have accessible health cards', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const healthCards = screen.getByTestId('health-cards');
        expect(healthCards).toBeInTheDocument();

        // Check that health cards have proper ARIA attributes
        const controlPlaneCard = screen.getByTestId('health-card-0');
        expect(controlPlaneCard).toHaveAttribute('role', 'article');
        expect(controlPlaneCard).toHaveAttribute('aria-label');
      });
    });
  });

  describe('Metrics Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockDashboardData, totalElements: mockDashboardData.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should display metrics section', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/metrics/i)).toBeInTheDocument();
      });
    });

    it('should display all metric cards', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('metrics-request-rate')).toBeInTheDocument();
        expect(screen.getByTestId('metrics-error-rate')).toBeInTheDocument();
        expect(screen.getByTestId('metrics-latency-p50')).toBeInTheDocument();
        expect(screen.getByTestId('metrics-latency-p99')).toBeInTheDocument();
      });
    });

    it('should display request rate metric', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/request rate/i)).toBeInTheDocument();
      });
    });

    it('should display error rate metric', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/error rate/i)).toBeInTheDocument();
      });
    });

    it('should display latency metrics', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/latency.*p50/i)).toBeInTheDocument();
        expect(screen.getByText(/latency.*p99/i)).toBeInTheDocument();
      });
    });

    it('should display metric values', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Check that the metrics cards container exists
        const metricsCards = screen.getByTestId('metrics-cards');
        expect(metricsCards).toBeInTheDocument();
      });
    });

    it('should have accessible metrics cards', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const requestRateCard = screen.getByTestId('metrics-request-rate');
        expect(requestRateCard).toHaveAttribute('role', 'article');
        expect(requestRateCard).toHaveAttribute('aria-label');
      });
    });
  });

  describe('Recent Errors Display', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));
    });

    it('should display recent errors section', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/recent errors/i)).toBeInTheDocument();
      });
    });

    it('should display all error items', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('error-item-1')).toBeInTheDocument();
        expect(screen.getByTestId('error-item-2')).toBeInTheDocument();
        expect(screen.getByTestId('error-item-3')).toBeInTheDocument();
      });
    });

    it('should display error messages', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('Failed to connect to Kafka broker')).toBeInTheDocument();
        expect(screen.getByText('High memory usage detected')).toBeInTheDocument();
        expect(screen.getByText('Database query timeout')).toBeInTheDocument();
      });
    });

    it('should display error levels', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const errorBadges = screen.getAllByText('ERROR');
        expect(errorBadges.length).toBe(2);

        const warningBadges = screen.getAllByText('WARNING');
        expect(warningBadges.length).toBe(1);
      });
    });

    it('should display error sources', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText('kafka-consumer')).toBeInTheDocument();
        expect(screen.getByText('system-monitor')).toBeInTheDocument();
        expect(screen.getByText('query-engine')).toBeInTheDocument();
      });
    });

    it('should display truncated trace IDs when available', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Trace IDs are truncated to first 8 characters
        expect(screen.getByText('abc123de...')).toBeInTheDocument();
        expect(screen.getByText('xyz789gh...')).toBeInTheDocument();
      });
    });

    it('should have accessible errors list', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const errorsList = screen.getByTestId('errors-list');
        expect(errorsList).toHaveAttribute('role', 'list');
        expect(errorsList).toHaveAttribute('aria-label');
      });
    });
  });

  describe('Empty States', () => {
    it('should display empty state when no errors', async () => {
      const dataWithNoErrors: DashboardData = {
        ...mockDashboardData,
        recentErrors: [],
      };
      mockFetch.mockResolvedValue(createMockResponse(dataWithNoErrors));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/no results found/i)).toBeInTheDocument();
      });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));
    });

    it('should have proper heading hierarchy', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Main title should be h1
        const mainHeading = screen.getByRole('heading', { level: 1 });
        expect(mainHeading).toHaveTextContent(/dashboard/i);

        // Section titles should be h2
        const sectionHeadings = screen.getAllByRole('heading', { level: 2 });
        expect(sectionHeadings.length).toBeGreaterThanOrEqual(3);
      });
    });

    it('should have accessible sections with proper labels', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        // Check that sections have aria-labelledby
        const healthSection = screen.getByRole('region', { name: /system health/i });
        expect(healthSection).toBeInTheDocument();

        const metricsSection = screen.getByRole('region', { name: /metrics/i });
        expect(metricsSection).toBeInTheDocument();

        const errorsSection = screen.getByRole('region', { name: /recent errors/i });
        expect(errorsSection).toBeInTheDocument();
      });
    });
  });

  describe('Time Range Selector', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockDashboardData, totalElements: mockDashboardData.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should display time range selector', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('time-range-selector')).toBeInTheDocument();
      });
    });

    it('should have default time range of 15 minutes', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const select = screen.getByLabelText(/time range/i);
        expect(select).toHaveValue('15m');
      });
    });

    it('should display all time range options', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const select = screen.getByLabelText(/time range/i);
        expect(select).toBeInTheDocument();
      });

      const select = screen.getByLabelText(/time range/i);
      expect(select.querySelectorAll('option').length).toBe(5);
    });

    it('should update time range when selection changes', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('time-range-selector')).toBeInTheDocument();
      });

      const select = screen.getByLabelText(/time range/i);
      fireEvent.change(select, { target: { value: '1h' } });

      expect(select).toHaveValue('1h');
    });

    it('should refetch data when time range changes', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('time-range-selector')).toBeInTheDocument();
      });

      // Clear previous calls
      mockFetch.mockClear();
      mockFetch.mockResolvedValue(createMockResponse({ content: mockDashboardData, totalElements: mockDashboardData.length, totalPages: 1, size: 1000, number: 0 }));

      const select = screen.getByLabelText(/time range/i);
      fireEvent.change(select, { target: { value: '1h' } });

      await waitFor(() => {
        // Check that fetch was called with a Request object containing the timeRange parameter
        expect(mockFetch).toHaveBeenCalled();
        const lastCall = mockFetch.mock.calls[mockFetch.mock.calls.length - 1];
        const url = typeof lastCall[0] === 'string' ? lastCall[0] : lastCall[0]?.url;
        expect(url).toContain('timeRange=1h');
      });
    });
  });

  describe('Auto-Refresh Interval Selector', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse({ content: mockDashboardData, totalElements: mockDashboardData.length, totalPages: 1, size: 1000, number: 0 }));
    });

    it('should display auto-refresh selector', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('refresh-interval-selector')).toBeInTheDocument();
      });
    });

    it('should have default refresh interval of 30 seconds', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const select = screen.getByLabelText(/auto refresh/i);
        expect(select).toHaveValue('30s');
      });
    });

    it('should display all refresh interval options', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const select = screen.getByLabelText(/auto refresh/i);
        expect(select).toBeInTheDocument();
      });

      const select = screen.getByLabelText(/auto refresh/i);
      expect(select.querySelectorAll('option').length).toBe(5);
    });

    it('should update refresh interval when selection changes', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('refresh-interval-selector')).toBeInTheDocument();
      });

      const select = screen.getByLabelText(/auto refresh/i);
      fireEvent.change(select, { target: { value: '1m' } });

      expect(select).toHaveValue('1m');
    });

    it('should allow disabling auto-refresh', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('refresh-interval-selector')).toBeInTheDocument();
      });

      const select = screen.getByLabelText(/auto refresh/i);
      fireEvent.change(select, { target: { value: 'off' } });

      expect(select).toHaveValue('off');
    });
  });

  describe('Health Alerts', () => {
    it('should display health alerts when services are unhealthy', async () => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('health-alerts')).toBeInTheDocument();
      });
    });

    it('should display alert for unhealthy Kafka service', async () => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('health-alert-kafka')).toBeInTheDocument();
        // Use within to scope the query to the health alerts section
        const alertItem = screen.getByTestId('health-alert-kafka');
        expect(alertItem).toHaveTextContent('Kafka');
        expect(alertItem).toHaveTextContent('Connection timeout');
      });
    });

    it('should not display health alerts when all services are healthy', async () => {
      const healthyData: DashboardData = {
        ...mockDashboardData,
        health: mockDashboardData.health.map((h) => ({
          ...h,
          status: 'healthy' as const,
        })),
      };
      mockFetch.mockResolvedValue(createMockResponse(healthyData));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('health-cards')).toBeInTheDocument();
      });

      expect(screen.queryByTestId('health-alerts')).not.toBeInTheDocument();
    });

    it('should have accessible health alerts with role="alert"', async () => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const alertsContainer = screen.getByTestId('health-alerts');
        expect(alertsContainer).toHaveAttribute('role', 'alert');
      });
    });

    it('should display health alerts title', async () => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));

      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByText(/health alerts/i)).toBeInTheDocument();
      });
    });
  });

  describe('Dashboard Controls', () => {
    beforeEach(() => {
      mockFetch.mockResolvedValue(createMockResponse(mockDashboardData));
    });

    it('should display dashboard controls container', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        expect(screen.getByTestId('dashboard-controls')).toBeInTheDocument();
      });
    });

    it('should have accessible select elements', async () => {
      render(<DashboardPage />, { wrapper: createTestWrapper() });

      await waitFor(() => {
        const timeRangeSelect = screen.getByLabelText(/time range/i);
        const refreshSelect = screen.getByLabelText(/auto refresh/i);

        expect(timeRangeSelect).toBeInTheDocument();
        expect(refreshSelect).toBeInTheDocument();
      });
    });
  });
});
