-- Development seed data.
--
-- CurrentUserProvider hardcodes user id 1 until auth exists, and the "add
-- member" form looks people up by email, so both need rows to point at.
-- TODO(auth): drop this migration, or gate it behind a dev-only Flyway
-- location, once real registration exists.
INSERT INTO "users" (name, email, password)
VALUES ('Dev User', 'dev@splitwise.local', 'not-a-real-password'),
       ('Ana Reyes', 'ana@splitwise.local', 'not-a-real-password'),
       ('Miguel Cruz', 'miguel@splitwise.local', 'not-a-real-password'),
       ('Joy Santos', 'joy@splitwise.local', 'not-a-real-password');
