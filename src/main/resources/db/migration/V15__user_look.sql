alter table user_setting add column ui_theme       varchar(8)  not null default 'CLASSIC';
alter table user_setting add column color_mode     varchar(8)  not null default 'AUTO';
alter table user_setting add column palette        varchar(12) not null default 'ELTEX';
alter table user_setting add column background     varchar(8)  not null default 'MESH';
alter table user_setting add column wallpaper      varchar(16) not null default 'dunes';
alter table user_setting add column wallpaper_key  varchar(80);
alter table user_setting add column wall_blur      integer     not null default 18;
alter table user_setting add column wall_dim       integer     not null default 0;
