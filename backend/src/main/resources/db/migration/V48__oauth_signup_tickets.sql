create table oauth_signup_tickets (
    ticket_hash char(64) primary key,
    email varchar(100) not null,
    display_name varchar(255),
    provider varchar(255) not null,
    provider_id varchar(255) not null,
    expires_at timestamp(6) not null
);

create index idx_oauth_signup_tickets_expires_at
    on oauth_signup_tickets (expires_at);
