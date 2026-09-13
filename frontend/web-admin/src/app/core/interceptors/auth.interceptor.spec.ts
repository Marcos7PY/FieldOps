import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { environment } from '../../../environments/environment';
import { AuthResponse, User } from '../models';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const mockUser: User = {
    id: 1,
    username: 'supervisor',
    fullName: 'Supervisor User',
    roles: ['ROLE_SUPERVISOR']
  };

  const initialAuth: AuthResponse = {
    accessToken: 'initial-access-token',
    refreshToken: 'initial-refresh-token',
    expiresIn: 900,
    user: mockUser
  };

  const refreshedAuth: AuthResponse = {
    accessToken: 'refreshed-access-token',
    refreshToken: 'new-refresh-token',
    expiresIn: 900,
    user: mockUser
  };

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should add Authorization header when user is authenticated', () => {
    authService.setSession(initialAuth);

    http.get('/api/v1/work-orders').subscribe();

    const req = httpMock.expectOne('/api/v1/work-orders');
    expect(req.request.headers.has('Authorization')).toBe(true);
    expect(req.request.headers.get('Authorization')).toBe('Bearer initial-access-token');
    req.flush([]);
  });

  it('should not add Authorization header to login or refresh endpoints', () => {
    authService.setSession(initialAuth);

    http.post(`${environment.apiBaseUrl}/auth/login`, { username: 'test', password: 'pwd' }).subscribe();
    const loginReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(loginReq.request.headers.has('Authorization')).toBe(false);
    loginReq.flush(initialAuth);

    http.post(`${environment.apiBaseUrl}/auth/refresh`, { refreshToken: 'token' }).subscribe();
    const refreshReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`);
    expect(refreshReq.request.headers.has('Authorization')).toBe(false);
    refreshReq.flush(refreshedAuth);
  });

  it('should catch 401, refresh token and retry original request with new token', () => {
    authService.setSession(initialAuth);

    let finalResponse: unknown = null;
    http.get('/api/v1/work-orders/1').subscribe(res => {
      finalResponse = res;
    });

    // Initial request fails with 401
    const firstReq = httpMock.expectOne('/api/v1/work-orders/1');
    expect(firstReq.request.headers.get('Authorization')).toBe('Bearer initial-access-token');
    firstReq.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // Interceptor triggers refresh
    const refreshReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`);
    expect(refreshReq.request.method).toBe('POST');
    refreshReq.flush(refreshedAuth);

    // Retried request with new access token
    const retriedReq = httpMock.expectOne('/api/v1/work-orders/1');
    expect(retriedReq.request.headers.get('Authorization')).toBe('Bearer refreshed-access-token');
    retriedReq.flush({ id: 1, title: 'Order 1' });

    expect(finalResponse).toEqual({ id: 1, title: 'Order 1' });
    expect(authService.getAccessToken()).toBe('refreshed-access-token');
  });

  it('should not retry infinitely if retried request also returns 401', () => {
    authService.setSession(initialAuth);

    let hasError = false;
    let errorStatus = 0;

    http.get('/api/v1/work-orders/1').subscribe({
      next: () => undefined,
      error: (err: HttpErrorResponse) => {
        hasError = true;
        errorStatus = err.status;
      }
    });

    // First attempt fails
    const firstReq = httpMock.expectOne('/api/v1/work-orders/1');
    firstReq.flush({ message: 'Token expired' }, { status: 401, statusText: 'Unauthorized' });

    // Refresh succeeds
    const refreshReq = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`);
    refreshReq.flush(refreshedAuth);

    // Retried request fails again with 401
    const retriedReq = httpMock.expectOne('/api/v1/work-orders/1');
    retriedReq.flush({ message: 'Invalid permissions' }, { status: 401, statusText: 'Unauthorized' });

    // Should NOT issue another refresh request
    httpMock.expectNone(`${environment.apiBaseUrl}/auth/refresh`);

    expect(hasError).toBe(true);
    expect(errorStatus).toBe(401);
  });
});
