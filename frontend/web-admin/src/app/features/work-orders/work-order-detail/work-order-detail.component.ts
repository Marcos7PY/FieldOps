import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService, WorkOrdersService } from '../../../core/services';
import {
  AssignWorkOrderRequest,
  ChangeStatusRequest,
  Evidence,
  OrderStatus,
  Priority,
  StatusHistory,
  WorkOrder
} from '../../../core/models';

@Component({
  selector: 'app-work-order-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressBarModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTabsModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './work-order-detail.component.html',
  styleUrl: './work-order-detail.component.scss'
})
export class WorkOrderDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workOrdersService = inject(WorkOrdersService);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly authService = inject(AuthService);

  readonly order = signal<WorkOrder | null>(null);
  readonly loading = signal<boolean>(true);
  readonly actionLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly actionSuccess = signal<string | null>(null);

  readonly showStatusForm = signal<boolean>(false);
  readonly showAssignForm = signal<boolean>(false);

  readonly statusForm = this.fb.group({
    newStatus: ['' as OrderStatus, [Validators.required]],
    notes: ['', [Validators.maxLength(500)]]
  });

  readonly assignForm = this.fb.group({
    technicianId: [null as number | null, [Validators.required, Validators.min(1)]],
    scheduledAt: ['']
  });

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.errorMessage.set('Identificador de orden inválido.');
      this.loading.set(false);
      return;
    }

    const orderId = Number(idParam);
    if (isNaN(orderId)) {
      this.errorMessage.set('Identificador numérico de orden requerido.');
      this.loading.set(false);
      return;
    }

    this.loadOrder(orderId);
  }

  loadOrder(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.workOrdersService.getWorkOrder(id).subscribe({
      next: (data) => {
        this.order.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.errorMessage.set('La orden de trabajo solicitada no existe.');
        } else if (err.status === 403) {
          this.errorMessage.set('No tiene autorización para visualizar esta orden.');
        } else {
          this.errorMessage.set('Error al cargar la información de la orden de trabajo.');
        }
      }
    });
  }

  toggleStatusForm(): void {
    this.showStatusForm.update(v => !v);
    this.actionError.set(null);
    this.actionSuccess.set(null);
  }

  toggleAssignForm(): void {
    this.showAssignForm.update(v => !v);
    this.actionError.set(null);
    this.actionSuccess.set(null);
  }

  submitStatusChange(): void {
    const current = this.order();
    if (!current || this.statusForm.invalid || this.actionLoading()) {
      return;
    }

    this.actionLoading.set(true);
    this.actionError.set(null);
    this.actionSuccess.set(null);

    const raw = this.statusForm.getRawValue();
    const payload: ChangeStatusRequest = {
      newStatus: raw.newStatus,
      notes: raw.notes ? raw.notes.trim() : null
    };

    this.workOrdersService.changeStatus(current.id, payload, current.version).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.actionLoading.set(false);
        this.showStatusForm.set(false);
        this.statusForm.reset();
        this.actionSuccess.set(`Estado cambiado exitosamente a ${this.getStatusLabel(updated.status)}`);
      },
      error: (err) => {
        this.actionLoading.set(false);
        if (err.status === 409 || err.status === 412) {
          this.actionError.set('Conflicto de concurrencia: la orden fue modificada por otro usuario. Recargando datos actualizados...');
          this.loadOrder(current.id);
        } else if (err.status === 422 || err.status === 400) {
          this.actionError.set(err.error?.detail || 'Transición de estado no permitida.');
        } else {
          this.actionError.set('Error al actualizar el estado de la orden.');
        }
      }
    });
  }

  submitAssign(): void {
    const current = this.order();
    if (!current || this.assignForm.invalid || this.actionLoading()) {
      return;
    }

    this.actionLoading.set(true);
    this.actionError.set(null);
    this.actionSuccess.set(null);

    const raw = this.assignForm.getRawValue();
    let scheduledAtFormatted: string | null = null;
    if (raw.scheduledAt) {
      scheduledAtFormatted = raw.scheduledAt.length === 16 ? `${raw.scheduledAt}:00` : raw.scheduledAt;
    }

    const payload: AssignWorkOrderRequest = {
      technicianId: Number(raw.technicianId),
      scheduledAt: scheduledAtFormatted
    };

    this.workOrdersService.assignWorkOrder(current.id, payload, current.version).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.actionLoading.set(false);
        this.showAssignForm.set(false);
        this.assignForm.reset();
        this.actionSuccess.set(`Orden asignada exitosamente al técnico #${payload.technicianId}`);
      },
      error: (err) => {
        this.actionLoading.set(false);
        if (err.status === 409 || err.status === 412) {
          this.actionError.set('Conflicto de concurrencia: la orden fue modificada. Recargando datos...');
          this.loadOrder(current.id);
        } else {
          this.actionError.set(err.error?.detail || 'Error al asignar el técnico a la orden.');
        }
      }
    });
  }

  getAvailableNextStatuses(currentStatus: OrderStatus): { label: string; value: OrderStatus }[] {
    switch (currentStatus) {
      case 'DRAFT':
        return [
          { label: 'Asignar (ASSIGNED)', value: 'ASSIGNED' },
          { label: 'Cancelar (CANCELLED)', value: 'CANCELLED' }
        ];
      case 'ASSIGNED':
        return [
          { label: 'Iniciar trabajo (IN_PROGRESS)', value: 'IN_PROGRESS' },
          { label: 'Cancelar (CANCELLED)', value: 'CANCELLED' }
        ];
      case 'IN_PROGRESS':
        return [
          { label: 'Completar trabajo (COMPLETED)', value: 'COMPLETED' },
          { label: 'Cancelar (CANCELLED)', value: 'CANCELLED' }
        ];
      default:
        return [];
    }
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

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  getMapUrl(lat: number, lng: number): string {
    return `https://www.google.com/maps?q=${lat},${lng}`;
  }
}
