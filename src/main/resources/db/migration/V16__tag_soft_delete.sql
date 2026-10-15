alter table tag add column deleted boolean not null default false;
alter table tag add column deleted_info varchar(500);
