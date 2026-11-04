import { Component } from '@angular/core';

/** The privacy text (L01–L03), used on the Privacy page and before connecting WebUntis. */
@Component({
  selector: 'app-privacy-info',
  templateUrl: './privacy-info.html',
  styles: `
    :host { display: block; }
    h2 { font-size: 1.05rem; margin: 1.1rem 0 0.35rem; }
    p, li { margin: 0.3rem 0; }
    ul { padding-left: 1.2rem; }
  `,
})
export class PrivacyInfo {}
