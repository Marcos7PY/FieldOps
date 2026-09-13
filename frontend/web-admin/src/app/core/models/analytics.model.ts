export interface DailyMetricItem {
  metricDate: string;
  technicianId?: number | null;
  status: string;
  orderCount: number;
  avgDurationMinutes?: number | null;
  updatedAt: string;
}

export interface DailyMetricsResponse {
  from: string;
  to: string;
  totalOrders: number;
  overallAvgDurationMinutes?: number | null;
  metrics: DailyMetricItem[];
}

export interface TechnicianMetricItem {
  technicianId: number;
  completedOrders: number;
  assignedOrders: number;
  inProgressOrders: number;
  avgDurationMinutes?: number | null;
}

export interface TechnicianMetricsResponse {
  technicians: TechnicianMetricItem[];
}

export interface RebuildProjectionResponse {
  status: string;
  message: string;
  initiatedAt: string;
}
