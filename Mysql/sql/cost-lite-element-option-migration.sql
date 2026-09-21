-- 轻量计费平台要素选项来源迁移
-- 用途：已有 cost_platform_lite 数据库补齐“规则编辑器选项来源”字段和字典。
-- 说明：只保存业务字典/主数据的目录编码与适配配置，不复制业务系统数据明细。
-- MySQL 8.0+

set names utf8mb4;

-- ---------------------------------------------------------
-- cost_variable.option_source_type
-- ---------------------------------------------------------
set @cost_variable_table_exists = (
  select count(*)
  from information_schema.tables
  where table_schema = database()
    and table_name = 'cost_variable'
);
set @cost_variable_option_source_type_ddl = if(
  @cost_variable_table_exists = 0,
  'select 1',
  if(
    (select count(*)
     from information_schema.columns
     where table_schema = database()
       and table_name = 'cost_variable'
       and column_name = 'option_source_type') = 0,
    'alter table cost_variable add column option_source_type varchar(32) not null default ''NONE'' comment ''规则编辑器选项来源，例如NONE、PLATFORM_DICT、BUSINESS_DICT、BUSINESS_MASTER'' after dict_type',
    'select 1'
  )
);
prepare cost_variable_option_source_type_stmt from @cost_variable_option_source_type_ddl;
execute cost_variable_option_source_type_stmt;
deallocate prepare cost_variable_option_source_type_stmt;

-- ---------------------------------------------------------
-- cost_variable.option_source_code
-- ---------------------------------------------------------
set @cost_variable_option_source_code_ddl = if(
  @cost_variable_table_exists = 0,
  'select 1',
  if(
    (select count(*)
     from information_schema.columns
     where table_schema = database()
       and table_name = 'cost_variable'
       and column_name = 'option_source_code') = 0,
    'alter table cost_variable add column option_source_code varchar(128) not null default '''' comment ''宿主业务字典或主数据目录编码'' after option_source_type',
    'select 1'
  )
);
prepare cost_variable_option_source_code_stmt from @cost_variable_option_source_code_ddl;
execute cost_variable_option_source_code_stmt;
deallocate prepare cost_variable_option_source_code_stmt;

-- ---------------------------------------------------------
-- cost_variable.option_config_json
-- ---------------------------------------------------------
set @cost_variable_option_config_json_ddl = if(
  @cost_variable_table_exists = 0,
  'select 1',
  if(
    (select count(*)
     from information_schema.columns
     where table_schema = database()
       and table_name = 'cost_variable'
       and column_name = 'option_config_json') = 0,
    'alter table cost_variable add column option_config_json json default null comment ''选项提供方扩展配置，不保存业务主数据明细'' after option_source_code',
    'select 1'
  )
);
prepare cost_variable_option_config_json_stmt from @cost_variable_option_config_json_ddl;
execute cost_variable_option_config_json_stmt;
deallocate prepare cost_variable_option_config_json_stmt;

-- 将旧版本的 DICT 变量补齐为平台字典选项来源；只更新可明确判断的旧数据。
update cost_variable
set option_source_type = 'PLATFORM_DICT',
    option_source_code = trim(dict_type)
where option_source_type = 'NONE'
  and trim(dict_type) <> ''
  and (upper(source_type) = 'DICT' or upper(variable_type) = 'DICT');

-- ---------------------------------------------------------
-- 选项来源字典
-- ---------------------------------------------------------
insert into sys_dict_type
  (dict_name, dict_type, status, create_by, create_time, remark)
select '核算-要素选项来源类型', 'cost_variable_option_source_type', '0', 'cost-lite', current_timestamp,
       '规则编辑器加载要素选项的来源'
where not exists (
  select 1 from sys_dict_type where dict_type = 'cost_variable_option_source_type'
);

insert into sys_dict_data
  (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select seed.dict_sort, seed.dict_label, seed.dict_value, seed.dict_type, null, seed.list_class,
       seed.is_default, '0', 'cost-lite', current_timestamp, seed.remark
from (
  select 1 dict_sort, '无选项' dict_label, 'NONE' dict_value, 'cost_variable_option_source_type' dict_type,
         'info' list_class, 'Y' is_default, '使用手工输入或运行上下文值' remark
  union all
  select 2, '轻量平台字典', 'PLATFORM_DICT', 'cost_variable_option_source_type',
         'success', 'N', '从轻量计费库 cost_* 字典加载'
  union all
  select 3, '业务系统字典', 'BUSINESS_DICT', 'cost_variable_option_source_type',
         'warning', 'N', '由宿主业务系统字典适配器加载'
  union all
  select 4, '业务主数据', 'BUSINESS_MASTER', 'cost_variable_option_source_type',
         'primary', 'N', '由宿主业务系统分页主数据适配器加载'
) seed
where not exists (
  select 1
  from sys_dict_data current_data
  where current_data.dict_type = seed.dict_type
    and current_data.dict_value = seed.dict_value
);
