/**
 * EMF SDK Error Classes
 */

/**
 * Base error class for all EMF errors
 */
export class EMFError extends Error {
  constructor(
    message: string,
    public readonly statusCode?: number,
    public readonly details?: unknown
  ) {
    super(message);
    this.name = 'EMFError';
    Object.setPrototypeOf(this, EMFError.prototype);
  }
}

/**
 * Validation error (HTTP 400)
 */
export class ValidationError extends EMFError {
  constructor(
    message: string,
    public readonly fieldErrors: Record<string, string[]> = {}
  ) {
    super(message, 400, { fieldErrors });
    this.name = 'ValidationError';
    Object.setPrototypeOf(this, ValidationError.prototype);
  }
}

/**
 * Authentication error (HTTP 401)
 */
export class AuthenticationError extends EMFError {
  constructor(message: string = 'Authentication failed') {
    super(message, 401);
    this.name = 'AuthenticationError';
    Object.setPrototypeOf(this, AuthenticationError.prototype);
  }
}

/**
 * Authorization error (HTTP 403)
 */
export class AuthorizationError extends EMFError {
  constructor(message: string = 'Access denied') {
    super(message, 403);
    this.name = 'AuthorizationError';
    Object.setPrototypeOf(this, AuthorizationError.prototype);
  }
}

/**
 * Not found error (HTTP 404)
 */
export class NotFoundError extends EMFError {
  constructor(resource: string, id: string) {
    super(`${resource} with id '${id}' not found`, 404, { resource, id });
    this.name = 'NotFoundError';
    Object.setPrototypeOf(this, NotFoundError.prototype);
  }
}

/**
 * Server error (HTTP 5xx)
 */
export class ServerError extends EMFError {
  constructor(message: string, statusCode: number = 500) {
    super(message, statusCode);
    this.name = 'ServerError';
    Object.setPrototypeOf(this, ServerError.prototype);
  }
}

/**
 * Network error (connection failures, timeouts)
 */
export class NetworkError extends EMFError {
  constructor(
    message: string,
    public readonly originalError: Error
  ) {
    super(message, undefined, { originalError: originalError.message });
    this.name = 'NetworkError';
    Object.setPrototypeOf(this, NetworkError.prototype);
  }
}

/**
 * Parse an API error response and return the appropriate error class
 */
export function parseErrorResponse(
  statusCode: number,
  data?: { 
    error?: string; 
    message?: string; 
    fieldErrors?: Record<string, string[]>;
    resource?: string;
    id?: string;
  }
): EMFError {
  const message = data?.message || data?.error || 'An error occurred';

  switch (statusCode) {
    case 400:
      return new ValidationError(message, data?.fieldErrors);
    case 401:
      return new AuthenticationError(message);
    case 403:
      return new AuthorizationError(message);
    case 404:
      // Extract resource and id from response data if available
      return new NotFoundError(
        data?.resource || 'Resource',
        data?.id || 'unknown'
      );
    default:
      if (statusCode >= 500) {
        return new ServerError(message, statusCode);
      }
      return new EMFError(message, statusCode);
  }
}

/**
 * Map an Axios error to the appropriate EMF error class
 */
export function mapAxiosError(error: unknown): EMFError {
  // Check if it's an Axios error with a response
  if (
    error &&
    typeof error === 'object' &&
    'isAxiosError' in error &&
    (error as { isAxiosError: boolean }).isAxiosError
  ) {
    const axiosError = error as unknown as {
      response?: { status: number; data?: unknown };
      message: string;
    };

    // Network error (no response)
    if (!axiosError.response) {
      return new NetworkError(axiosError.message || 'Network error', error as unknown as Error);
    }

    // Parse the error response
    return parseErrorResponse(
      axiosError.response.status,
      axiosError.response.data as { 
        error?: string; 
        message?: string; 
        fieldErrors?: Record<string, string[]>;
        resource?: string;
        id?: string;
      }
    );
  }

  // Unknown error
  if (error instanceof Error) {
    return new EMFError(error.message);
  }

  return new EMFError('An unknown error occurred');
}
