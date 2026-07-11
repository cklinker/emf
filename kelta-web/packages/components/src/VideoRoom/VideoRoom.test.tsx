import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { LocalUserChoices } from '@livekit/components-react';

// Mock the LiveKit libraries so tests never open a real WebRTC connection.
// PreJoin exposes a Join button that fires onSubmit; an "error" button fires onError.
vi.mock('@livekit/components-react', () => ({
  PreJoin: ({
    onSubmit,
    onError,
    joinLabel,
  }: {
    onSubmit: (v: LocalUserChoices) => void;
    onError: (e: Error) => void;
    joinLabel: string;
  }) => (
    <div data-testid="mock-prejoin">
      <button
        data-testid="mock-prejoin-join"
        onClick={() =>
          onSubmit({
            videoEnabled: true,
            audioEnabled: true,
            videoDeviceId: '',
            audioDeviceId: '',
            username: '',
          } as LocalUserChoices)
        }
      >
        {joinLabel}
      </button>
      <button data-testid="mock-prejoin-fail" onClick={() => onError(new Error('camera blocked'))}>
        fail
      </button>
    </div>
  ),
  LiveKitRoom: ({
    children,
    onDisconnected,
  }: {
    children: React.ReactNode;
    onDisconnected: () => void;
  }) => (
    <div data-testid="mock-livekit-room">
      <button data-testid="mock-leave" onClick={onDisconnected}>
        leave
      </button>
      {children}
    </div>
  ),
  GridLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="mock-grid">{children}</div>
  ),
  ParticipantTile: ({ children }: { children?: React.ReactNode }) => (
    <div data-testid="mock-tile">{children}</div>
  ),
  ControlBar: () => <div data-testid="mock-controlbar" />,
  RoomAudioRenderer: () => <div data-testid="mock-audio" />,
  ConnectionQualityIndicator: () => <div data-testid="mock-quality" />,
  useTracks: () => [],
}));

vi.mock('livekit-client', () => ({
  Track: { Source: { Camera: 'camera', ScreenShare: 'screen_share' } },
}));

vi.mock('@livekit/components-styles', () => ({}));

// Import AFTER the mocks are registered.
import { VideoRoom } from './VideoRoom';

const baseProps = {
  serverUrl: 'wss://livekit.example',
  token: 'test-token',
};

describe('VideoRoom', () => {
  it('renders the pre-join state first (device pickers + join), not the room', () => {
    render(<VideoRoom {...baseProps} />);
    expect(screen.getByTestId('kelta-video-room-prejoin')).toBeInTheDocument();
    expect(screen.getByTestId('mock-prejoin')).toBeInTheDocument();
    expect(screen.queryByTestId('kelta-video-room-inroom')).not.toBeInTheDocument();
  });

  it('shows the permissions-troubleshooting message when the browser blocks devices', () => {
    const onError = vi.fn();
    render(<VideoRoom {...baseProps} onError={onError} />);

    fireEvent.click(screen.getByTestId('mock-prejoin-fail'));

    expect(screen.getByTestId('kelta-video-room-permissions')).toBeInTheDocument();
    expect(onError).toHaveBeenCalledWith(expect.any(Error));
  });

  it('transitions into the room (grid + controls) after Join', () => {
    render(<VideoRoom {...baseProps} />);
    fireEvent.click(screen.getByTestId('mock-prejoin-join'));

    expect(screen.getByTestId('kelta-video-room-inroom')).toBeInTheDocument();
    expect(screen.getByTestId('mock-livekit-room')).toBeInTheDocument();
    // Control bar + grid render inside the (mocked) room.
    expect(screen.getByTestId('mock-controlbar')).toBeInTheDocument();
    expect(screen.getByTestId('mock-grid')).toBeInTheDocument();
  });

  it('shows the ended summary and fires onLeave when the room disconnects', () => {
    const onLeave = vi.fn();
    render(<VideoRoom {...baseProps} onLeave={onLeave} />);
    fireEvent.click(screen.getByTestId('mock-prejoin-join'));
    fireEvent.click(screen.getByTestId('mock-leave'));

    expect(onLeave).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId('kelta-video-room-ended')).toBeInTheDocument();
    expect(screen.getByTestId('kelta-video-room-rejoin')).toBeInTheDocument();
  });

  it('shows the recording indicator when recordingActive is true', () => {
    render(<VideoRoom {...baseProps} recordingActive />);
    expect(screen.getByTestId('kelta-video-room-recording')).toBeInTheDocument();
  });

  it('renders the side-panel slot in the room when children are provided', () => {
    render(
      <VideoRoom {...baseProps}>
        <div data-testid="side-chat">chat</div>
      </VideoRoom>
    );
    fireEvent.click(screen.getByTestId('mock-prejoin-join'));
    expect(screen.getByTestId('kelta-video-room-panel')).toBeInTheDocument();
    expect(screen.getByTestId('side-chat')).toBeInTheDocument();
  });

  it('applies injected labels (i18n) for the join button', () => {
    render(<VideoRoom {...baseProps} labels={{ join: 'Entrar' }} />);
    expect(screen.getByText('Entrar')).toBeInTheDocument();
  });
});
