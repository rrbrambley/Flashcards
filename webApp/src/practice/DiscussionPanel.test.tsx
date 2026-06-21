import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DiscussionPanel } from './DiscussionPanel';
import { api } from '../api/client';
import type { DiscussionMessage, DiscussionThread } from '../api/types';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
  api: {
    getDiscussionThread: vi.fn(),
    getDiscussionMessages: vi.fn(),
    postDiscussionMessage: vi.fn(),
    lockDiscussionThread: vi.fn(),
    register: vi.fn(),
    login: vi.fn(),
  },
}));

const applyAuth = vi.fn();
vi.mock('../auth/auth-context', () => ({ useAuth: () => ({ applyAuth }) }));
vi.mock('../auth/token', () => ({ setTokens: vi.fn() }));

const thread = (over: Partial<DiscussionThread> = {}): DiscussionThread => ({
  cardUid: 'c1',
  isLocked: false,
  messageCount: 1,
  ...over,
});
const message = (over: Partial<DiscussionMessage> = {}): DiscussionMessage => ({
  id: 1,
  authorDisplayName: 'Quiz Whiz',
  content: 'Why is it Paris?',
  parentMessageId: null,
  createdAtMillis: Date.now(),
  ...over,
});

function setup(opts: { isGuest?: boolean; canModerate?: boolean; thread?: DiscussionThread; messages?: DiscussionMessage[] } = {}) {
  vi.mocked(api.getDiscussionThread).mockResolvedValue(opts.thread ?? thread());
  vi.mocked(api.getDiscussionMessages).mockResolvedValue({ items: opts.messages ?? [message()], nextCursor: null });
  render(
    <DiscussionPanel
      cardUid="c1"
      isGuest={opts.isGuest ?? false}
      canModerate={opts.canModerate ?? false}
      onClose={vi.fn()}
    />,
  );
}

describe('DiscussionPanel', () => {
  beforeEach(() => vi.clearAllMocks());

  it('loads and renders messages with their author display name', async () => {
    setup();
    expect(await screen.findByText('Why is it Paris?')).toBeInTheDocument();
    expect(screen.getByText('Quiz Whiz')).toBeInTheDocument();
    expect(screen.getByLabelText('Your message')).toBeInTheDocument();
  });

  it('posts a message as a signed-in user and shows it', async () => {
    setup();
    await screen.findByText('Why is it Paris?');
    vi.mocked(api.postDiscussionMessage).mockResolvedValue(message({ id: 2, content: 'Because it is the capital.' }));

    await userEvent.type(screen.getByLabelText('Your message'), 'Because it is the capital.');
    await userEvent.click(screen.getByRole('button', { name: 'Post' }));

    await waitFor(() => expect(api.postDiscussionMessage).toHaveBeenCalledWith('c1', 'Because it is the capital.', undefined));
    expect(await screen.findByText('Because it is the capital.')).toBeInTheDocument();
  });

  it('threads a reply with the parent id', async () => {
    setup();
    await screen.findByText('Why is it Paris?');
    vi.mocked(api.postDiscussionMessage).mockResolvedValue(message({ id: 3, content: 'A reply', parentMessageId: 1 }));

    await userEvent.click(screen.getByRole('button', { name: 'Reply' }));
    expect(screen.getByText(/Replying to Quiz Whiz/)).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Your message'), 'A reply');
    await userEvent.click(screen.getByRole('button', { name: 'Post' }));

    await waitFor(() => expect(api.postDiscussionMessage).toHaveBeenCalledWith('c1', 'A reply', 1));
  });

  it('a locked thread hides the post box and is read-only', async () => {
    setup({ thread: thread({ isLocked: true }) });
    await screen.findByText('Why is it Paris?');
    expect(screen.getByText(/This discussion is locked/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Your message')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reply' })).not.toBeInTheDocument();
  });

  it('shows the lock control to a moderator', async () => {
    setup({ canModerate: true });
    await screen.findByText('Why is it Paris?');
    await userEvent.click(screen.getByRole('button', { name: 'Lock' }));
    expect(api.lockDiscussionThread).toHaveBeenCalledWith('c1', true);
  });

  it('a guest is prompted to sign in when posting (conversion)', async () => {
    setup({ isGuest: true });
    await screen.findByText('Why is it Paris?');

    await userEvent.type(screen.getByLabelText('Your message'), 'My thoughts');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in to post' }));

    expect(await screen.findByText('Join the discussion')).toBeInTheDocument();
    expect(api.postDiscussionMessage).not.toHaveBeenCalled();
  });

  it('guest conversion: registers, posts the pending message, then applies auth', async () => {
    setup({ isGuest: true });
    await screen.findByText('Why is it Paris?');
    vi.mocked(api.register).mockResolvedValue({ accessToken: 'a', refreshToken: 'r', userId: 1, permissions: [] });
    vi.mocked(api.postDiscussionMessage).mockResolvedValue(message({ id: 9, content: 'My thoughts' }));

    await userEvent.type(screen.getByLabelText('Your message'), 'My thoughts');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in to post' }));
    await userEvent.type(screen.getByLabelText('Email'), 'new@user.com');
    await userEvent.type(screen.getByLabelText('Password'), 'password1');
    await userEvent.click(screen.getByRole('button', { name: 'Create account & post' }));

    await waitFor(() => expect(api.register).toHaveBeenCalledWith('new@user.com', 'password1'));
    expect(api.postDiscussionMessage).toHaveBeenCalledWith('c1', 'My thoughts', undefined);
    expect(applyAuth).toHaveBeenCalled();
  });
});
