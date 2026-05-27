/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { Database, ArrowRight, Layers, Sliders, GitFork, FileText, AlertTriangle, Monitor, Sparkles, ChevronRight, HelpCircle, Cpu, Globe, Terminal, Shield, Zap, ExternalLink, Loader2, LogIn, User, Filter, Brain, Bookmark } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import Simulator from './components/Simulator';
import AiRiskAssessment from './components/AiRiskAssessment';
import DataConstellation from './components/DataConstellation';

export default function App() {
  const [[page, direction], setPage] = useState([0, 0]);
  const [hoveredFeat, setHoveredFeat] = useState(0);
  const [launchState, setLaunchState] = useState<'idle' | 'connecting' | 'fetching' | 'calibrating' | 'active'>('idle');
  const [launchProgress, setLaunchProgress] = useState(0);
  const [launchLogs, setLaunchLogs] = useState<string[]>([]);
  const [dashboardQuota, setDashboardQuota] = useState(12);
  const [dashboardTuition, setDashboardTuition] = useState(18000);
  const [isExportingReport, setIsExportingReport] = useState(false);
  const [exportSuccess, setExportSuccess] = useState(false);

  const [workflowVisible, setWorkflowVisible] = useState(false);
  const workflowRef = React.useRef<HTMLDivElement>(null);

  const [cardsVisible, setCardsVisible] = useState(false);
  const cardsRef = React.useRef<HTMLDivElement>(null);

  const [footerVisible, setFooterVisible] = useState(false);
  const footerRef = React.useRef<HTMLElement>(null);

  useEffect(() => {
    if (page !== 3) {
      setWorkflowVisible(false);
      setCardsVisible(false);
      setFooterVisible(false);
      return;
    }
    
    // Auto trigger visible states to ensure entry animations run perfectly without scroll observer issues of iframe/nested containers
    const timer = setTimeout(() => {
      setWorkflowVisible(true);
      setCardsVisible(true);
      setFooterVisible(true);
    }, 100);

    return () => {
      clearTimeout(timer);
    };
  }, [page]);

  const triggerTerminalLaunch = () => {
    if (launchState !== 'idle') return;
    
    setLaunchState('connecting');
    setLaunchProgress(15);
    setLaunchLogs([
      '⚡ [SYS_CONN] 正在与 408 统考主数据池建立专属 TLS 加密安全通道...',
      '📡 [PORT_3000] 反向代理静态路由层握手成功：已连接 Cloud Run 分布式服务器。'
    ]);

    setTimeout(() => {
      setLaunchState('fetching');
      setLaunchProgress(45);
      setLaunchLogs(prev => [
        ...prev,
        '📥 [RECALL] 自主抓取当前所设考生分数学科特征 (N=180 个拓扑节点已调回)。',
        '📊 [INTEGRITY] 数据完整度自动校验：数据可信等级 AAA 级，无遗漏样本。',
        '🔑 [SECURE] 自动通过 OAuth 安全签名沙盒，同步当前登录会话...'
      ]);
    }, 900);

    setTimeout(() => {
      setLaunchState('calibrating');
      setLaunchProgress(80);
      setLaunchLogs(prev => [
        ...prev,
        '🧠 [AI_COGNITIVE] 暖机启动：正在唤醒 Gemini AI 大脑与考研研招专有知识图谱。',
        '🎯 [CALIBRATE] 根据您输入的估分表现，完成 21 所推荐院校起伏地形的加权演算。',
        '✨ [SYS_STATUS] 预加载校验通过，终端系统启动就绪。'
      ]);
    }, 2000);

    setTimeout(() => {
      setLaunchState('active');
      setLaunchProgress(100);
      setLaunchLogs(prev => [
        ...prev,
        '🚀 [READY] 专业版决策终端启动完成！已成功载入完整正式用户端控制面板。'
      ]);
    }, 2900);
  };

  const coreFeatures = [
    {
      title: "不只看复试线",
      desc: "同时展示复试线、拟录取最低分与统考招生名额，综合计算有效难度分，避免只看复试线被虚高数据误导。",
      icon: Layers
    },
    {
      title: "冲稳保分层",
      desc: "AI 综合你的分数与院校历史数据，输出冲刺、稳妥、保底三档判断，每档均附风险概率与推荐理由，志愿梯度有据可查。",
      icon: Sliders
    },
    {
      title: "多院校横向对比",
      desc: "在收藏列表中横向对比备选院校的复试线、招生人数、数据完整度和 AI 风险评级，迅速缩小志愿范围。",
      icon: GitFork
    },
    {
      title: "收藏备选清单",
      desc: "一键收藏感兴趣的专业方向，支持添加备注，历史推荐记录完整保存，志愿填报前随时回溯对比。",
      icon: FileText
    }
  ];

  const sectionsList = [
    { id: 'overview-section', label: '首页面板', sub: 'CS EXAM OVERVIEW' },
    { id: 'simulator-section', label: '估分模拟', sub: 'COGNITIVE ASSESSMENT' },
    { id: 'risk-assessment-section', label: 'AI风险评估', sub: 'AI RISK ASSESSMENT' },
    { id: 'features-section', label: '专业客户端', sub: 'ENTERPRISE TERMINAL' }
  ];

  const currentIdx = page;
  const activeSection = sectionsList[currentIdx].id;

  const paginate = (newPageIndex: number) => {
    if (newPageIndex < 0 || newPageIndex >= sectionsList.length) return;
    const scrollDirection = newPageIndex > page ? 1 : -1;
    setPage([newPageIndex, scrollDirection]);
  };

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Skip if typing in an input/textarea
      if (document.activeElement?.tagName === 'INPUT' || document.activeElement?.tagName === 'TEXTAREA') {
        return;
      }
      if (e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') {
        e.preventDefault();
        if (page < sectionsList.length - 1) paginate(page + 1);
      } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
        e.preventDefault();
        if (page > 0) paginate(page - 1);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [page]);

  // Touch Swipe for Mobile smoothness
  const [touchStart, setTouchStart] = useState<number | null>(null);
  const handleTouchStart = (e: React.TouchEvent) => {
    setTouchStart(e.touches[0].clientY);
  };
  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStart === null) return;
    const touchEnd = e.changedTouches[0].clientY;
    const diff = touchStart - touchEnd;
    if (Math.abs(diff) > 50) {
      if (diff > 0) {
        // Swiped up -> next slide
        if (page < sectionsList.length - 1) paginate(page + 1);
      } else {
        // Swiped down -> prev slide
        if (page > 0) paginate(page - 1);
      }
    }
    setTouchStart(null);
  };

  // Smart Wheel Navigation Controller
  useEffect(() => {
    let lastTime = Date.now();
    const handleWheel = (e: WheelEvent) => {
      const now = Date.now();
      // Allow scroll changes every 800ms
      if (now - lastTime < 800) return;

      const deltaY = e.deltaY;
      if (Math.abs(deltaY) < 30) return; // filter drift

      // Check if mouse is hovering an element that has overflow scroll we should preserve
      let target = e.target as HTMLElement | null;
      let insideScrollable = false;

      while (target && target !== document.body) {
        const overflowY = window.getComputedStyle(target).overflowY;
        const isScrollable = overflowY === 'auto' || overflowY === 'scroll';
        if (isScrollable) {
          const hasScrollUp = target.scrollTop > 5;
          const hasScrollDown = target.scrollHeight - target.scrollTop > target.clientHeight + 5;
          if ((deltaY > 0 && hasScrollDown) || (deltaY < 0 && hasScrollUp)) {
            insideScrollable = true;
            break;
          }
        }
        target = target.parentElement;
      }

      if (insideScrollable) {
        return; // Preserve component native scroll
      }

      if (deltaY > 0) {
        if (page < sectionsList.length - 1) {
          paginate(page + 1);
          lastTime = now;
        }
      } else {
        if (page > 0) {
          paginate(page - 1);
          lastTime = now;
        }
      }
    };

    window.addEventListener('wheel', handleWheel, { passive: true });
    return () => window.removeEventListener('wheel', handleWheel);
  }, [page]);

  // Framer Motion direction-based Apple slide transition variants
  const slideVariants = {
    enter: (dir: number) => ({
      y: dir > 0 ? '100vh' : '-100vh',
      opacity: 0.1,
      scale: 0.98,
    }),
    center: {
      y: 0,
      opacity: 1,
      scale: 1,
      transition: {
        y: { type: 'spring', stiffness: 280, damping: 30 },
        opacity: { duration: 0.4 },
        scale: { duration: 0.5, ease: [0.16, 1, 0.3, 1] }
      }
    },
    exit: (dir: number) => ({
      y: dir > 0 ? '-40vh' : '40vh',
      opacity: 0,
      scale: 0.97,
      transition: {
        y: { type: 'spring', stiffness: 280, damping: 30 },
        opacity: { duration: 0.3 }
      }
    })
  };

  const isDarkTheme = false;
  const themeBgClass = page === 3 ? 'bg-[#F0F4FF] text-[#0f172a]' : 'bg-[#eff6ff] text-[#0f172a]';

  return (
    <div 
      className={`h-screen w-screen overflow-hidden font-sans antialiased flex flex-col relative select-none transition-colors duration-1000 ${themeBgClass}`}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {/* Background radial ambient highlights */}
      {page === 3 ? (
        <div className="absolute inset-0 pointer-events-none overflow-hidden -z-10">
          <style>{`
            @keyframes scale_rt {
              0%, 100% { transform: scale(1.0); }
              50% { transform: scale(1.06); }
            }
            @keyframes scale_lb {
              0%, 100% { transform: scale(1.0); }
              50% { transform: scale(1.04); }
            }
            @keyframes translateX_rt {
              0%, 100% { transform: translateX(0); }
              50% { transform: translateX(8px); }
            }
            @keyframes translateX_lb {
              0%, 100% { transform: translateX(0); }
              50% { transform: translateX(-6px); }
            }

            @keyframes slideDownIn {
              from {
                transform: translateY(-8px);
                opacity: 0;
              }
              to {
                transform: translateY(0);
                opacity: 1;
              }
            }
            @keyframes slideUpIn {
              from {
                transform: translateY(12px);
                opacity: 0;
              }
              to {
                transform: translateY(0);
                opacity: 1;
              }
            }
            @keyframes slideUpInMuted {
              from {
                transform: translateY(10px);
                opacity: 0;
              }
              to {
                transform: translateY(0);
                opacity: 1;
              }
            }
            @keyframes arrowFlow {
              0%, 100% { transform: translateX(0); }
              50% { transform: translateX(3px); }
            }
            @keyframes ctaPulse {
              0%, 100% {
                box-shadow: 0 4px 20px rgba(37,99,235,0.20);
              }
              50% {
                box-shadow: 0 4px 28px rgba(37,99,235,0.30);
              }
            }

            .animate-custom-header {
              animation: slideDownIn 400ms cubic-bezier(0.16, 1, 0.3, 1) forwards;
            }
            .animate-custom-capsule {
              animation: slideUpInMuted 500ms cubic-bezier(0.16, 1, 0.3, 1) both;
              animation-delay: 100ms;
            }
            .animate-custom-title1 {
              animation: slideUpIn 550ms cubic-bezier(0.16, 1, 0.3, 1) both;
              animation-delay: 200ms;
            }
            .animate-custom-title2 {
              animation: slideUpIn 550ms cubic-bezier(0.16, 1, 0.3, 1) both;
              animation-delay: 300ms;
            }
            .animate-custom-subtitle {
              animation: slideUpIn 500ms cubic-bezier(0.16, 1, 0.3, 1) both;
              animation-delay: 400ms;
            }
            .animate-custom-cta {
              animation: slideUpIn 500ms cubic-bezier(0.16, 1, 0.3, 1) both;
              animation-delay: 500ms;
            }
            .animate-cta-pulse {
              animation: ctaPulse 1200ms cubic-bezier(0.16, 1, 0.3, 1) 1;
              animation-delay: 800ms;
              animation-fill-mode: forwards;
            }

            .workflow-step {
              opacity: 0;
              transform: translateY(10px);
              transition: transform 450ms cubic-bezier(0.16, 1, 0.3, 1), opacity 450ms cubic-bezier(0.16, 1, 0.3, 1);
            }
            .workflow-step.visible {
              opacity: 1;
              transform: translateY(0);
            }

            .workflow-arrow {
              display: inline-block;
              color: #CBD5E1;
              transition: color 300ms ease;
            }
            .workflow-arrow.flow {
              animation: arrowFlow 600ms cubic-bezier(0.16, 1, 0.3, 1) 1;
              animation-delay: 200ms;
              animation-fill-mode: forwards;
            }

            .feature-card {
              opacity: 0;
              transform: translateY(8px);
              transition: 
                transform 400ms cubic-bezier(0.16, 1, 0.3, 1), 
                opacity 400ms cubic-bezier(0.16, 1, 0.3, 1),
                border-color 200ms cubic-bezier(0.16, 1, 0.3, 1),
                box-shadow 200ms cubic-bezier(0.16, 1, 0.3, 1);
            }
            .feature-card.visible {
              opacity: 1;
              transform: translateY(0);
            }
            .feature-card.visible:hover {
              border-color: rgba(37, 99, 235, 0.32) !important;
              box-shadow: 0 2px 12px rgba(37, 99, 235, 0.08) !important;
              transform: translateY(-2px) !important;
            }

            .main-cta-btn {
              box-shadow: 0 4px 20px rgba(37,99,235,0.20);
              transition: 
                transform 150ms cubic-bezier(0.16, 1, 0.3, 1), 
                box-shadow 150ms cubic-bezier(0.16, 1, 0.3, 1),
                background-color 150ms cubic-bezier(0.16, 1, 0.3, 1);
            }
            .main-cta-btn:hover {
              transform: translateY(-1px) !important;
              box-shadow: 0 6px 24px rgba(37,99,235,0.28) !important;
            }
            .main-cta-btn:active {
              transform: translateY(0) !important;
            }

            .custom-footer {
              opacity: 0;
              transition: opacity 600ms cubic-bezier(0.16, 1, 0.3, 1);
            }
            .custom-footer.visible {
              opacity: 1;
            }
          `}</style>
          {/* Top-right radial glow */}
          <div className="absolute -top-40 -right-40 w-[500px] h-[500px]" style={{ animation: 'translateX_rt 12s ease-in-out infinite' }}>
            <div 
              className="w-full h-full rounded-full blur-[100px]"
              style={{
                background: 'radial-gradient(circle, rgba(37,99,235,0.10) 0%, rgba(37,99,235,0.01) 70%, transparent 100%)',
                animation: 'scale_rt 8s ease-in-out infinite'
              }}
            />
          </div>
          {/* Bottom-left radial glow */}
          <div className="absolute -bottom-24 -left-24 w-[300px] h-[300px]" style={{ animation: 'translateX_lb 15s ease-in-out infinite' }}>
            <div 
              className="w-full h-full rounded-full blur-[80px]"
              style={{
                background: 'radial-gradient(circle, rgba(96,165,250,0.08) 0%, rgba(96,165,250,0.01) 70%, transparent 100%)',
                animation: 'scale_lb 11s ease-in-out infinite'
              }}
            />
          </div>
        </div>
      ) : (
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-screen h-screen pointer-events-none -z-10 transition-opacity duration-1000 opacity-100">
          <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[700px] h-[700px] bg-blue-600/[0.04] rounded-full blur-[150px]"></div>
        </div>
      )}

      {/* APPLE-STYLE TIMELINE NAVIGATION HARNESSED ON THE RIGHT EDGE */}
      <div 
        className="fixed right-8 top-1/2 -translate-y-1/2 z-40 hidden md:flex flex-col items-end gap-6 p-5 rounded-3xl border transition-all duration-500 backdrop-blur-md bg-white/90 border-[#cbe0fb] shadow-lg"
        id="timeline-nav-pills"
      >
        <div className="flex flex-col gap-5 relative">
          {/* Active timeline thread track */}
          <div className="absolute right-[9px] top-1 bottom-1 w-[2px] bg-slate-200/40 rounded-full">
            <motion.div 
              className="w-full rounded-full bg-[#2563eb]"
              animate={{ 
                height: `${(page / (sectionsList.length - 1)) * 100}%` 
              }}
              transition={{ type: "spring", stiffness: 100, damping: 18 }}
            />
          </div>

          {sectionsList.map((item, index) => {
            const isActive = page === index;
            return (
              <button
                key={item.id}
                onClick={() => paginate(index)}
                className="flex items-center justify-end group focus:outline-none relative pr-0.5 text-right cursor-pointer"
                title={item.label}
              >
                {/* Descriptive sub-badges on hover */}
                <div className="mr-4 flex flex-col items-end opacity-0 group-hover:opacity-100 transition-all duration-300 pointer-events-none absolute right-full transform translate-x-2 group-hover:translate-x-0">
                  <span className="text-[11px] font-bold px-2 py-0.5 rounded shadow-sm whitespace-nowrap border bg-white text-[#2563eb] border-[#d7e3f8]">
                    {item.label}
                  </span>
                  <span className="text-[7px] text-slate-500 font-mono tracking-widest uppercase block mt-0.5 pr-0.5">{item.sub}</span>
                </div>

                <div className="flex items-center gap-3">
                  <span className={`text-[10px] font-mono tracking-wider font-semibold transition-all duration-300 ${
                    isActive ? 'text-[#2563eb]' : 'text-slate-400'
                  }`}>
                    0{index + 1}
                  </span>
                  <span className={`w-4 h-4 rounded-full flex items-center justify-center transition-all duration-300 relative z-10 ${
                    isActive ? 'bg-[#2563eb] scale-125 shadow-md' : 'bg-slate-300/60 hover:bg-[#2563eb]/50'
                  }`}>
                    {isActive && <span className="w-1.5 h-1.5 rounded-full bg-white block" />}
                  </span>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* TOP NAVIGATION HEADER */}
      <header className="w-full shrink-0 z-50 px-4 py-2 sm:py-2.5 transition-all duration-500">
        <nav className="max-w-7xl mx-auto flex items-center justify-between border backdrop-blur-md rounded-full px-5 md:px-8 py-1.5 sm:py-2 transition-all duration-500 bg-white/95 border-[#d7e3f8] text-[#0f172a] shadow-md">
          <button 
            onClick={() => paginate(0)}
            className="flex items-center gap-3 hover:opacity-90 select-none text-left cursor-pointer"
          >
            <div className="w-8 h-8 rounded-full border flex items-center justify-center shrink-0 border-[#d7e3f8] bg-[#eff6ff]">
              <Database className="w-4 h-4 text-[#2563eb]" />
            </div>
            <div>
              <span className="text-xs font-bold tracking-widest block uppercase">
                408 <span className="font-light opacity-80 transition-colors text-[#64748b]">研招智能数据系统</span>
              </span>
              <span className="text-[9px] font-mono tracking-wider uppercase block transition-colors text-[#64748b]">
                一站式初试估分与择校决策服务
              </span>
            </div>
          </button>

          {/* Desktop Navigation Link Tabs */}
          <div className="hidden md:flex items-center gap-7 text-xs font-semibold">
            {sectionsList.map((item, index) => {
              const isActive = page === index;
              return (
                <button
                  key={item.id}
                  onClick={() => paginate(index)}
                  className={`transition-colors relative px-1 py-1.5 duration-300 select-none cursor-pointer ${
                    isActive ? 'text-[#2563eb]' : 'text-slate-500 hover:text-[#2563eb]'
                  }`}
                >
                  <span>{item.label}</span>
                  {isActive && (
                    <motion.span 
                      layoutId="active-indicator"
                      className="absolute bottom-0 left-0 right-0 h-0.5 rounded bg-[#2563eb]"
                      transition={{ type: "spring", stiffness: 350, damping: 28 }}
                    />
                  )}
                </button>
              );
            })}
          </div>

          <div className="flex items-center gap-3">
            <span className="hidden sm:inline-flex items-center gap-1.5 border font-mono text-[9px] uppercase tracking-widest px-3 py-1 rounded-full select-none bg-[#eff6ff] border-[#d7e3f8] text-[#2563eb]">
              <span className="w-1.5 h-1.5 rounded-full animate-ping bg-[#2563eb]"></span>
              2026 择校辅佐
            </span>
            <button 
              onClick={() => paginate(page === 1 ? 2 : 1)}
              className="px-5 py-2 text-xs font-bold rounded-full transition shadow-sm hover:shadow-md cursor-pointer inline-flex items-center gap-1.5 bg-[#2563eb] hover:bg-blue-700 text-white"
            >
              <span>{page === 1 ? 'AI风险评估' : '开始择校'}</span>
              <ChevronRight className="w-3" />
            </button>
          </div>
        </nav>
      </header>

      {/* IMMERSIVE MAIN STAGE CONTAINER */}
      <main className="flex-grow w-full overflow-hidden relative flex flex-col justify-center items-center">
        <AnimatePresence mode="wait" initial={false} custom={direction}>
          <motion.div
            key={page}
            custom={direction}
            variants={slideVariants}
            initial="enter"
            animate="center"
            exit="exit"
            className="w-full h-full max-w-7xl mx-auto px-4 md:px-8 flex flex-col justify-center overflow-hidden absolute inset-0"
          >
            {/* SCREEN 1: IMMERSIVE OVERVIEW */}
            {activeSection === 'overview-section' && (
              <div className="flex flex-col items-center justify-center text-center space-y-6 py-6 select-none mt-[-5vh]">
                <div className="space-y-4">
                  <span className="text-[10px] text-[#2563eb] font-extrabold tracking-[0.3em] uppercase bg-white/95 border border-[#d7e3f8] px-4 py-2 rounded-full shadow-md">
                    ★ 408 CS 考研择校 · 数据筛选 × AI研判 ★
                  </span>
                  
                  <h1 className="text-3xl sm:text-5xl lg:text-6.5xl font-black tracking-tight text-[#0f172a] leading-[1.12] pt-4">
                    精准破译 408 统考指标
                    <span className="text-[#2563eb] block mt-3.5 relative text-lg sm:text-3xl lg:text-[2.6rem] font-bold tracking-tight select-none pb-2">
                      告别信息泡沫，做有据可查的择校决策
                      <span className="absolute bottom-0 left-1/2 -translate-x-1/2 w-48 h-1 bg-[#2563eb] rounded-full opacity-30"></span>
                    </span>
                  </h1>

                  <p className="text-sm sm:text-base text-[#64748b] font-light max-w-2xl mx-auto leading-relaxed pt-2">
                    填写你的分数与偏好，系统从真实招生数据中精准召回匹配院校，再由 AI 结合你的情况逐一研判，给出冲稳保三档有依据的择校建议。
                  </p>
                </div>

                <div className="flex flex-col sm:flex-row justify-center items-center gap-4 pt-1">
                  <button 
                    onClick={() => paginate(1)}
                    className="px-8 py-3 bg-[#2563eb] hover:bg-blue-700 text-white text-xs font-bold uppercase tracking-widest rounded-full transition shadow-md hover:shadow-lg inline-flex items-center gap-2 h-12 cursor-pointer"
                  >
                    <Sliders className="w-4 h-4" />
                    <span>即刻开始估分预测</span>
                  </button>
                  <button 
                     onClick={() => paginate(2)}
                     className="px-8 py-3 border border-[#d7e3f8] bg-white text-[#0f172a] hover:bg-[#eff6ff] text-xs font-bold uppercase tracking-widest rounded-full transition shadow-sm inline-flex items-center gap-2 h-12 cursor-pointer"
                  >
                    <Sparkles className="w-4 h-4 text-[#2563eb]" />
                    <span>AI风险评估系统</span>
                  </button>
                </div>

                {/* Performance indicators - custom strip */}
                <div 
                  className="w-screen bg-white/70 py-[28px] px-10 relative left-1/2 right-1/2 -translate-x-1/2 select-none"
                  style={{
                    borderTop: '0.5px solid rgba(37,99,235,0.08)',
                    borderBottom: '0.5px solid rgba(37,99,235,0.08)',
                    backgroundColor: 'rgba(255, 255, 255, 0.7)'
                  }}
                >
                  <div className="max-w-4xl mx-auto flex flex-row items-center justify-between">
                    <div className="flex-1 text-center font-sans">
                      <span className="text-2xl md:text-3xl font-extrabold font-mono text-[#2563eb]">100%</span>
                      <span className="text-[14px] text-[#475569] block mt-1.5 font-sans font-medium">CS 统考科目全覆盖</span>
                    </div>
                    <div 
                      className="w-[1px] h-10 shrink-0 self-center" 
                      style={{ backgroundColor: 'rgba(37,99,235,0.12)' }}
                    />
                    <div className="flex-1 text-center font-sans">
                      <span className="text-2xl md:text-3xl font-extrabold font-mono text-[#0f172a]">21所</span>
                      <span className="text-[14px] text-[#475569] block mt-1.5 font-sans font-medium">首批重点院校真数据</span>
                    </div>
                    <div 
                      className="w-[1px] h-10 shrink-0 self-center" 
                      style={{ backgroundColor: 'rgba(37,99,235,0.12)' }}
                    />
                    <div className="flex-1 text-center font-sans">
                      <span className="text-2xl md:text-3xl font-extrabold font-mono text-[#2563eb]">100%</span>
                      <span className="text-[14px] text-[#475569] block mt-1.5 font-sans font-medium">招生出处真实可追溯</span>
                    </div>
                  </div>
                </div>

                {/* Subtle instruction tip - moved below and set 20px spacer */}
                <div className="mt-[20px] flex items-center gap-2 text-[10px] text-slate-400 font-mono tracking-widest animate-pulse max-w-xs mx-auto">
                  <ChevronRight className="w-3.5 h-3.5 rotate-90" />
                  <span>使用滚轮、方向键或滑动手势翻页</span>
                </div>
              </div>
            )}

            {/* SCREEN 2: ESTIMATION SIMULATOR PANEL */}
            {activeSection === 'simulator-section' && (
              <div className="w-full h-full flex flex-col justify-center py-4 mt-2 max-h-[85vh]">
                <div className="bg-white rounded-3xl p-3 md:p-6 border border-[#d7e3f8] shadow-2xl overflow-y-auto max-h-[81vh] scrollbar-thin scrollbar-track-slate-50 scrollbar-thumb-slate-200">
                  <Simulator />
                </div>
              </div>
            )}

            {/* SCREEN 3: AI INTELLIGENT RISK ASSESSMENT */}
            {activeSection === 'risk-assessment-section' && (
              <div className="w-full h-full flex flex-col justify-center py-4">
                <div className="mb-4 text-left max-w-3xl px-1 shrink-0">
                  <div className="flex items-center gap-2 text-[#2563eb] font-bold tracking-widest text-[11px] uppercase mb-1">
                    <Sparkles className="w-4 h-4 text-[#2563eb]" />
                    <span>像顾问一样分析，像数据一样精准</span>
                  </div>
                  <h2 className="text-2xl md:text-3xl font-black text-[#0f172a] tracking-tight leading-none mb-1">
                    AI智能风险评估
                  </h2>
                  <p className="text-[#64748b] text-xs leading-relaxed font-light">
                    系统将筛选出的院校数据交给 AI 综合研判，输出冲/稳/保三档分类与风险概率，并给出你能看懂的择校理由
                  </p>
                </div>
                <div className="w-full">
                  <AiRiskAssessment />
                </div>
              </div>
            )}

            {/* SCREEN 4: START SCHOOL SELECTING CONVERSION SCREEN */}
            {activeSection === 'features-section' && (
              <div id="school-selector-conversion" className="w-full flex-grow flex flex-col justify-center items-center py-10 md:py-16 relative overflow-hidden">
                {/* Extremely faint dot matrix background to add high quality texture */}
                <div 
                  className="absolute inset-0 pointer-events-none opacity-40 select-none z-0" 
                  style={{ 
                    backgroundImage: 'radial-gradient(rgba(37,99,235,0.06) 1.5px, transparent 1.5px)', 
                    backgroundSize: '24px 24px' 
                  }} 
                />

                <motion.div
                  key="start-selecting"
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                  className="max-w-5xl w-full mx-auto px-4 text-center flex flex-col items-center py-8 z-10 relative"
                >
                  {/* Layer 1: Capsule Badge */}
                  <motion.div 
                    initial={{ opacity: 0, scale: 0.95 }}
                    animate={{ opacity: 1, scale: 1 }}
                    transition={{ duration: 0.6, delay: 0.1, ease: 'easeOut' }}
                    className="inline-flex items-center gap-2 px-4 py-1.5 bg-[#2563eb]/[0.06] border border-[#2563eb]/15 rounded-full text-xs font-bold text-[#2563eb] mb-8 select-none tracking-wider"
                  >
                    <span className="flex h-1.5 w-1.5 relative">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#2563eb] opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-1.5 w-1.5 bg-[#2563eb]"></span>
                    </span>
                    <span>准备就绪 · 随时进入正式客户端</span>
                  </motion.div>

                  {/* Layer 2: Main Title (72px, two lines, abundant negative space, staggered character fade-in) */}
                  <h1 className="text-4xl sm:text-[68px] lg:text-[76px] font-black text-[#0F172A] leading-[1.12] mb-6 tracking-tight font-sans text-center max-w-5xl">
                    <span className="block mb-2 md:mb-4 select-none">
                      {"填写画像，让数据".split("").map((char, index) => (
                        <motion.span
                          key={`l1-${index}`}
                          initial={{ opacity: 0, y: 15 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ 
                            duration: 0.5, 
                            delay: index * 0.05, 
                            ease: [0.16, 1, 0.3, 1] 
                          }}
                          className="inline-block"
                        >
                          {char}
                        </motion.span>
                      ))}
                    </span>
                    <span className="block text-[#2563eb] select-none">
                      {"帮你决策".split("").map((char, index) => (
                        <motion.span
                          key={`l2-${index}`}
                          initial={{ opacity: 0, y: 15 }}
                          animate={{ opacity: 1, y: 0 }}
                          transition={{ 
                            duration: 0.5, 
                            delay: (8 + index) * 0.05, 
                            ease: [0.16, 1, 0.3, 1] 
                          }}
                          className="inline-block"
                        >
                          {char}
                        </motion.span>
                      ))}
                    </span>
                  </h1>

                  {/* Layer 3: Elegant Subtitle */}
                  <motion.p 
                    initial={{ opacity: 0, y: 15 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.8, delay: 0.8, ease: [0.16, 1, 0.3, 1] }}
                    className="text-sm md:text-base text-[#64748B] leading-relaxed max-w-[560px] text-center mb-10 font-light"
                  >
                    注册登录后，填写你的估分画像与院校偏好，正式端系统将精准召回并适配招生目标，AI 深度研判冲稳保推荐，极速完成客观科学报考决策。
                  </motion.p>

                  {/* Layer 4: Big Core Focus CTA Buttons with Glow Overlay */}
                  <motion.div 
                    initial={{ opacity: 0, y: 15 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.8, delay: 0.95, ease: [0.16, 1, 0.3, 1] }}
                    className="flex flex-col sm:flex-row items-center justify-center gap-4 z-10 w-full"
                  >
                    <a
                      href={import.meta.env.VITE_MAIN_APP_URL || `http://localhost:8081/app/recommend`}
                      target="_blank"
                      rel="noreferrer"
                      className="w-full sm:w-auto px-10 py-4.5 bg-[#2563eb] text-white text-sm font-semibold rounded-full shadow-[0_4px_16px_rgba(37,99,235,0.25)] hover:shadow-[0_0_24px_2px_rgba(37,99,235,0.6)] hover:bg-[#1d4ed8] hover:scale-[1.02] active:scale-[0.98] transition-all duration-300 inline-flex items-center justify-center gap-2.5 cursor-pointer text-center group tracking-wide z-10"
                    >
                      <LogIn className="w-4 h-4 text-white group-hover:translate-x-0.5 transition-transform duration-150" />
                      <span>进入正式客户端</span>
                    </a>

                    <button
                      onClick={() => paginate(0)}
                      className="w-full sm:w-auto px-10 py-4.5 border border-slate-200 bg-white/80 hover:bg-[#f8fafc] text-slate-700 hover:border-slate-300 text-sm font-medium rounded-full active:scale-[0.98] transition-all duration-300 cursor-pointer inline-flex items-center justify-center gap-2 z-10"
                    >
                      <Sparkles className="w-4 h-4 text-[#2563eb]" />
                      <span>了解更多 · 回到首页</span>
                    </button>
                  </motion.div>

                  {/* Security Hint Accent */}
                  <motion.div 
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ duration: 1, delay: 1.1 }}
                    className="flex items-center justify-center gap-1.5 mt-3 mb-10 select-none text-[#94A3B8] text-xs"
                  >
                    <span>🔒</span>
                    <span>您的任何敏感评估数据均本地加密，安全私密，绝不对外公开</span>
                  </motion.div>

                  {/* Layer 5: Three Core Selling Points Horizontally Layout at the bottom */}
                  <motion.div 
                    initial={{ opacity: 0, y: 15 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.8, delay: 1.25, ease: [0.16, 1, 0.3, 1] }}
                    className="w-full max-w-4xl border-t border-slate-200/50 pt-8 mt-12 md:mt-20 z-10"
                  >
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-center md:text-left select-none">
                      <div className="flex flex-col items-center md:items-start px-4">
                        <div className="flex items-center gap-2 mb-1.5 justify-center md:justify-start">
                          <span className="text-[10px] bg-blue-50 text-blue-600 font-extrabold font-mono px-2.5 py-0.5 rounded-full">01 / 来源可查</span>
                        </div>
                        <p className="text-xs text-[#64748B] font-light leading-relaxed mt-1">
                          每条招生和录取指标全部标注官方招生大纲出处，数据指标透明真实可验证。
                        </p>
                      </div>
                      <div className="flex flex-col items-center md:items-start px-4 border-t md:border-t-0 md:border-l border-slate-100 pt-4 md:pt-0 md:pl-6">
                        <div className="flex items-center gap-2 mb-1.5 justify-center md:justify-start">
                          <span className="text-[10px] bg-blue-50 text-blue-600 font-extrabold font-mono px-2.5 py-0.5 rounded-full">02 / 科学评判</span>
                        </div>
                        <p className="text-xs text-[#64748B] font-light leading-relaxed mt-1">
                          不唯最低复试线单级指标论，综合历年录取中位数及录取比例多维加权科学精研。
                        </p>
                      </div>
                      <div className="flex flex-col items-center md:items-start px-4 border-t md:border-t-0 md:border-l border-slate-100 pt-4 md:pt-0 md:pl-6">
                        <div className="flex items-center gap-2 mb-1.5 justify-center md:justify-start">
                          <span className="text-[10px] bg-blue-50 text-blue-600 font-extrabold font-mono px-2.5 py-0.5 rounded-full">03 / 透明无欺</span>
                        </div>
                        <p className="text-xs text-[#64748B] font-light leading-relaxed mt-1">
                          历史年份样本不足时明确说明提示，坚守客观原则，决不强行胡乱推断误导志愿。
                        </p>
                      </div>
                    </div>
                  </motion.div>

                  {/* Centered inline disclaimer finishing layout seamlessly */}
                  <div className="w-full mt-8 text-center select-none text-[10px] text-[#94A3B8] leading-[1.6] max-w-[560px] font-light border-t border-slate-100 pt-4 opacity-75">
                    <p>
                      数据声明：推荐评估基于各高校历年公开披露录取数据合成，不构成任何刚性报考承诺。<br />
                      考生实际报考时请以当年各大高校及教育部最新招生公告简章为准。
                    </p>
                  </div>
                </motion.div>
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
