import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';

/**
 * Is this a phone-sized screen?
 *
 * **This is the only definition of "phone" in the app.** Everything that arranges itself
 * differently on a small screen asks this one question, and nothing anywhere hard-codes a width.
 *
 * WHY THAT MATTERS MORE THAN IT LOOKS. The two layouts are complements of a single condition: the
 * phone arrangement appears exactly where the desktop one disappears. If a second component picks
 * its own threshold — 640px here, 600 there — a band of widths opens up in which the phone
 * navigation has already gone and the desktop navigation has not yet arrived, and an operator in
 * that band has no navigation at all. Nobody finds that bug, because finding it means dragging a
 * window edge slowly, which nobody does.
 *
 * So: import this. Do not write `useMediaQuery('(max-width:600px)')`, and do not reach for
 * `theme.breakpoints` with a different key. If a component genuinely needs a different boundary,
 * that is a change to the contract in
 * `specs/025-mobile-layout/contracts/layout-contract.md`, not a local decision.
 *
 * The boundary is MUI's `sm`, which is 600px in the installed MUI 9.0.1 — checked in node_modules
 * rather than assumed, because a default that moved between major versions would silently move the
 * whole layout. `down('sm')` is `max-width: 599.95px`, so 600 itself is desktop.
 *
 * `noSsr` because this app has no server rendering: without it the first render always answers
 * "not a phone" and then corrects itself, which on a phone is a visible flash of the desktop
 * header.
 */
export function useIsPhone(): boolean {
  const theme = useTheme();
  return useMediaQuery(theme.breakpoints.down('sm'), { noSsr: true });
}
