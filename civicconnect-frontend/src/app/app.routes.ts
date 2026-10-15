import { Routes } from '@angular/router';
import { roleGuard } from './core/role.guard';
import { LoginPage } from './features/login/login-page';
import { CategoriesPage } from './features/management/categories-page';
import { ActivityPage } from './features/management/activity-page';
import { AuditPage } from './features/management/audit-page';
import { StaffPage } from './features/management/staff-page';
import { RequestQueuePage } from './features/handler/request-queue-page';
import { ProfilePage } from './features/requester/profile-page';
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
  {
    path: 'requests/profile',
    component: ProfilePage,
    canActivate: [roleGuard],
    data: { roles: ['REQUESTER'] },
  },

  // Wireframe 2 - handler queue. Managers reuse it for "Requests" (all categories).
  {
    path: 'staff/requests',
    component: RequestQueuePage,
    canActivate: [roleGuard],
    data: { roles: ['STAFF'] },
  },
  {
    path: 'management/requests',
    component: RequestQueuePage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGEMENT'] },
  },

  // Wireframe 3 + other Management-only screens
  {
    path: 'management/activity',
    component: ActivityPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGEMENT'] },
  },
  {
    path: 'management/categories',
    component: CategoriesPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGEMENT'] },
  },
  {
    path: 'management/staff',
    component: StaffPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGEMENT'] },
  },
  {
    path: 'management/audit',
    component: AuditPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGEMENT'] },
  },

  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
