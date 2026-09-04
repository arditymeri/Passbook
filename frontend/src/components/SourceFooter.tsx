import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { fetchSystemVersion } from '../api/client';

const UPSTREAM_SOURCE_URL = 'https://github.com/arditymeri/Passbook';
const LICENSE_URL = 'https://www.gnu.org/licenses/agpl-3.0.html';

/**
 * Offers every user of this instance the source of the version they are using, and names that
 * version.
 *
 * AGPL-3.0 section 13 requires that anyone interacting with the app remotely over a network be
 * given a way to obtain the source of the *running* version — not merely of the upstream project.
 * Operators who deploy a modified Passbook must therefore point this at their own repository by
 * setting `VITE_SOURCE_URL` at build time; it falls back to upstream for unmodified deployments.
 *
 * The version comes from the backend (`GET /system/version`), not from the frontend build: the
 * two can diverge, and it is the running backend an operator needs to identify (feature 021,
 * FR-013). It renders on every view because this footer does. If the request fails — offline, or
 * an older backend without the endpoint — the footer renders without it rather than breaking:
 * the licence and source link are the part that must never disappear.
 */
export function SourceFooter() {
  const sourceUrl = import.meta.env.VITE_SOURCE_URL || UPSTREAM_SOURCE_URL;
  const [version, setVersion] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchSystemVersion()
      .then((v) => { if (!cancelled) setVersion(v.version); })
      .catch(() => { /* footer stays useful without a version */ });
    return () => { cancelled = true; };
  }, []);

  return (
    <Box component="footer" sx={{ py: 3, mt: 'auto' }}>
      <Container maxWidth="lg">
        <Typography variant="body2" align="center" sx={{ color: 'text.secondary' }}>
          Passbook{version ? ` v${version}` : ''} — free software under the{' '}
          <Link href={LICENSE_URL} target="_blank" rel="noopener noreferrer" color="inherit">
            AGPL&#8209;3.0
          </Link>
          .{' '}
          <Link href={sourceUrl} target="_blank" rel="noopener noreferrer" color="inherit">
            Get the source code
          </Link>
          .
        </Typography>
      </Container>
    </Box>
  );
}
