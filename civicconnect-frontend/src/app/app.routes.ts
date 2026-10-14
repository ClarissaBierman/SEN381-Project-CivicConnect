import { Routes } from '@angular/router';
import { roleGuard } from './core/role.guard';
import { LoginPage } from './features/login/login-page';
import { CategoriesPage } from './features/management/categories-page';
import { ActivityPage } from './features/management/activity-page';
import { PlannedPage } from './features/planned-page';
import { RequestQueuePage } from './features/handler/request-queue-page';
import { RequesterPage } from './features/requester/requester-page';
import { RegisterPage } from './features/register/register-page';

// URLs follow the wireframes: /requests/new (WF1), /staff/requests (WF2), /management/activity (WF3).
export const routes: Routes = [
  { path: 'login', component: LoginPage },
  { path: 'register', component: RegisterPage },

  // Wireframe 1 - one page, both panels; the two links just land on the same screen.
  {
    path: 'requests/new',
    component: RequesterPage,
    canActivate: [roleGuard],
    data: { roles: ['REQUESTER'] },
  },
  {
    path: 'requests/mine',
    component: RequesterPage,
    canActivate: [roleGuard],
    data: { roles: ['REQUESTER'] },
  },

  // Wireframe 2 - handler queue. Managers reuse it for "Requests" (all categories).
  {
    path: 'staff/requests',
    component: RequestQueuePage,
    canActivate: [roleGuard],
    data: { roles: ['HANDLER'] },
  },
  {
    path: 'management/requests',
    component: RequestQueuePage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGER'] },
  },

  // Wireframe 3 + other Management-only screens
  {
    path: 'management/activity',
    component: ActivityPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGER'] },
  },
  {
    path: 'management/categories',
    component: CategoriesPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGER'] },
  },
  {
    path: 'management/handler-categories',
    component: PlannedPage,
    canActivate: [roleGuard],
    data: {
      roles: ['MANAGER'],
      title: 'Handler categories',
      note: 'FR-005, API-16 GET/PUT /employees/{id}/categories - Planned.',
    },
  },

  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
