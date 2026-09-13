import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { AnalyticsService, AuthService } from '../../core/services';
import {
  DailyMetricItem,
  DailyMetricsResponse,
  RebuildProjectionResponse,
  TechnicianMetricItem,
  TechnicianMetricsResponse
} from '../../core/models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly analyticsService = inject(AnalyticsService);
  readonly authService = inject(AuthService);

  readonly loading = signal<boolean>(true);
  readonly rebuilding = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly rebuildMessage = signal<string | null>(null);

  readonly dailyMetrics = signal<DailyMetricsResponse | null>(null);
  readonly techMetrics = signal<TechnicianMetricsResponse | null>(null);

  readonly totalOrders = computed(() => this.dailyMetrics()?.totalOrders ?? 0);
  readonly avgDuration = computed(() => {
    const val = this.dailyMetrics()?.overallAvgDurationMinutes;
    return val !== undefined && val !== null ? Number(val).toFixed(1) : '0.0';
  });

  readonly totalCompletedOrders = computed(() => {
    const list: TechnicianMetricItem[] = this.techMetrics()?.technicians ?? [];
    return list.reduce((acc: number, curr: TechnicianMetricItem) => acc + curr.completedOrders, 0);
  });

  readonly totalInProgressOrders = computed(() => {
    const list: TechnicianMetricItem[] = this.techMetrics()?.technicians ?? [];
    return list.reduce((acc: number, curr: TechnicianMetricItem) => acc + curr.inProgressOrders, 0);
  });

  readonly activeTechniciansCount = computed(() => {
    return this.techMetrics()?.technicians.length ?? 0;
  });

  readonly dailyChartData = computed(() => {
    const metrics: DailyMetricItem[] = this.dailyMetrics()?.metrics ?? [];
    const grouped = new Map<string, { date: string; completed: number; inProgress: number; other: number; total: number }>();

    for (const item of metrics) {
      const dateKey = item.metricDate;
      const current = grouped.get(dateKey) || { date: dateKey, completed: 0, inProgress: 0, other: 0, total: 0 };

      if (item.status === 'COMPLETED') {
        current.completed += item.orderCount;
      } else if (item.status === 'IN_PROGRESS') {
        current.inProgress += item.orderCount;
      } else {
        current.other += item.orderCount;
      }
      current.total += item.orderCount;
      grouped.set(dateKey, current);
    }

    const items = Array.from(grouped.values()).slice(-10);
    const maxTotal = Math.max(1, ...items.map(i => i.total));

    return items.map(item => ({
      ...item,
      completedHeight: Math.round((item.completed / maxTotal) * 160),
      inProgressHeight: Math.round((item.inProgress / maxTotal) * 160),
      otherHeight: Math.round((item.other / maxTotal) * 160)
    }));
  });

  readonly maxTechOrders = computed(() => {
    const list: TechnicianMetricItem[] = this.techMetrics()?.technicians ?? [];
    const max = Math.max(1, ...list.map((t: TechnicianMetricItem) => Math.max(t.completedOrders, t.assignedOrders, t.inProgressOrders)));
    return max;
  });

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    forkJoin({
      daily: this.analyticsService.getDailyMetrics(),
      techs: this.analyticsService.getTechnicianMetrics()
    }).subscribe({
      next: ({ daily, techs }: { daily: DailyMetricsResponse; techs: TechnicianMetricsResponse }) => {
        this.dailyMetrics.set(daily);
        this.techMetrics.set(techs);
        this.loading.set(false);
      },
      error: () => {
        this.errorMessage.set('No se pudieron cargar las métricas de analítica.');
        this.loading.set(false);
      }
    });
  }

  rebuildProjection(): void {
    if (this.rebuilding()) {
      return;
    }

    this.rebuilding.set(true);
    this.rebuildMessage.set(null);
    this.errorMessage.set(null);

    this.analyticsService.rebuildProjection().subscribe({
      next: (res: RebuildProjectionResponse) => {
        this.rebuilding.set(false);
        this.rebuildMessage.set(`Reconstrucción iniciada: ${res.message}`);
        setTimeout(() => this.loadDashboardData(), 3000);
      },
      error: (err: HttpErrorResponse) => {
        this.rebuilding.set(false);
        if (err.status === 403) {
          this.errorMessage.set('Solo usuarios con rol SUPERVISOR pueden reconstruir la proyección.');
        } else {
          this.errorMessage.set('Error al solicitar la reconstrucción de la proyección histórica.');
        }
      }
    });
  }

  getBarWidthPercent(value: number): number {
    const max = this.maxTechOrders();
    return Math.min(100, Math.round((value / max) * 100));
  }
}
