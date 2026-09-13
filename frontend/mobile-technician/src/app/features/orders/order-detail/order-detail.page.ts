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
  IonProgressBar,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import { WorkOrder } from '../../../core/models';
import { WorkOrderService } from '../../../core/services/work-order.service';
import { CameraService, CapturedPhoto } from '../../../core/services/camera.service';
import { GeolocationService } from '../../../core/services/geolocation.service';
import { NetworkService } from '../../../core/services/network.service';
import { DatabaseService } from '../../../core/services/database.service';
import { OfflineQueueService } from '../../../core/services/offline-queue.service';
import {
  businessOutline,
  calendarOutline,
  cameraOutline,
  checkmarkCircleOutline,
  cloudOfflineOutline,
  cloudUploadOutline,
  navigateOutline,
  callOutline,
  playOutline,
  syncOutline,
  timeOutline,
} from 'ionicons/icons';

@Component({
  selector: 'app-order-detail',
  templateUrl: './order-detail.page.html',
  styleUrls: ['./order-detail.page.scss'],
  imports: [
    CommonModule,
    IonContent,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonBackButton,
    IonButton,
    IonIcon,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardSubtitle,
    IonCardContent,
    IonBadge,
    IonList,
    IonItem,
    IonLabel,
    IonSpinner,
    IonProgressBar,
  ],
})
export class OrderDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workOrderService = inject(WorkOrderService);
  private readonly alertController = inject(AlertController);
  private readonly cameraService = inject(CameraService);
  private readonly geolocationService = inject(GeolocationService);
  readonly network = inject(NetworkService);
  readonly db = inject(DatabaseService);
  readonly offlineQueue = inject(OfflineQueueService);

  readonly order = signal<WorkOrder | null>(null);
  readonly loading = signal(true);
  readonly updatingStatus = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly currentPhoto = signal<CapturedPhoto | null>(null);
  readonly uploadingEvidence = signal<boolean>(false);
  readonly uploadProgress = signal<number | null>(null);

  constructor() {
    addIcons({
      businessOutline,
      calendarOutline,
      cameraOutline,
      checkmarkCircleOutline,
      cloudOfflineOutline,
      cloudUploadOutline,
      navigateOutline,
      callOutline,
      playOutline,
      syncOutline,
      timeOutline,
    });
  }

  async capturePhoto(): Promise<void> {
    const photo = await this.cameraService.takePhoto();
    if (photo) {
      this.currentPhoto.set(photo);
    }
  }

  clearPhoto(): void {
    this.currentPhoto.set(null);
    this.uploadProgress.set(null);
  }

  async uploadEvidence(): Promise<void> {
    const photo = this.currentPhoto();
    const ord = this.order();
    if (!photo || !ord || this.uploadingEvidence()) return;

    this.uploadingEvidence.set(true);
    this.uploadProgress.set(0);

    const coords = await this.geolocationService.getCurrentPosition();
    const metadata = {
      latitude: coords?.latitude,
      longitude: coords?.longitude,
      capturedAt: new Date().toISOString(),
    };

    const filename = `evidence-${Date.now()}.${photo.format || 'jpg'}`;

    this.workOrderService
      .uploadEvidenceWithProgress(ord.id, photo.blob, filename, metadata)
      .subscribe({
        next: (state) => {
          this.uploadProgress.set(state.progress);
          if (state.response) {
            const newEvidence = state.response;
            const updatedEvidences = [...(ord.evidences || []), newEvidence];
            this.order.set({ ...ord, evidences: updatedEvidences });
            this.uploadingEvidence.set(false);
            this.uploadProgress.set(null);
            this.currentPhoto.set(null);
          }
        },
        error: async () => {
          this.uploadingEvidence.set(false);
          this.uploadProgress.set(null);
          const alert = await this.alertController.create({
            header: 'Error de Subida',
            message: 'No fue posible subir la evidencia fotográfica. Intente nuevamente.',
            buttons: ['Aceptar'],
          });
          await alert.present();
        },
      });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadOrderDetail(Number(id));
    }
  }

  async loadOrderDetail(id: number): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);

    if (!this.network.isOnline()) {
      const local = await this.db.getLocalOrderById(id);
      if (local) {
        this.order.set(this.mapLocalToWorkOrder(local));
      } else {
        this.errorMessage.set('Sin conexión. No hay datos locales para esta orden.');
      }
      this.loading.set(false);
      return;
    }

    this.workOrderService.getWorkOrderById(id).subscribe({
      next: (data) => {
        this.order.set(data);
        this.loading.set(false);
      },
      error: async (err) => {
        const local = await this.db.getLocalOrderById(id);
        if (local) {
          this.order.set(this.mapLocalToWorkOrder(local));
          this.errorMessage.set(null);
        } else if (err.status === 404) {
          this.errorMessage.set('La orden solicitada no existe');
        } else {
          this.errorMessage.set('Error al cargar los detalles de la orden');
        }
        this.loading.set(false);
      },
    });
  }

  async startWork(): Promise<void> {
    const current = this.order();
    if (!current || this.updatingStatus()) return;

    this.updatingStatus.set(true);

    if (!this.network.isOnline()) {
      await this.offlineQueue.queueStatusChange(
        current.id,
        'IN_PROGRESS',
        'Inicio de labores reportado sin conexión',
        current.version
      );
      this.order.set({ ...current, status: 'IN_PROGRESS', version: current.version + 1 });
      this.updatingStatus.set(false);
      const alert = await this.alertController.create({
        header: 'Guardado Sin Conexión',
        message:
          'El inicio de labores se registró localmente. Se sincronizará automáticamente al recuperar la conexión.',
        buttons: ['Entendido'],
      });
      await alert.present();
      return;
    }

    this.workOrderService
      .changeStatus(
        current.id,
        {
          newStatus: 'IN_PROGRESS',
          notes: 'Inicio de labores reportado desde app móvil',
        },
        current.version
      )
      .subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.updatingStatus.set(false);
        },
        error: async (err) => {
          if (err.status === 0) {
            await this.offlineQueue.queueStatusChange(
              current.id,
              'IN_PROGRESS',
              'Inicio de labores reportado tras pérdida de red',
              current.version
            );
            this.order.set({ ...current, status: 'IN_PROGRESS', version: current.version + 1 });
            this.updatingStatus.set(false);
            const alert = await this.alertController.create({
              header: 'Guardado Local',
              message:
                'Se perdió la conexión. La operación fue guardada en la cola de sincronización.',
              buttons: ['Entendido'],
            });
            await alert.present();
          } else {
            this.updatingStatus.set(false);
            await this.handleStatusError(err);
          }
        },
      });
  }

  async promptCompleteWork(): Promise<void> {
    const current = this.order();
    if (!current || this.updatingStatus()) return;

    const alert = await this.alertController.create({
      header: 'Completar Orden',
      subHeader: 'Ingrese las notas finales de servicio',
      inputs: [
        {
          name: 'notes',
          type: 'textarea',
        },
      ],
      buttons: [
        {
          text: 'Cancelar',
          role: 'cancel',
        },
        {
          text: 'Completar',
          handler: (data) => {
            this.completeWork(data.notes || 'Trabajo completado satisfactoriamente');
          },
        },
      ],
    });

    await alert.present();
  }

  private async completeWork(notes: string): Promise<void> {
    const current = this.order();
    if (!current) return;

    this.updatingStatus.set(true);

    const coords = await this.geolocationService.getCurrentPosition();
    const finalNotes = coords
      ? `${notes} [GPS: ${coords.latitude.toFixed(6)}, ${coords.longitude.toFixed(6)}]`
      : notes;

    if (!this.network.isOnline()) {
      await this.offlineQueue.queueStatusChange(
        current.id,
        'COMPLETED',
        finalNotes,
        current.version
      );
      this.order.set({ ...current, status: 'COMPLETED', version: current.version + 1 });
      this.updatingStatus.set(false);
      const alert = await this.alertController.create({
        header: 'Guardado Sin Conexión',
        message:
          'El cierre de orden se guardó localmente. Se sincronizará automáticamente al recuperar la conexión.',
        buttons: ['Entendido'],
      });
      await alert.present();
      return;
    }

    this.workOrderService
      .changeStatus(
        current.id,
        {
          newStatus: 'COMPLETED',
          notes: finalNotes,
        },
        current.version
      )
      .subscribe({
        next: (updated) => {
          this.order.set(updated);
          this.updatingStatus.set(false);
        },
        error: async (err) => {
          if (err.status === 0) {
            await this.offlineQueue.queueStatusChange(
              current.id,
              'COMPLETED',
              finalNotes,
              current.version
            );
            this.order.set({ ...current, status: 'COMPLETED', version: current.version + 1 });
            this.updatingStatus.set(false);
            const alert = await this.alertController.create({
              header: 'Guardado Local',
              message:
                'Se perdió la conexión. El cierre fue guardado en la cola de sincronización.',
              buttons: ['Entendido'],
            });
            await alert.present();
          } else {
            this.updatingStatus.set(false);
            await this.handleStatusError(err);
          }
        },
      });
  }

  private mapLocalToWorkOrder(local: any): WorkOrder {
    return {
      id: local.id,
      code: local.code,
      title: local.title,
      description: local.description,
      status: local.status,
      priority: local.priority,
      client: {
        id: local.clientId,
        businessName: local.clientName,
        taxId: '',
        address: local.clientAddress,
        phone: local.clientPhone,
        latitude: local.clientLatitude,
        longitude: local.clientLongitude,
        active: true,
      },
      assignedTechnicianId: local.assignedTechnicianId,
      createdBy: 1,
      createdAt: local.createdAt,
      scheduledAt: local.scheduledAt,
      startedAt: local.startedAt,
      completedAt: local.completedAt,
      version: local.version,
      evidences: [],
      statusHistory: [],
    };
  }

  private async handleStatusError(err: any): Promise<void> {
    let message = 'No se pudo actualizar el estado de la orden.';
    if (err.status === 412 || err.status === 409) {
      message =
        'Conflicto de concurrencia: la orden fue modificada por otro usuario. Se recargarán los datos actualizados.';
      const current = this.order();
      if (current) {
        this.loadOrderDetail(current.id);
      }
    } else if (err.error?.detail) {
      message = err.error.detail;
    }

    const alert = await this.alertController.create({
      header: 'Error de actualización',
      message,
      buttons: ['Aceptar'],
    });
    await alert.present();
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'ASSIGNED':
        return 'warning';
      case 'IN_PROGRESS':
        return 'primary';
      case 'COMPLETED':
        return 'success';
      case 'CANCELLED':
        return 'danger';
      default:
        return 'medium';
    }
  }

  getPriorityColor(priority: string): string {
    switch (priority) {
      case 'CRITICAL':
        return 'danger';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'secondary';
      default:
        return 'medium';
    }
  }
}
