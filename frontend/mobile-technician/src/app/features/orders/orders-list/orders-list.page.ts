import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import {
  IonBadge,
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonIcon,
  IonInfiniteScroll,
  IonInfiniteScrollContent,
  IonItem,
  IonList,
  IonRefresher,
  IonRefresherContent,
  IonSpinner,
  IonTitle,
  IonToolbar
} from '@ionic/angular';
import { addIcons } from 'ionicons';
import {
  businessOutline,
  calendarOutline,
  chevronForwardOutline,
  logOutOutline,
  refreshOutline
} from 'ionicons/icons';
import { WorkOrderSummary } from '../../../core/models';
import { WorkOrderService } from '../../../core/services/work-order.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-orders-list',
  templateUrl: './orders-list.page.html',
  styleUrls: ['./orders-list.page.scss'],
  imports: [
    CommonModule,
    IonContent,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonButtons,
    IonButton,
    IonIcon,
    IonList,
    IonItem,
    IonBadge,
    IonRefresher,
    IonRefresherContent,
    IonInfiniteScroll,
    IonInfiniteScrollContent,
    IonSpinner
  ]
})
export class OrdersListPage implements OnInit {
  private readonly workOrderService = inject(WorkOrderService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly orders = signal<WorkOrderSummary[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly currentPage = signal(0);
  readonly isLastPage = signal(false);

  constructor() {
    addIcons({
      businessOutline,
      calendarOutline,
      chevronForwardOutline,
      logOutOutline,
      refreshOutline
    });
  }

  ngOnInit(): void {
    this.loadOrders(0, false);
  }

  loadOrders(page: number, append: boolean, event?: CustomEvent): void {
    if (!append) {
      this.loading.set(true);
      this.errorMessage.set(null);
    }

    this.workOrderService.getAssignedWorkOrders(page, 20).subscribe({
      next: (response) => {
        this.loading.set(false);
        this.currentPage.set(response.page);
        this.isLastPage.set(response.last);

        if (append) {
          this.orders.update((prev) => [...prev, ...response.content]);
        } else {
          this.orders.set(response.content);
        }

        if (event) {
          (event.target as HTMLIonRefresherElement | HTMLIonInfiniteScrollElement)?.complete();
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set('Error al cargar las órdenes asignadas');
        if (event) {
          (event.target as HTMLIonRefresherElement | HTMLIonInfiniteScrollElement)?.complete();
        }
      }
    });
  }

  onRefresh(event: CustomEvent): void {
    this.loadOrders(0, false, event);
  }

  onInfiniteScroll(event: CustomEvent): void {
    if (this.isLastPage()) {
      (event.target as HTMLIonInfiniteScrollElement).complete();
      return;
    }
    this.loadOrders(this.currentPage() + 1, true, event);
  }

  goToDetail(orderId: number): void {
    this.router.navigate(['/orders', orderId]);
  }

  logout(): void {
    this.authService.logout().subscribe({
      complete: () => {
        this.router.navigate(['/login']);
      }
    });
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
