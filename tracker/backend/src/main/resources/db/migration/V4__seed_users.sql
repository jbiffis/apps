-- Seed initial users. The password hash here is a bcrypt of the throwaway
-- placeholder string "changeme-on-first-login" — Carley and Jeremy MUST run
-- the set-password ritual described in tracker/docs/PRIVACY.md before this
-- instance handles real data.
--
-- Why a real hash and not NULL: NOT NULL constraint + simpler local-dev story
-- (you can log in immediately with the placeholder password while wiring the
-- frontend). On prod, the set-password ritual replaces both rows' hashes
-- before the app is exposed to the public URL.

INSERT INTO users (username, display_name, password_hash, gender) VALUES
    ('carley', 'Carley',
     '$2b$10$TprcmORkBZ4brRj8t5IBM.KPwpMQk4KgiE.p/5mjfKdzjcCWl6.kS', 'female'),
    ('jeremy', 'Jeremy',
     '$2b$10$TprcmORkBZ4brRj8t5IBM.KPwpMQk4KgiE.p/5mjfKdzjcCWl6.kS', 'male');
