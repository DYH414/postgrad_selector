create table if not exists ai_chat_conversation (
  id bigint not null auto_increment comment 'conversation id',
  user_id bigint not null comment 'app user id',
  draft_key varchar(128) null comment 'current draft key, reserved for future draft persistence',
  title varchar(128) null comment 'conversation title',
  status varchar(32) not null default 'active' comment 'active/finalized/archived/deleted',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  last_message_at datetime null,
  primary key (id),
  key idx_ai_chat_conv_user_status (user_id, status),
  key idx_ai_chat_conv_last_message (last_message_at)
) engine=InnoDB default charset=utf8mb4 comment='AI recommendation chat conversations';

create table if not exists ai_chat_message (
  id bigint not null auto_increment comment 'message id',
  conversation_id bigint not null comment 'conversation id',
  user_id bigint not null comment 'app user id',
  role varchar(32) not null comment 'user/assistant/system/tool',
  content text null comment 'raw content for AI context',
  display_content text null comment 'sanitized content for user display',
  message_type varchar(32) not null default 'text' comment 'text/action_plan/action_result/error',
  status varchar(32) not null default 'completed' comment 'streaming/completed/failed',
  seq int not null comment 'stable display order inside conversation',
  metadata_json json null comment 'structured action plan/result metadata',
  created_at datetime not null default current_timestamp,
  primary key (id),
  unique key uk_ai_chat_msg_seq (conversation_id, seq),
  key idx_ai_chat_msg_conv (conversation_id, id),
  key idx_ai_chat_msg_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4 comment='AI recommendation chat messages';
