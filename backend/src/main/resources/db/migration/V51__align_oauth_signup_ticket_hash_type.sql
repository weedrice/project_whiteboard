alter table oauth_signup_tickets
    alter column ticket_hash type varchar(64)
    using ticket_hash::varchar(64);
