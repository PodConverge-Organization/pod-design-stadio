import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import './plugin-modal.js';

type PointerLikeEvent = MouseEvent & { pointerId: number };

function createPointerEvent(
  type: string,
  init: Partial<PointerEventInit> = {},
): PointerLikeEvent {
  const event = new MouseEvent(type, {
    bubbles: true,
    cancelable: true,
    clientX: init.clientX ?? 0,
    clientY: init.clientY ?? 0,
    button: init.button ?? 0,
  }) as PointerLikeEvent;

  Object.defineProperty(event, 'pointerId', {
    configurable: true,
    value: init.pointerId ?? 1,
  });

  return event;
}

describe('PluginModalElement', () => {
  let setPointerCaptureSpy: ReturnType<typeof vi.fn>;
  let releasePointerCaptureSpy: ReturnType<typeof vi.fn>;
  let hasPointerCaptureSpy: ReturnType<typeof vi.fn>;
  let originalSetPointerCapture: typeof HTMLElement.prototype.setPointerCapture;
  let originalReleasePointerCapture: typeof HTMLElement.prototype.releasePointerCapture;
  let originalHasPointerCapture: typeof HTMLElement.prototype.hasPointerCapture;

  beforeEach(() => {
    originalSetPointerCapture = HTMLElement.prototype.setPointerCapture;
    originalReleasePointerCapture = HTMLElement.prototype.releasePointerCapture;
    originalHasPointerCapture = HTMLElement.prototype.hasPointerCapture;

    setPointerCaptureSpy = vi.fn();
    releasePointerCaptureSpy = vi.fn();
    hasPointerCaptureSpy = vi.fn().mockReturnValue(true);

    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: setPointerCaptureSpy,
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: releasePointerCaptureSpy,
    });
    Object.defineProperty(HTMLElement.prototype, 'hasPointerCapture', {
      configurable: true,
      value: hasPointerCaptureSpy,
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: originalSetPointerCapture,
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: originalReleasePointerCapture,
    });
    Object.defineProperty(HTMLElement.prototype, 'hasPointerCapture', {
      configurable: true,
      value: originalHasPointerCapture,
    });
    vi.restoreAllMocks();
  });

  function createModal(attributes: Record<string, string> = {}) {
    const modal = document.createElement('plugin-modal');
    modal.setAttribute('title', 'Test modal');
    modal.setAttribute('iframe-src', 'about:blank');
    for (const [name, value] of Object.entries(attributes)) {
      modal.setAttribute(name, value);
    }
    document.body.appendChild(modal);

    return modal as HTMLElement & { resize(width: number, height: number): void };
  }

  it('should not start dragging on header control pointerdown', () => {
    const modal = createModal();
    const shadow = modal.shadowRoot;
    expect(shadow).toBeTruthy();

    const wrapper = shadow?.querySelector<HTMLElement>('.wrapper');
    const toggleButton = shadow?.querySelector<HTMLElement>('button');

    expect(wrapper).toBeTruthy();
    expect(toggleButton).toBeTruthy();

    toggleButton?.dispatchEvent(
      createPointerEvent('pointerdown', {
        pointerId: 11,
        button: 0,
      }),
    );

    expect(wrapper?.classList.contains('is-dragging')).toBe(false);
    expect(setPointerCaptureSpy).not.toHaveBeenCalled();

    modal.remove();
  });

  it('should set iframe allow attribute for clipboard permissions', () => {
    const modal = createModal({
      'allow-clipboard-read': 'true',
      'allow-clipboard-write': 'true',
    });

    const iframe = modal.shadowRoot?.querySelector('iframe');
    expect(iframe).toBeTruthy();
    expect(iframe?.allow).toContain('clipboard-read');
    expect(iframe?.allow).toContain('clipboard-write');

    modal.remove();
  });

  it('should not set clipboard allow attributes when permissions are absent', () => {
    const modal = createModal();

    const iframe = modal.shadowRoot?.querySelector('iframe');
    expect(iframe).toBeTruthy();
    expect(iframe?.allow).toBe('');

    modal.remove();
  });

  it('should render a minimize toggle without a close control', () => {
    const modal = createModal();

    const toggleButton = modal.shadowRoot?.querySelector('button');

    expect(modal.shadowRoot?.querySelector('.close')).toBeNull();
    expect(toggleButton).toBeTruthy();
    expect(toggleButton?.getAttribute('aria-expanded')).toBe('true');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Minimize plugin');
    expect(toggleButton?.getAttribute('title')).toBe('Minimize plugin');
    expect(toggleButton?.textContent).not.toMatch(/close/i);

    modal.remove();
  });

  it('should minimize and maximize without replacing the iframe or closing', () => {
    const modal = createModal();

    const onClose = vi.fn();
    modal.addEventListener('close', onClose);

    const wrapper = modal.shadowRoot?.querySelector<HTMLElement>('.wrapper');
    const toggleButton = modal.shadowRoot?.querySelector<HTMLElement>('button');
    const iframe = modal.shadowRoot?.querySelector('iframe');

    expect(wrapper).toBeTruthy();
    expect(toggleButton).toBeTruthy();
    expect(iframe).toBeTruthy();

    wrapper?.style.setProperty('width', '335px');
    wrapper?.style.setProperty('inline-size', '335px');
    wrapper?.style.setProperty('height', '590px');
    wrapper?.style.setProperty('block-size', '590px');
    wrapper?.style.setProperty('min-height', '210px');
    wrapper?.style.setProperty('min-block-size', '210px');
    wrapper?.style.setProperty('resize', 'both');
    wrapper?.style.setProperty('overflow', 'auto');

    const contentWindow = iframe?.contentWindow;

    toggleButton?.dispatchEvent(
      new MouseEvent('click', {
        bubbles: true,
        cancelable: true,
      }),
    );

    expect(onClose).not.toHaveBeenCalled();
    expect(toggleButton?.getAttribute('aria-expanded')).toBe('false');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Maximize plugin');
    expect(toggleButton?.getAttribute('title')).toBe('Maximize plugin');
    expect(wrapper?.style.height).toBe('40px');
    expect(wrapper?.style.blockSize).toBe('40px');
    expect(wrapper?.style.minHeight).toBe('40px');
    expect(wrapper?.style.minBlockSize).toBe('40px');
    expect(wrapper?.style.resize).toBe('none');
    expect(wrapper?.style.overflow).toBe('hidden');
    expect(modal.shadowRoot?.querySelector('iframe')).toBe(iframe);
    expect(iframe?.contentWindow).toBe(contentWindow);

    toggleButton?.dispatchEvent(
      new MouseEvent('click', {
        bubbles: true,
        cancelable: true,
      }),
    );

    expect(onClose).not.toHaveBeenCalled();
    expect(toggleButton?.getAttribute('aria-expanded')).toBe('true');
    expect(toggleButton?.getAttribute('aria-label')).toBe('Minimize plugin');
    expect(toggleButton?.getAttribute('title')).toBe('Minimize plugin');
    expect(wrapper?.style.width).toBe('335px');
    expect(wrapper?.style.inlineSize).toBe('335px');
    expect(wrapper?.style.height).toBe('590px');
    expect(wrapper?.style.blockSize).toBe('590px');
    expect(wrapper?.style.minHeight).toBe('210px');
    expect(wrapper?.style.minBlockSize).toBe('210px');
    expect(wrapper?.style.resize).toBe('both');
    expect(wrapper?.style.overflow).toBe('auto');
    expect(modal.shadowRoot?.querySelector('iframe')).toBe(iframe);
    expect(iframe?.contentWindow).toBe(contentWindow);

    modal.remove();
  });

  it('should keep minimized presentation when resized until the next maximize', () => {
    const modal = createModal();
    const wrapper = modal.shadowRoot?.querySelector<HTMLElement>('.wrapper');
    const toggleButton = modal.shadowRoot?.querySelector<HTMLElement>('button');
    const iframe = modal.shadowRoot?.querySelector('iframe');

    expect(wrapper).toBeTruthy();
    expect(toggleButton).toBeTruthy();
    expect(iframe).toBeTruthy();

    wrapper?.style.setProperty('width', '335px');
    wrapper?.style.setProperty('inline-size', '335px');
    wrapper?.style.setProperty('height', '590px');
    wrapper?.style.setProperty('block-size', '590px');

    const contentWindow = iframe?.contentWindow;

    toggleButton?.click();
    modal.resize(420, 360);

    expect(toggleButton?.getAttribute('aria-expanded')).toBe('false');
    expect(wrapper?.style.width).toBe('420px');
    expect(wrapper?.style.inlineSize).toBe('335px');
    expect(wrapper?.style.height).toBe('40px');
    expect(wrapper?.style.blockSize).toBe('40px');
    expect(modal.shadowRoot?.querySelector('iframe')).toBe(iframe);
    expect(iframe?.contentWindow).toBe(contentWindow);

    toggleButton?.click();

    expect(toggleButton?.getAttribute('aria-expanded')).toBe('true');
    expect(wrapper?.style.width).toBe('420px');
    expect(wrapper?.style.inlineSize).toBe('420px');
    expect(wrapper?.style.height).toBe('360px');
    expect(wrapper?.style.blockSize).toBe('360px');
    expect(modal.shadowRoot?.querySelector('iframe')).toBe(iframe);
    expect(iframe?.contentWindow).toBe(contentWindow);

    modal.remove();
  });

  it('should preserve iframe sandbox without top navigation capabilities', () => {
    const modal = createModal();
    const iframe = modal.shadowRoot?.querySelector('iframe');

    expect(iframe).toBeTruthy();
    expect(iframe?.sandbox.contains('allow-scripts')).toBe(true);
    expect(iframe?.sandbox.contains('allow-forms')).toBe(true);
    expect(iframe?.sandbox.contains('allow-modals')).toBe(true);
    expect(iframe?.sandbox.contains('allow-popups')).toBe(true);
    expect(iframe?.sandbox.contains('allow-popups-to-escape-sandbox')).toBe(
      true,
    );
    expect(
      iframe?.sandbox.contains('allow-storage-access-by-user-activation'),
    ).toBe(true);
    expect(iframe?.sandbox.contains('allow-same-origin')).toBe(true);
    expect(iframe?.sandbox.contains('allow-top-navigation')).toBe(false);
    expect(
      iframe?.sandbox.contains('allow-top-navigation-by-user-activation'),
    ).toBe(false);

    modal.remove();
  });
});
