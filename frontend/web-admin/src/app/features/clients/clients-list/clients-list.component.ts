import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ClientsService } from '../../../core/services/clients.service';
import { Client, CreateClientRequest } from '../../../core/models';

@Component({
  selector: 'app-clients-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatDialogModule,
    MatSlideToggleModule,
    MatSnackBarModule,
    MatChipsModule,
    MatTooltipModule,
  ],
  templateUrl: './clients-list.component.html',
  styleUrl: './clients-list.component.scss',
})
export class ClientsListComponent implements OnInit {
  private readonly clientsService = inject(ClientsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly displayedColumns = ['businessName', 'taxId', 'phone', 'address', 'active', 'actions'];

  readonly clients = signal<Client[]>([]);
  readonly totalClients = signal(0);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly pageSize = signal(20);
  readonly pageIndex = signal(0);

  // Create / Edit form state
  readonly showForm = signal(false);
  readonly editingClient = signal<Client | null>(null);
  readonly formSaving = signal(false);
  readonly formError = signal<string | null>(null);

  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly clientForm = this.fb.group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(200)]],
    taxId: ['', [Validators.required, Validators.minLength(9), Validators.maxLength(20)]],
    phone: ['', [Validators.maxLength(30)]],
    address: ['', [Validators.maxLength(300)]],
  });

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(400), distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.loadClients();
      });
  }

  ngOnInit(): void {
    this.loadClients();
  }

  loadClients(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.clientsService.getClients(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        // Filter by search string client-side when no server-side search is available
        const q = this.searchControl.value.trim().toLowerCase();
        const filtered = q
          ? page.content.filter(
              (c) => c.businessName.toLowerCase().includes(q) || c.taxId.toLowerCase().includes(q)
            )
          : page.content;
        this.clients.set(filtered);
        this.totalClients.set(q ? filtered.length : page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar los clientes. Compruebe la conexión.');
        this.loading.set(false);
      },
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadClients();
  }

  openCreateForm(): void {
    this.editingClient.set(null);
    this.clientForm.reset();
    this.formError.set(null);
    this.showForm.set(true);
  }

  openEditForm(client: Client): void {
    this.editingClient.set(client);
    this.clientForm.patchValue({
      businessName: client.businessName,
      taxId: client.taxId,
      phone: client.phone ?? '',
      address: client.address ?? '',
    });
    this.formError.set(null);
    this.showForm.set(true);
  }

  cancelForm(): void {
    this.showForm.set(false);
    this.editingClient.set(null);
    this.clientForm.reset();
    this.formError.set(null);
  }

  submitForm(): void {
    if (this.clientForm.invalid || this.formSaving()) return;

    this.formSaving.set(true);
    this.formError.set(null);

    const raw = this.clientForm.getRawValue();
    const payload: CreateClientRequest = {
      businessName: raw.businessName.trim(),
      taxId: raw.taxId.trim(),
      phone: raw.phone?.trim() || null,
      address: raw.address?.trim() || null,
    };

    const isEdit = !!this.editingClient();

    const request$ = isEdit
      ? this.clientsService.updateClient(this.editingClient()!.id, payload)
      : this.clientsService.createClient(payload);

    request$.subscribe({
      next: () => {
        this.formSaving.set(false);
        this.showForm.set(false);
        this.clientForm.reset();
        this.snackBar.open(
          isEdit ? 'Cliente actualizado correctamente.' : 'Cliente creado correctamente.',
          'Cerrar',
          { duration: 4000, panelClass: ['snack-success'] }
        );
        this.loadClients();
      },
      error: (err) => {
        this.formSaving.set(false);
        if (err.status === 409) {
          this.formError.set(`El NIF/CIF "${payload.taxId}" ya existe en el sistema.`);
        } else if (err.status === 400) {
          this.formError.set(err.error?.detail || 'Datos inválidos. Compruebe el formulario.');
        } else {
          this.formError.set('Error al guardar el cliente. Intente de nuevo.');
        }
      },
    });
  }

  deactivateClient(client: Client): void {
    if (!confirm(`¿Desactivar el cliente "${client.businessName}"?`)) return;

    this.clientsService.deactivateClient(client.id).subscribe({
      next: () => {
        this.snackBar.open('Cliente desactivado.', 'Cerrar', { duration: 3000 });
        this.loadClients();
      },
      error: () => {
        this.snackBar.open('Error al desactivar el cliente.', 'Cerrar', { duration: 3000 });
      },
    });
  }
}
