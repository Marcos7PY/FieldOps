import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ClientsService, WorkOrdersService } from '../../../core/services';
import { Client, CreateWorkOrderRequest, Priority } from '../../../core/models';

@Component({
  selector: 'app-work-order-create',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './work-order-create.component.html',
  styleUrl: './work-order-create.component.scss'
})
export class WorkOrderCreateComponent implements OnInit {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly workOrdersService = inject(WorkOrdersService);
  private readonly clientsService = inject(ClientsService);
  private readonly router = inject(Router);

  readonly clients = signal<Client[]>([]);
  readonly loading = signal<boolean>(false);
  readonly loadingClients = signal<boolean>(false);
  readonly generalError = signal<string | null>(null);

  readonly priorityOptions: { label: string; value: Priority }[] = [
    { label: 'Baja', value: 'LOW' },
    { label: 'Media', value: 'MEDIUM' },
    { label: 'Alta', value: 'HIGH' },
    { label: 'Crítica', value: 'CRITICAL' }
  ];

  readonly createForm = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(150)]],
    description: ['', [Validators.maxLength(1000)]],
    priority: ['MEDIUM' as Priority, [Validators.required]],
    clientId: [null as number | null, [Validators.required]],
    assignedTechnicianId: [null as number | null],
    scheduledAt: ['']
  });

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loadingClients.set(true);
    this.clientsService.getClients(0, 100).subscribe({
      next: (page) => {
        this.clients.set(page.content);
        this.loadingClients.set(false);
      },
      error: () => {
        this.loadingClients.set(false);
      }
    });
  }

  onSubmit(): void {
    if (this.createForm.invalid || this.loading()) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.generalError.set(null);

    const raw = this.createForm.getRawValue();
    let scheduledAtFormatted: string | null = null;
    if (raw.scheduledAt) {
      scheduledAtFormatted = raw.scheduledAt.length === 16 ? `${raw.scheduledAt}:00` : raw.scheduledAt;
    }

    const payload: CreateWorkOrderRequest = {
      title: raw.title.trim(),
      description: raw.description ? raw.description.trim() : null,
      priority: raw.priority,
      clientId: raw.clientId as number,
      assignedTechnicianId: raw.assignedTechnicianId ? Number(raw.assignedTechnicianId) : null,
      scheduledAt: scheduledAtFormatted
    };

    this.workOrdersService.createWorkOrder(payload).subscribe({
      next: (created) => {
        this.loading.set(false);
        this.router.navigate(['/work-orders', created.id]);
      },
      error: (err) => {
        this.loading.set(false);

        if (err.status === 400) {
          const invalidParams = err.error?.invalidParams as Record<string, string> | undefined;
          if (invalidParams && typeof invalidParams === 'object') {
            let matchedAny = false;
            for (const [field, message] of Object.entries(invalidParams)) {
              const control = this.createForm.get(field);
              if (control) {
                control.setErrors({ serverError: message });
                control.markAsTouched();
                matchedAny = true;
              }
            }
            if (!matchedAny) {
              this.generalError.set(err.error?.detail || 'Datos de la orden de trabajo inválidos.');
            }
          } else {
            this.generalError.set(err.error?.detail || 'Error de validación al crear la orden.');
          }
        } else if (err.status === 403) {
          this.generalError.set('No tiene permisos para crear órdenes de trabajo.');
        } else {
          this.generalError.set('Ocurrió un error inesperado al conectar con el servidor.');
        }
      }
    });
  }
}
