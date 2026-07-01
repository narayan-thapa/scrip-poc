import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { TokenStore } from './token-store';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let tokens: TokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    tokens = TestBed.inject(TokenStore);
  });

  afterEach(() => httpMock.verify());

  it('stores the access token and loads the profile on login', () => {
    let emitted: unknown = null;
    service.login('a@b.com', 'password12').subscribe((u) => (emitted = u));

    httpMock
      .expectOne((r) => r.url.endsWith('/api/v1/auth/login'))
      .flush({ accessToken: 'access-tok', tokenType: 'Bearer', expiresIn: 900 });

    httpMock
      .expectOne((r) => r.url.endsWith('/api/v1/users/me'))
      .flush({ id: 'u1', email: 'a@b.com', role: 'USER' });

    expect(tokens.token()).toBe('access-tok');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.user()?.email).toBe('a@b.com');
    expect(emitted).toBeTruthy();
  });

  it('clears the session on logout', () => {
    tokens.set('x');
    service.logout().subscribe();
    httpMock.expectOne((r) => r.url.endsWith('/api/v1/auth/logout')).flush(null);
    expect(tokens.token()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});
