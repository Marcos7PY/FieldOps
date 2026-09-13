import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage),
    canActivate: [guestGuard],
  },
  {
    path: 'orders',
    loadComponent: () =>
      import('./features/orders/orders-list/orders-list.page').then((m) => m.OrdersListPage),
    canActivate: [authGuard],
  },
  {
    path: 'orders/:id',
    loadComponent: () =>
      import('./features/orders/order-detail/order-detail.page').then((m) => m.OrderDetailPage),
    canActivate: [authGuard],
  },
  {
    path: 'home',
    redirectTo: 'orders',
    pathMatch: 'full',
  },
  {
    path: '',
    redirectTo: 'orders',
    pathMatch: 'full',
  },
];
