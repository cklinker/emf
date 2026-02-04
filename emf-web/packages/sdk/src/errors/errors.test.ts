import { describe, it, expect } from 'vitest';
import {
  EMFError,
  ValidationError,
  AuthenticationError,
  AuthorizationError,
  NotFoundError,
  ServerError,
  NetworkError,
  parseErrorResponse,
  mapAxiosError,
} from './index';

describe('Error Class Hierarchy', () => {
  describe('EMFError (Base Class)', () => {
    it('should create an error with message only', () => {
      const error = new EMFError('Something went wrong');
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error.message).toBe('Something went wrong');
      expect(error.name).toBe('EMFError');
      expect(error.statusCode).toBeUndefined();
      expect(error.details).toBeUndefined();
    });

    it('should create an error with message and status code', () => {
      const error = new EMFError('Bad request', 400);
      
      expect(error.message).toBe('Bad request');
      expect(error.statusCode).toBe(400);
      expect(error.details).toBeUndefined();
    });

    it('should create an error with message, status code, and details', () => {
      const details = { field: 'email', reason: 'invalid format' };
      const error = new EMFError('Validation failed', 400, details);
      
      expect(error.message).toBe('Validation failed');
      expect(error.statusCode).toBe(400);
      expect(error.details).toEqual(details);
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new EMFError('Test error');
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
    });
  });

  describe('ValidationError (HTTP 400) - Requirement 8.1', () => {
    it('should create a validation error with message and field errors', () => {
      const fieldErrors = {
        email: ['Invalid email format', 'Email is required'],
        password: ['Password must be at least 8 characters'],
      };
      const error = new ValidationError('Validation failed', fieldErrors);
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(ValidationError);
      expect(error.message).toBe('Validation failed');
      expect(error.name).toBe('ValidationError');
      expect(error.statusCode).toBe(400);
      expect(error.fieldErrors).toEqual(fieldErrors);
    });

    it('should default to empty field errors when not provided', () => {
      const error = new ValidationError('Validation failed');
      
      expect(error.fieldErrors).toEqual({});
    });

    it('should include field errors in details', () => {
      const fieldErrors = { name: ['Name is required'] };
      const error = new ValidationError('Validation failed', fieldErrors);
      
      expect(error.details).toEqual({ fieldErrors });
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new ValidationError('Test');
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof ValidationError).toBe(true);
    });
  });

  describe('AuthenticationError (HTTP 401) - Requirement 8.2', () => {
    it('should create an authentication error with default message', () => {
      const error = new AuthenticationError();
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(AuthenticationError);
      expect(error.message).toBe('Authentication failed');
      expect(error.name).toBe('AuthenticationError');
      expect(error.statusCode).toBe(401);
    });

    it('should create an authentication error with custom message', () => {
      const error = new AuthenticationError('Invalid token');
      
      expect(error.message).toBe('Invalid token');
      expect(error.statusCode).toBe(401);
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new AuthenticationError();
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof AuthenticationError).toBe(true);
    });
  });

  describe('AuthorizationError (HTTP 403) - Requirement 8.3', () => {
    it('should create an authorization error with default message', () => {
      const error = new AuthorizationError();
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(AuthorizationError);
      expect(error.message).toBe('Access denied');
      expect(error.name).toBe('AuthorizationError');
      expect(error.statusCode).toBe(403);
    });

    it('should create an authorization error with custom message', () => {
      const error = new AuthorizationError('Insufficient permissions');
      
      expect(error.message).toBe('Insufficient permissions');
      expect(error.statusCode).toBe(403);
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new AuthorizationError();
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof AuthorizationError).toBe(true);
    });
  });

  describe('NotFoundError (HTTP 404) - Requirement 8.4', () => {
    it('should create a not found error with resource and id', () => {
      const error = new NotFoundError('User', '123');
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(NotFoundError);
      expect(error.message).toBe("User with id '123' not found");
      expect(error.name).toBe('NotFoundError');
      expect(error.statusCode).toBe(404);
    });

    it('should include resource and id in details', () => {
      const error = new NotFoundError('Order', 'abc-456');
      
      expect(error.details).toEqual({ resource: 'Order', id: 'abc-456' });
    });

    it('should handle various resource names and ids', () => {
      const error1 = new NotFoundError('Product', 'SKU-001');
      expect(error1.message).toBe("Product with id 'SKU-001' not found");
      
      const error2 = new NotFoundError('Collection', 'my-collection');
      expect(error2.message).toBe("Collection with id 'my-collection' not found");
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new NotFoundError('Resource', 'id');
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof NotFoundError).toBe(true);
    });
  });

  describe('ServerError (HTTP 5xx) - Requirement 8.5', () => {
    it('should create a server error with message and status code', () => {
      const error = new ServerError('Internal server error', 500);
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(ServerError);
      expect(error.message).toBe('Internal server error');
      expect(error.name).toBe('ServerError');
      expect(error.statusCode).toBe(500);
    });

    it('should default to status code 500 when not provided', () => {
      const error = new ServerError('Server error');
      
      expect(error.statusCode).toBe(500);
    });

    it('should handle various 5xx status codes', () => {
      const error502 = new ServerError('Bad gateway', 502);
      expect(error502.statusCode).toBe(502);
      
      const error503 = new ServerError('Service unavailable', 503);
      expect(error503.statusCode).toBe(503);
      
      const error504 = new ServerError('Gateway timeout', 504);
      expect(error504.statusCode).toBe(504);
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new ServerError('Test', 500);
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof ServerError).toBe(true);
    });
  });

  describe('NetworkError - Requirement 8.6', () => {
    it('should create a network error with message and original error', () => {
      const originalError = new Error('Connection refused');
      const error = new NetworkError('Network request failed', originalError);
      
      expect(error).toBeInstanceOf(Error);
      expect(error).toBeInstanceOf(EMFError);
      expect(error).toBeInstanceOf(NetworkError);
      expect(error.message).toBe('Network request failed');
      expect(error.name).toBe('NetworkError');
      expect(error.originalError).toBe(originalError);
      expect(error.statusCode).toBeUndefined();
    });

    it('should include original error message in details', () => {
      const originalError = new Error('ECONNREFUSED');
      const error = new NetworkError('Connection failed', originalError);
      
      expect(error.details).toEqual({ originalError: 'ECONNREFUSED' });
    });

    it('should preserve the original error reference', () => {
      const originalError = new TypeError('Network error');
      const error = new NetworkError('Request failed', originalError);
      
      expect(error.originalError).toBe(originalError);
      expect(error.originalError.message).toBe('Network error');
    });

    it('should preserve prototype chain for instanceof checks', () => {
      const error = new NetworkError('Test', new Error('Original'));
      
      expect(error instanceof Error).toBe(true);
      expect(error instanceof EMFError).toBe(true);
      expect(error instanceof NetworkError).toBe(true);
    });
  });

  describe('parseErrorResponse', () => {
    it('should return ValidationError for status 400', () => {
      const error = parseErrorResponse(400, {
        message: 'Validation failed',
        fieldErrors: { email: ['Invalid email'] },
      });
      
      expect(error).toBeInstanceOf(ValidationError);
      expect(error.message).toBe('Validation failed');
      expect((error as ValidationError).fieldErrors).toEqual({ email: ['Invalid email'] });
    });

    it('should return AuthenticationError for status 401', () => {
      const error = parseErrorResponse(401, { message: 'Token expired' });
      
      expect(error).toBeInstanceOf(AuthenticationError);
      expect(error.message).toBe('Token expired');
    });

    it('should return AuthorizationError for status 403', () => {
      const error = parseErrorResponse(403, { message: 'Forbidden' });
      
      expect(error).toBeInstanceOf(AuthorizationError);
      expect(error.message).toBe('Forbidden');
    });

    it('should return NotFoundError for status 404', () => {
      const error = parseErrorResponse(404, { message: 'Not found' });
      
      expect(error).toBeInstanceOf(NotFoundError);
    });

    it('should return NotFoundError with resource and id from response data', () => {
      const error = parseErrorResponse(404, { 
        message: 'Not found',
        resource: 'User',
        id: '123'
      });
      
      expect(error).toBeInstanceOf(NotFoundError);
      expect(error.message).toBe("User with id '123' not found");
      expect(error.details).toEqual({ resource: 'User', id: '123' });
    });

    it('should return NotFoundError with defaults when resource/id not provided', () => {
      const error = parseErrorResponse(404, { message: 'Not found' });
      
      expect(error).toBeInstanceOf(NotFoundError);
      expect(error.message).toBe("Resource with id 'unknown' not found");
    });

    it('should return ServerError for 5xx status codes', () => {
      const error500 = parseErrorResponse(500, { message: 'Internal error' });
      expect(error500).toBeInstanceOf(ServerError);
      expect(error500.statusCode).toBe(500);
      
      const error502 = parseErrorResponse(502, { message: 'Bad gateway' });
      expect(error502).toBeInstanceOf(ServerError);
      expect(error502.statusCode).toBe(502);
      
      const error503 = parseErrorResponse(503, { message: 'Service unavailable' });
      expect(error503).toBeInstanceOf(ServerError);
      expect(error503.statusCode).toBe(503);
    });

    it('should return generic EMFError for other status codes', () => {
      const error = parseErrorResponse(418, { message: "I'm a teapot" });
      
      expect(error).toBeInstanceOf(EMFError);
      expect(error).not.toBeInstanceOf(ValidationError);
      expect(error).not.toBeInstanceOf(AuthenticationError);
      expect(error).not.toBeInstanceOf(AuthorizationError);
      expect(error).not.toBeInstanceOf(NotFoundError);
      expect(error).not.toBeInstanceOf(ServerError);
      expect(error.statusCode).toBe(418);
    });

    it('should use error field as fallback message', () => {
      const error = parseErrorResponse(400, { error: 'Bad request' });
      
      expect(error.message).toBe('Bad request');
    });

    it('should use default message when no message provided', () => {
      const error = parseErrorResponse(500, {});
      
      expect(error.message).toBe('An error occurred');
    });

    it('should handle undefined data', () => {
      const error = parseErrorResponse(500, undefined);
      
      expect(error.message).toBe('An error occurred');
    });
  });

  describe('mapAxiosError', () => {
    it('should map Axios error with response to appropriate error type', () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 401,
          data: { message: 'Unauthorized' },
        },
        message: 'Request failed',
      };
      
      const error = mapAxiosError(axiosError);
      
      expect(error).toBeInstanceOf(AuthenticationError);
      expect(error.message).toBe('Unauthorized');
    });

    it('should map Axios error without response to NetworkError', () => {
      const axiosError = {
        isAxiosError: true,
        message: 'Network Error',
      };
      
      const error = mapAxiosError(axiosError);
      
      expect(error).toBeInstanceOf(NetworkError);
      expect(error.message).toBe('Network Error');
    });

    it('should map regular Error to EMFError', () => {
      const regularError = new Error('Something went wrong');
      
      const error = mapAxiosError(regularError);
      
      expect(error).toBeInstanceOf(EMFError);
      expect(error.message).toBe('Something went wrong');
    });

    it('should handle unknown error types', () => {
      const unknownError = 'string error';
      
      const error = mapAxiosError(unknownError);
      
      expect(error).toBeInstanceOf(EMFError);
      expect(error.message).toBe('An unknown error occurred');
    });

    it('should handle null/undefined errors', () => {
      const error1 = mapAxiosError(null);
      expect(error1).toBeInstanceOf(EMFError);
      expect(error1.message).toBe('An unknown error occurred');
      
      const error2 = mapAxiosError(undefined);
      expect(error2).toBeInstanceOf(EMFError);
      expect(error2.message).toBe('An unknown error occurred');
    });

    it('should extract field errors from validation response', () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 400,
          data: {
            message: 'Validation failed',
            fieldErrors: {
              email: ['Invalid format'],
              name: ['Required'],
            },
          },
        },
        message: 'Request failed',
      };
      
      const error = mapAxiosError(axiosError);
      
      expect(error).toBeInstanceOf(ValidationError);
      expect((error as ValidationError).fieldErrors).toEqual({
        email: ['Invalid format'],
        name: ['Required'],
      });
    });

    it('should extract resource and id from 404 response', () => {
      const axiosError = {
        isAxiosError: true,
        response: {
          status: 404,
          data: {
            message: 'Not found',
            resource: 'Order',
            id: 'order-456',
          },
        },
        message: 'Request failed',
      };
      
      const error = mapAxiosError(axiosError);
      
      expect(error).toBeInstanceOf(NotFoundError);
      expect(error.message).toBe("Order with id 'order-456' not found");
    });

    it('should handle Axios error with empty message for network error', () => {
      const axiosError = {
        isAxiosError: true,
        message: '',
      };
      
      const error = mapAxiosError(axiosError);
      
      expect(error).toBeInstanceOf(NetworkError);
      expect(error.message).toBe('Network error');
    });

    it('should map all 5xx status codes to ServerError', () => {
      const statusCodes = [500, 501, 502, 503, 504, 505, 520, 599];
      
      for (const status of statusCodes) {
        const axiosError = {
          isAxiosError: true,
          response: {
            status,
            data: { message: `Error ${status}` },
          },
          message: 'Request failed',
        };
        
        const error = mapAxiosError(axiosError);
        
        expect(error).toBeInstanceOf(ServerError);
        expect(error.statusCode).toBe(status);
      }
    });
  });

  describe('Error type discrimination', () => {
    it('should allow type narrowing with instanceof', () => {
      const errors: EMFError[] = [
        new ValidationError('Validation failed', { field: ['error'] }),
        new AuthenticationError('Auth failed'),
        new AuthorizationError('Access denied'),
        new NotFoundError('User', '123'),
        new ServerError('Server error', 500),
        new NetworkError('Network error', new Error('Original')),
      ];

      for (const error of errors) {
        if (error instanceof ValidationError) {
          // TypeScript should know fieldErrors exists
          expect(error.fieldErrors).toBeDefined();
        } else if (error instanceof AuthenticationError) {
          expect(error.statusCode).toBe(401);
        } else if (error instanceof AuthorizationError) {
          expect(error.statusCode).toBe(403);
        } else if (error instanceof NotFoundError) {
          expect(error.statusCode).toBe(404);
        } else if (error instanceof ServerError) {
          expect(error.statusCode).toBeGreaterThanOrEqual(500);
        } else if (error instanceof NetworkError) {
          // TypeScript should know originalError exists
          expect(error.originalError).toBeDefined();
        }
      }
    });
  });
});
