import { Injectable, inject } from '@angular/core';
import { ConfirmationService, MessageService } from 'primeng/api';

@Injectable({ providedIn: 'root' })
export class UiService {
  private readonly messages = inject(MessageService);
  private readonly confirmations = inject(ConfirmationService);

  success(summary: string, detail: string): void {
    this.messages.add({ severity: 'success', summary, detail, life: 3000 });
  }

  error(summary: string, detail: string): void {
    this.messages.add({ severity: 'error', summary, detail, life: 4000 });
  }

  info(summary: string, detail: string): void {
    this.messages.add({ severity: 'info', summary, detail, life: 3000 });
  }

  confirmDelete(message: string, accept: () => void): void {
    this.confirmations.confirm({
      header: 'Êtes-vous sûr ?',
      message,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Oui, supprimer',
      rejectLabel: 'Annuler',
      acceptButtonStyleClass: 'p-button-danger',
      accept
    });
  }
}
