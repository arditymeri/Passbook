# Research: Material UI Migration

**Feature**: 006-mui-migration | **Date**: 2026-05-25

## Decision 1: MUI Version

**Decision**: MUI v5 (`@mui/material ^5.x`)

**Rationale**: MUI v5 is the current stable release. It is fully compatible with React 18 and TypeScript. It has first-class Vite support via emotion's runtime — no Babel plugin or special Vite plugin required. MUI v6 is in development but not yet stable at time of writing.

**Alternatives considered**:
- MUI v6 (alpha/beta): Rejected — not production-stable.
- Ant Design: Rejected — heavier, less idiomatic with React, different visual language.
- Chakra UI: Rejected — less component breadth; MUI has a more complete suite matching all existing component needs (tables, progress bars, select, date, modals).

---

## Decision 2: Required Package Set

**Decision**: Install exactly:
```
@mui/material
@emotion/react
@emotion/styled
@mui/icons-material
```

**Rationale**: `@emotion/react` and `@emotion/styled` are required peer dependencies of `@mui/material`. Without them, MUI components will not render. `@mui/icons-material` provides Material Design icons needed for navigation arrows, status icons, and action buttons. All four are tree-shaken by Vite — only used components are bundled.

**Alternatives considered**:
- Styled-components instead of emotion: Rejected — MUI v5 defaults to emotion; switching requires additional MUI config and adds complexity.
- Skipping icons package: Rejected — the MonthNav arrows, form submit icons, and status badges all benefit from crisp SVG icons from the same design system.

---

## Decision 3: Theming Approach

**Decision**: Create a minimal `frontend/src/theme.ts` that wraps `createTheme` with a finance-appropriate palette (blue primary, neutral background). Apply via `ThemeProvider` at the root in `App.tsx`.

**Rationale**: Centralising the theme in one file keeps all visual constants in one place and makes future colour changes trivial. The default MUI blue (`#1976d2`) is close to the existing app's `#1a1a2e` dark blue; a small palette tweak is all that's needed.

**Alternatives considered**:
- Inline `sx` props everywhere, no ThemeProvider: Rejected — loses consistency guarantee and makes global changes painful.
- Full custom design system: Rejected — out of scope; this is a migration not a rebrand.

---

## Decision 4: Migration Strategy

**Decision**: Migrate component-by-component, keeping all TypeScript prop interfaces unchanged. Only the internal JSX is replaced. Priority order: App.tsx shell → SummaryCard → MonthNav → BudgetStatus → CategorySpend → RecentTransactions → Modal → AddBillForm → AddIncomeForm → AddCategoryForm → CategoryList → CategoriesPage.

**Rationale**: Keeping prop interfaces stable means zero changes are needed in parent components during migration. Each component can be migrated and visually verified independently. This is the lowest-risk approach for a pure presentational migration.

**Alternatives considered**:
- Rewrite all components in one pass: Rejected — increases blast radius; a bug in one component is harder to isolate.
- Introduce new MUI wrapper components alongside old ones: Rejected — unnecessary complexity, migration is straightforward enough to do in-place.

---

## Decision 5: CSS Strategy

**Decision**: Delete `App.css` entirely. Reduce `index.css` to a minimal global reset (box-sizing, body margin). Use MUI `Box`, `Container`, `Stack`, and `Grid` for all layout. Use MUI `sx` prop for component-specific tweaks.

**Rationale**: MUI manages its own CSS-in-JS via emotion. Mixing a large hand-written CSS file with MUI components creates specificity conflicts and maintenance burden. Removing the custom CSS is required to achieve consistent styling.

**Alternatives considered**:
- Keep App.css alongside MUI: Rejected — CSS class selectors will conflict with MUI's emotion-injected styles, producing unpredictable specificity battles.
- Use MUI `styled()` for all styling: Rejected — `sx` prop is sufficient for component-level tweaks and is more concise for one-off adjustments.

---

## Decision 6: Currency Amount Fields (Constitution Principle IV Compliance)

**Decision**: All `amount` form fields use `TextField` with `type="text"` and `value={amount}` where `amount` is a `string` state variable. The string is only converted to a number at the API call boundary via `parseFloat`.

**Rationale**: Constitution Principle IV forbids floating-point types for monetary values. React `<input type="number">` returns a `number` which is a JavaScript IEEE-754 double. Keeping the field as `type="text"` with string state avoids any floating-point representation in the UI layer.

**Alternatives considered**:
- `type="number"` TextField: Rejected — violates Principle IV; JavaScript number is floating-point.
- Decimal.js in the frontend: Rejected — over-engineering for a display-layer form; string → parseFloat at the API boundary is sufficient since the backend uses BigDecimal.

---

## Decision 7: Modal Implementation

**Decision**: Replace the custom `Modal.tsx` wrapper with MUI `Dialog` + `DialogTitle` + `DialogContent` + `DialogActions`. The `Modal` component is re-implemented internally; prop interface (`open`, `onClose`, `title`, `children`) stays identical.

**Rationale**: MUI `Dialog` provides built-in accessibility (focus trap, aria-modal, Escape key), scroll locking, and consistent backdrop styling — all features the custom `Modal.tsx` currently implements manually. Using MUI Dialog removes ~40 lines of hand-written event-handler and DOM code.

**Alternatives considered**:
- Keep custom Modal, only style it with MUI inside: Rejected — duplicates MUI Dialog's accessibility infrastructure and creates a hybrid that's harder to maintain.
