import { Component, inject, effect, OnInit } from '@angular/core';
import { AlertController, IonApp, IonRouterOutlet } from '@ionic/angular';
import { SyncService } from './core/services/sync.service';
import { DatabaseService } from './core/services/database.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  imports: [IonApp, IonRouterOutlet],
})
export class AppComponent implements OnInit {
  private readonly syncService = inject(SyncService);
  private readonly db = inject(DatabaseService);
  private readonly alertController = inject(AlertController);
  private hasShownStorageAlert = false;

  constructor() {
    effect(() => {
      const mode = this.db.storageMode();
      if (mode === 'failed' && !this.hasShownStorageAlert) {
        this.hasShownStorageAlert = true;
        void this.showStorageUnavailableAlert();
      }
    });
  }

  async ngOnInit(): Promise<void> {
    try {
      await this.db.initialize();
    } catch {
      // Alert will be handled by the effect
    }
  }

  private async showStorageUnavailableAlert(): Promise<void> {
    const alert = await this.alertController.create({
      header: 'Almacenamiento No Disponible',
      message:
        'Almacenamiento local no disponible: no se pueden registrar operaciones sin conexión',
      backdropDismiss: false,
      buttons: [],
    });
    await alert.present();
  }
}
