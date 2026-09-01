const minimizeSvg = `
<svg width="16" height="16" xmlns="http://www.w3.org/2000/svg" fill="none"><path d="M4 8h8" style="fill: none; stroke-width: 1.5; stroke: rgb(143, 157, 163); stroke-opacity: 1; stroke-linecap: round;"/></svg>`;

const maximizeSvg = `
<svg width="16" height="16" xmlns="http://www.w3.org/2000/svg" fill="none"><path d="M5 11V5h6M5 5l6 6" style="fill: none; stroke-width: 1.5; stroke: rgb(143, 157, 163); stroke-opacity: 1; stroke-linecap: round; stroke-linejoin: round;"/></svg>`;

import type { Theme } from '@penpot/plugin-types';
import { dragHandler } from '../drag-handler.js';
import modalCss from './plugin.modal.css?inline';
import { resizeModal } from '../create-modal.js';

const MIN_Z_INDEX = 3;
const MINIMIZED_SIZE = '40px';
const MINIMIZE_LABEL = 'Minimize plugin';
const MAXIMIZE_LABEL = 'Maximize plugin';

type ExpandedFrameStyles = {
  width: string;
  inlineSize: string;
  height: string;
  blockSize: string;
  minHeight: string;
  minBlockSize: string;
  resize: string;
  overflow: string;
};

export class PluginModalElement extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  wrapper = document.createElement('div');
  #inner = document.createElement('div');
  #dragEvents: ReturnType<typeof dragHandler> | null = null;
  #expandedFrameStyles: ExpandedFrameStyles | null = null;
  #minimizeToggle: HTMLButtonElement | null = null;

  setTheme(theme: Theme) {
    if (this.wrapper) {
      this.wrapper.setAttribute('data-theme', theme);
    }
  }

  resize(width: number, height: number) {
    if (this.wrapper) {
      resizeModal(this, width, height);

      if (this.#expandedFrameStyles) {
        this.#expandedFrameStyles.width = this.wrapper.style.width;
        this.#expandedFrameStyles.height = this.wrapper.style.height;
        if (this.#expandedFrameStyles.inlineSize) {
          this.#expandedFrameStyles.inlineSize = this.wrapper.style.width;
        }
        if (this.#expandedFrameStyles.blockSize) {
          this.#expandedFrameStyles.blockSize = this.wrapper.style.height;
        }
        this.#applyMinimizedFrameStyles();
      }
    }
  }

  disconnectedCallback() {
    this.#dragEvents?.();
  }

  calculateZIndex() {
    const modals = document.querySelectorAll<HTMLElement>('plugin-modal');

    const zIndexModals = Array.from(modals)
      .filter((modal) => modal !== this)
      .map((modal) => {
        return Number(modal.style.zIndex);
      });

    const maxZIndex = Math.max(...zIndexModals, MIN_Z_INDEX);

    this.style.zIndex = (maxZIndex + 1).toString();
  }

  connectedCallback() {
    const title = this.getAttribute('title');
    const iframeSrc = this.getAttribute('iframe-src');
    const allowDownloads = this.getAttribute('allow-downloads') || false;
    const allowClipboardRead =
      this.getAttribute('allow-clipboard-read') || false;
    const allowClipboardWrite =
      this.getAttribute('allow-clipboard-write') || false;

    if (!title || !iframeSrc) {
      throw new Error('title and iframe-src attributes are required');
    }

    if (!this.shadowRoot) {
      throw new Error('Error creating shadow root');
    }

    this.#inner.classList.add('inner');

    this.wrapper.classList.add('wrapper');
    this.wrapper.style.maxInlineSize = '90vw';
    this.wrapper.style.maxBlockSize = '90vh';

    const header = document.createElement('div');
    header.classList.add('header');

    const h1 = document.createElement('h1');
    h1.textContent = title;

    header.appendChild(h1);

    const minimizeToggle = document.createElement('button');
    minimizeToggle.setAttribute('type', 'button');
    minimizeToggle.innerHTML = minimizeSvg;
    this.#minimizeToggle = minimizeToggle;
    this.#setToggleState(true);
    minimizeToggle.addEventListener('click', () => {
      this.#toggleMinimized();
    });

    header.appendChild(minimizeToggle);

    const iframe = document.createElement('iframe');
    iframe.src = iframeSrc;

    const allowList: string[] = [];
    if (allowClipboardRead) allowList.push('clipboard-read');
    if (allowClipboardWrite) allowList.push('clipboard-write');
    iframe.allow = allowList.join('; ');

    iframe.sandbox.add(
      'allow-scripts',
      'allow-forms',
      'allow-modals',
      'allow-popups',
      'allow-popups-to-escape-sandbox',
      'allow-storage-access-by-user-activation',
      'allow-same-origin',
    );

    if (allowDownloads) {
      iframe.sandbox.add('allow-downloads');
    }

    iframe.addEventListener('load', () => {
      this.shadowRoot?.dispatchEvent(
        new CustomEvent('load', {
          composed: true,
          bubbles: true,
        }),
      );
    });

    // move modal to the top
    this.#dragEvents = dragHandler(
      header,
      this.wrapper,
      () => {
        this.calculateZIndex();
      },
      {
        start: () => {
          this.wrapper.classList.add('is-dragging');
        },
        end: () => {
          this.wrapper.classList.remove('is-dragging');
        },
      },
    );

    this.addEventListener('message', (e: Event) => {
      if (!iframe.contentWindow) {
        return;
      }

      try {
        iframe.contentWindow.postMessage((e as CustomEvent).detail, '*');
      } catch (err) {
        console.error(
          'plugin modal: failed to send message to iframe via postMessage.',
          err,
        );
      }
    });

    this.shadowRoot.appendChild(this.wrapper);

    this.wrapper.appendChild(this.#inner);
    this.#inner.appendChild(header);
    this.#inner.appendChild(iframe);

    const style = document.createElement('style');
    style.textContent = modalCss;

    this.shadowRoot.appendChild(style);

    this.calculateZIndex();
  }

  size() {
    const width = Number(this.wrapper.style.width.replace('px', '') || '300');
    const height = Number(this.wrapper.style.height.replace('px', '') || '400');

    return { width, height };
  }

  #snapshotExpandedFrameStyles(): ExpandedFrameStyles {
    return {
      width: this.wrapper.style.width,
      inlineSize: this.wrapper.style.inlineSize,
      height: this.wrapper.style.height,
      blockSize: this.wrapper.style.blockSize,
      minHeight: this.wrapper.style.minHeight,
      minBlockSize: this.wrapper.style.minBlockSize,
      resize: this.wrapper.style.resize,
      overflow: this.wrapper.style.overflow,
    };
  }

  #setToggleState(expanded: boolean) {
    if (!this.#minimizeToggle) {
      return;
    }

    const label = expanded ? MINIMIZE_LABEL : MAXIMIZE_LABEL;
    this.#minimizeToggle.setAttribute('aria-expanded', String(expanded));
    this.#minimizeToggle.setAttribute('aria-label', label);
    this.#minimizeToggle.setAttribute('title', label);
    this.#minimizeToggle.innerHTML = expanded ? minimizeSvg : maximizeSvg;
  }

  #applyMinimizedFrameStyles() {
    this.wrapper.style.height = MINIMIZED_SIZE;
    this.wrapper.style.blockSize = MINIMIZED_SIZE;
    this.wrapper.style.minHeight = MINIMIZED_SIZE;
    this.wrapper.style.minBlockSize = MINIMIZED_SIZE;
    this.wrapper.style.resize = 'none';
    this.wrapper.style.overflow = 'hidden';
  }

  #minimize() {
    const iframe = this.shadowRoot?.querySelector('iframe');
    const iframeHasFocus =
      iframe &&
      (this.shadowRoot?.activeElement === iframe ||
        document.activeElement === iframe);

    this.#expandedFrameStyles = this.#snapshotExpandedFrameStyles();
    this.#applyMinimizedFrameStyles();
    this.#setToggleState(false);

    if (iframeHasFocus) {
      this.#minimizeToggle?.focus();
    }
  }

  #maximize() {
    if (!this.#expandedFrameStyles) {
      return;
    }

    const expandedFrameStyles = this.#expandedFrameStyles;
    this.wrapper.style.width = expandedFrameStyles.width;
    this.wrapper.style.inlineSize = expandedFrameStyles.inlineSize;
    this.wrapper.style.height = expandedFrameStyles.height;
    this.wrapper.style.blockSize = expandedFrameStyles.blockSize;
    this.wrapper.style.minHeight = expandedFrameStyles.minHeight;
    this.wrapper.style.minBlockSize = expandedFrameStyles.minBlockSize;
    this.wrapper.style.resize = expandedFrameStyles.resize;
    this.wrapper.style.overflow = expandedFrameStyles.overflow;

    this.#expandedFrameStyles = null;
    this.#setToggleState(true);
  }

  #toggleMinimized() {
    if (this.#expandedFrameStyles) {
      this.#maximize();
    } else {
      this.#minimize();
    }
  }
}

customElements.define('plugin-modal', PluginModalElement);
