import { ChangeDetectionStrategy, Component, DestroyRef, OnChanges, SimpleChanges, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from 'primeng/button';
import { FileUploadModule, FileUploadHandlerEvent } from 'primeng/fileupload';
import { ApiService } from '../../core/services/api.service';
import { UiService } from '../../core/services/ui.service';
import { Attachment, AttachmentOwnerType } from '../models/models';

/** Matches the backend's whitelist and cap (AttachmentServiceImpl). */
const ACCEPTED_TYPES = 'application/pdf,image/jpeg,image/png';
const MAX_SIZE_BYTES = 10 * 1024 * 1024;

/**
 * Proof files for one business document (FE-05): upload, open, remove.
 *
 * <p>Shown inside the four forms in place of the old attachmentName / attachmentUrl text
 * inputs. A file can only be attached to a saved document, so the panel explains itself when
 * the form is creating a new one.</p>
 */
@Component({
  selector: 'app-attachments-panel',
  standalone: true,
  imports: [CommonModule, ButtonModule, FileUploadModule],
  templateUrl: './attachments-panel.component.html',
  styleUrl: './attachments-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AttachmentsPanelComponent implements OnChanges {
  private readonly api = inject(ApiService);
  private readonly ui = inject(UiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly ownerType = input.required<AttachmentOwnerType>();
  /** Null while the parent form is creating the document: nothing to attach to yet. */
  readonly ownerId = input<number | null>(null);

  readonly attachments = signal<Attachment[]>([]);
  readonly uploading = signal(false);
  readonly acceptedTypes = ACCEPTED_TYPES;
  readonly maxSizeBytes = MAX_SIZE_BYTES;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['ownerId'] || changes['ownerType']) {
      this.reload();
    }
  }

  reload(): void {
    const ownerId = this.ownerId();
    if (!ownerId) {
      this.attachments.set([]);
      return;
    }
    this.api.getAttachments(this.ownerType(), ownerId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (data) => this.attachments.set(data) });
  }

  /** PrimeNG hands over the chosen files; each goes to the backend as its own request. */
  onUpload(event: FileUploadHandlerEvent): void {
    const ownerId = this.ownerId();
    if (!ownerId) return;
    const files = Array.from(event.files ?? []);
    if (!files.length) return;

    this.uploading.set(true);
    let pending = files.length;
    for (const file of files) {
      this.api.uploadAttachment(this.ownerType(), ownerId, file).subscribe({
        next: (stored) => {
          this.attachments.update((current) => [stored, ...current]);
          this.ui.success('Pièce jointe ajoutée', `${stored.originalName} a été enregistrée.`);
        },
        // The HTTP error interceptor already shows the backend's reason (FE-02).
        complete: () => this.done(--pending),
        error: () => this.done(--pending)
      });
    }
  }

  private done(pending: number): void {
    if (pending <= 0) this.uploading.set(false);
  }

  open(attachment: Attachment): void {
    window.open(this.api.attachmentUrl(attachment.id), '_blank', 'noopener');
  }

  remove(attachment: Attachment): void {
    this.ui.confirmDelete(`Supprimer la pièce jointe ${attachment.originalName} ?`, () => {
      this.api.deleteAttachment(attachment.id).subscribe({
        next: () => {
          this.attachments.update((current) => current.filter((item) => item.id !== attachment.id));
          this.ui.success('Pièce jointe supprimée', `${attachment.originalName} a été supprimée.`);
        }
      });
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} o`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} Ko`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
  }
}
