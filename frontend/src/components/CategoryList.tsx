import type { Category, CategoryType } from '../types';

interface CategoryListProps {
  categories: Category[];
  loading: boolean;
  error: string | null;
  activeTypeFilter: CategoryType | 'ALL';
  onTypeFilterChange: (filter: CategoryType | 'ALL') => void;
  onAddClick: () => void;
}

const TYPE_FILTERS: Array<CategoryType | 'ALL'> = ['ALL', 'EXPENSE', 'INCOME', 'BOTH'];

export function CategoryList({
  categories,
  loading,
  error,
  activeTypeFilter,
  onTypeFilterChange,
  onAddClick,
}: CategoryListProps) {
  const filtered = activeTypeFilter === 'ALL'
    ? categories
    : categories.filter((c) => c.type === activeTypeFilter);

  return (
    <div className="category-list-container">
      <div className="category-filter-bar">
        {TYPE_FILTERS.map((f) => (
          <button
            key={f}
            className={`btn-filter${activeTypeFilter === f ? ' btn-filter--active' : ''}`}
            onClick={() => onTypeFilterChange(f)}
          >
            {f}
          </button>
        ))}
      </div>

      {loading && <p className="state-message">Loading categories…</p>}

      {!loading && error && (
        <p className="state-message state-message--error">{error}</p>
      )}

      {!loading && !error && filtered.length === 0 && (
        <div className="empty-state">
          <p>No categories yet.</p>
          <button className="btn-primary" onClick={onAddClick}>
            + Add your first category
          </button>
        </div>
      )}

      {!loading && !error && filtered.length > 0 && (
        <ul className="category-list">
          {filtered.map((cat) => (
            <li key={cat.id} className="category-item">
              {cat.color && (
                <span
                  className="category-color-swatch"
                  style={{ backgroundColor: cat.color }}
                />
              )}
              <span className="category-name">{cat.name}</span>
              <span className={`category-type-badge category-type-badge--${cat.type.toLowerCase()}`}>
                {cat.type}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
