import { Injectable } from '@angular/core';
import { Filesystem, Directory } from '@capacitor/filesystem';

@Injectable({
  providedIn: 'root',
})
export class EvidenceStorageService {
  async persist(orderId: number, blob: Blob, extension: string): Promise<string> {
    const base64 = await this.blobToBase64(blob);
    const cleanExt = extension.replace(/^\./, '');
    const path = `evidence/${orderId}/${crypto.randomUUID()}.${cleanExt}`;
    await Filesystem.writeFile({
      path,
      data: base64,
      directory: Directory.Data,
      recursive: true,
    });
    return path;
  }

  async readAsBlob(path: string, mimeType = 'image/jpeg'): Promise<Blob> {
    const fileResult = await Filesystem.readFile({
      path,
      directory: Directory.Data,
    });

    const base64Data = typeof fileResult.data === 'string' ? fileResult.data : '';
    return this.base64ToBlob(base64Data, mimeType);
  }

  async deleteFile(path: string): Promise<void> {
    try {
      await Filesystem.deleteFile({
        path,
        directory: Directory.Data,
      });
    } catch {
      // File may have already been removed
    }
  }

  async cleanupOrphans(activePaths: Set<string>): Promise<void> {
    try {
      const ordersDir = await Filesystem.readdir({
        path: 'evidence',
        directory: Directory.Data,
      });

      for (const orderEntry of ordersDir.files) {
        const orderPath = `evidence/${orderEntry.name}`;
        try {
          const files = await Filesystem.readdir({
            path: orderPath,
            directory: Directory.Data,
          });

          for (const f of files.files) {
            const filePath = `${orderPath}/${f.name}`;
            if (!activePaths.has(filePath)) {
              await this.deleteFile(filePath);
            }
          }
        } catch {
          // In case orderEntry is not a directory
        }
      }
    } catch {
      // Directory may not exist yet
    }
  }

  blobToBase64(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        const res = reader.result as string;
        const base64 = res.split(',')[1] || res;
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  private base64ToBlob(base64: string, mimeType: string): Blob {
    const byteCharacters = atob(base64);
    const byteNumbers = new Array(byteCharacters.length);
    for (let i = 0; i < byteCharacters.length; i++) {
      byteNumbers[i] = byteCharacters.charCodeAt(i);
    }
    const byteArray = new Uint8Array(byteNumbers);
    return new Blob([byteArray], { type: mimeType });
  }
}
