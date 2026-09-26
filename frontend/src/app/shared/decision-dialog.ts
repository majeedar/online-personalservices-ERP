import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { firstValueFrom } from 'rxjs';

export interface DecisionDialogData {
  title: string;
  message?: string;
  confirmLabel: string;
  commentRequired: boolean;
  destructive?: boolean;
}

/** Confirmation with an optional or required comment (reason for rejecting/returning). */
@Component({
  selector: 'ops-decision-dialog',
  imports: [MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      @if (data.message) {
        <p>{{ data.message }}</p>
      }
      <mat-form-field appearance="outline" class="full">
        <mat-label>{{ data.commentRequired ? 'Reason (required)' : 'Comment (optional)' }}</mat-label>
        <textarea matInput [formControl]="comment" rows="3" maxlength="1000"></textarea>
        @if (comment.hasError('required')) {
          <mat-error>Please give a reason.</mat-error>
        }
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancel</button>
      <button mat-flat-button type="button" [class.destructive]="data.destructive" (click)="confirm()">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .full { width: 100%; min-width: min(420px, 80vw); }
    .destructive { background: var(--mat-sys-error); color: var(--mat-sys-on-error); }
  `,
})
export class DecisionDialog {
  protected readonly data = inject<DecisionDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<DecisionDialog, { comment?: string }>);
  protected readonly comment = new FormControl('', {
    nonNullable: true,
    validators: this.data.commentRequired ? [Validators.required] : [],
  });

  protected confirm(): void {
    if (this.comment.invalid) {
      this.comment.markAsTouched();
      return;
    }
    this.ref.close({ comment: this.comment.value.trim() || undefined });
  }
}

/** Opens the dialog; resolves to the comment result, or null if cancelled. */
export async function askDecision(dialog: MatDialog, data: DecisionDialogData): Promise<{ comment?: string } | null> {
  const ref = dialog.open(DecisionDialog, { data, autoFocus: 'first-tabbable' });
  return (await firstValueFrom(ref.afterClosed())) ?? null;
}
