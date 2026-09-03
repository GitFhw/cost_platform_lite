-- Cost Lite MySQL schema baseline
-- Source: mother cost_init.sql; generated without DROP TABLE statements.
-- Frozen on: 2026-09-02
-- MySQL 8.0+; run in a new schema or verify an existing schema before use.

set names utf8mb4;
set foreign_key_checks = 0;

-- =========================================================
-- cost_scene
-- =========================================================
create table if not exists cost_scene (
  scene_id                  bigint          not null auto_increment comment '场景主键',
  scene_code                varchar(64)     not null comment '场景编码，用于唯一标识一个核算主题或合同场景',
  scene_name                varchar(128)    not null comment '场景名称，供业务人员识别核算主题',
  business_domain           varchar(64)     not null comment '业务域字典值，对应 cost_business_domain',
  org_code                  varchar(64)     default '' comment '所属组织编码，用于按组织隔离场景',
  scene_type                varchar(32)     default 'CONTRACT' comment '场景类型，例如合同、方案、公司级核算域',
  active_version_id         bigint          default null comment '当前生效版本主键，对应 cost_publish_version.version_id',
  default_object_dimension  varchar(64)     default '' comment '场景默认对象维度',
  status                    char(1)         not null default '0' comment '场景状态（0正常 1停用 2草稿）',
  remark                    varchar(500)    default null comment '场景说明，用于补充业务口径和适用边界',
  create_by                 varchar(64)     default '' comment '创建人',
  create_time               datetime        default current_timestamp comment '创建时间',
  update_by                 varchar(64)     default '' comment '更新人',
  update_time               datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (scene_id),
  unique key uk_cost_scene_code (scene_code),
  key idx_cost_scene_domain_status (business_domain, status),
  key idx_cost_scene_org_status (org_code, status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-场景主数据表';

-- =========================================================
-- cost_fee_item
-- =========================================================
create table if not exists cost_fee_item (
  fee_id                    bigint          not null auto_increment comment '费用主键',
  scene_id                  bigint          not null comment '所属场景主键，对应 cost_scene.scene_id',
  fee_code                  varchar(64)     not null comment '费用编码，用于唯一标识一个费用项',
  fee_name                  varchar(128)    not null comment '费用名称，供业务人员识别费用项',
  fee_category              varchar(64)     default '' comment '费用分类，例如固定薪资、补贴、港杂费、附加费',
  unit_code                 varchar(32)     default '' comment '计价单位编码，例如人、吨、箱、元',
  factor_summary            varchar(255)    default '' comment '影响因素摘要，用于帮助业务快速理解费用依赖',
  scope_description         varchar(255)    default '' comment '适用范围说明，补充当前场景下该费用的适用边界',
  object_dimension          varchar(64)     default '' comment '核算对象维度，例如人、班组、协力公司、船舶',
  sort_no                   int             not null default 0 comment '排序号，用于列表和工作台展示顺序',
  status                    char(1)         not null default '0' comment '费用状态（0正常 1停用）',
  remark                    varchar(500)    default null comment '备注',
  create_by                 varchar(64)     default '' comment '创建人',
  create_time               datetime        default current_timestamp comment '创建时间',
  update_by                 varchar(64)     default '' comment '更新人',
  update_time               datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (fee_id),
  unique key uk_cost_fee_scene_code (scene_id, fee_code),
  key idx_cost_fee_scene_status (scene_id, status),
  key idx_cost_fee_scene_sort (scene_id, sort_no),
  constraint fk_cost_fee_scene foreign key (scene_id) references cost_scene (scene_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-费用主数据表';

-- =========================================================
-- cost_variable_group
-- =========================================================
create table if not exists cost_variable_group (
  group_id                  bigint          not null auto_increment comment '变量分组主键',
  scene_id                  bigint          not null comment '所属场景主键',
  group_code                varchar(64)     not null comment '变量分组编码',
  group_name                varchar(128)    not null comment '变量分组名称',
  sort_no                   int             not null default 0 comment '排序号',
  status                    char(1)         not null default '0' comment '分组状态（0正常 1停用）',
  remark                    varchar(500)    default null comment '备注',
  create_by                 varchar(64)     default '' comment '创建人',
  create_time               datetime        default current_timestamp comment '创建时间',
  update_by                 varchar(64)     default '' comment '更新人',
  update_time               datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (group_id),
  unique key uk_cost_var_group_scene_code (scene_id, group_code),
  key idx_cost_var_group_scene_sort (scene_id, sort_no),
  constraint fk_cost_var_group_scene foreign key (scene_id) references cost_scene (scene_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-变量分组表';

-- =========================================================
-- cost_variable
-- =========================================================
create table if not exists cost_variable (
  variable_id               bigint          not null auto_increment comment '变量主键',
  scene_id                  bigint          not null comment '所属场景主键',
  group_id                  bigint          default null comment '所属变量分组主键',
  variable_code             varchar(64)     not null comment '变量编码，用于规则、公式、快照引用',
  variable_name             varchar(128)    not null comment '变量名称，供业务人员识别',
  variable_type             varchar(32)     not null comment '变量类型，例如NUMBER、TEXT、DICT、REMOTE、FORMULA、BOOLEAN、DATE',
  source_type               varchar(32)     not null comment '变量来源，例如INPUT、DICT、REMOTE、FORMULA',
  source_system             varchar(64)     default '' comment '来源系统标识，例如 WMS、ERP、TMS',
  dict_type                 varchar(64)     default '' comment '字典类型，当变量来源为字典时使用',
  remote_api                varchar(255)    default '' comment '远程接口地址或标识，当变量来源为接口时使用',
  request_method            varchar(16)     default 'GET' comment '第三方接口请求方式，例如GET、POST、PUT、DELETE',
  content_type              varchar(128)    default 'application/json' comment '第三方接口请求内容类型',
  query_config_json         json            default null comment '第三方接口查询参数配置JSON',
  request_headers_json      json            default null comment '第三方接口请求头配置JSON',
  body_template_json        json            default null comment '第三方接口请求体模板JSON',
  auth_type                 varchar(32)     default 'NONE' comment '接口鉴权方式，例如NONE、BASIC、BEARER、API_KEY',
  auth_config_json          json            default null comment '接口鉴权配置 JSON，用于托管请求头、账号、密钥占位信息',
  data_path                 varchar(255)    default '' comment '取值路径，用于从外部数据中提取字段值',
  response_config_json      json            default null comment '第三方接口响应提取配置JSON',
  mapping_config_json       json            default null comment '字段映射配置 JSON，表达第三方字段到平台变量的映射关系',
  page_config_json          json            default null comment '第三方接口分页策略配置JSON',
  sync_mode                 varchar(32)     default 'REALTIME' comment '拉取方式，例如REALTIME、NEAR_REALTIME、SCHEDULED',
  cache_policy              varchar(32)     default 'MANUAL_REFRESH' comment '缓存策略，例如NONE、TTL、MANUAL_REFRESH',
  fallback_policy           varchar(32)     default 'FAIL_FAST' comment '失败兜底策略，例如FAIL_FAST、DEFAULT_VALUE、LAST_SNAPSHOT',
  adapter_type              varchar(32)     default 'STANDARD' comment '第三方接口适配器类型，例如STANDARD、ROOT_ARRAY、PAGE_ENVELOPE、SINGLE_OBJECT',
  adapter_config_json       json            default null comment '第三方接口特殊适配器配置JSON',
  formula_expr              varchar(2000)   default null comment '公式表达式，当变量为公式变量时使用',
  formula_code              varchar(64)     default '' comment '引用的公式编码',
  data_type                 varchar(32)     default 'STRING' comment '数据类型，例如STRING、NUMBER、BOOLEAN、DATE',
  default_value             varchar(255)    default '' comment '默认值',
  precision_scale           int             default 2 comment '数值精度，用于金额或数量变量',
  status                    char(1)         not null default '0' comment '变量状态（0正常 1停用）',
  sort_no                   int             not null default 0 comment '排序号',
  remark                    varchar(500)    default null comment '备注',
  create_by                 varchar(64)     default '' comment '创建人',
  create_time               datetime        default current_timestamp comment '创建时间',
  update_by                 varchar(64)     default '' comment '更新人',
  update_time               datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (variable_id),
  unique key uk_cost_variable_scene_code (scene_id, variable_code),
  key idx_cost_variable_scene_group (scene_id, group_id),
  key idx_cost_variable_scene_status (scene_id, status),
  key idx_cost_variable_scene_source (scene_id, source_type, source_system),
  key idx_cost_variable_scene_formula (scene_id, formula_code),
  constraint fk_cost_variable_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_variable_group foreign key (group_id) references cost_variable_group (group_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-变量主数据表';

-- =========================================================
-- cost_fee_variable_rel
-- =========================================================
create table if not exists cost_fee_variable_rel (
  rel_id                    bigint          not null auto_increment comment '费用与变量关系主键',
  scene_id                  bigint          not null comment '所属场景主键',
  fee_id                    bigint          not null comment '费用主键',
  variable_id               bigint          not null comment '变量主键',
  relation_type             varchar(32)     not null default 'OPTIONAL' comment '关系类型，例如REQUIRED、OPTIONAL、TIER_BASIS、FORMULA_INPUT',
  source_type               varchar(32)     not null default 'RULE_DERIVED' comment '来源类型，例如RULE_DERIVED、MANUAL_REQUIRED',
  source_rule_id            bigint          default null comment '来源规则主键，规则派生关系使用',
  source_code               varchar(128)    not null default '' comment '来源编码，例如规则编码或手工配置编码',
  sort_no                   int             not null default 0 comment '排序号',
  remark                    varchar(500)    default null comment '备注',
  create_by                 varchar(64)     default '' comment '创建人',
  create_time               datetime        default current_timestamp comment '创建时间',
  update_by                 varchar(64)     default '' comment '更新人',
  update_time               datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (rel_id),
  unique key uk_cost_fee_var_rel (fee_id, variable_id, relation_type, source_type, source_code),
  key idx_cost_fee_var_scene_fee (scene_id, fee_id),
  key idx_cost_fee_var_scene_var (scene_id, variable_id),
  key idx_cost_fee_var_source (source_type, source_rule_id, source_code),
  constraint fk_cost_fee_var_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_fee_var_fee foreign key (fee_id) references cost_fee_item (fee_id),
  constraint fk_cost_fee_var_variable foreign key (variable_id) references cost_variable (variable_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-费用与变量适用关系表';

-- =========================================================
-- cost_rule
-- =========================================================
create table if not exists cost_rule (
  rule_id                    bigint          not null auto_increment comment '规则主键',
  scene_id                   bigint          not null comment '所属场景主键',
  fee_id                     bigint          not null comment '所属费用主键',
  rule_code                  varchar(64)     not null comment '规则编码',
  rule_name                  varchar(128)    default '' comment '规则名称，便于业务识别',
  rule_type                  varchar(32)     not null comment '规则类型，例如FIXED_RATE、FIXED_AMOUNT、FORMULA、TIER_RATE、ALLOCATE',
  condition_logic            varchar(16)     not null default 'AND' comment '条件逻辑，例如AND、OR',
  priority                   int             not null default 0 comment '优先级，数值越大越优先',
  quantity_variable_code     varchar(64)     default '' comment '阶梯或公式依赖的数量变量编码',
  pricing_mode               varchar(32)     not null default 'TYPED' comment '定价模式，例如TYPED、ADVANCED_JSON',
  pricing_json               json            default null comment '规则定价结构化配置，承接固定费率、固定金额、阶梯明细、公式等',
  amount_formula             varchar(2000)   default null comment '金额公式表达式',
  amount_formula_code        varchar(64)     default '' comment '金额公式编码',
  note_template              varchar(500)    default '' comment '结果备注模板',
  status                     char(1)         not null default '0' comment '规则状态（0正常 1停用）',
  sort_no                    int             not null default 0 comment '排序号',
  remark                     varchar(500)    default null comment '备注',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (rule_id),
  unique key uk_cost_rule_scene_code (scene_id, rule_code),
  key idx_cost_rule_scene_fee (scene_id, fee_id),
  key idx_cost_rule_scene_status_priority (scene_id, status, priority),
  key idx_cost_rule_scene_formula (scene_id, amount_formula_code),
  constraint fk_cost_rule_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_rule_fee foreign key (fee_id) references cost_fee_item (fee_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-费率规则主表';

-- =========================================================
-- cost_rule_condition
-- =========================================================
create table if not exists cost_rule_condition (
  condition_id               bigint          not null auto_increment comment '规则条件主键',
  scene_id                   bigint          not null comment '所属场景主键',
  rule_id                    bigint          not null comment '所属规则主键',
  group_no                   int             not null default 1 comment '组号，用于表达同组条件',
  sort_no                    int             not null default 0 comment '排序号',
  variable_code              varchar(64)     not null comment '变量编码，指向规则判断使用的变量',
  display_name               varchar(128)    default '' comment '显示名称，用于业务页展示',
  operator_code              varchar(32)     not null comment '操作符，例如EQ、IN、GT、GTE、LT、LTE、BETWEEN',
  compare_value              varchar(1000)   default '' comment '比较值，统一以字符串存储并由前端/服务层解析',
  status                     char(1)         not null default '0' comment '条件状态（0正常 1停用）',
  remark                     varchar(500)    default null comment '备注',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (condition_id),
  key idx_cost_rule_cond_rule_group (rule_id, group_no, sort_no),
  key idx_cost_rule_cond_scene_var (scene_id, variable_code),
  constraint fk_cost_rule_condition_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_rule_condition_rule foreign key (rule_id) references cost_rule (rule_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-规则条件表';

-- =========================================================
-- cost_rule_tier
-- =========================================================
create table if not exists cost_rule_tier (
  tier_id                    bigint          not null auto_increment comment '阶梯主键',
  scene_id                   bigint          not null comment '所属场景主键',
  rule_id                    bigint          not null comment '所属规则主键',
  start_value                decimal(18,4)   default null comment '阶梯起始值，允许为空表示首档负无穷起始',
  end_value                  decimal(18,4)   default null comment '阶梯截止值，允许为空表示末档无上限',
  rate_value                 decimal(18,6)   not null comment '阶梯费率或阶梯单价',
  interval_mode              varchar(32)     not null default 'LEFT_CLOSED_RIGHT_OPEN' comment '区间模式，例如LEFT_CLOSED_RIGHT_OPEN、LEFT_OPEN_RIGHT_CLOSED',
  tier_no                    int             not null default 1 comment '阶梯序号',
  status                     char(1)         not null default '0' comment '阶梯状态（0正常 1停用）',
  remark                     varchar(500)    default null comment '备注',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (tier_id),
  key idx_cost_rule_tier_rule_no (rule_id, tier_no),
  key idx_cost_rule_tier_scene_rule (scene_id, rule_id),
  constraint fk_cost_rule_tier_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_rule_tier_rule foreign key (rule_id) references cost_rule (rule_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-规则阶梯明细表';

-- =========================================================
-- cost_formula
-- =========================================================
create table if not exists cost_formula
(
    formula_id             bigint          not null auto_increment comment '公式主键',
    scene_id               bigint          not null comment '所属场景主键',
    formula_code           varchar(64)     not null comment '公式编码，供变量/规则/运行链引用',
    formula_name           varchar(128)    not null comment '公式名称',
    formula_desc           varchar(500)    default '' comment '公式用途说明',
    business_formula       varchar(1000)   default '' comment '业务中文公式/口径说明',
    formula_expr           varchar(2000)   not null comment '标准执行表达式',
    asset_type             varchar(32)     default 'FORMULA' comment '资产类型（FORMULA/TEMPLATE）',
    workbench_mode         varchar(32)     default 'GUIDED' comment '工作台模式（GUIDED/EXPERT）',
    workbench_pattern      varchar(32)     default 'IF_ELSE' comment '工作台结构类型',
    template_code          varchar(64)     default '' comment '工作台模板编码',
    workbench_config_json  json            default null comment '工作台点选配置',
    namespace_scope        varchar(128)    default 'V,C,I,F,T' comment '允许引用的命名空间范围',
    return_type            varchar(32)     default 'NUMBER' comment '公式返回类型',
    test_case_json         json            default null comment '公式测试样例上下文',
    sample_result_json     json            default null comment '最近一次测试结果样例',
    last_test_time         datetime        default null comment '最近测试时间',
    status                 char(1)         default '0' comment '状态（0正常 1停用）',
    sort_no                int             default 10 comment '排序号',
    remark                 varchar(500)    default '' comment '备注',
    create_by              varchar(64)     default '' comment '创建人',
    create_time            datetime        default current_timestamp comment '创建时间',
    update_by              varchar(64)     default '' comment '更新人',
    update_time            datetime        default current_timestamp on update current_timestamp comment '更新时间',
    primary key (formula_id),
    unique key uk_cost_formula_scene_code (scene_id, formula_code),
    key idx_cost_formula_scene_status (scene_id, status)
) engine = innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment = '公式实验室主表';

-- =========================================================
-- cost_formula_version
-- =========================================================
create table if not exists cost_formula_version
(
    version_id             bigint          not null auto_increment comment '版本主键',
    formula_id             bigint          not null comment '公式主键',
    scene_id               bigint          not null comment '场景主键',
    formula_code           varchar(64)     not null comment '公式编码',
    formula_name           varchar(128)    not null comment '公式名称',
    asset_type             varchar(32)     default 'FORMULA' comment '资产类型（FORMULA/TEMPLATE）',
    version_no             int             not null comment '版本号',
    change_type            varchar(32)     default 'UPDATE' comment '变更类型（CREATE/UPDATE）',
    business_formula       varchar(1000)   default '' comment '业务中文公式',
    formula_expr           varchar(2000)   default '' comment '标准执行表达式',
    workbench_mode         varchar(32)     default 'GUIDED' comment '工作台模式',
    workbench_pattern      varchar(32)     default 'IF_ELSE' comment '工作台结构类型',
    template_code          varchar(64)     default '' comment '模板编码',
    workbench_config_json  json            default null comment '工作台配置',
    snapshot_json          json            default null comment '完整版本快照',
    create_by              varchar(64)     default '' comment '创建人',
    create_time            datetime        default current_timestamp comment '创建时间',
    primary key (version_id),
    unique key uk_cost_formula_version_no (formula_id, version_no),
    key idx_cost_formula_version_scene (scene_id, formula_code),
    key idx_cost_formula_version_formula (formula_id, create_time)
) engine = innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment = '公式版本台账';

-- =========================================================
-- cost_publish_version
-- =========================================================
create table if not exists cost_publish_version (
  version_id                 bigint          not null auto_increment comment '发布版本主键',
  scene_id                   bigint          not null comment '所属场景主键',
  version_no                 varchar(64)     not null comment '发布版本号，例如 V2026.03.001',
  version_status             varchar(32)     not null default 'PUBLISHED' comment '版本状态，例如DRAFT、PUBLISHED、ACTIVE、ROLLED_BACK',
  publish_desc               varchar(1000)   default '' comment '发布说明',
  validation_result_json     json            default null comment '发布校验结果快照',
  snapshot_hash              varchar(128)    default '' comment '快照哈希，用于快速识别配置是否变化',
  published_by               varchar(64)     default '' comment '发布人',
  published_time             datetime        default null comment '发布时间',
  activated_by               varchar(64)     default '' comment '生效操作人',
  activated_time             datetime        default null comment '生效时间',
  rollback_by                varchar(64)     default '' comment '回滚操作人',
  rollback_time              datetime        default null comment '回滚时间',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (version_id),
  unique key uk_cost_publish_scene_ver (scene_id, version_no),
  key idx_cost_publish_scene_status (scene_id, version_status),
  key idx_cost_publish_scene_time (scene_id, published_time),
  constraint fk_cost_publish_scene foreign key (scene_id) references cost_scene (scene_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-场景发布版本表';

-- =========================================================
-- cost_publish_snapshot
-- =========================================================
create table if not exists cost_publish_snapshot (
  snapshot_id                bigint          not null auto_increment comment '快照明细主键',
  version_id                 bigint          not null comment '所属发布版本主键',
  snapshot_type              varchar(32)     not null comment '快照对象类型，例如SCENE、FEE、VARIABLE、RULE、RULE_CONDITION、RULE_TIER',
  object_code                varchar(64)     not null comment '对象编码，用于识别被快照的业务对象',
  object_name                varchar(128)    default '' comment '对象名称，用于工作台和审计展示',
  snapshot_json              json            not null comment '业务对象快照 JSON',
  sort_no                    int             not null default 0 comment '排序号',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  primary key (snapshot_id),
  key idx_cost_snapshot_version_type (version_id, snapshot_type),
  key idx_cost_snapshot_version_code (version_id, object_code),
  constraint fk_cost_snapshot_version foreign key (version_id) references cost_publish_version (version_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-发布快照明细表';

-- =========================================================
-- cost_simulation_record
-- =========================================================
create table if not exists cost_simulation_record (
  simulation_id              bigint          not null auto_increment comment '试算记录主键',
  scene_id                   bigint          not null comment '场景主键',
  version_id                 bigint          default null comment '试算使用的版本主键，优先记录发布版本',
  bill_month                 varchar(16)     not null default '' comment 'bill month, yyyy-MM',
  simulation_no              varchar(64)     not null comment '试算编号',
  input_json                 json            not null comment '试算输入数据',
  variable_json              json            default null comment '变量计算结果',
  explain_json               json            default null comment '试算解释结果，包括命中规则、阶梯、公式等',
  result_json                json            default null comment '试算输出结果',
  status                     varchar(32)     not null default 'SUCCESS' comment '试算状态，例如SUCCESS、FAILED',
  error_message              varchar(1000)   default '' comment '失败信息',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  primary key (simulation_id),
  unique key uk_cost_simulation_no (simulation_no),
  key idx_cost_simulation_scene_time (scene_id, create_time),
  constraint fk_cost_simulation_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_simulation_version foreign key (version_id) references cost_publish_version (version_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-试算记录表';

-- =========================================================
-- cost_audit_log
-- =========================================================
create table if not exists cost_audit_log (
  audit_id                   bigint          not null auto_increment comment '审计日志主键',
  scene_id                   bigint          default null comment '所属场景主键',
  object_type                varchar(32)     not null comment '对象类型，例如SCENE、FEE、VARIABLE、RULE、PUBLISH、DICT',
  object_code                varchar(64)     default '' comment '对象编码',
  action_type                varchar(32)     not null comment '操作类型，例如CREATE、UPDATE、DELETE、DISABLE、PUBLISH、ACTIVATE、ROLLBACK',
  action_summary             varchar(500)    default '' comment '操作摘要',
  before_json                json            default null comment '变更前快照',
  after_json                 json            default null comment '变更后快照',
  operator_code              varchar(64)     default '' comment '操作人编码',
  operator_name              varchar(128)    default '' comment '操作人名称',
  operate_time               datetime        default current_timestamp comment '操作时间',
  request_no                 varchar(64)     default '' comment '请求流水号',
  primary key (audit_id),
  key idx_cost_audit_scene_time (scene_id, operate_time),
  key idx_cost_audit_object (object_type, object_code),
  key idx_cost_audit_action (action_type, operate_time)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-配置审计日志表';

-- =========================================================
-- cost_bill_period
-- =========================================================
create table if not exists cost_bill_period (
  period_id bigint not null auto_increment comment '账期主键',
  scene_id bigint not null comment '所属场景主键',
  bill_month varchar(7) not null comment '账期，格式 yyyy-MM',
  period_status varchar(32) not null default 'NOT_STARTED' comment '账期状态',
  active_version_id bigint default null comment '当前账期默认版本',
  result_count bigint not null default 0 comment '当前账期结果条数',
  amount_total decimal(18, 2) not null default 0.00 comment '当前账期结果金额汇总',
  last_task_id bigint default null comment '最近一次正式任务主键',
  last_task_no varchar(64) default '' comment '最近一次正式任务编号',
  sealed_by varchar(64) default '' comment '封存操作人',
  sealed_time datetime default null comment '封存时间',
  create_by varchar(64) default '' comment '创建人',
  create_time datetime default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新人',
  update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
  remark varchar(500) default '' comment '备注',
  primary key (period_id),
  unique key uk_cost_bill_period_scene_month (scene_id, bill_month),
  key idx_cost_bill_period_status (period_status),
  key idx_cost_bill_period_version (active_version_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-账期治理表';

-- =========================================================
-- cost_recalc_order
-- =========================================================
create table if not exists cost_recalc_order (
  recalc_id bigint not null auto_increment comment '重算申请主键',
  scene_id bigint not null comment '所属场景主键',
  bill_month varchar(7) not null comment '目标账期',
  version_id bigint not null comment '目标发布版本主键',
  period_id bigint default null comment '账期主键',
  baseline_task_id bigint default null comment '基准任务主键',
  baseline_task_no varchar(64) default '' comment '基准任务编号',
  target_task_id bigint default null comment '重算任务主键',
  target_task_no varchar(64) default '' comment '重算任务编号',
  recalc_status varchar(32) not null default 'PENDING_APPROVAL' comment '重算状态',
  apply_reason varchar(500) default '' comment '申请原因',
  approve_opinion varchar(500) default '' comment '审核意见',
  diff_summary_json json default null comment '重算前后差异摘要',
  diff_amount decimal(18, 2) not null default 0.00 comment '重算差异金额',
  request_no varchar(64) default '' comment '幂等请求号',
  approve_by varchar(64) default '' comment '审核人',
  approve_time datetime default null comment '审核时间',
  execute_by varchar(64) default '' comment '执行人',
  execute_time datetime default null comment '执行时间',
  finish_time datetime default null comment '完成时间',
  create_by varchar(64) default '' comment '创建人',
  create_time datetime default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新人',
  update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
  remark varchar(500) default '' comment '备注',
  primary key (recalc_id),
  key idx_cost_recalc_scene_month (scene_id, bill_month),
  key idx_cost_recalc_status (recalc_status),
  key idx_cost_recalc_target_task (target_task_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-重算申请与记录表';

-- =========================================================
-- cost_alarm_record
-- =========================================================
create table if not exists cost_alarm_record (
  alarm_id bigint not null auto_increment comment '告警主键',
  scene_id bigint default null comment '所属场景主键',
  version_id bigint default null comment '关联版本主键',
  task_id bigint default null comment '关联任务主键',
  detail_id bigint default null comment '关联任务明细主键',
  bill_month varchar(7) default '' comment '关联账期',
  alarm_type varchar(32) not null comment '告警类型',
  alarm_level varchar(32) not null default 'WARN' comment '告警级别',
  alarm_status varchar(32) not null default 'OPEN' comment '告警状态',
  source_key varchar(128) default '' comment '来源唯一键',
  alarm_title varchar(200) default '' comment '告警标题',
  alarm_content varchar(1000) default '' comment '告警内容',
  trigger_time datetime default current_timestamp comment '触发时间',
  first_trigger_time datetime null comment '首次触发时间',
  latest_trigger_time datetime null comment '最近触发时间',
  occurrence_count int not null default 1 comment '累计触发次数',
  ack_by varchar(64) default '' comment '确认人',
  ack_time datetime default null comment '确认时间',
  resolve_by varchar(64) default '' comment '处理人',
  resolve_time datetime default null comment '处理时间',
  create_by varchar(64) default '' comment '创建人',
  create_time datetime default current_timestamp comment '创建时间',
  update_by varchar(64) default '' comment '更新人',
  update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
  remark varchar(500) default '' comment '备注',
  primary key (alarm_id),
  key idx_cost_alarm_scene_time (scene_id, trigger_time),
  key idx_cost_alarm_task (task_id, detail_id),
  key idx_cost_alarm_status (alarm_status, alarm_level),
  key idx_cost_alarm_source_status (source_key, alarm_status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-运行告警台账';

-- =========================================================
-- cost_access_profile
-- =========================================================
create table if not exists cost_access_profile (
    profile_id bigint primary key auto_increment comment '接入方案主键',
    scene_id bigint not null comment '所属场景主键',
    fee_id bigint null comment '目标费用主键',
    fee_scope_type varchar(16) not null default 'ALL' comment '费用范围类型',
    fee_ids_json longtext null comment '多费用主键JSON',
    version_id bigint null comment '绑定版本主键',
    profile_code varchar(64) not null comment '方案编码',
    profile_name varchar(128) not null comment '方案名称',
    source_type varchar(32) not null default 'RAW_JSON' comment '来源类型',
    task_type varchar(32) not null default 'FORMAL_BATCH' comment '任务类型',
    request_method varchar(16) not null default 'GET' comment '请求方法',
    endpoint_url varchar(255) null comment '外部接口地址',
    auth_type varchar(32) not null default 'NONE' comment '鉴权方式',
    auth_config_json text null comment '鉴权配置JSON',
    fetch_config_json longtext null comment '拉取策略JSON',
    mapping_json longtext null comment '字段映射JSON',
    sample_payload_json longtext null comment '样例原始载荷JSON',
    sample_input_json longtext null comment '样例标准计费对象JSON',
    status char(1) not null default '0' comment '状态（0正常 1停用）',
    sort_no int null default 0 comment '排序号',
    create_by varchar(64) null comment '创建人',
    create_time datetime null comment '创建时间',
    update_by varchar(64) null comment '更新人',
    update_time datetime null comment '更新时间',
    remark varchar(500) null comment '备注',
    unique key uk_cost_access_profile_scene_code (scene_id, profile_code),
    key idx_cost_access_profile_fee (fee_id),
    key idx_cost_access_profile_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='数据接入方案';

-- =========================================================
-- cost_calc_input_batch
-- =========================================================
create table if not exists cost_calc_input_batch (
  batch_id                   bigint          not null auto_increment comment '输入批次主键',
  batch_no                   varchar(64)     not null comment '输入批次号',
  scene_id                   bigint          not null comment '场景主键',
  version_id                 bigint          default null comment '版本主键',
  bill_month                 varchar(16)     not null default '' comment '账期',
  source_type                varchar(32)     not null default 'JSON_IMPORT' comment '来源类型',
  batch_status               varchar(32)     not null default 'READY' comment '批次状态，例如READY、SUBMITTED、CONSUMED',
  total_count                int             not null default 0 comment '总条数',
  valid_count                int             not null default 0 comment '有效条数',
  error_count                int             not null default 0 comment '错误条数',
  remark                     varchar(500)    default null comment '备注',
  error_message              varchar(1000)   default '' comment '错误摘要',
  access_profile_id          bigint          null comment '接入方案ID',
  checkpoint_json            longtext        null comment '断点检查点JSON',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (batch_id),
  unique key uk_cost_calc_input_batch_no (batch_no),
  key idx_cost_calc_input_batch_scene_month (scene_id, bill_month),
  constraint fk_cost_calc_input_batch_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_calc_input_batch_version foreign key (version_id) references cost_publish_version (version_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-正式核算输入批次表';

-- =========================================================
-- cost_calc_input_batch_item
-- =========================================================
create table if not exists cost_calc_input_batch_item (
  item_id                    bigint          not null auto_increment comment '输入批次明细主键',
  batch_id                   bigint          not null comment '输入批次主键',
  batch_no                   varchar(64)     not null comment '输入批次号冗余字段',
  item_no                    int             not null comment '批次内序号',
  biz_no                     varchar(128)    not null comment '业务单号',
  item_status                varchar(32)     not null default 'READY' comment '明细状态，例如READY、IMPORTED、ERROR',
  input_json                 json            not null comment '输入数据',
  error_message              varchar(1000)   default '' comment '错误摘要',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (item_id),
  unique key uk_cost_calc_input_batch_item (batch_id, biz_no),
  key idx_cost_calc_input_batch_item_batch (batch_id, item_no),
  constraint fk_cost_calc_input_batch_item_batch foreign key (batch_id) references cost_calc_input_batch (batch_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-正式核算输入批次明细表';

-- =========================================================
-- cost_calc_task
-- =========================================================
create table if not exists cost_calc_task (
  task_id                    bigint          not null auto_increment comment '核算任务主键',
  task_no                    varchar(64)     not null comment '任务编号',
  scene_id                   bigint          not null comment '场景主键',
  version_id                 bigint          not null comment '运行使用的发布版本主键',
  task_type                  varchar(32)     not null comment '任务类型，例如FORMAL_SINGLE、FORMAL_BATCH、SIMULATION_BATCH',
  bill_month                 varchar(16)     default '' comment '账期，例如2026-03',
  source_count               int             not null default 0 comment '输入数据总量',
  success_count              int             not null default 0 comment '成功处理数量',
  fail_count                 int             not null default 0 comment '失败数量',
  task_status                varchar(32)     not null default 'INIT' comment '任务状态，例如INIT、RUNNING、SUCCESS、PART_SUCCESS、FAILED',
  progress_percent           decimal(5,2)    not null default 0 comment '任务进度百分比',
  started_time               datetime        default null comment '开始时间',
  finished_time              datetime        default null comment '结束时间',
  duration_ms                bigint          default 0 comment '任务总耗时，单位毫秒',
  request_no                 varchar(64)     default '' comment '幂等请求号',
  request_no_key             varchar(64)     generated always as (nullif(request_no, '')) stored comment '非空幂等请求号唯一键辅助列',
  execute_node               varchar(128)    default '' comment '执行节点标识，用于分布式任务追踪',
  input_source_type          varchar(32)     not null default 'INLINE_JSON' comment '输入来源类型，例如INLINE_JSON、INPUT_BATCH',
  source_batch_no            varchar(64)     default '' comment '来源批次号',
  error_message              varchar(1000)   default '' comment '任务失败摘要',
  remark                     varchar(500)    default null comment '备注',
  create_by                  varchar(64)     default '' comment '创建人',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_by                  varchar(64)     default '' comment '更新人',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (task_id),
  unique key uk_cost_calc_task_no (task_no),
  unique key uk_cost_calc_task_request_no (scene_id, version_id, bill_month, request_no_key),
  key idx_cost_calc_task_scene_month (scene_id, bill_month),
  key idx_cost_calc_task_scene_status (scene_id, task_status),
  key idx_cost_calc_task_version (version_id),
  constraint fk_cost_calc_task_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_calc_task_version foreign key (version_id) references cost_publish_version (version_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-正式核算任务表';

-- =========================================================
-- cost_calc_task_detail
-- =========================================================
create table if not exists cost_calc_task_detail (
  detail_id                  bigint          not null auto_increment comment '任务明细主键',
  task_id                    bigint          not null comment '所属任务主键',
  task_no                    varchar(64)     not null comment '任务编号冗余字段，便于快速过滤',
  biz_no                     varchar(128)    not null comment '业务单号，用于唯一标识一条待计费业务数据',
  partition_no               int             not null default 1 comment '分片号，用于批量任务并行处理',
  detail_status              varchar(32)     not null default 'INIT' comment '明细状态，例如INIT、SUCCESS、FAILED',
  retry_count                int             not null default 0 comment '重试次数',
  input_json                 json            not null comment '输入业务数据',
  result_summary             varchar(1000)   default '' comment '结果摘要，用于列表快速展示',
  error_message              varchar(1000)   default '' comment '失败信息',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (detail_id),
  unique key uk_cost_calc_task_detail (task_id, biz_no),
  key idx_cost_calc_task_detail_task_status (task_id, detail_status),
  key idx_cost_calc_task_detail_task_partition (task_id, partition_no),
  key idx_cost_calc_task_detail_task_no (task_no),
  constraint fk_cost_calc_task_detail_task foreign key (task_id) references cost_calc_task (task_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-正式核算任务明细表';

-- =========================================================
-- cost_calc_task_partition
-- =========================================================
create table if not exists cost_calc_task_partition (
  partition_id               bigint          not null auto_increment comment '任务分片主键',
  task_id                    bigint          not null comment '所属任务主键',
  task_no                    varchar(64)     not null comment '任务编号冗余字段',
  partition_no               int             not null comment '分片序号',
  start_item_no              int             not null default 1 comment '起始明细序号',
  end_item_no                int             not null default 1 comment '结束明细序号',
  partition_status           varchar(32)     not null default 'INIT' comment '分片状态，例如INIT、RUNNING、SUCCESS、PART_SUCCESS、FAILED、CANCELLED',
  total_count                int             not null default 0 comment '分片总条数',
  processed_count            int             not null default 0 comment '已处理条数',
  success_count              int             not null default 0 comment '成功条数',
  fail_count                 int             not null default 0 comment '失败条数',
  amount_total               decimal(18,2)   not null default 0.00 comment '分片金额汇总',
  persist_mode               varchar(32)     not null default 'BATCH' comment '结果落库模式',
  recovery_hint              varchar(500)    not null default '' comment '恢复提示',
  last_error_stage           varchar(64)     not null default '' comment '最近错误阶段',
  execute_node               varchar(64)     default null comment '当前认领执行节点',
  claim_time                 datetime        default null comment '最近认领时间',
  started_time               datetime        default null comment '开始时间',
  finished_time              datetime        default null comment '结束时间',
  duration_ms                bigint          default 0 comment '耗时毫秒',
  last_error                 varchar(1000)   default '' comment '最近错误摘要',
  create_time                datetime        default current_timestamp comment '创建时间',
  update_time                datetime        default current_timestamp on update current_timestamp comment '更新时间',
  primary key (partition_id),
  unique key uk_cost_calc_task_partition (task_id, partition_no),
  key idx_cost_calc_task_partition_task_status (task_id, partition_status),
  key idx_cost_calc_task_partition_task_no (task_no),
  key idx_cost_calc_task_partition_status_claim (partition_status, claim_time),
  constraint fk_cost_calc_task_partition_task foreign key (task_id) references cost_calc_task (task_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-正式核算任务分片表';

-- =========================================================
-- cost_result_ledger
-- =========================================================
create table if not exists cost_result_ledger (
  result_id                  bigint          not null auto_increment comment '结果台账主键',
  task_id                    bigint          not null comment '所属任务主键',
  task_no                    varchar(64)     not null comment '任务编号冗余字段，便于查询',
  scene_id                   bigint          not null comment '场景主键',
  version_id                 bigint          not null comment '发布版本主键',
  fee_id                     bigint          not null comment '费用主键',
  fee_code                   varchar(64)     not null comment '费用编码',
  fee_name                   varchar(128)    not null comment '费用名称',
  biz_no                     varchar(128)    not null comment '业务单号',
  bill_month                 varchar(16)     not null comment '账期，例如2026-03',
  object_dimension           varchar(64)     default '' comment '核算对象维度，例如人、班组、协力公司、船舶',
  object_code                varchar(128)    default '' comment '核算对象编码',
  object_name                varchar(128)    default '' comment '核算对象名称',
  quantity_value             decimal(18,4)   default null comment '参与费率计算的数量值',
  unit_price                 decimal(18,6)   default null comment '最终命中的单价或费率',
  amount_value               decimal(18,2)   not null comment '最终金额',
  currency_code              varchar(32)     default 'CNY' comment '币种编码',
  result_status              varchar(32)     not null default 'SUCCESS' comment '结果状态，例如SUCCESS、FAILED、ADJUSTED',
  trace_id                   bigint          default null comment '追溯记录主键，对应 cost_result_trace.trace_id',
  create_time                datetime        default current_timestamp comment '创建时间',
  primary key (result_id),
  key idx_cost_result_scene_month (scene_id, bill_month),
  key idx_cost_result_task_fee (task_id, fee_id),
  key idx_cost_result_biz_fee (biz_no, fee_code),
  key idx_cost_result_version (version_id),
  constraint fk_cost_result_task foreign key (task_id) references cost_calc_task (task_id),
  constraint fk_cost_result_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_result_version foreign key (version_id) references cost_publish_version (version_id),
  constraint fk_cost_result_fee foreign key (fee_id) references cost_fee_item (fee_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-结果台账表';

-- =========================================================
-- cost_result_trace
-- =========================================================
create table if not exists cost_result_trace (
  trace_id                   bigint          not null auto_increment comment '结果追溯主键',
  scene_id                   bigint          not null comment '场景主键',
  version_id                 bigint          not null comment '发布版本主键',
  rule_id                    bigint          default null comment '命中的规则主键',
  tier_id                    bigint          default null comment '命中的阶梯主键',
  variable_json              json            default null comment '变量计算结果，用于解释命中过程',
  condition_json             json            default null comment '规则条件匹配结果',
  pricing_json               json            default null comment '定价过程结果，包括单价来源、阶梯摘要等',
  timeline_json              json            default null comment '执行时间线，用于回放输入、变量、规则、金额和结果过程',
  create_time                datetime        default current_timestamp comment '创建时间',
  primary key (trace_id),
  key idx_cost_trace_scene_ver (scene_id, version_id),
  key idx_cost_trace_rule (rule_id),
  key idx_cost_trace_tier (tier_id),
  constraint fk_cost_trace_scene foreign key (scene_id) references cost_scene (scene_id),
  constraint fk_cost_trace_version foreign key (version_id) references cost_publish_version (version_id),
  constraint fk_cost_trace_rule foreign key (rule_id) references cost_rule (rule_id),
  constraint fk_cost_trace_tier foreign key (tier_id) references cost_rule_tier (tier_id)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='核算平台-结果追溯解释表';

-- =========================================================
-- cost_open_app
-- =========================================================
create table if not exists cost_open_app (
    app_id bigint primary key auto_increment comment '开放应用主键',
    app_code varchar(64) not null comment '开放应用编码',
    app_name varchar(128) not null comment '开放应用名称',
    app_secret_hash char(64) not null comment '开放应用密钥 SHA-256 摘要',
    scene_scope_type varchar(16) not null default 'ALL' comment '场景授权范围类型',
    scene_ids_json longtext null comment '授权场景主键 JSON',
    allow_draft_snapshot tinyint(1) not null default 0 comment '是否允许草稿联调',
    token_ttl_seconds int not null default 7200 comment '访问令牌有效期（秒）',
    effective_start_time datetime null comment '生效开始时间',
    effective_end_time datetime null comment '生效结束时间',
    status char(1) not null default '0' comment '状态（0正常 1停用）',
    sort_no int null default 0 comment '排序号',
    create_by varchar(64) null comment '创建人',
    create_time datetime null comment '创建时间',
    update_by varchar(64) null comment '更新人',
    update_time datetime null comment '更新时间',
    remark varchar(500) null comment '备注',
    unique key uk_cost_open_app_code (app_code),
    key idx_cost_open_app_status (status)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='第三方开放应用';

set foreign_key_checks = 1;

-- =========================================================
-- Lightweight cost dictionaries only
-- =========================================================
-- The two dictionary tables keep the mother platform entity contract.
-- No other RuoYi sys_* table is required by the lightweight package.
create table if not exists sys_dict_type
(
  dict_id          bigint          not null auto_increment comment '字典主键',
  dict_name        varchar(100)    default '' comment '字典名称',
  dict_type        varchar(100)    default '' comment '字典类型',
  status           char(1)         default '0' comment '状态（0正常 1停用）',
  create_by        varchar(64)     default '' comment '创建者',
  create_time      datetime        default current_timestamp comment '创建时间',
  update_by        varchar(64)     default '' comment '更新者',
  update_time      datetime        default null comment '更新时间',
  remark           varchar(500)    default null comment '备注',
  primary key (dict_id),
  unique key uk_sys_dict_type_type (dict_type)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='轻量计费字典类型表';

create table if not exists sys_dict_data
(
  dict_code        bigint          not null auto_increment comment '字典编码',
  dict_sort        int             default 0 comment '字典排序',
  dict_label       varchar(100)    default '' comment '字典标签',
  dict_value       varchar(100)    default '' comment '字典键值',
  dict_type        varchar(100)    default '' comment '字典类型',
  css_class        varchar(100)    default null comment '样式属性',
  list_class       varchar(100)    default null comment '表格字典样式',
  is_default       char(1)         default 'N' comment '是否默认（Y是 N否）',
  status           char(1)         default '0' comment '状态（0正常 1停用）',
  create_by        varchar(64)     default '' comment '创建者',
  create_time      datetime        default current_timestamp comment '创建时间',
  update_by        varchar(64)     default '' comment '更新者',
  update_time      datetime        default null comment '更新时间',
  remark           varchar(500)    default null comment '备注',
  primary key (dict_code),
  key idx_sys_dict_data_type (dict_type, dict_sort, status),
  unique key uk_sys_dict_data_type_value (dict_type, dict_value)
) engine=innodb default charset=utf8mb4 collate=utf8mb4_general_ci comment='轻量计费字典数据表';

insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select seed.dict_name, seed.dict_type, '0', 'cost-lite', current_timestamp, seed.remark
from (
  select '核算-业务域' dict_name, 'cost_business_domain' dict_type, '场景、费目、要素和规则使用的业务域' remark
  union all select '核算-场景状态', 'cost_scene_status', '场景维护状态'
  union all select '核算-场景类型', 'cost_scene_type', '场景分类'
  union all select '核算-费用状态', 'cost_fee_status', '费目维护状态'
  union all select '核算-计价单位', 'cost_unit_code', '费目计价单位'
  union all select '核算-要素分组状态', 'cost_variable_group_status', '要素分组状态'
  union all select '核算-要素类型', 'cost_variable_type', '要素展示和处理类型'
  union all select '核算-要素来源类型', 'cost_variable_source_type', '要素取值来源'
  union all select '核算-要素数据类型', 'cost_variable_data_type', '要素数据类型'
  union all select '核算-变量鉴权方式', 'cost_variable_auth_type', '远程要素鉴权方式'
  union all select '核算-变量同步方式', 'cost_variable_sync_mode', '远程要素同步方式'
  union all select '核算-变量缓存策略', 'cost_variable_cache_policy', '远程要素缓存策略'
  union all select '核算-变量失败兜底策略', 'cost_variable_fallback_policy', '远程要素失败处理方式'
  union all select '核算-变量状态', 'cost_variable_status', '要素维护状态'
  union all select '核算-规则状态', 'cost_rule_status', '规则维护状态'
  union all select '核算-规则类型', 'cost_rule_type', '规则定价类型'
  union all select '核算-规则条件逻辑', 'cost_rule_condition_logic', '条件组之间的逻辑关系'
  union all select '核算-规则操作符', 'cost_rule_operator', '条件比较操作符'
  union all select '核算-阶梯区间模式', 'cost_rule_interval_mode', '阶梯边界模式'
  union all select '核算-发布版本状态', 'cost_publish_version_status', '发布版本状态'
  union all select '核算-试算状态', 'cost_simulation_status', '同步计费和试算状态'
  union all select '核算-正式任务类型', 'cost_calc_task_type', '正式任务类型'
  union all select '核算-正式任务状态', 'cost_calc_task_status', '正式任务状态'
  union all select '核算-结果状态', 'cost_result_status', '正式结果状态'
) seed
where not exists (
  select 1 from sys_dict_type current_type where current_type.dict_type = seed.dict_type
);

insert into sys_dict_data
  (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select seed.dict_sort, seed.dict_label, seed.dict_value, seed.dict_type, null, seed.list_class,
       seed.is_default, '0', 'cost-lite', current_timestamp, seed.remark
from (
  select 1 dict_sort, '薪资结算' dict_label, 'SALARY' dict_value, 'cost_business_domain' dict_type, 'success' list_class, 'N' is_default, '业务域：薪资结算' remark
  union all select 2, '港口作业', 'PORT', 'cost_business_domain', 'primary', 'Y', '业务域：港口作业'
  union all select 3, '仓储结算', 'STORAGE', 'cost_business_domain', 'warning', 'N', '业务域：仓储结算'
  union all select 4, '运输计费', 'TRANSPORT', 'cost_business_domain', 'info', 'N', '业务域：运输计费'
  union all select 5, '材料成本', 'MATERIAL', 'cost_business_domain', 'danger', 'N', '业务域：材料成本'
  union all select 6, '制造成本', 'MANUFACTURE', 'cost_business_domain', 'default', 'N', '业务域：制造成本'
  union all select 1, '正常', '0', 'cost_scene_status', 'success', 'Y', '场景可维护且可被下游使用'
  union all select 2, '停用', '1', 'cost_scene_status', 'danger', 'N', '场景已停用'
  union all select 3, '草稿', '2', 'cost_scene_status', 'warning', 'N', '场景仍在整理中'
  union all select 1, '合同场景', 'CONTRACT', 'cost_scene_type', 'primary', 'Y', '以合同为边界维护核算配置'
  union all select 2, '核算主题', 'THEME', 'cost_scene_type', 'success', 'N', '以统一核算主题组织配置'
  union all select 3, '业务方案', 'PLAN', 'cost_scene_type', 'warning', 'N', '以具体业务方案组织配置'
  union all select 4, '公司级核算域', 'COMPANY', 'cost_scene_type', 'info', 'N', '以公司级统一口径组织配置'
  union all select 1, '正常', '0', 'cost_fee_status', 'success', 'Y', '费目可维护'
  union all select 2, '停用', '1', 'cost_fee_status', 'danger', 'N', '费目停用'
  union all select 1, '吨', '吨', 'cost_unit_code', 'primary', 'Y', '重量单位'
  union all select 2, '天', '天', 'cost_unit_code', 'success', 'N', '时间单位'
  union all select 3, '次', '次', 'cost_unit_code', 'info', 'N', '次数单位'
  union all select 4, '航次', '航次', 'cost_unit_code', 'warning', 'N', '航运业务单位'
  union all select 5, '人', '人', 'cost_unit_code', 'primary', 'N', '人员单位'
  union all select 6, '箱', '箱', 'cost_unit_code', 'success', 'N', '箱量单位'
  union all select 7, '元', '元', 'cost_unit_code', 'danger', 'N', '金额单位'
  union all select 8, '平方米*天', '平方米*天', 'cost_unit_code', 'warning', 'N', '仓储复合单位'
  union all select 1, '正常', '0', 'cost_variable_group_status', 'success', 'Y', '要素分组可维护'
  union all select 2, '停用', '1', 'cost_variable_group_status', 'danger', 'N', '要素分组停用'
  union all select 1, '文本', 'TEXT', 'cost_variable_type', 'primary', 'Y', '文本要素'
  union all select 2, '数值', 'NUMBER', 'cost_variable_type', 'success', 'N', '数值要素'
  union all select 3, '字典下拉', 'DICT', 'cost_variable_type', 'warning', 'N', '字典要素'
  union all select 4, '接口下拉', 'REMOTE', 'cost_variable_type', 'info', 'N', '第三方接口要素'
  union all select 5, '公式', 'FORMULA', 'cost_variable_type', 'danger', 'N', '公式要素'
  union all select 6, '布尔', 'BOOLEAN', 'cost_variable_type', 'default', 'N', '布尔要素'
  union all select 7, '日期', 'DATE', 'cost_variable_type', 'default', 'N', '日期要素'
  union all select 1, '手工输入', 'INPUT', 'cost_variable_source_type', 'primary', 'Y', '由调用方传入'
  union all select 2, '字典接入', 'DICT', 'cost_variable_source_type', 'success', 'N', '从字典读取'
  union all select 3, '第三方接口', 'REMOTE', 'cost_variable_source_type', 'warning', 'N', '从远程接口读取'
  union all select 4, '公式派生', 'FORMULA', 'cost_variable_source_type', 'info', 'N', '由公式派生'
  union all select 1, '字符串', 'STRING', 'cost_variable_data_type', 'primary', 'Y', '字符串数据'
  union all select 2, '数值', 'NUMBER', 'cost_variable_data_type', 'success', 'N', '数值数据'
  union all select 3, '布尔', 'BOOLEAN', 'cost_variable_data_type', 'warning', 'N', '布尔数据'
  union all select 4, '日期', 'DATE', 'cost_variable_data_type', 'info', 'N', '日期数据'
  union all select 5, 'JSON', 'JSON', 'cost_variable_data_type', 'default', 'N', 'JSON 数据'
  union all select 1, '无鉴权', 'NONE', 'cost_variable_auth_type', 'info', 'Y', '无需鉴权'
  union all select 2, 'Basic', 'BASIC', 'cost_variable_auth_type', 'primary', 'N', 'Basic 鉴权'
  union all select 3, 'Bearer Token', 'BEARER', 'cost_variable_auth_type', 'success', 'N', 'Bearer Token 鉴权'
  union all select 4, 'API Key', 'API_KEY', 'cost_variable_auth_type', 'warning', 'N', 'API Key 鉴权'
  union all select 1, '实时拉取', 'REALTIME', 'cost_variable_sync_mode', 'primary', 'Y', '调用时实时拉取'
  union all select 2, '准实时缓存', 'NEAR_REALTIME', 'cost_variable_sync_mode', 'success', 'N', '按短周期缓存刷新'
  union all select 3, '定时同步', 'SCHEDULED', 'cost_variable_sync_mode', 'warning', 'N', '按任务定时同步'
  union all select 1, '不缓存', 'NONE', 'cost_variable_cache_policy', 'info', 'N', '不做缓存'
  union all select 2, 'TTL缓存', 'TTL', 'cost_variable_cache_policy', 'success', 'N', '按失效时间缓存'
  union all select 3, '手动刷新', 'MANUAL_REFRESH', 'cost_variable_cache_policy', 'primary', 'Y', '仅手动刷新'
  union all select 1, '失败即终止', 'FAIL_FAST', 'cost_variable_fallback_policy', 'danger', 'Y', '失败后直接终止'
  union all select 2, '回退默认值', 'DEFAULT_VALUE', 'cost_variable_fallback_policy', 'warning', 'N', '失败后使用默认值'
  union all select 3, '回退快照值', 'LAST_SNAPSHOT', 'cost_variable_fallback_policy', 'info', 'N', '失败后回退最近快照值'
  union all select 1, '正常', '0', 'cost_variable_status', 'success', 'Y', '要素可维护'
  union all select 2, '停用', '1', 'cost_variable_status', 'danger', 'N', '要素停用'
  union all select 1, '正常', '0', 'cost_rule_status', 'success', 'Y', '规则可维护'
  union all select 2, '停用', '1', 'cost_rule_status', 'danger', 'N', '规则停用'
  union all select 1, '固定费率', 'FIXED_RATE', 'cost_rule_type', 'primary', 'Y', '固定费率规则'
  union all select 2, '固定金额', 'FIXED_AMOUNT', 'cost_rule_type', 'success', 'N', '固定金额规则'
  union all select 3, '公式金额', 'FORMULA', 'cost_rule_type', 'warning', 'N', '公式金额规则'
  union all select 4, '阶梯费率', 'TIER_RATE', 'cost_rule_type', 'info', 'N', '阶梯费率规则'
  union all select 1, '且', 'AND', 'cost_rule_condition_logic', 'primary', 'Y', '条件同时满足'
  union all select 2, '或', 'OR', 'cost_rule_condition_logic', 'warning', 'N', '条件任一满足'
  union all select 1, '等于', 'EQ', 'cost_rule_operator', 'primary', 'Y', '等于'
  union all select 2, '不等于', 'NE', 'cost_rule_operator', 'info', 'N', '不等于'
  union all select 3, '大于', 'GT', 'cost_rule_operator', 'warning', 'N', '大于'
  union all select 4, '大于等于', 'GE', 'cost_rule_operator', 'success', 'N', '大于等于'
  union all select 5, '小于', 'LT', 'cost_rule_operator', 'warning', 'N', '小于'
  union all select 6, '小于等于', 'LE', 'cost_rule_operator', 'success', 'N', '小于等于'
  union all select 7, '包含任一值', 'IN', 'cost_rule_operator', 'info', 'N', '多值包含'
  union all select 8, '不包含', 'NOT_IN', 'cost_rule_operator', 'danger', 'N', '多值排除'
  union all select 9, '区间', 'BETWEEN', 'cost_rule_operator', 'primary', 'N', '区间比较'
  union all select 10, '表达式', 'EXPR', 'cost_rule_operator', 'default', 'N', '表达式条件'
  union all select 11, '为空', 'IS_NULL', 'cost_rule_operator', 'warning', 'N', '空值判断'
  union all select 12, '不为空', 'IS_NOT_NULL', 'cost_rule_operator', 'success', 'N', '非空判断'
  union all select 1, '左闭右开 [a,b)', 'LEFT_CLOSED_RIGHT_OPEN', 'cost_rule_interval_mode', 'primary', 'Y', 'start <= x < end'
  union all select 2, '左开右闭 (a,b]', 'LEFT_OPEN_RIGHT_CLOSED', 'cost_rule_interval_mode', 'warning', 'N', 'start < x <= end'
  union all select 1, '已发布', 'PUBLISHED', 'cost_publish_version_status', 'primary', 'Y', '已发布未生效'
  union all select 2, '生效中', 'ACTIVE', 'cost_publish_version_status', 'success', 'N', '当前生效版本'
  union all select 3, '已回滚', 'ROLLED_BACK', 'cost_publish_version_status', 'warning', 'N', '已被回滚的版本'
  union all select 1, '成功', 'SUCCESS', 'cost_simulation_status', 'success', 'Y', '试算成功'
  union all select 2, '失败', 'FAILED', 'cost_simulation_status', 'danger', 'N', '试算失败'
  union all select 1, '单笔正式核算', 'FORMAL_SINGLE', 'cost_calc_task_type', 'primary', 'Y', '单笔正式核算'
  union all select 2, '批量正式核算', 'FORMAL_BATCH', 'cost_calc_task_type', 'warning', 'N', '批量正式核算'
  union all select 1, '待执行', 'INIT', 'cost_calc_task_status', 'info', 'Y', '任务初始化'
  union all select 2, '执行中', 'RUNNING', 'cost_calc_task_status', 'primary', 'N', '任务执行中'
  union all select 3, '成功', 'SUCCESS', 'cost_calc_task_status', 'success', 'N', '任务全部成功'
  union all select 4, '部分成功', 'PART_SUCCESS', 'cost_calc_task_status', 'warning', 'N', '任务部分成功'
  union all select 5, '失败', 'FAILED', 'cost_calc_task_status', 'danger', 'N', '任务失败'
  union all select 6, '已取消', 'CANCELLED', 'cost_calc_task_status', 'default', 'N', '任务已取消'
  union all select 1, '成功', 'SUCCESS', 'cost_result_status', 'success', 'Y', '结果成功'
  union all select 2, '失败', 'FAILED', 'cost_result_status', 'danger', 'N', '结果失败'
  union all select 3, '调整后', 'ADJUSTED', 'cost_result_status', 'warning', 'N', '结果经调整'
) seed
where not exists (
  select 1 from sys_dict_data current_data
  where current_data.dict_type = seed.dict_type and current_data.dict_value = seed.dict_value
);

-- End of Cost Lite MySQL schema baseline.
