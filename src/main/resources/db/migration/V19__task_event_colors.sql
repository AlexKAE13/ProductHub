-- Цвета колонок в записях о перемещении карточек: в журнале названия колонок показываются в их цвете.
alter table task_event add column color1 varchar(16);
alter table task_event add column color2 varchar(16);
