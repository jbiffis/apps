-- Switch login identity from username → email, and force a password change on
-- first login.
--
-- 1. Add `email` (backfilled from the two seeded accounts), then make it the
--    NOT NULL UNIQUE login key and drop `username`. `display_name` still drives
--    what the UI shows ("Carley"/"Jeremy"); `email` is purely the login id.
-- 2. Add `must_change_password` (default true). Both seeded rows get a bcrypt
--    of the throwaway password "password"; the app forces a real password on
--    first login, after which the flag is cleared.

ALTER TABLE users ADD COLUMN email text;

UPDATE users SET email = 'jeremy@biffis.com' WHERE username = 'jeremy';
UPDATE users SET email = 'carley401@gmail.com' WHERE username = 'carley';

ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE users DROP COLUMN username;

ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT true;

-- Reset both accounts to the throwaway temp password + force change.
UPDATE users
   SET password_hash = '$2b$10$7pH3K99rEV.pPuyjCj2Q..J.MOCeVcz9cTK2SdwMIOx6MNkUGtFwa',
       must_change_password = true
 WHERE email IN ('jeremy@biffis.com', 'carley401@gmail.com');

COMMENT ON COLUMN users.email IS 'Login identity (case-insensitive). Replaced username in V7.';
COMMENT ON COLUMN users.must_change_password IS 'True until the user sets their own password via /api/auth/change-password.';
