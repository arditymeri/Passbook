import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';

const UPSTREAM_SOURCE_URL = 'https://github.com/arditymeri/MyFinance';
const LICENSE_URL = 'https://www.gnu.org/licenses/agpl-3.0.html';

/**
 * Offers every user of this instance the source of the version they are using.
 *
 * AGPL-3.0 section 13 requires that anyone interacting with the app remotely over a network be
 * given a way to obtain the source of the *running* version — not merely of the upstream project.
 * Operators who deploy a modified MyFinance must therefore point this at their own repository by
 * setting `VITE_SOURCE_URL` at build time; it falls back to upstream for unmodified deployments.
 */
export function SourceFooter() {
  const sourceUrl = import.meta.env.VITE_SOURCE_URL || UPSTREAM_SOURCE_URL;

  return (
    <Box component="footer" sx={{ py: 3, mt: 'auto' }}>
      <Container maxWidth="lg">
        <Typography variant="body2" align="center" sx={{ color: 'text.secondary' }}>
          MyFinance — free software under the{' '}
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
