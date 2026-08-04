import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationBell } from './NotificationBell';
import { api } from '../api/client';
import type { NotificationDto } from '../api/types';

vi.mock('../api/client', () => ({
  api: {
    getUnreadNotificationCount: vi.fn(),
    getNotifications: vi.fn(),
    markNotificationRead: vi.fn(),
    markAllNotificationsRead: vi.fn(),
  },
}));

const notif = (id: number, isRead = false): NotificationDto => ({
  id,
  type: 'discussion_reply',
  data: { replierDisplayName: `User${id}`, cardUid: `c${id}` },
  isRead,
  createdAtMillis: Date.now() - id * 60_000,
});

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getUnreadNotificationCount).mockResolvedValue({ count: 2 });
    vi.mocked(api.getNotifications).mockResolvedValue({ items: [notif(1), notif(2, true)], nextCursor: null });
    vi.mocked(api.markNotificationRead).mockResolvedValue(undefined);
    vi.mocked(api.markAllNotificationsRead).mockResolvedValue(undefined);
  });

  const openPanel = async () => {
    await screen.findByText('2'); // badge from the unread count
    await userEvent.click(screen.getByRole('button', { name: /Notifications/ }));
  };

  it('shows the unread badge from the count', async () => {
    render(<NotificationBell />);
    expect(await screen.findByText('2')).toBeInTheDocument();
  });

  it('opens the panel and lists notifications with per-type copy', async () => {
    render(<NotificationBell />);
    await openPanel();
    expect(await screen.findByText('User1 replied to your comment')).toBeInTheDocument();
    expect(screen.getByText('User2 replied to your comment')).toBeInTheDocument();
  });

  it('renders answer-suggestion-reviewed copy for accepted + declined', async () => {
    const reviewed = (id: number, accepted: boolean): NotificationDto => ({
      id,
      type: 'answer_suggestion_reviewed',
      data: { accepted: String(accepted), suggestedAnswer: 'Paris, France', deckTitle: 'Capitals', cardUid: `c${id}` },
      isRead: false,
      createdAtMillis: Date.now() - id * 60_000,
    });
    vi.mocked(api.getNotifications).mockResolvedValue({ items: [reviewed(1, true), reviewed(2, false)], nextCursor: null });
    render(<NotificationBell />);
    await openPanel();

    expect(await screen.findByText('Your suggestion "Paris, France" was added to Capitals')).toBeInTheDocument();
    expect(screen.getByText('Your suggestion "Paris, France" wasn\'t added to Capitals')).toBeInTheDocument();
  });

  it('renders streak-milestone and thread-activity copy', async () => {
    vi.mocked(api.getNotifications).mockResolvedValue({
      items: [
        {
          id: 1,
          type: 'streak_milestone',
          data: { streak: '7' },
          isRead: false,
          createdAtMillis: Date.now() - 60_000,
        },
        {
          id: 2,
          type: 'thread_activity',
          data: { replierDisplayName: 'Carol', cardUid: 'c2' },
          isRead: false,
          createdAtMillis: Date.now() - 120_000,
        },
      ],
      nextCursor: null,
    });
    render(<NotificationBell />);
    await openPanel();

    expect(await screen.findByText('🔥 7-day streak! Keep it up.')).toBeInTheDocument();
    expect(screen.getByText('Carol also posted in a discussion you joined')).toBeInTheDocument();
  });

  it('marks one read on click and decrements the badge', async () => {
    render(<NotificationBell />);
    await openPanel();
    await userEvent.click(await screen.findByText('User1 replied to your comment'));

    expect(api.markNotificationRead).toHaveBeenCalledWith(1);
    await waitFor(() => expect(screen.getByText('1')).toBeInTheDocument());
  });

  it('marks all read — clears the badge and the action', async () => {
    render(<NotificationBell />);
    await openPanel();
    await screen.findByText('User1 replied to your comment');
    await userEvent.click(screen.getByRole('button', { name: 'Mark all read' }));

    expect(api.markAllNotificationsRead).toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Mark all read' })).not.toBeInTheDocument());
    expect(screen.queryByText('2')).not.toBeInTheDocument();
  });

  it('shows an empty state when there are none', async () => {
    vi.mocked(api.getUnreadNotificationCount).mockResolvedValue({ count: 0 });
    vi.mocked(api.getNotifications).mockResolvedValue({ items: [], nextCursor: null });
    render(<NotificationBell />);
    await userEvent.click(screen.getByRole('button', { name: /Notifications/ }));

    expect(await screen.findByText('No notifications yet.')).toBeInTheDocument();
  });
});
