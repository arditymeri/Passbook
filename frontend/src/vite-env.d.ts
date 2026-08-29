/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Repository URL offered to users by {@link SourceFooter}, for AGPL-3.0 section 13.
   * Operators running a MODIFIED Passbook must set this to their own repository so users are
   * offered the source of the version actually running. Defaults to upstream when unset.
   */
  readonly VITE_SOURCE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
