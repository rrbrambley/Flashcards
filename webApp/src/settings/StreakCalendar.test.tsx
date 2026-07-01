import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StreakCalendar } from './StreakCalendar';
import { api } from '../api/client';
import type { StreakCalendarResponse } from '../api/types';

vi.mock('../api/client', () => ({ api: { getStreakCalendar: vi.fn() } }));

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

// The component defaults to the current month, so derive the expectations from the same clock.
const now = new Date();
const curYear = now.getFullYear();
const curMonth = now.getMonth() + 1; // 1–12
const curKey = `${curYear}-${String(curMonth).padStart(2, '0')}`;
const curLabel = `${MONTHS[curMonth - 1]} ${curYear}`;
const prevYear = curMonth === 1 ? curYear - 1 : curYear;
const prevMonthNum = curMonth === 1 ? 12 : curMonth - 1;
const prevKey = `${prevYear}-${String(prevMonthNum).padStart(2, '0')}`;
const prevLabel = `${MONTHS[prevMonthNum - 1]} ${prevYear}`;

const calendar = (over: Partial<StreakCalendarResponse> = {}): StreakCalendarResponse => ({
  month: curKey,
  activeDays: [],
  current: 0,
  longest: 0,
  ...over,
});

describe('StreakCalendar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(api.getStreakCalendar).mockResolvedValue(calendar());
  });

  it('loads the current month and marks only the active days', async () => {
    vi.mocked(api.getStreakCalendar).mockResolvedValue(calendar({ activeDays: [1, 3, 5], current: 2, longest: 9 }));
    render(<StreakCalendar />);

    expect(await screen.findByText(curLabel)).toBeInTheDocument();
    expect(api.getStreakCalendar).toHaveBeenCalledWith(curKey, expect.any(String));

    // Active days carry the "practiced" label; a non-active day does not.
    expect(screen.getByLabelText(`${MONTHS[curMonth - 1]} 1: practiced`)).toBeInTheDocument();
    expect(screen.getByLabelText(`${MONTHS[curMonth - 1]} 3: practiced`)).toBeInTheDocument();
    expect(screen.queryByLabelText(`${MONTHS[curMonth - 1]} 2: practiced`)).not.toBeInTheDocument();

    expect(screen.getByText(/2 day streak/)).toBeInTheDocument();
    expect(screen.getByText(/Longest: 9 days/)).toBeInTheDocument();
  });

  it('disables next at the current month and pages back on prev', async () => {
    render(<StreakCalendar />);
    await screen.findByText(curLabel);

    expect(screen.getByLabelText('Next month')).toBeDisabled();

    await userEvent.click(screen.getByLabelText('Previous month'));

    await waitFor(() => expect(api.getStreakCalendar).toHaveBeenCalledWith(prevKey, expect.any(String)));
    expect(await screen.findByText(prevLabel)).toBeInTheDocument();
    // Now viewing a past month, so forward navigation is enabled again.
    expect(screen.getByLabelText('Next month')).toBeEnabled();
  });

  it('shows no markers for a month with no activity', async () => {
    render(<StreakCalendar />);
    await screen.findByText(curLabel);
    expect(screen.queryByLabelText(/practiced/)).not.toBeInTheDocument();
  });

  it('surfaces a load error without crashing', async () => {
    vi.mocked(api.getStreakCalendar).mockRejectedValue(new Error('offline'));
    render(<StreakCalendar />);
    expect(await screen.findByText('offline')).toBeInTheDocument();
  });
});
