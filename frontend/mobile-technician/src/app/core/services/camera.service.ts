import { Injectable, inject } from '@angular/core';
import { Camera, CameraResultType, CameraSource, PermissionStatus } from '@capacitor/camera';
import { AlertController } from '@ionic/angular';

export interface CapturedPhoto {
  blob: Blob;
  format: string;
  webPath: string;
}

@Injectable({
  providedIn: 'root'
})
export class CameraService {
  private readonly alertController = inject(AlertController);

  async takePhoto(): Promise<CapturedPhoto | null> {
    const hasPermission = await this.verifyCameraPermission();
    if (!hasPermission) {
      await this.showPermissionAlert();
      return null;
    }

    try {
      const image = await Camera.getPhoto({
        quality: 85,
        allowEditing: false,
        resultType: CameraResultType.Uri,
        source: CameraSource.Camera
      });

      if (!image.webPath) {
        return null;
      }

      const response = await fetch(image.webPath);
      const blob = await response.blob();

      return {
        blob,
        format: image.format,
        webPath: image.webPath
      };
    } catch (error: unknown) {
      // User cancelled camera dialog or closed camera app
      const msg = error instanceof Error ? error.message : String(error);
      if (!msg.toLowerCase().includes('cancel') && !msg.toLowerCase().includes('closed')) {
        await this.showCameraErrorAlert();
      }
      return null;
    }
  }

  private async verifyCameraPermission(): Promise<boolean> {
    try {
      let status: PermissionStatus = await Camera.checkPermissions();

      if (status.camera === 'denied') {
        return false;
      }

      if (status.camera !== 'granted') {
        status = await Camera.requestPermissions({ permissions: ['camera'] });
      }

      return status.camera === 'granted';
    } catch {
      return false;
    }
  }

  private async showPermissionAlert(): Promise<void> {
    const alert = await this.alertController.create({
      header: 'Permiso Denegado',
      subHeader: 'Acceso a la cámara requerido',
      message: 'Se requiere permiso de acceso a la cámara para capturar evidencias fotográficas del servicio. Por favor, habilite el permiso en la configuración de su dispositivo.',
      buttons: ['Entendido']
    });
    await alert.present();
  }

  private async showCameraErrorAlert(): Promise<void> {
    const alert = await this.alertController.create({
      header: 'Error de Cámara',
      message: 'No fue posible acceder a la cámara en este momento. Intente nuevamente.',
      buttons: ['Aceptar']
    });
    await alert.present();
  }
}
