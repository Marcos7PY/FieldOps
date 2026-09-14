import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  AlertController,
  IonBackButton,
  IonBadge,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonSpinner,
  IonTitle,
  IonToolbar,
  ToastController,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  checkmarkCircleOutline,
  closeCircleOutline,
  refreshOutline,
  serverOutline,
  timeOutline,
  warningOutline,
} from 'ionicons/icons';
import { firstValueFrom } from 'rxjs';
import { DatabaseService } from '../../../core/services/database.service';
import { WorkOrderService } from '../../../core/services/work-order.service';
import { SyncService } from '../../../core/services/sync.service';
import { NetworkService } from '../../../core/services/network.service';
import { PendingOperation, WorkOrder } from '../../../core/models';

@Component({
  selector: 'app-conflict-detail',
  templateUrl: './conflict-detail.page.html',
  styleUrls: ['./conflict-detail.page.scss'],
  imports: [
    CommonModule,
    IonContent,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonBackButton,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardSubtitle,
    IonCardContent,
    IonBadge,
    IonButton,
    IonIcon,
    IonSpinner,
  ],
})
export class ConflictDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly db = inject(DatabaseService);
  private readonly workOrderService = inject(WorkOrderService);
  private readonly syncService = inject(SyncService);
  private readonly network = inject(NetworkService);
  private readonly alertController = inject(AlertController);
  private readonly toastController = inject(ToastController);

  readonly operation = signal<PendingOperation | null>(null);
  readonly serverOrder = signal<WorkOrder | null>(null);
  readonly parsedPayload = signal<any>(null);
  readonly loading = signal<boolean>(true);
  readonly resolving = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    addIcons({
      alertCircleOutline,
      checkmarkCircleOutline,
      closeCircleOutline,
      refreshOutline,
      serverOutline,
      timeOutline,
      warningOutline,
    });
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    const opId = idParam ? parseInt(idParam, 10) : NaN;
    if (isNaN(opId)) {
      this.errorMessage.set('Identificador de operación inválido');
      this.loading.set(false);
      return;
    }

    void this.loadDetails(opId);
  }

  async loadDetails(opId: number): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const op = await this.db.getPendingOperationById(opId);
      if (!op) {
        this.errorMessage.set('La operación en conflicto no existe o ya fue resuelta.');
        this.loading.set(false);
        return;
      }
      this.operation.set(op);

      try {
        this.parsedPayload.set(JSON.parse(op.payloadJson));
      } catch {
        this.parsedPayload.set({});
      }

      if (this.network.isOnline()) {
        try {
          const serverData = await firstValueFrom(
            this.workOrderService.getWorkOrderById(op.orderId)
          );
          this.serverOrder.set(serverData);
        } catch {
          // If server call fails, attempt to read local cached order
          const localOrder = await this.db.getLocalOrderById(op.orderId);
          if (localOrder) {
            this.serverOrder.set(localOrder as unknown as WorkOrder);
          }
        }
      } else {
        const localOrder = await this.db.getLocalOrderById(op.orderId);
        if (localOrder) {
          this.serverOrder.set(localOrder as unknown as WorkOrder);
        }
      }
    } finally {
      this.loading.set(false);
    }
  }

  canRetryWithServer(): { allowed: boolean; reason?: string } {
    const sOrder = this.serverOrder();
    const op = this.operation();
    const payload = this.parsedPayload();

    if (!sOrder || !op) {
      return { allowed: false, reason: 'Información del servidor no disponible' };
    }

    if (op.operationType === 'STATUS_CHANGE') {
      const targetStatus = payload?.newStatus;
      if (!targetStatus) {
        return { allowed: false, reason: 'Estado destino no especificado' };
      }

      if (sOrder.status === 'CANCELLED') {
        return {
          allowed: false,
          reason: 'La orden fue cancelada en el servidor y no admite modificaciones.',
        };
      }
      if (sOrder.status === 'COMPLETED') {
        return {
          allowed: false,
          reason: 'La orden ya fue completada en el servidor.',
        };
      }
      if (sOrder.status === 'ASSIGNED') {
        if (targetStatus === 'IN_PROGRESS') return { allowed: true };
        return {
          allowed: false,
          reason: 'Desde estado ASIGNADA solo se puede iniciar la orden (IN_PROGRESS).',
        };
      }
      if (sOrder.status === 'IN_PROGRESS') {
        if (targetStatus === 'COMPLETED') return { allowed: true };
        if (targetStatus === 'IN_PROGRESS') {
          return {
            allowed: false,
            reason: 'La orden ya se encuentra en progreso en el servidor.',
          };
        }
      }
    }

    return { allowed: true };
  }

  async retryWithCurrentVersion(): Promise<void> {
    const op = this.operation();
    const server = this.serverOrder();
    if (!op || !server || this.resolving()) return;

    const validation = this.canRetryWithServer();
    if (!validation.allowed) {
      const alert = await this.alertController.create({
        header: 'Acción no permitida',
        message: validation.reason,
        buttons: ['Entendido'],
      });
      await alert.present();
      return;
    }

    this.resolving.set(true);
    try {
      await this.syncService.retryConflict(op.id, server.version);
      const toast = await this.toastController.create({
        message: 'Operación reencolada con la versión actual del servidor.',
        duration: 3000,
        color: 'success',
      });
      await toast.present();
      void this.router.navigate(['/sync/conflicts']);
    } finally {
      this.resolving.set(false);
    }
  }

  async discardChange(): Promise<void> {
    const op = this.operation();
    if (!op || this.resolving()) return;

    const alert = await this.alertController.create({
      header: 'Descartar Cambio Local',
      message:
        '¿Está seguro de descartar su modificación local? Los datos de la orden se alinearán con el estado del servidor.',
      buttons: [
        { text: 'Cancelar', role: 'cancel' },
        {
          text: 'Descartar',
          role: 'destructive',
          handler: async () => {
            this.resolving.set(true);
            try {
              await this.syncService.discardConflict(op.id);
              const toast = await this.toastController.create({
                message: 'Cambio descartado y orden sincronizada.',
                duration: 3000,
                color: 'medium',
              });
              await toast.present();
              void this.router.navigate(['/sync/conflicts']);
            } finally {
              this.resolving.set(false);
            }
          },
        },
      ],
    });

    await alert.present();
  }

  keepForLater(): void {
    void this.router.navigate(['/sync/conflicts']);
  }
}
