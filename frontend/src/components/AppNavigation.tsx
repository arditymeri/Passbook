import { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';
import MenuIcon from '@mui/icons-material/Menu';
import { useIsPhone } from '../hooks/useIsPhone';

export interface NavDestination {
  label: string;
  onClick: () => void;
}

interface AppNavigationProps {
  /** Places you can go. A row of buttons on desktop; behind a drawer on a phone. */
  destinations: NavDestination[];
  /**
   * Things you do rather than places you go — adding an expense, adding an income. These stay in
   * the bar at every width: they are what the app is opened to do, and burying the two most
   * frequent actions behind a menu to save 40px would be a worse phone app than the one this
   * feature is fixing.
   */
  actions: NavDestination[];
}

/**
 * The header, in its two arrangements.
 *
 * Extracted out of App.tsx rather than branched inside it: the desktop arrangement is eleven
 * buttons written out longhand, and a second arrangement inline would have made a 377-line
 * component unreadable.
 *
 * THE TWO ARRANGEMENTS ARE COMPLEMENTS OF ONE CONDITION — `useIsPhone()`, and nothing else. That is
 * deliberate and it is the whole reason that hook exists: there is no width at which the drawer has
 * not appeared and the button row has already gone, because they are the two halves of a single
 * ternary rather than two independent media queries that happen to agree today.
 *
 * ONE DESKTOP-VISIBLE CHANGE, made knowingly. The button row wraps (`flexWrap: 'wrap'`) where it
 * previously overflowed. This feature's own check found that the existing header is 1284px wide and
 * so scrolls sideways at exactly 1280 — the width this project calls desktop — which is the bug
 * SC-001 forbids, not a design decision worth preserving. Above ~1290px nothing about the rendering
 * changes; at and below it, a button moves to a second line instead of off the screen.
 */
export function AppNavigation({ destinations, actions }: AppNavigationProps) {
  const isPhone = useIsPhone();
  const [drawerOpen, setDrawerOpen] = useState(false);

  /**
   * Every drawer entry closes the drawer as well as doing its job. A destination that leaves the
   * drawer covering the screen it just opened is worse than no drawer: the operator taps
   * "Budgeting", sees the same menu, and taps it again.
   */
  function run(destination: NavDestination) {
    setDrawerOpen(false);
    destination.onClick();
  }

  const outlined = {
    borderColor: 'rgba(255,255,255,0.5)',
  } as const;

  return (
    <AppBar position="static" color="primary" elevation={2}>
      <Toolbar sx={{ flexWrap: 'wrap', rowGap: 1, py: { xs: 1, sm: 0 } }}>
        {isPhone && (
          <IconButton
            edge="start"
            color="inherit"
            aria-label="Open navigation"
            onClick={() => setDrawerOpen(true)}
            // 44px is the smallest target a finger hits reliably (FR-010, SC-005). MUI's default
            // IconButton padding gets close; this states it rather than hoping.
            sx={{ mr: 1, minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}
          >
            <MenuIcon />
          </IconButton>
        )}

        <Typography
          variant="h6"
          // minWidth: 0 lets the title shrink instead of forcing the bar wider than the screen —
          // a flex item's default minimum is its content, which is how one long word overflows a
          // whole page.
          sx={{ flexGrow: 1, fontWeight: 700, minWidth: 0 }}
          noWrap
        >
          {isPhone ? 'Passbook' : 'Passbook Dashboard'}
        </Typography>

        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          {!isPhone && destinations.map((d) => (
            <Button key={d.label} color="inherit" variant="outlined" sx={outlined} onClick={d.onClick}>
              {d.label}
            </Button>
          ))}
          {actions.map((a) => (
            <Button
              key={a.label}
              color="inherit"
              variant="contained"
              sx={{ bgcolor: 'secondary.main', minHeight: { xs: 44, sm: 'auto' }, whiteSpace: 'nowrap' }}
              onClick={a.onClick}
            >
              {a.label}
            </Button>
          ))}
        </Stack>
      </Toolbar>

      <Drawer anchor="right" open={drawerOpen} onClose={() => setDrawerOpen(false)}>
        <Box sx={{ width: 260 }} role="presentation">
          <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 1.5 }}>
            <Typography variant="subtitle1" sx={{ flexGrow: 1, fontWeight: 700 }}>
              Passbook
            </Typography>
            <IconButton
              aria-label="Close navigation"
              onClick={() => setDrawerOpen(false)}
              sx={{ minWidth: { xs: 44, sm: 'auto' }, minHeight: { xs: 44, sm: 'auto' } }}
            >
              <CloseIcon />
            </IconButton>
          </Box>
          <Divider />
          <List>
            {destinations.map((d) => (
              <ListItem key={d.label} disablePadding>
                {/*
                  A ListItemButton is a button in the accessibility tree, which is what lets the
                  layout check find these by the same role and label it uses for the desktop row —
                  so one navigateTo() drives both arrangements and neither can rot unnoticed.
                */}
                <ListItemButton onClick={() => run(d)} sx={{ minHeight: 48 }}>
                  <ListItemText primary={d.label} />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        </Box>
      </Drawer>
    </AppBar>
  );
}
