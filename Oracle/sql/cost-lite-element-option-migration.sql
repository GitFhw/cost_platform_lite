-- Cost Lite Oracle: bring an existing database to the typed option-source contract.
-- Safe to run repeatedly. The schema file contains the same columns for new installs.

DECLARE
  v_count NUMBER;
BEGIN
  SELECT COUNT(1) INTO v_count FROM user_tab_columns
   WHERE table_name = 'COST_VARIABLE' AND column_name = 'OPTION_SOURCE_TYPE';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE cost_variable ADD (option_source_type VARCHAR2(32 CHAR) DEFAULT ''NONE'')';
  END IF;

  SELECT COUNT(1) INTO v_count FROM user_tab_columns
   WHERE table_name = 'COST_VARIABLE' AND column_name = 'OPTION_SOURCE_CODE';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE cost_variable ADD (option_source_code VARCHAR2(128 CHAR) DEFAULT NULL)';
  END IF;

  SELECT COUNT(1) INTO v_count FROM user_tab_columns
   WHERE table_name = 'COST_VARIABLE' AND column_name = 'OPTION_CONFIG_JSON';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE cost_variable ADD (option_config_json CLOB DEFAULT NULL)';
  END IF;
END;
/

UPDATE cost_variable
   SET option_source_type = 'PLATFORM_DICT',
       option_source_code = dict_type
 WHERE (option_source_type IS NULL OR option_source_type = 'NONE')
   AND (UPPER(source_type) = 'DICT' OR UPPER(variable_type) = 'DICT')
   AND dict_type IS NOT NULL;

UPDATE cost_variable
   SET option_source_type = 'NONE'
 WHERE option_source_type IS NULL;

MERGE INTO sys_dict_type target
USING (SELECT '核算-要素选项来源类型' dict_name,
              'cost_variable_option_source_type' dict_type,
              '规则编辑器加载要素选项的来源' remark
         FROM dual) seed
ON (target.dict_type = seed.dict_type)
WHEN NOT MATCHED THEN INSERT
  (dict_name, dict_type, status, create_by, create_time, remark)
VALUES
  (seed.dict_name, seed.dict_type, '0', 'cost-lite', SYSTIMESTAMP, seed.remark);

MERGE INTO sys_dict_data target
USING (
  SELECT 1 dict_sort, '无选项' dict_label, 'NONE' dict_value,
         'cost_variable_option_source_type' dict_type, 'info' list_class, 'Y' is_default,
         '使用手工输入或运行上下文值' remark FROM dual
  UNION ALL SELECT 2, '轻量平台字典', 'PLATFORM_DICT', 'cost_variable_option_source_type', 'success', 'N', '从轻量计费库字典加载' FROM dual
  UNION ALL SELECT 3, '业务系统字典', 'BUSINESS_DICT', 'cost_variable_option_source_type', 'warning', 'N', '由宿主业务系统字典适配器加载' FROM dual
  UNION ALL SELECT 4, '业务主数据', 'BUSINESS_MASTER', 'cost_variable_option_source_type', 'primary', 'N', '由宿主业务系统分页主数据适配器加载' FROM dual
) seed
ON (target.dict_type = seed.dict_type AND target.dict_value = seed.dict_value)
WHEN NOT MATCHED THEN INSERT
  (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
VALUES
  (seed.dict_sort, seed.dict_label, seed.dict_value, seed.dict_type, NULL, seed.list_class,
   seed.is_default, '0', 'cost-lite', SYSTIMESTAMP, seed.remark);
