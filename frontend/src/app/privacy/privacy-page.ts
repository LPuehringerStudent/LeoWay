import { Component } from '@angular/core';
import { PrivacyInfo } from './privacy-info';

@Component({
  selector: 'app-privacy-page',
  imports: [PrivacyInfo],
  template: `<h1>Privacy</h1><app-privacy-info />`,
  styles: `h1 { font-size: 1.4rem; margin: 0.25rem 0 0.5rem; }`,
})
export class PrivacyPage {}
