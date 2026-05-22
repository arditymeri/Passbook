interface MonthNavProps {
  year: number;
  month: number;
  onPrevious: () => void;
  onNext: () => void;
}

export function MonthNav({ year, month, onPrevious, onNext }: MonthNavProps) {
  const label = new Date(year, month - 1).toLocaleString('default', {
    month: 'long',
    year: 'numeric',
  });

  return (
    <div className="month-nav">
      <button className="nav-btn" onClick={onPrevious} aria-label="Previous month">‹</button>
      <span className="month-label">{label}</span>
      <button className="nav-btn" onClick={onNext} aria-label="Next month">›</button>
    </div>
  );
}
