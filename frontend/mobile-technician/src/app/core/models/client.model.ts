export interface Client {
  id: number;
  businessName: string;
  taxId: string;
  address?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  phone?: string | null;
  active: boolean;
}
