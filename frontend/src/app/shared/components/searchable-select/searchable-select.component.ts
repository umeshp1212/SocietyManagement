import { CommonModule } from '@angular/common';
import {
  Component,
  forwardRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import {
  ControlValueAccessor,
  FormControl,
  NG_VALUE_ACCESSOR,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

/**
 * Reusable type-ahead (searchable) dropdown.
 *
 * <p>A drop-in replacement for a plain {@code <mat-select>} that adds client-side
 * text search over the options. Built on Material's {@code MatAutocomplete} so it
 * needs no third-party dependency and matches the existing member-login pattern.
 *
 * <p>Implements {@link ControlValueAccessor} so it works with BOTH reactive forms
 * ({@code formControlName}) and template-driven forms ({@code [(ngModel)]}). The
 * model value is, by default, the option's {@code valueKey} (a primitive id), which
 * matches how the app binds {@code <mat-select>} today (e.g. {@code unit.unitId}).
 *
 * Usage (reactive):
 * ```html
 * <app-searchable-select formControlName="unitId" label="Select Unit *"
 *     [options]="units" valueKey="unitId" [labelWith]="unitLabel"
 *     [required]="true" [errorText]="unitError"></app-searchable-select>
 * ```
 *
 * Usage (template-driven):
 * ```html
 * <app-searchable-select [(ngModel)]="form.unitId" label="Unit"
 *     [options]="units" valueKey="unitId" [labelWith]="unitLabel"></app-searchable-select>
 * ```
 */
@Component({
  selector: 'app-searchable-select',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatIconModule,
  ],
  template: `
    <mat-form-field appearance="outline" class="ss-field">
      <mat-label>{{ label }}</mat-label>
      <input
        matInput
        type="text"
        [placeholder]="placeholder"
        [formControl]="searchCtrl"
        [matAutocomplete]="auto"
        (focus)="onFocus()"
        (blur)="onBlur()"
      />
      <mat-icon matPrefix *ngIf="prefixIcon">{{ prefixIcon }}</mat-icon>
      <mat-autocomplete
        #auto="matAutocomplete"
        [displayWith]="displayFn"
        (optionSelected)="onOptionSelected($event.option.value)"
      >
        <mat-option *ngIf="allowClear" [value]="null">{{ clearLabel }}</mat-option>
        <mat-option *ngFor="let opt of filteredOptions" [value]="opt">
          {{ labelWith(opt) }}
        </mat-option>
        <mat-option *ngIf="filteredOptions.length === 0" [disabled]="true">
          {{ noResultsText }}
        </mat-option>
      </mat-autocomplete>
      <mat-error *ngIf="required && showError">{{ errorText }}</mat-error>
    </mat-form-field>
  `,
  styles: [
    `
      .ss-field {
        width: 100%;
      }
    `,
  ],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SearchableSelectComponent),
      multi: true,
    },
  ],
})
export class SearchableSelectComponent<T = unknown> implements ControlValueAccessor, OnChanges {
  /** Field label shown above the input. */
  @Input() label = '';

  /** Placeholder text shown in the input. */
  @Input() placeholder = 'Type to search...';

  /** Optional Material icon name rendered as a prefix. */
  @Input() prefixIcon = '';

  /** The full list of options to search over. */
  @Input() options: T[] = [];

  /**
   * Property name on an option whose value is used as the model value (typically a
   * primitive id, e.g. 'unitId'). When null/empty the WHOLE option object is used
   * as the model value.
   */
  @Input() valueKey: string | null = null;

  /** Produces the display text for an option. */
  @Input() labelWith: (option: T) => string = (o) => String(o ?? '');

  /** Whether an explicit "clear" option is offered at the top of the list. */
  @Input() allowClear = false;

  /** Label for the clear option. */
  @Input() clearLabel = '-- None --';

  /** Text shown when no option matches the search. */
  @Input() noResultsText = 'No matches found';

  /** Whether the field is required (drives error display). */
  @Input() required = false;

  /** Error message shown when required and empty after being touched. */
  @Input() errorText = 'This field is required';

  @ViewChild(MatAutocompleteTrigger) private trigger?: MatAutocompleteTrigger;

  /** The text-search control that backs the input. */
  readonly searchCtrl = new FormControl<string>('', { nonNullable: true });

  filteredOptions: T[] = [];

  /** The currently selected option object (kept so displayWith/reset work). */
  private selectedOption: T | null = null;
  private touched = false;
  private disabled = false;

  // ControlValueAccessor callbacks
  private onChange: (value: unknown) => void = () => {};
  private onTouched: () => void = () => {};

  constructor() {
    this.searchCtrl.valueChanges.subscribe((text) => {
      // While the user types (text is a string), filter. When an option object is
      // chosen, displayWith turns it back into text; we don't re-filter on that.
      if (typeof text === 'string') {
        this.applyFilter(text);
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['options']) {
      this.filteredOptions = [...(this.options ?? [])];
      // If a value was written before options arrived, resolve its display text now.
      if (this.selectedOption == null && this.pendingValue !== undefined) {
        this.writeValue(this.pendingValue);
      }
    }
  }

  // ---- ControlValueAccessor ----

  private pendingValue: unknown = undefined;

  writeValue(value: unknown): void {
    this.pendingValue = value;
    if (value == null) {
      this.selectedOption = null;
      this.searchCtrl.setValue('', { emitEvent: false });
      return;
    }
    const match = this.findOptionByValue(value);
    this.selectedOption = match;
    // Set the input text to the matched option's label (or blank until options load).
    this.searchCtrl.setValue(match ? this.labelWith(match) : '', { emitEvent: false });
  }

  registerOnChange(fn: (value: unknown) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.searchCtrl.disable({ emitEvent: false });
    } else {
      this.searchCtrl.enable({ emitEvent: false });
    }
  }

  // ---- Template handlers ----

  displayFn = (option: T | null): string => (option ? this.labelWith(option) : '');

  onOptionSelected(option: T | null): void {
    this.selectedOption = option;
    const modelValue = this.toModelValue(option);
    this.pendingValue = modelValue;
    this.onChange(modelValue);
  }

  onFocus(): void {
    // Show the full list on focus so it behaves like a dropdown, not just search.
    if (!this.disabled) {
      this.applyFilter(this.isPlaceholderText() ? '' : this.searchCtrl.value);
    }
  }

  onBlur(): void {
    if (!this.touched) {
      this.touched = true;
      this.onTouched();
    }
    // If the user typed free text that matches nothing, snap back to the selected
    // option's label (or empty) so the input never shows an invalid value.
    const current = this.searchCtrl.value;
    if (typeof current === 'string') {
      const stillValid = this.selectedOption && this.labelWith(this.selectedOption) === current;
      if (!stillValid) {
        this.searchCtrl.setValue(this.selectedOption ? this.labelWith(this.selectedOption) : '', {
          emitEvent: false,
        });
      }
    }
  }

  get showError(): boolean {
    return this.touched && this.selectedOption == null;
  }

  // ---- Helpers ----

  private applyFilter(text: string): void {
    const q = (text ?? '').toLowerCase().trim();
    const all = this.options ?? [];
    this.filteredOptions = q
      ? all.filter((o) => this.labelWith(o).toLowerCase().includes(q))
      : [...all];
  }

  private isPlaceholderText(): boolean {
    // When an option is selected, the input holds its label; focusing should still
    // reveal the whole list, so treat a fully-matching label as "show all".
    return (
      !!this.selectedOption && this.labelWith(this.selectedOption) === this.searchCtrl.value
    );
  }

  private toModelValue(option: T | null): unknown {
    if (option == null) {
      return null;
    }
    if (this.valueKey) {
      return (option as Record<string, unknown>)[this.valueKey];
    }
    return option;
  }

  private findOptionByValue(value: unknown): T | null {
    const all = this.options ?? [];
    if (this.valueKey) {
      return all.find((o) => (o as Record<string, unknown>)[this.valueKey!] === value) ?? null;
    }
    return all.find((o) => o === value) ?? null;
  }
}
