/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { Terminal, Shield, Sparkles, TrendingUp, CheckCircle2, Clock, Check, AlertCircle } from 'lucide-react';
import { motion } from 'motion/react';

export default function AiRiskAssessment() {
  const [typedLines, setTypedLines] = useState<string[]>([]);
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const [typingText, setTypingText] = useState('');
  const [isFinished, setIsFinished] = useState(false);

  const logs = [
    "⚡ [SYS_INIT] 正在连接 408 统考主数据池...",
    "📦 [DATA_LOAD] 读取历年 21 所主流高校 408 复试线...",
    "🔍 [FILTERS] 自适应匹配省市与学位类型偏好...",
    "🔄 [ALIGNMENT] 考生预估初试线与 408 复试发布库对齐...",
    "🎯 [WEIGHTS] 正在启动研招知识图谱加权决策引擎...",
    "📊 [SIMULATION] 完成 10,000 次自主博弈拟录取偏差估算...",
    "🧠 [AI_COGNITIVE] 调用 Gemini 研判三档分类指标...",
    "🛡️ [SECURITY] 数据加密沙盒隔离，已阻断敏感数据泄露...",
    "🚀 [COMPLETED] 冲顶/稳妥/保底三档推荐信全量装填就绪！"
  ];

  // Micro typewriter effect
  useEffect(() => {
    if (currentLineIndex < logs.length) {
      const targetText = logs[currentLineIndex];
      let charIndex = 0;
      setTypingText('');

      const interval = setInterval(() => {
        if (charIndex < targetText.length) {
          setTypingText((prev) => prev + targetText.charAt(charIndex));
          charIndex++;
        } else {
          clearInterval(interval);
          setTimeout(() => {
            setTypedLines((prev) => [...prev, targetText]);
            setCurrentLineIndex((prev) => prev + 1);
            setTypingText('');
          }, 450);
        }
      }, 15);

      return () => clearInterval(interval);
    } else {
      setIsFinished(true);
    }
  }, [currentLineIndex]);

  // Restart trigger helper
  const handleRestart = () => {
    setTypedLines([]);
    setCurrentLineIndex(0);
    setTypingText('');
    setIsFinished(false);
  };

  return (
    <div className="w-full bg-white rounded-3xl p-6 md:p-8 border border-[#d7e3f8] shadow-xl text-left space-y-8 select-none" id="ai-risk-assessment-panel">
      {/* Upper Grid Layout: Terminal & Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
        
        {/* LEFT COLUMN: Terminal typewriter simulator */}
        <div className="lg:col-span-5 flex flex-col justify-between bg-slate-50 border border-[#e2effe] rounded-2xl p-5 relative overflow-hidden shadow-inner min-h-[340px]">
          {/* Subtle light-blue fluorescent background */}
          <div className="absolute top-0 right-0 w-48 h-48 bg-blue-100/40 rounded-full blur-3xl pointer-events-none -z-10"></div>
          
          <div className="space-y-4">
            {/* Terminal Header Info */}
            <div className="flex items-center justify-between pb-3 border-b border-blue-100/60">
              <div className="flex items-center gap-2">
                <div className="flex gap-1.5">
                  <span className="w-2.5 h-2.5 rounded-full bg-rose-400"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-amber-400"></span>
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-400"></span>
                </div>
                <span className="text-[10px] text-[#2563eb] font-mono tracking-widest font-semibold ml-2">408-RECRUIT-ENGINE</span>
              </div>
              <span className="w-2 h-2 rounded-full bg-[#2563eb] animate-pulse"></span>
            </div>

            {/* Typewritten console lines */}
            <div className="font-mono text-xs text-slate-700 space-y-3.5 pt-2 min-h-[220px]">
              {typedLines.map((line, idx) => (
                <motion.div 
                  key={idx}
                  initial={{ opacity: 0, x: -5 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ duration: 0.3 }}
                  className="flex items-start gap-2"
                >
                  <span className="text-[#2563eb] text-[10px] select-none mt-0.5">❯</span>
                  <span className={idx === logs.length - 1 ? "text-emerald-600 font-bold" : "text-slate-800"}>
                    {line}
                  </span>
                </motion.div>
              ))}

              {/* Active typing live cursor line */}
              {currentLineIndex < logs.length && (
                <div className="flex items-start gap-2 text-[#2563eb]">
                  <span className="text-[10px] select-none mt-0.5">❯</span>
                  <span className="text-[#2563eb]">
                    {typingText}
                    <span className="inline-block w-1.5 h-4 bg-[#2563eb] ml-0.5 animate-pulse" />
                  </span>
                </div>
              )}

              {/* Completion micro button */}
              {isFinished && (
                <motion.button
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  onClick={handleRestart}
                  className="mt-4 px-3 py-1 bg-white hover:bg-slate-100 ring-1 ring-blue-100 rounded-md text-[10px] font-bold text-[#2563eb] cursor-pointer transition flex items-center gap-1"
                >
                  <Sparkles className="w-3 h-3" />
                  <span>重新评估生成</span>
                </motion.button>
              )}
            </div>
          </div>

          <div className="pt-4 border-t border-slate-200/50 flex justify-between items-center text-[10px] text-slate-400 font-mono">
            <span>MODEL: 408-RECRUIT-V1</span>
            <span>STATUS: {isFinished ? "COMPLETED" : "PROCESSING..."}</span>
          </div>
        </div>

        {/* RIGHT COLUMN: Red/Blue/Green alignment result cards */}
        <div className="lg:col-span-7 flex flex-col justify-between gap-4">
          
          {/* 1. Sprint challenging card (Red block indicator) */}
          <motion.div 
            whileHover={{ x: 4, transition: { duration: 0.2 } }}
            className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-5 bg-white border border-[#fecdd3] hover:border-rose-300 rounded-2xl relative overflow-hidden group shadow-sm transition-colors duration-200"
          >
            {/* Red left solid block line element */}
            <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-rose-500"></div>
            
            <div className="pl-4 space-y-1">
              <span className="inline-flex items-center gap-1 text-[10px] font-extrabold text-rose-600 uppercase tracking-widest bg-rose-50 px-2 py-0.5 rounded">
                冲刺目标
              </span>
              <h3 className="text-base font-bold text-slate-900 font-sans">
                浙江大学 — 软件学院
              </h3>
              <p className="text-xs text-slate-500 font-light">
                专业: 软件工程 (085400) · 全日制专硕 · 历年招考大热点
              </p>
            </div>

            <div className="mt-3 sm:mt-0 text-left sm:text-right pl-4 sm:pl-0 border-l sm:border-l-0 border-slate-100 flex flex-col justify-center min-w-[120px]">
              <span className="text-[10px] text-slate-400 block font-mono">AI研判风险概率</span>
              <span className="text-lg font-black text-rose-500 block font-mono">68% <span className="text-[10px] font-light text-rose-400">竞争激烈</span></span>
              <span className="text-[10px] font-bold text-slate-600 font-sans block bg-slate-50 border border-slate-100 px-1.5 py-0.5 rounded mt-1 text-center inline-block">
                基准参考分: 395
              </span>
            </div>
          </motion.div>

          {/* 2. Steady safe card (Blue block indicator) */}
          <motion.div 
            whileHover={{ x: 4, transition: { duration: 0.2 } }}
            className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-5 bg-white border border-[#bfdbfe] hover:border-blue-300 rounded-2xl relative overflow-hidden group shadow-sm transition-colors duration-200"
          >
            {/* Blue left solid block line element */}
            <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-[#2563eb]"></div>
            
            <div className="pl-4 space-y-1">
              <span className="inline-flex items-center gap-1 text-[10px] font-extrabold text-[#2563eb] uppercase tracking-widest bg-[#eff6ff] px-2 py-0.5 rounded">
                稳妥关注
              </span>
              <h3 className="text-base font-bold text-slate-900 font-sans">
                华东师范大学 — 数据科学与工程学院
              </h3>
              <p className="text-xs text-slate-500 font-light">
                专业: 数据科学与技术 (081200) · 卓越计划生源名额宽宏
              </p>
            </div>

            <div className="mt-3 sm:mt-0 text-left sm:text-right pl-4 sm:pl-0 border-l sm:border-l-0 border-slate-100 flex flex-col justify-center min-w-[120px]">
              <span className="text-[10px] text-slate-400 block font-mono">AI研判风险概率</span>
              <span className="text-lg font-black text-[#2563eb] block font-mono">23% <span className="text-[10px] font-light text-blue-400">竞争适中</span></span>
              <span className="text-[10px] font-bold text-slate-600 font-sans block bg-slate-50 border border-slate-100 px-1.5 py-0.5 rounded mt-1 text-center inline-block">
                基准参考分: 365
              </span>
            </div>
          </motion.div>

          {/* 3. 保底 fallback card (Green block indicator) */}
          <motion.div 
            whileHover={{ x: 4, transition: { duration: 0.2 } }}
            className="flex flex-col sm:flex-row items-start sm:items-center justify-between p-5 bg-white border border-[#bbf7d0] hover:border-emerald-300 rounded-2xl relative overflow-hidden group shadow-sm transition-colors duration-200"
          >
            {/* Green left solid block line element */}
            <div className="absolute left-0 top-0 bottom-0 w-1.5 bg-emerald-500"></div>
            
            <div className="pl-4 space-y-1">
              <span className="inline-flex items-center gap-1 text-[10px] font-extrabold text-emerald-600 uppercase tracking-widest bg-emerald-50 px-2 py-0.5 rounded">
                保底安全档
              </span>
              <h3 className="text-base font-bold text-slate-900 font-sans">
                杭州电子科技大学 — 计算机学院
              </h3>
              <p className="text-xs text-slate-500 font-light">
                专业: 计算机科学与技术 (081200) · 卓越计划生源名额宽宏
              </p>
            </div>

            <div className="mt-3 sm:mt-0 text-left sm:text-right pl-4 sm:pl-0 border-l sm:border-l-0 border-slate-100 flex flex-col justify-center min-w-[120px]">
              <span className="text-[10px] text-slate-400 block font-mono">AI研判风险概率</span>
              <span className="text-lg font-black text-emerald-500 block font-mono">8% <span className="text-[10px] font-light text-emerald-400">竞争平稳</span></span>
              <span className="text-[10px] font-bold text-slate-600 font-sans block bg-slate-50 border border-slate-100 px-1.5 py-0.5 rounded mt-1 text-center inline-block">
                基准参考分: 340
              </span>
            </div>
          </motion.div>

        </div>
      </div>
    </div>
  );
}
