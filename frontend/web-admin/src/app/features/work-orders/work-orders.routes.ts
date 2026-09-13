import { Routes } from '@angular/router';

export const WORK_ORDERS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./work-orders-list/work-orders-list.component').then(m => m.WorkOrdersListComponent),
  },
  {
    path: 'new',
    loadComponent: () => import('./work-order-create/work-order-create.component').then(m => m.WorkOrderCreateComponent),
  },
  {
    path: ':id',
    loadComponent: () => import('./work-order-detail/work-order-detail.component').then(m => m.WorkOrderDetailComponent),
  },
];
