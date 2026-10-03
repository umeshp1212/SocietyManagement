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
      // If a value was written before its matching option existed, resolve its
      // display text now that the options have (re)loaded.
      if (this.selectedOption == null && this.pendingValue != null) {
        this.resolvePendingValue(false);
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
    this.resolvePendingValue();
  }

  /**
   * Resolves {@link pendingValue} against the current options and sets the display
   * text. If no match is found yet (options not loaded), it schedules a retry on the
   * next tick so the label appears as soon as the options arrive. This makes the
   * component robust to any order in which the value and the options are provided.
   */
  private resolvePendingValue(retry = true): void {
    const value = this.pendingValue;
    if (value == null) {
      return;
    }
    const match = this.findOptionByValue(value);
    if (match) {
      this.selectedOption = match;
      // Store the matched OPTION OBJECT (not its label string). MatAutocomplete's
      // [displayWith]="displayFn" turns the control value into display text, so the
      // control must hold what displayFn expects (an option), otherwise displayFn
      // would run labelWith() on a raw string and render blank.
      this.searchCtrl.setValue(match as unknown as string, { emitEvent: false });
      return;
    }
    // No match yet: retry once on the next tick, by which time late-arriving
    // options may have been bound.
    if (retry) {
      Promise.resolve().then(() => this.resolvePendingValue(false));
    }
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

  private applyFilter(text: unknown): void {
    // The bound control may hold either the search string (while typing) or the
    // selected option object (after selection / programmatic set). Only a string is
    // a real search query; anything else means "show the full list".
    const q = typeof text === 'string' ? text.toLowerCase().trim() : '';
    const all = this.options ?? [];
    this.filteredOptions = q
      ? all.filter((o) => this.labelWith(o).toLowerCase().includes(q))
      : [...all];
  }

  private isPlaceholderText(): boolean {
    // When an option is selected, the control holds the option object (not text), so
    // focusing should reveal the whole list rather than filter by a bogus query.
    const current = this.searchCtrl.value;
    if (typeof current !== 'string') {
      return true;
    }
    return !!this.selectedOption && this.labelWith(this.selectedOption) === current;
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
      return (
        all.find((o) => this.looseEquals((o as Record<string, unknown>)[this.valueKey!], value)) ??
        null
      );
    }
    return all.find((o) => this.looseEquals(o, value)) ?? null;
  }

  /**
   * Compares two option values tolerantly. Falls back to string comparison so a
   * numeric option id (e.g. 15) still matches a value written as a string ("15"),
   * which can happen when values pass through route params or serialization.
   */
  private looseEquals(a: unknown, b: unknown): boolean {
    if (a === b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return String(a) === String(b);
  }
}
