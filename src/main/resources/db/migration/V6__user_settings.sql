create table user_setting (
    user_id             bigint      primary key,
    menu_layout         varchar(8)  not null default 'TOP',
    sidebar_collapsed   boolean     not null default false,
    density             varchar(8)  not null default 'NORMAL',
    start_page          varchar(16) not null default 'HOME',
    catalog_only_mine   boolean     not null default false,
    catalog_include_eol boolean     not null default false,
    constraint fk_user_setting_user foreign key (user_id) references app_user (id)
);
