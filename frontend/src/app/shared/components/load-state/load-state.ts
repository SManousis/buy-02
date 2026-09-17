import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * The loading spinner and the "could not be loaded" retry panel shown by list
 * and cart pages while their data request is pending or after it failed.
 * Styling comes from the global .state-center and .empty-dash rules.
 */
@Component({
  selector: 'app-load-state',
  standalone: false,
  templateUrl: './load-state.html',
  styles: [':host { display: contents; }'],
})
export class LoadState {
  @Input({ required: true }) loading = false;
  @Input({ required: true }) error = false;
  @Input({ required: true }) errorTitle = '';
  @Output() retry = new EventEmitter<void>();
}
