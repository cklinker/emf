-- ---------------------------------------------------------------------------
-- Support mailbox: separate what the sender claimed an attachment was from what
-- it actually is.
--
-- V191 declared content_type as "Determined by our own sniffing, never taken
-- from the MIME part header" and then stored exactly that header, because no
-- sniffing existed. AttachmentContentType now supplies the verdict, and this
-- migration gives the sender's claim a column of its own rather than letting it
-- keep occupying the one reserved for ours.
--
-- Both are kept. The claim is not worthless — a disagreement between the two is
-- the single most useful signal an operator has that an attachment is trying to
-- be something other than what it says, and discarding it would throw that away.
--
-- No permission rows and no RLS changes: mailbox_attachment already carries both
-- from V191, and adding a column does not alter either.
-- ---------------------------------------------------------------------------

ALTER TABLE mailbox_attachment
    ADD COLUMN IF NOT EXISTS declared_content_type character varying(200);

COMMENT ON COLUMN mailbox_attachment.declared_content_type IS
    'The Content-Type the sender put in the MIME part. Untrusted: retained for '
    'display and for comparison against content_type, never used to serve bytes.';

COMMENT ON COLUMN mailbox_attachment.content_type IS
    'Determined by AttachmentContentType.sniff() from the leading bytes. This is '
    'the value the download path reasons about; it is still never sent verbatim '
    'as a response Content-Type — see AttachmentContentType.serveAs().';

-- Existing rows hold the sender's claim in content_type, because that is what was
-- stored before sniffing existed. Move it to the column it belongs in rather than
-- leaving it to masquerade as a verdict this system never made.
--
-- content_type is NOT NULL, so it cannot simply be cleared. Rows are marked
-- application/octet-stream: it is the honest answer for content nothing has
-- inspected, and it is what serveAs() would return for them anyway. Re-sniffing
-- historical rows would mean fetching every stored object, which is a backfill
-- job and not a migration.
UPDATE mailbox_attachment
   SET declared_content_type = content_type,
       content_type = 'application/octet-stream',
       updated_at = now()
 WHERE declared_content_type IS NULL;
