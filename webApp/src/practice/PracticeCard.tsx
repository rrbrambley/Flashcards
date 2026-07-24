import { useRef, useState, type PointerEvent } from 'react';
import type { FlashcardDto } from '../api/types';
import { PromptImage } from './components/PromptImage';

interface PracticeCardProps {
  card: FlashcardDto;
  isFlipped: boolean;
  onFlip: () => void;
  onSwipeLeft: () => void; // incorrect
  onSwipeRight: () => void; // correct
  /** Reports the front image has settled, so a timed run can resume its countdown (#317). */
  onImageReady?: () => void;
}

const SWIPE_THRESHOLD = 90; // px of horizontal travel to count as a swipe

export function PracticeCard({ card, isFlipped, onFlip, onSwipeLeft, onSwipeRight, onImageReady }: PracticeCardProps) {
  const [dragX, setDragX] = useState(0);
  const startX = useRef<number | null>(null);
  // A committed swipe shouldn't also fire the trailing click (which would flip).
  const swiped = useRef(false);

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    startX.current = e.clientX;
    swiped.current = false;
    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {
      // setPointerCapture isn't available in every environment (e.g. jsdom).
    }
  };

  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => {
    if (startX.current === null) return;
    setDragX(e.clientX - startX.current);
  };

  const onPointerUp = (e: PointerEvent<HTMLDivElement>) => {
    if (startX.current === null) return;
    const dx = e.clientX - startX.current;
    startX.current = null;
    setDragX(0);
    if (dx > SWIPE_THRESHOLD) {
      swiped.current = true;
      onSwipeRight();
    } else if (dx < -SWIPE_THRESHOLD) {
      swiped.current = true;
      onSwipeLeft();
    }
  };

  const onClick = () => {
    if (swiped.current) {
      swiped.current = false;
      return;
    }
    onFlip();
  };

  const hasImage = card.imageUrl != null && card.imageUrl !== '';

  return (
    <div
      className="practice-card"
      role="button"
      tabIndex={0}
      aria-label={isFlipped ? 'Show question' : 'Show answer'}
      style={{ transform: `translateX(${dragX}px) rotate(${dragX * 0.03}deg)` }}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onClick={onClick}
    >
      <div className={`flip-inner${isFlipped ? ' flipped' : ''}`}>
        <div className="flip-face flip-front">
          {card.question && <p className="practice-term">{card.question}</p>}
          {hasImage && (
            <PromptImage
              src={card.imageUrl ?? ''}
              alt={card.question || 'card image'}
              className="practice-image"
              onReady={onImageReady}
            />
          )}
        </div>
        <div className="flip-face flip-back">
          <p className="practice-answer">{card.answer}</p>
        </div>
      </div>
    </div>
  );
}
