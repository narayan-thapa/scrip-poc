import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';
import { UserProfile } from './core/api/models';

describe('App shell', () => {
  // Authenticated stub so the sidebar chrome renders; the real AuthService needs a live backend.
  const authStub = {
    user: signal<UserProfile>({ id: '1', email: 'trader@example.com', role: 'USER' } as UserProfile),
    isAuthenticated: signal(true),
    logout: () => of(void 0),
    hasRole: () => false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authStub },
      ],
    }).compileComponents();
  });

  it('creates the shell', () => {
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the brand, side-nav groups and a connection badge', () => {
    const fixture = TestBed.createComponent(App);
    // Render synchronously; the ping httpResource stays pending in the test backend, so we don't
    // await stability — the shell chrome renders regardless of connection state.
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.brand-name')?.textContent).toContain('NEPSE Signal');
    expect(el.querySelector('.conn-badge')).toBeTruthy();
    // Non-admin sees the four public groups (Overview/Analysis/Personal/Reference), not Admin.
    expect(el.querySelectorAll('.nav-group').length).toBe(4);
    expect(el.querySelector('.side-nav')?.textContent).not.toContain('Ingestion');
  });

  it('reveals the Admin group for ADMIN users', () => {
    authStub.user.set({ id: '1', email: 'admin@example.com', role: 'ADMIN' } as UserProfile);
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.nav-group').length).toBe(5);
    expect(el.querySelector('.side-nav')?.textContent).toContain('Ingestion');
  });
});
