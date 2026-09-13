import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService, WorkOrdersService } from '../../../core/services';
import { OrderStatus, Priority, WorkOrderSummary } from '../../../core/models';

@Component({
  selector: 'app-work-orders-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTooltipModule
  ],
  templateUrl: './work-orders-list.component.html',
  styleUrl: './work-orders-list.component.scss'
})
export class WorkOrdersListComponent implements OnInit {
  private readonly workOrdersService = inject(WorkOrdersService);
  readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly displayedColumns: string[] = [
    'code',
    'title',
    'clientName',
    'status',
    'priority',
    'scheduledAt',
    'actions'
  ];

  readonly orders = signal<WorkOrderSummary[]>([]);
  readonly totalElements = signal<number>(0);
  readonly loading = signal<boolean>(false);
  readonly pageIndex = signal<number>(0);
  readonly pageSize = signal<number>(10);

  readonly searchControl = new FormControl<string>('', { nonNullable: true });
  readonly statusControl = new FormControl<OrderStatus | ''>('', { nonNullable: true });

  readonly statusOptions: { label: string; value: OrderStatus | '' }[] = [
    { label: 'Todos los estados', value: '' },
    { label: 'Borrador', value: 'DRAFT' },
    { label: 'Asignada', value: 'ASSIGNED' },
    { label: 'En Progreso', value: 'IN_PROGRESS' },
    { label: 'Completada', value: 'COMPLETED' },
    { label: 'Cancelada', value: 'CANCELLED' }
  ];

  constructor() {
    this.searchControl.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntilDestroyed()
    ).subscribe(() => {
      this.pageIndex.set(0);
      this.loadOrders();
    });

    this.statusControl.valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe(() => {
      this.pageIndex.set(0);
      this.loadOrders();
    });
  }

  ngOnInit(): void {
    this.loadOrders();
  }

  loadOrders(): void {
    this.loading.set(true);

    this.workOrdersService.getWorkOrders({
      page: this.pageIndex(),
      size: this.pageSize(),
      search: this.searchControl.value,
      status: this.statusControl.value
    }).subscribe({
      next: (page) => {
        this.orders.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.orders.set([]);
        this.totalElements.set(0);
        this.loading.set(false);
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadOrders();
  }

  clearFilters(): void {
    this.searchControl.setValue('');
    this.statusControl.setValue('');
  }

  viewDetail(id: number): void {
    this.router.navigate(['/work-orders', id]);
  }

  getStatusClass(status: OrderStatus): string {
    switch (status) {
      case 'DRAFT': return 'status-draft';
      case 'ASSIGNED': return 'status-assigned';
      case 'IN_PROGRESS': return 'status-in-progress';
      case 'COMPLETED': return 'status-completed';
      case 'CANCELLED': return 'status-cancelled';
      default: return '';
    }
  }

  getStatusLabel(status: OrderStatus): string {
    switch (status) {
      case 'DRAFT': return 'Borrador';
      case 'ASSIGNED': return 'Asignada';
      case 'IN_PROGRESS': return 'En Progreso';
      case 'COMPLETED': return 'Completada';
      case 'CANCELLED': return 'Cancelada';
      default: return status;
    }
  }

  getPriorityClass(priority: Priority): string {
    switch (priority) {
      case 'LOW': return 'priority-low';
      case 'MEDIUM': return 'priority-medium';
      case 'HIGH': return 'priority-high';
      case 'CRITICAL': return 'priority-critical';
      default: return '';
    }
  }

  getPriorityLabel(priority: Priority): string {
    switch (priority) {
      case 'LOW': return 'Baja';
      case 'MEDIUM': return 'Media';
      case 'HIGH': return 'Alta';
      case 'CRITICAL': return 'Crítica';
      default: return priority;
    }
  }
}
