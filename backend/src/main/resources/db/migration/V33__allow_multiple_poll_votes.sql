ALTER TABLE poll_votes
    DROP CONSTRAINT IF EXISTS uk_poll_votes_single_choice;

ALTER TABLE poll_votes
    ADD CONSTRAINT uk_poll_votes_user_option UNIQUE (poll_id, user_id, option_id);
