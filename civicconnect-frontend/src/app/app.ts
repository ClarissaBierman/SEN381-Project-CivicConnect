import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Role } from './core/models';
import { SessionService } from './core/session.service';

interface NavLink {
  path: string;
  label: string;
}

// Screen map, PED Section 22.1: one Angular app, the menu only shows the current role's screens.
const NAV: Record<Role, NavLink[]> = {
  REQUESTER: [
    { path: '/requests/mine', label: 'My Requests' },
    { path: '/requests/new', label: 'New Request' },
  ],
  HANDLER: [{ path: '/staff/requests', label: 'Requests' }],
  MANAGER: [
    { path: '/management/activity', label: 'Activity' },
    { path: '/management/requests', label: 'Requests' },
    { path: '/management/categories', label: 'Categories' },
    { path: '/management/handler-categories', label: 'Handler categories' },
  ],
};

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);

  protected readonly links = computed(() => {
    const user = this.session.user();
    return user ? NAV[user.role] : [];
  });

  logout(): void {
    this.session.signOut();
    this.router.navigateByUrl('/login');
  }
}
