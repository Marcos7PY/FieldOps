import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  IonBackButton,
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonList,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  alertCircleOutline,
  chevronForwardOutline,
  documentTextOutline,
  refreshOutline,
  warningOutline,
} from 'ionicons/icons';
import { DatabaseService } from '../../../core/services/database.service';
import { PendingOperation } from '../../../core/models';

@Component({
  selector: 'app-conflict-list',
  templateUrl: './conflict-list.page.html',
  styleUrls: ['./conflict-list.page.scss'],
  imports: [
    CommonModule,
    IonContent,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonBackButton,
    IonList,
    IonItem,
    IonBadge,
    IonIcon,
    IonRefresher,
    IonRefresherContent,
    IonSpinner,
  ],
})
export class ConflictListPage implements OnInit {
  private readonly db = inject(DatabaseService);
  private readonly router = inject(Router);

  readonly conflicts = signal<PendingOperation[]>([]);
  readonly loading = signal(true);

  constructor() {
    addIcons({
      alertCircleOutline,
      chevronForwardOutline,
      documentTextOutline,
      refreshOutline,
      warningOutline,
    });
  }

  ngOnInit(): void {
    void this.loadConflicts();
  }

  async loadConflicts(event?: CustomEvent): Promise<void> {
    this.loading.set(true);
    try {
      const list = await this.db.getConflictOperations();
      this.conflicts.set(list);
    } finally {
      this.loading.set(false);
      if (event) {
        (event.target as HTMLIonRefresherElement)?.complete();
      }
    }
  }

  onRefresh(event: CustomEvent): void {
    void this.loadConflicts(event);
  }

  goToDetail(opId: number): void {
    void this.router.navigate(['/sync/conflicts', opId]);
  }

  getOperationDescription(op: PendingOperation): string {
    if (op.operationType === 'STATUS_CHANGE') {
      try {
        const payload = JSON.parse(op.payloadJson);
        return `Cambio a estado: ${payload.newStatus || 'Desconocido'}`;
      } catch {
        return 'Cambio de estado';
      }
    } else if (op.operationType === 'UPLOAD_EVIDENCE') {
      try {
        const payload = JSON.parse(op.payloadJson);
        return `Subida de evidencia: ${payload.filename || 'Foto'}`;
      } catch {
        return 'Subida de evidencia';
      }
    }
    return op.operationType;
  }

  getStatusBadgeColor(status: string): string {
    switch (status) {
      case 'CONFLICT_MANUAL_REVIEW':
        return 'danger';
      case 'FAILED_PERMANENT':
        return 'warning';
      case 'BLOCKED_BY_CONFLICT':
        return 'medium';
      default:
        return 'primary';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'CONFLICT_MANUAL_REVIEW':
        return 'Conflicto';
      case 'FAILED_PERMANENT':
        return 'Falló permanentemente';
      case 'BLOCKED_BY_CONFLICT':
        return 'Bloqueada por conflicto previo';
      default:
        return status;
    }
  }
}
