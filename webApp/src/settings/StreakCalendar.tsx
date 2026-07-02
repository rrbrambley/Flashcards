import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { StreakCalendarResponse } from '../api/types';

const WEEKDAYS = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;

/**
 * Practice-activity calendar (FLA-171): a month grid with a warm marker on each day the user
 * completed a session, month navigation (never into the future), and the current/longest streak.
 * Data from GET /streaks/calendar; the marker reuses the 🔥 streak-badge palette.
 */
export function StreakCalendar() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1); // 1–12
  const [data, setData] = useState<StreakCalendarResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true);
    const monthStr = `${year}-${String(month).padStart(2, '0')}`;
    api
      .getStreakCalendar(monthStr, TZ)
      .then((res) => {
        if (!active) return;
        setData(res);
        setError(null);
      })
      .catch((err: unknown) => {
        if (active) setError(err instanceof Error ? err.message : 'Could not load your activity.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [year, month]);

  const nowYear = now.getFullYear();
  const nowMonth = now.getMonth() + 1;
  const isCurrentMonth = year === nowYear && month === nowMonth;
  const canGoNext = year < nowYear || (year === nowYear && month < nowMonth);
  const todayDate = isCurrentMonth ? now.getDate() : null;

  const goPrev = () => {
    if (month === 1) {
      setYear((y) => y - 1);
      setMonth(12);
    } else {
      setMonth((m) => m - 1);
    }
  };
  const goNext = () => {
    if (!canGoNext) return;
    if (month === 12) {
      setYear((y) => y + 1);
      setMonth(1);
    } else {
      setMonth((m) => m + 1);
    }
  };

  const daysInMonth = new Date(year, month, 0).getDate();
  const leadingBlanks = new Date(year, month - 1, 1).getDay();
  const activeDays = new Set(data?.activeDays ?? []);
  const monthLabel = `${MONTHS[month - 1]} ${year}`;

  return (
    <div className="streak-calendar">
      <div className="streak-calendar-summary">
        {data && data.current > 0 && (
          <span className="streak-badge" title="Days in a row with a completed practice">
            🔥 {data.current} day streak
          </span>
        )}
        {data && (
          <span className="muted">
            Longest streak: {data.longest} {data.longest === 1 ? 'day' : 'days'}
          </span>
        )}
      </div>

      <div className="streak-calendar-nav">
        <button type="button" className="link-btn" onClick={goPrev} aria-label="Previous month">
          ‹
        </button>
        <span className="streak-calendar-month">{monthLabel}</span>
        <button
          type="button"
          className="link-btn"
          onClick={goNext}
          disabled={!canGoNext}
          aria-label="Next month"
        >
          ›
        </button>
      </div>

      <div className="streak-calendar-grid">
        {WEEKDAYS.map((day, index) => (
          <span key={`wd-${index}`} className="streak-calendar-weekday" aria-hidden="true">
            {day}
          </span>
        ))}
        {Array.from({ length: leadingBlanks }, (_, index) => (
          <span key={`blank-${index}`} className="streak-calendar-blank" aria-hidden="true" />
        ))}
        {Array.from({ length: daysInMonth }, (_, index) => {
          const day = index + 1;
          const isActive = activeDays.has(day);
          const isToday = day === todayDate;
          const className = [
            'streak-calendar-day',
            isActive ? 'streak-calendar-day-active' : '',
            isToday ? 'streak-calendar-day-today' : '',
          ]
            .filter(Boolean)
            .join(' ');
          return (
            <span
              key={day}
              className={className}
              aria-label={isActive ? `${MONTHS[month - 1]} ${day}: practiced` : undefined}
            >
              {day}
            </span>
          );
        })}
      </div>

      {error && <p className="error">{error}</p>}
      {loading && !data && <p className="muted">Loading…</p>}
    </div>
  );
}
