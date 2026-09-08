-- Cost Lite MySQL optional formal/open schema
-- Source: the same mother cost_init.sql table definitions; execute after cost-lite-schema.sql.
-- This script is required only when formal tasks/results, recalculation, alarms or open-app APIs are enabled.
-- MySQL 8.0+.

set names utf8mb4;
set foreign_key_checks = 0;

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

-- =========================================================

set foreign_key_checks = 1;

-- Formal/open dictionary seeds; the dictionary tables themselves are created by cost-lite-schema.sql.
insert into sys_dict_type (dict_name, dict_type, status, create_by, create_time, remark)
select seed.dict_name, seed.dict_type, '0', 'cost-lite', current_timestamp, seed.remark
from (
  select '核算-正式任务类型' dict_name, 'cost_calc_task_type' dict_type, '正式任务类型' remark
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
  select 1 dict_sort, '单笔正式核算' dict_label, 'FORMAL_SINGLE' dict_value, 'cost_calc_task_type' dict_type, 'primary' list_class, 'Y' is_default, '单笔正式核算' remark
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

set foreign_key_checks = 1;

-- End of Cost Lite MySQL optional formal/open schema.
