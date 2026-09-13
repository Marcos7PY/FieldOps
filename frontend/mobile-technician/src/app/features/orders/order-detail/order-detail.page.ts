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
  IonToolbar
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  businessOutline,
  calendarOutline,
  cameraOutline,
  checkmarkCircleOutline,
  cloudUploadOutline,
  navigateOutline,
  callOutline,
  playOutline,
  timeOutline
} from 'ionicons/icons';
import { WorkOrder } from '../../../core/models';
import { WorkOrderService } from '../../../core/services/work-order.service';
import { CameraService, CapturedPhoto } from '../../../core/services/camera.service';

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
    IonProgressBar
  ]
})
export class OrderDetailPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workOrderService = inject(WorkOrderService);
  private readonly alertController = inject(AlertController);
  private readonly cameraService = inject(CameraService);

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
      cloudUploadOutline,
      navigateOutline,
      callOutline,
      playOutline,
      timeOutline
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

  uploadEvidence(): void {
    const photo = this.currentPhoto();
    const ord = this.order();
    if (!photo || !ord || this.uploadingEvidence()) return;

    this.uploadingEvidence.set(true);
    this.uploadProgress.set(0);

    const filename = `evidence-${Date.now()}.${photo.format || 'jpg'}`;

    this.workOrderService.uploadEvidenceWithProgress(ord.id, photo.blob, filename).subscribe({
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
          buttons: ['Aceptar']
        });
        await alert.present();
      }
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadOrderDetail(Number(id));
    }
  }

  loadOrderDetail(id: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.workOrderService.getWorkOrderById(id).subscribe({
      next: (data) => {
        this.order.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        if (err.status === 404) {
          this.errorMessage.set('La orden solicitada no existe');
        } else {
          this.errorMessage.set('Error al cargar los detalles de la orden');
        }
      }
    });
  }

  startWork(): void {
    const current = this.order();
    if (!current || this.updatingStatus()) return;

    this.updatingStatus.set(true);
    this.workOrderService.changeStatus(current.id, {
      newStatus: 'IN_PROGRESS',
      notes: 'Inicio de labores reportado desde app móvil'
    }, current.version).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.updatingStatus.set(false);
      },
      error: async (err) => {
        this.updatingStatus.set(false);
        await this.handleStatusError(err);
      }
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
          placeholder: 'Detalle del trabajo realizado...'
        }
      ],
      buttons: [
        {
          text: 'Cancelar',
          role: 'cancel'
        },
        {
          text: 'Completar',
          handler: (data) => {
            this.completeWork(data.notes || 'Trabajo completado satisfactoriamente');
          }
        }
      ]
    });

    await alert.present();
  }

  private completeWork(notes: string): void {
    const current = this.order();
    if (!current) return;

    this.updatingStatus.set(true);
    this.workOrderService.changeStatus(current.id, {
      newStatus: 'COMPLETED',
      notes
    }, current.version).subscribe({
      next: (updated) => {
        this.order.set(updated);
        this.updatingStatus.set(false);
      },
      error: async (err) => {
        this.updatingStatus.set(false);
        await this.handleStatusError(err);
      }
    });
  }

  private async handleStatusError(err: any): Promise<void> {
    let message = 'No se pudo actualizar el estado de la orden.';
    if (err.status === 412 || err.status === 409) {
      message = 'Conflicto de concurrencia: la orden fue modificada por otro usuario. Se recargarán los datos actualizados.';
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
      buttons: ['Aceptar']
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
