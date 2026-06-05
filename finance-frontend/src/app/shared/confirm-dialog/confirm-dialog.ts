import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatDialogModule, MatButtonModule,
            MatFormFieldModule, MatInputModule],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.scss',
})
export class ConfirmDialog {
  inputValue = '';

  constructor(
    public dialogRef: MatDialogRef<ConfirmDialog>,
    @Inject(MAT_DIALOG_DATA) public data: {
      title: string;
      message: string;
      confirmText: string;
      confirmColor: string;
      hideCancel?: boolean;
      showInput?: boolean;       // show a textarea (e.g. rejection reason)
      inputLabel?: string;
      inputRequired?: boolean;
    }
  ) {}

  confirm(): void {
    this.dialogRef.close({ confirmed: true, inputValue: this.inputValue });
  }

  cancel(): void {
    this.dialogRef.close({ confirmed: false });
  }
}
