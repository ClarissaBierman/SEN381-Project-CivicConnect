import { Routes } from '@angular/router';
import { roleGuard } from './core/role.guard';
import { LoginPage } from './features/login/login-page';
import { CategoriesPage } from './features/management/categories-page';
import { PlannedPage } from './features/planned-page';
import { RequestQueuePage } from './features/handler/request-queue-page';
import { RequesterPage } from './features/requester/requester-page';

// URLs follow the wireframes: /requests/new (WF1), /staff/requests (WF2), /management/activity (WF3).
// Screens not built yet point at PlannedPage and are swapped in one wireframe at a time.
export const routes: Routes = [
  { path: 'login', component: LoginPage },

  { path: 'requests/new', component: RequesterPage, canActivate: [roleGuard], data: { roles: ['REQUESTER'] } },
  { path: 'requests/mine', component: RequesterPage, canActivate: [roleGuard], data: { roles: ['REQUESTER'] } },

  { path: 'staff/requests', component: RequestQueuePage, canActivate: [roleGuard], data: { roles: ['HANDLER'] } },
  { path: 'management/requests', component: RequestQueuePage, canActivate: [roleGuard], data: { roles: ['MANAGER'] } },

  { path: 'management/activity', component: PlannedPage, canActivate: [roleGuard], data: { roles: ['MANAGER'], title: 'Service activity', note: 'Wireframe 3 - Planned.' } },
  { path: 'management/categories', component: CategoriesPage, canActivate: [roleGuard], data: { roles: ['MANAGER'] } },
  {
    path: 'management/handler-categories',
    component: PlannedPage,
    canActivate: [roleGuard],
    data: { roles: ['MANAGER'], title: 'Handler categories', note: 'FR-005, API-16 GET/PUT /employees/{id}/categories - Planned.' },
  },

  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' },
];
