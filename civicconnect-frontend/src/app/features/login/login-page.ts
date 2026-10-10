import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Role } from '../../core/models';
import { HOME_ROUTE, SessionService } from '../../core/session.service';

// TEMPORARY development sign-in. Replaced by the real Login screen (FR-014, API-02) once
// JWT authentication (DEC-010) is built. No password is collected here on purpose.
@Component({
  selector: 'app-login-page',
  template: `
    <section class="panel narrow">
      <h1>CivicConnect</h1>
      <p class="hint">
        Development sign-in only. This screen is replaced by the Login screen
        (FR-014, POST /api/v1/auth/login) when DEC-010 is implemented.
      </p>
      <div class="stack">
        <button type="button" class="btn" (click)="go('REQUESTER')">Continue as Requester</button>
        <button type="button" class="btn" (click)="go('HANDLER')">Continue as Service Handler</button>
        <button type="button" class="btn" (click)="go('MANAGER')">Continue as Management</button>
      </div>
    </section>
  `,
})
export class LoginPage {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  constructor() {
    const user = this.session.user();
    if (user) this.router.navigateByUrl(HOME_ROUTE[user.role]);
  }

  go(role: Role): void {
    this.session.signInAs(role);
    this.router.navigateByUrl(HOME_ROUTE[role]);
  }
}
