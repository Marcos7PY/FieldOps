export interface Evidence {
  id: number;
  workOrderId: number;
  filePath: string;
  contentType: string;
  sizeBytes: number;
  latitude?: number | null;
  longitude?: number | null;
  capturedAt?: string | null;
  uploadedAt: string;
}
