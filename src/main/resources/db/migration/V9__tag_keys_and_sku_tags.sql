-- Ключ без регистра, пробелов, дефисов и подчеркиваний: Tag, tAg и TAG - один и тот же тег
alter table tag add norm_key varchar(200);
update tag set norm_key = lower(replace(replace(replace(tag_value, ' ', ''), '-', ''), '_', ''));
alter table tag alter column norm_key set not null;
create unique index uq_tag_norm_key on tag (norm_key);

-- Теги SKU (рынки, сегменты, темы и любые другие теги из справочника)
create table sku_tag (
    sku_id bigint not null,
    tag_id bigint not null,
    primary key (sku_id, tag_id),
    constraint fk_sku_tag_sku foreign key (sku_id) references sku (id),
    constraint fk_sku_tag_tag foreign key (tag_id) references tag (id)
);
create index ix_sku_tag_tag on sku_tag (tag_id);
