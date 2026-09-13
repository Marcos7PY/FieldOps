import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
import { AuthResponse, LoginRequest, User } from '../models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const mockUser: User = {
    id: 1,
    username: 'supervisor',
    fullName: 'Supervisor User',
    roles: ['ROLE_SUPERVISOR'],
  };

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-access-token-123',
    refreshToken: 'test-refresh-token-456',
    expiresIn: 900,
    user: mockUser,
  };

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created with initial unauthenticated state', () => {
    expect(service).toBeTruthy();
    expect(service.isAuthenticated()).toBe(false);
    expect(service.currentUser()).toBeNull();
    expect(service.getAccessToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
  });

  it('should authenticate user, store access token in memory and refresh token in localStorage on login', () => {
    const credentials: LoginRequest = { username: 'supervisor', password: 'Password123!' };

    service.login(credentials).subscribe((response) => {
      expect(response).toEqual(mockAuthResponse);
      expect(service.isAuthenticated()).toBe(true);
      expect(service.getAccessToken()).toBe('test-access-token-123');
      expect(service.currentUser()).toEqual(mockUser);
      expect(service.getRefreshToken()).toBe('test-refresh-token-456');
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(credentials);
    req.flush(mockAuthResponse);
  });

  it('should check user roles correctly', () => {
    service.setSession(mockAuthResponse);

    expect(service.hasRole('ROLE_SUPERVISOR')).toBe(true);
    expect(service.hasRole('ROLE_TECHNICIAN')).toBe(false);
    expect(service.hasAnyRole(['ROLE_ADMIN', 'ROLE_SUPERVISOR'])).toBe(true);
    expect(service.hasAnyRole(['ROLE_TECHNICIAN'])).toBe(false);
    expect(service.userRole()).toBe('ROLE_SUPERVISOR');
  });

  it('should refresh token using stored refresh token', () => {
    localStorage.setItem('fieldops_refresh_token', 'stored-refresh-token');

    const newResponse: AuthResponse = {
      accessToken: 'new-access-token',
      refreshToken: 'new-refresh-token',
      expiresIn: 900,
      user: mockUser,
    };

    service.refreshToken().subscribe((response) => {
      expect(response).toEqual(newResponse);
      expect(service.getAccessToken()).toBe('new-access-token');
      expect(service.getRefreshToken()).toBe('new-refresh-token');
    });

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/refresh`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'stored-refresh-token' });
    req.flush(newResponse);
  });

  it('should clear session on logout and call logout endpoint', () => {
    service.setSession(mockAuthResponse);
    expect(service.isAuthenticated()).toBe(true);

    service.logout().subscribe();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getAccessToken()).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/auth/logout`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('should return of(null) from initSession when no refresh token is present', () => {
    service.initSession().subscribe((user) => {
      expect(user).toBeNull();
    });

    httpMock.expectNone(`${environment.apiBaseUrl}/auth/refresh`);
  });
});
