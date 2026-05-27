/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState } from 'react';
import { Layers, Database, ShieldAlert, Sparkles, Binary, Key, HelpCircle } from 'lucide-react';

interface DBTable {
  name: string;
  group: 'basic' | 'stats' | 'checks' | 'client';
  description: string;
  columns: { name: string; type: string; isKey: 'PK' | 'FK' | 'NONE'; desc: string }[];
  importance: 'critical' | 'important' | 'supporting';
}

const DB_SCHEMAS: DBTable[] = [
  {
    name: 'school (学校基础表)',
    group: 'basic',
    importance: 'critical',
    description: '作为整个考研目录体系的顶级骨架。包含省市属省划分、985/211/双一流层次标定、官网及招生网的原始抓取入口。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '学校自增唯一主干标识' },
      { name: 'name', type: 'VARCHAR(128)', isKey: 'NONE', desc: '学校官方全名称 (唯一约束)' },
      { name: 'short_name', type: 'VARCHAR(64)', isKey: 'NONE', desc: '学校拼音缩短称呼 (如北航,杭电)' },
      { name: 'province', type: 'VARCHAR(32)', isKey: 'NONE', desc: '所属省份 / 行政区' },
      { name: 'level_tags', type: 'VARCHAR(255)', isKey: 'NONE', desc: '985/211/双一流/省属评级标记' },
      { name: 'is_public', type: 'TINYINT(1)', isKey: 'NONE', desc: '是否为公办高校' }
    ]
  },
  {
    name: 'program (具体招生方向表)',
    group: 'basic',
    importance: 'critical',
    description: '核心二级节点，归属到具体的学院。标定代码（如085400）名、研究方向、学硕/专硕、全制还是非全等强过滤判定线。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '专业自增唯一主干标识' },
      { name: 'college_id', type: 'BIGINT', isKey: 'FK', desc: '关联的具体院系表ID' },
      { name: 'code', type: 'VARCHAR(16)', isKey: 'NONE', desc: '研招网官方标准专业代码 (085400/081200等)' },
      { name: 'name', type: 'VARCHAR(128)', isKey: 'NONE', desc: '专业名称' },
      { name: 'direction', type: 'VARCHAR(255)', isKey: 'NONE', desc: '具体申报研究方向分流' },
      { name: 'learning_type', type: 'VARCHAR(16)', isKey: 'NONE', desc: '全日制 (full-time) 或非全日制 (part-time)' },
      { name: 'is_408', type: 'TINYINT(1)', isKey: 'NONE', desc: '初试科目中是否包含标准408考联合' }
    ]
  },
  {
    name: 'admission_score (历年复试分数线表)',
    group: 'stats',
    importance: 'critical',
    description: '统计各专业招生年份红线。拆解到政治、外语、数学、专业课单科分数线，是计算有效难度分的底座。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '分数线自增标识' },
      { name: 'program_id', type: 'BIGINT', isKey: 'FK', desc: '关联的具体自研专业代码方案' },
      { name: 'year', type: 'INT', isKey: 'NONE', desc: '考研所属完整年份 (e.g., 2025)' },
      { name: 'total_score', type: 'INT', isKey: 'NONE', desc: '国家或高校划定的总分线' },
      { name: 'politics / english', type: 'INT', isKey: 'NONE', desc: '初试政治/英语单科核算红线' },
      { name: 'source_id', type: 'BIGINT', isKey: 'FK', desc: '溯源绑定官方数据源凭证卡号' }
    ]
  },
  {
    name: 'admission_result (拟录取统考录取表)',
    group: 'stats',
    importance: 'critical',
    description: '核实最终上岸人员录取单。包含最低、平均与最高分数点、复试比例，剔除由于推免名额导致的分数线虚报陷阱。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '录取统计自增标识' },
      { name: 'program_id', type: 'BIGINT', isKey: 'FK', desc: '关联的方向ID' },
      { name: 'enrolled_count', type: 'INT', isKey: 'NONE', desc: '最终合规录取的人头总和' },
      { name: 'min_score', type: 'INT', isKey: 'NONE', desc: '拟录取的守门人底线分 (有效分关键参数)' },
      { name: 'avg_score', type: 'INT', isKey: 'NONE', desc: '平均上岸考学分段' },
      { name: 'reexam_admission_ratio', type: 'DECIMAL(4,2)', isKey: 'NONE', desc: '复试淘汰系数（复录比）' }
    ]
  },
  {
    name: 'user_profile (考生画像表)',
    group: 'client',
    importance: 'important',
    description: '保存考生的自主画像信息。包含其估分、地域偏好、接受非全或跨境联培的态度、目标风险偏向等级等。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '用户个人档案标识 (与 app_user 外键绑定)' },
      { name: 'estimated_score', type: 'INT', isKey: 'NONE', desc: '初试模拟或预估总得分' },
      { name: 'preferred_provinces', type: 'VARCHAR(512)', isKey: 'NONE', desc: '意向攻读省份及城市序列 (支持多选)' },
      { name: 'risk_preference', type: 'VARCHAR(16)', isKey: 'NONE', desc: '风险偏向 (conservative/balanced/aggressive)' },
      { name: 'cross_major', type: 'TINYINT(1)', isKey: 'NONE', desc: '本硕拼图是否大跨度考跨' }
    ]
  },
  {
    name: 'data_source (原始发布源表)',
    group: 'checks',
    importance: 'important',
    description: '存储所有采集数据的电子指纹，每一个核心数字都必须有原文及URL溯源证据链支撑，消除AI捏造弊端。',
    columns: [
      { name: 'id', type: 'BIGINT', isKey: 'PK', desc: '数据溯源码' },
      { name: 'type', type: 'VARCHAR(32)', isKey: 'NONE', desc: '来源载体 (招生简章、拟公布名单、贴吧待证)' },
      { name: 'title', type: 'VARCHAR(255)', isKey: 'NONE', desc: '依据简章标题全称' },
      { name: 'url', type: 'VARCHAR(512)', isKey: 'NONE', desc: '官方直链及留档备份地址' },
      { name: 'file_hash', type: 'VARCHAR(64)', isKey: 'NONE', desc: '本地PDF或采集截像哈希密匙，保持防篡改' }
    ]
  }
];

export default function DBExplorer() {
  const [activeGroup, setActiveGroup] = useState<'all' | 'basic' | 'stats' | 'checks' | 'client'>('all');
  const [selectedSchema, setSelectedSchema] = useState<DBTable>(DB_SCHEMAS[1]);

  const filtered = activeGroup === 'all' 
    ? DB_SCHEMAS 
    : DB_SCHEMAS.filter(sc => sc.group === activeGroup);

  return (
    <div className="bg-slate-900/30 rounded-3xl p-6 lg:p-8 border border-slate-800/80 backdrop-blur-sm space-y-6" id="db-metadata-explorer">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b border-slate-850 pb-5">
        <div>
          <span className="text-xs text-brand-secondary font-mono tracking-widest uppercase block mb-1">数据库核心模型检视</span>
          <h3 className="text-2xl font-display font-medium text-white flex items-center gap-2">
            <Layers className="w-5 h-5 text-brand-primary" />
            <span>三维一体：MVP 18 表层级模型</span>
          </h3>
        </div>

        {/* Group select buttons */}
        <div className="flex flex-wrap gap-1 bg-slate-950 p-1 rounded-lg border border-slate-800">
          <button 
            onClick={() => setActiveGroup('all')}
            className={`px-3 py-1 text-xs rounded transition-all ${activeGroup === 'all' ? 'bg-slate-800 text-white font-medium' : 'text-slate-400 hover:text-slate-200'}`}
          >
            整站一览
          </button>
          <button 
            onClick={() => setActiveGroup('basic')}
            className={`px-3 py-1 text-xs rounded transition-all ${activeGroup === 'basic' ? 'bg-slate-800 text-cyan-400 font-medium' : 'text-slate-400 hover:text-slate-200'}`}
          >
            基础目录
          </button>
          <button 
            onClick={() => setActiveGroup('stats')}
            className={`px-3 py-1 text-xs rounded transition-all ${activeGroup === 'stats' ? 'bg-slate-800 text-blue-400 font-medium' : 'text-slate-400 hover:text-slate-200'}`}
          >
            招生录取
          </button>
          <button 
            onClick={() => setActiveGroup('checks')}
            className={`px-3 py-1 text-xs rounded transition-all ${activeGroup === 'checks' ? 'bg-slate-800 text-emerald-400 font-medium' : 'text-slate-400 hover:text-slate-200'}`}
          >
            溯源质控
          </button>
          <button 
            onClick={() => setActiveGroup('client')}
            className={`px-3 py-1 text-xs rounded transition-all ${activeGroup === 'client' ? 'bg-slate-800 text-pink-400 font-medium' : 'text-slate-400 hover:text-slate-200'}`}
          >
            考生算法
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Schema tables choices list */}
        <div className="lg:col-span-5 space-y-3">
          <span className="text-xs text-slate-500 font-mono block">主要展示表范例：</span>
          <div className="grid grid-cols-1 gap-2 max-h-[380px] overflow-y-auto pr-1">
            {filtered.map(tb => (
              <button
                key={tb.name}
                onClick={() => setSelectedSchema(tb)}
                className={`p-4 text-left rounded-xl border transition-all duration-150 flex items-center justify-between ${
                  selectedSchema.name === tb.name 
                    ? 'bg-slate-900 border-slate-700 shadow-lg ring-1 ring-slate-800' 
                    : 'bg-slate-950/40 border-slate-900 hover:bg-slate-950 hover:border-slate-800'
                }`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Database className={`w-3.5 h-3.5 ${
                      tb.group === 'basic' ? 'text-cyan-400' :
                      tb.group === 'stats' ? 'text-blue-400' :
                      tb.group === 'checks' ? 'text-emerald-400' : 'text-pink-400'
                    }`} />
                    <span className="text-xs font-semibold font-mono text-slate-250">{tb.name}</span>
                  </div>
                  <p className="text-[11px] text-slate-500 line-clamp-1">{tb.description}</p>
                </div>
                <div>
                  <span className={`text-[9px] px-1.5 py-0.5 rounded uppercase font-mono ${
                    tb.importance === 'critical' ? 'bg-red-500/10 text-red-400 border border-red-500/20' : 
                    'bg-slate-900 text-slate-400 border border-slate-800'
                  }`}>
                    {tb.importance === 'critical' ? '核心主干' : '关键绑定'}
                  </span>
                </div>
              </button>
            ))}
          </div>

          <div className="bg-slate-950/60 p-4 rounded-xl border border-slate-900 flex gap-3 text-xs leading-relaxed text-slate-400">
            <ShieldAlert className="w-5 h-5 text-emerald-400 flex-shrink-0 mt-0.5 animate-pulse" />
            <p>
              全部 <span className="text-white font-semibold font-mono">18表架构堆叠</span> 完全契合全国研究生初试与复试统筹体系。字段之间均配置严格的外键锁定、级联更新和联合主轴索引保护。
            </p>
          </div>
        </div>

        {/* Selected table structure inspector detail */}
        <div className="lg:col-span-7 bg-slate-950 rounded-2xl border border-slate-850 p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between border-b border-slate-900 pb-3 gap-2">
            <div>
              <span className="text-[10px] text-slate-500 font-mono block uppercase">
                {selectedSchema.group === 'basic' && '基础数据层 (Core Catalog)'}
                {selectedSchema.group === 'stats' && '招生核心层 (Admission Statistics)'}
                {selectedSchema.group === 'checks' && '溯源风控层 (Audit & Checklist)'}
                {selectedSchema.group === 'client' && '算法与考生层 (Calculator Engine)'}
              </span>
              <h4 className="text-lg font-display font-medium text-white flex items-center gap-1.5 mt-0.5">
                <Database className="w-4 h-4 text-brand-secondary" />
                <span className="font-mono text-brand-secondary">{selectedSchema.name.split(' ')[0]}</span>
                <span className="text-slate-400 text-xs font-sans">结构拓扑字典</span>
              </h4>
            </div>

            <span className="text-[10px] bg-slate-900 text-slate-400 border border-slate-800 px-2 py-0.5 rounded font-mono">
              元数据覆盖：{selectedSchema.columns.length} Fields
            </span>
          </div>

          <p className="text-xs text-slate-400 leading-relaxed bg-slate-900/40 p-3 rounded-lg border border-slate-900">
            {selectedSchema.description}
          </p>

          <div className="space-y-2">
            <span className="text-[11px] text-slate-500 font-mono block">字段及模型完整设计（明细索引）：</span>
            <div className="border border-slate-900 rounded-xl overflow-hidden shadow-inner">
              <table className="w-full text-left border-collapse text-[11px]">
                <thead>
                  <tr className="bg-slate-900/80 border-b border-slate-850 text-slate-400 font-mono">
                    <th className="p-2.5 font-semibold">字段名 (Column)</th>
                    <th className="p-2.5 font-semibold">类型 (Type)</th>
                    <th className="p-2.5 font-semibold">键值 (Index)</th>
                    <th className="p-2.5 font-semibold">业务释意 (Definitions)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-900">
                  {selectedSchema.columns.map(col => (
                    <tr key={col.name} className="hover:bg-slate-900/35 transition-colors">
                      <td className="p-2.5 font-mono text-slate-200 font-medium">{col.name}</td>
                      <td className="p-2.5 font-mono text-brand-primary">{col.type}</td>
                      <td className="p-2.5">
                        {col.isKey === 'PK' && (
                          <span className="inline-flex items-center gap-0.5 px-1 py-0.5 bg-red-950 text-red-400 border border-red-900/30 rounded text-[9px] font-bold font-mono">
                            <Key className="w-2.5 h-2.5" />
                            PK
                          </span>
                        )}
                        {col.isKey === 'FK' && (
                          <span className="inline-flex items-center gap-0.5 px-1 py-0.5 bg-blue-950 text-blue-400 border border-blue-900/30 rounded text-[9px] font-bold font-mono">
                            FK
                          </span>
                        )}
                        {col.isKey === 'NONE' && <span className="text-slate-600 font-mono">-</span>}
                      </td>
                      <td className="p-2.5 text-slate-400 leading-normal font-sans">{col.desc}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
