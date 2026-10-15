-- Совладельцы SKU: у SKU может быть несколько владельцев. Поле sku.owner_id остается основным владельцем
-- (получает автоматические задачи и вопросы), все владельцы, включая основного, лежат в sku_owner.
create table sku_owner (
    sku_id  bigint not null,
    user_id bigint not null,
    primary key (sku_id, user_id),
    constraint fk_sku_owner_sku foreign key (sku_id) references sku (id),
    constraint fk_sku_owner_user foreign key (user_id) references app_user (id)
);
create index ix_sku_owner_user on sku_owner (user_id);

-- Распределение по семействам (то же правило задано в application.yml для новых SKU при импорте)
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('kuznetsov', 'smith')
where l.family = 'Wi-Fi';
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('osokin', 'nagornova')
where l.family = 'Media';
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('polukhin', 'litosh', 'maslov')
where l.family = 'MES';
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('polukhin', 'litosh', 'maslov')
where l.family = 'ESR';
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('polukhin', 'litosh')
where l.family = 'PON';
insert into sku_owner (sku_id, user_id)
select s.id, u.id from sku s join product_line l on l.id = s.line_id join app_user u on u.login in ('golovanov')
where l.family = 'VoIP';

-- Основной владелец: по кругу среди владельцев семейства в порядке линеек (сортировка, затем код)
update sku set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = sku.line_id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'kuznetsov' else 'smith' end)
where line_id in (select id from product_line where family = 'Wi-Fi');
update product_line set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = product_line.id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'kuznetsov' else 'smith' end)
where family = 'Wi-Fi';
update sku set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = sku.line_id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'osokin' else 'nagornova' end)
where line_id in (select id from product_line where family = 'Media');
update product_line set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = product_line.id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'osokin' else 'nagornova' end)
where family = 'Media';
update sku set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = sku.line_id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 3) when 0 then 'polukhin' when 1 then 'litosh' else 'maslov' end)
where line_id in (select id from product_line where family = 'MES');
update product_line set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = product_line.id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 3) when 0 then 'polukhin' when 1 then 'litosh' else 'maslov' end)
where family = 'MES';
update sku set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = sku.line_id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 3) when 0 then 'polukhin' when 1 then 'litosh' else 'maslov' end)
where line_id in (select id from product_line where family = 'ESR');
update product_line set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = product_line.id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 3) when 0 then 'polukhin' when 1 then 'litosh' else 'maslov' end)
where family = 'ESR';
update sku set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = sku.line_id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'polukhin' else 'litosh' end)
where line_id in (select id from product_line where family = 'PON');
update product_line set owner_id = (select u.id from app_user u where u.login = case mod((select count(*) from product_line l2 join product_line l on l.id = product_line.id where l2.family = l.family and (l2.sort_order < l.sort_order or (l2.sort_order = l.sort_order and l2.code < l.code))), 2) when 0 then 'polukhin' else 'litosh' end)
where family = 'PON';
update sku set owner_id = (select u.id from app_user u where u.login = 'golovanov')
where line_id in (select id from product_line where family = 'VoIP');
update product_line set owner_id = (select u.id from app_user u where u.login = 'golovanov')
where family = 'VoIP';

-- Основной владелец всегда входит в список владельцев (в том числе у семейств без распределения)
insert into sku_owner (sku_id, user_id)
select s.id, s.owner_id from sku s
where not exists (select 1 from sku_owner so where so.sku_id = s.id and so.user_id = s.owner_id);
