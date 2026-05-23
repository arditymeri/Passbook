import { useEffect, useState } from 'react';
import { useCategories } from '../hooks/useCategories';
import { CategoryList } from './CategoryList';
import { AddCategoryForm } from './AddCategoryForm';
import type { Category, CategoryType } from '../types';

interface CategoriesPageProps {
  onBack: () => void;
}

export function CategoriesPage({ onBack }: CategoriesPageProps) {
  const { categories, loading, error, refresh } = useCategories();
  const [localCategories, setLocalCategories] = useState<Category[] | null>(null);
  const [addFormOpen, setAddFormOpen] = useState(false);
  const [activeTypeFilter, setActiveTypeFilter] = useState<CategoryType | 'ALL'>('ALL');

  const displayCategories = localCategories ?? categories;

  useEffect(() => {
    if (!loading && displayCategories.length === 0) {
      setAddFormOpen(true);
    }
  }, [loading, displayCategories.length]);

  function handleSuccess(created: Category) {
    setLocalCategories([...(localCategories ?? categories), created]);
    setAddFormOpen(false);
  }

  return (
    <div className="categories-page">
      <header className="dashboard-header">
        <div className="header-left">
          <button className="btn-secondary" onClick={onBack}>← Back</button>
          <h1>Categories</h1>
        </div>
        <button className="btn-primary" onClick={() => setAddFormOpen(true)}>
          + Add Category
        </button>
      </header>

      <CategoryList
        categories={displayCategories}
        loading={loading && localCategories === null}
        error={error}
        activeTypeFilter={activeTypeFilter}
        onTypeFilterChange={setActiveTypeFilter}
        onAddClick={() => setAddFormOpen(true)}
      />

      <AddCategoryForm
        open={addFormOpen}
        onClose={() => setAddFormOpen(false)}
        onSuccess={handleSuccess}
        existingCategories={displayCategories}
      />
    </div>
  );
}
