import { describe, it, expect } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { RecordHeader } from './RecordHeader';

const baseProps = {
  recordId: 'rec-1',
  collectionLabel: 'Titles',
  fallbackTitle: 'Tell Me What You Want',
};

function avatarBox(container: HTMLElement): HTMLElement {
  const el = container.querySelector('.kelta-hero-avatar');
  if (!(el instanceof HTMLElement)) throw new Error('avatar box not rendered');
  return el;
}

describe('RecordHeader avatar', () => {
  it('derives initials from the fallback title when no avatarFrom is configured', () => {
    const { container } = render(<RecordHeader {...baseProps} record={{}} />);
    expect(avatarBox(container).textContent).toBe('TM');
  });

  it('derives initials from configured text fields', () => {
    const { container } = render(
      <RecordHeader
        {...baseProps}
        record={{ firstName: 'ada', lastName: 'lovelace' }}
        config={{ avatarFrom: ['firstName', 'lastName'] }}
      />
    );
    expect(avatarBox(container).textContent).toBe('AL');
  });

  it('renders an image when the avatarFrom field holds an http(s) URL', () => {
    const { container } = render(
      <RecordHeader
        {...baseProps}
        record={{ posterUrl: 'https://cdn.example.com/poster.jpg' }}
        config={{ avatarFrom: ['posterUrl'] }}
      />
    );
    const img = avatarBox(container).querySelector('img');
    expect(img).not.toBeNull();
    expect(img).toHaveAttribute('src', 'https://cdn.example.com/poster.jpg');
    // No "H"-from-"https" initials next to the image
    expect(avatarBox(container).textContent).toBe('');
  });

  it('renders an image for data:image URIs', () => {
    const uri = 'data:image/png;base64,iVBORw0KGgo=';
    const { container } = render(
      <RecordHeader {...baseProps} record={{ poster: uri }} config={{ avatarFrom: ['poster'] }} />
    );
    expect(avatarBox(container).querySelector('img')).toHaveAttribute('src', uri);
  });

  it('falls back to title initials when the image fails to load', () => {
    const { container } = render(
      <RecordHeader
        {...baseProps}
        record={{ posterUrl: 'https://cdn.example.com/broken.jpg' }}
        config={{ avatarFrom: ['posterUrl'] }}
      />
    );
    const img = avatarBox(container).querySelector('img');
    expect(img).not.toBeNull();
    fireEvent.error(img as HTMLImageElement);
    expect(avatarBox(container).querySelector('img')).toBeNull();
    // URL value is skipped as an initials source — use the fallback title
    expect(avatarBox(container).textContent).toBe('TM');
  });

  it('uses the first image URL when avatarFrom mixes text and URL fields', () => {
    const { container } = render(
      <RecordHeader
        {...baseProps}
        record={{ name: 'Widget', posterUrl: 'https://cdn.example.com/p.jpg' }}
        config={{ avatarFrom: ['name', 'posterUrl'] }}
      />
    );
    expect(avatarBox(container).querySelector('img')).toHaveAttribute(
      'src',
      'https://cdn.example.com/p.jpg'
    );
  });

  it('keeps the presence dot when showing an image avatar', () => {
    const { container } = render(
      <RecordHeader
        {...baseProps}
        record={{ posterUrl: 'https://cdn.example.com/p.jpg' }}
        config={{ avatarFrom: ['posterUrl'] }}
        showPresence
      />
    );
    expect(avatarBox(container).querySelector('.kelta-presence-dot')).not.toBeNull();
    expect(avatarBox(container).querySelector('img')).not.toBeNull();
  });

  it('renders the title from the record when titleFields is set', () => {
    render(
      <RecordHeader
        {...baseProps}
        record={{ title: 'Actual Title' }}
        config={{ titleFields: ['title'] }}
      />
    );
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Actual Title');
  });
});
