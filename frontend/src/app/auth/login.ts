import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../core/auth';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="card" style="max-width:380px; margin:3rem auto">
      <h1>{{ mode() === 'login' ? 'Sign in' : 'Create account' }}</h1>
      <p class="muted">NEPSE Signal Platform</p>

      <div class="field" style="margin-top:1rem">
        <label>Email</label>
        <input type="email" [value]="email()" (input)="email.set(value($event))" autocomplete="username" />
      </div>
      <div class="field" style="margin-top:0.75rem">
        <label>Password</label>
        <input type="password" [value]="password()" (input)="password.set(value($event))"
          autocomplete="current-password" placeholder="at least 12 characters" />
      </div>

      @if (error()) {
        <p class="error" style="margin-top:0.75rem">{{ error() }}</p>
      }

      <button style="margin-top:1rem; width:100%" (click)="submit()" [disabled]="busy() || !email() || !password()">
        {{ busy() ? 'Please wait…' : mode() === 'login' ? 'Sign in' : 'Register' }}
      </button>

      <p class="muted" style="margin-top:1rem; text-align:center">
        @if (mode() === 'login') {
          No account?
          <a class="link" (click)="mode.set('register'); error.set(null)">Register</a>
        } @else {
          Have an account?
          <a class="link" (click)="mode.set('login'); error.set(null)">Sign in</a>
        }
      </p>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly mode = signal<'login' | 'register'>('login');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected submit(): void {
    this.busy.set(true);
    this.error.set(null);
    if (this.mode() === 'login') {
      this.signIn();
    } else {
      this.auth.register(this.email(), this.password()).subscribe({
        next: () => this.signIn(),
        error: (e) => this.fail(e?.error?.message ?? 'Registration failed'),
      });
    }
  }

  private signIn(): void {
    this.auth.login(this.email(), this.password()).subscribe({
      next: () => this.router.navigateByUrl('/signals'),
      error: () => this.fail('Invalid email or password'),
    });
  }

  private fail(message: string): void {
    this.error.set(message);
    this.busy.set(false);
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }
}
