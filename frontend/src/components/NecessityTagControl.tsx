import { useState } from 'react';
import type * as React from 'react';
import Chip from '@mui/material/Chip';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import type { NecessityTag } from '../types';
import { updateBillNecessityTag } from '../api/client';

interface NecessityTagControlProps {
  billId: string;
  tag?: NecessityTag | null;
  onChanged: (tag: NecessityTag | null) => void;
}

const TAG_LABELS: Record<NecessityTag, string> = {
  NECESSARY: 'Necessary',
  AVOIDABLE: 'Avoidable',
  UNNECESSARY: 'Unnecessary',
};

const TAG_COLORS: Record<NecessityTag, 'default' | 'warning' | 'error'> = {
  NECESSARY: 'default',
  AVOIDABLE: 'warning',
  UNNECESSARY: 'error',
};

const TAG_OPTIONS: NecessityTag[] = ['NECESSARY', 'AVOIDABLE', 'UNNECESSARY'];

export function NecessityTagControl({ billId, tag, onChanged }: NecessityTagControlProps) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [saving, setSaving] = useState(false);

  function openMenu(e: React.MouseEvent<HTMLElement>) {
    setAnchor(e.currentTarget);
  }

  function closeMenu() {
    setAnchor(null);
  }

  async function select(newTag: NecessityTag | null) {
    closeMenu();
    setSaving(true);
    try {
      const updated = await updateBillNecessityTag(billId, newTag);
      onChanged(updated.necessityTag ?? null);
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <Chip
        label={tag ? TAG_LABELS[tag] : 'Tag'}
        color={tag ? TAG_COLORS[tag] : 'default'}
        variant={tag ? 'filled' : 'outlined'}
        size="small"
        onClick={openMenu}
        disabled={saving}
      />
      <Menu anchorEl={anchor} open={!!anchor} onClose={closeMenu}>
        {TAG_OPTIONS.map((option) => (
          <MenuItem key={option} onClick={() => select(option)}>{TAG_LABELS[option]}</MenuItem>
        ))}
        {tag && <MenuItem onClick={() => select(null)}>Clear tag</MenuItem>}
      </Menu>
    </>
  );
}
