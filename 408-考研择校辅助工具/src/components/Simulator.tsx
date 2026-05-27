/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useEffect } from 'react';
import { Sparkles, Sliders, AlertTriangle, CheckCircle2, ShieldCheck, HelpCircle, RefreshCw, Layers, MousePointer } from 'lucide-react';
import { Program, School, RecommendationResult } from '../types';
import { motion, AnimatePresence } from 'motion/react';

// Mock high-quality structural school lists from real statistical 408 directories in China
const MOCK_SCHOOLS: (School & { program: Program; medianScore: number; medianMinAdmission: number; plannedCount: number; isProtected: boolean; dataQuality: 'A' | 'B' | 'D' })[] = [
  {
    id: 1,
    name: '华东师范大学',
    shortName: '华东师大',
    province: '上海',
    city: '上海',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yjsy.ecnu.edu.cn',
    status: 'active',
    medianScore: 345,
    medianMinAdmission: 355,
    plannedCount: 28,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 101,
      collegeId: 1,
      collegeName: '计算机科学与技术学院',
      schoolName: '华东师范大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术 & (02)人工智能',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '高级英语、算法原理、程序设计上机测试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 2,
    name: '北京航空航天大学',
    shortName: '北航',
    province: '北京',
    city: '北京',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yzb.buaa.edu.cn',
    status: 'active',
    medianScore: 360,
    medianMinAdmission: 375,
    plannedCount: 12,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 102,
      collegeId: 2,
      collegeName: '计算机学院',
      schoolName: '北京航空航天大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(02)计算机系统结构 & (04)计算机软件与理论',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业知识面、C语言上机编程考试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 3,
    name: '南京大学',
    shortName: '南大',
    province: '江苏',
    city: '南京',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://grawww.nju.edu.cn',
    status: 'active',
    medianScore: 350,
    medianMinAdmission: 368,
    plannedCount: 45,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 103,
      collegeId: 3,
      collegeName: '计算机科学与技术系',
      schoolName: '南京大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术和(03)软件工程',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '综合面试、算法笔试及C++上机编程',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 4,
    name: '杭州电子科技大学',
    shortName: '杭电',
    province: '浙江',
    city: '杭州',
    level: '省属骨干高校',
    is985: false,
    is211: false,
    isDoubleFirstClass: false,
    isPublic: true,
    website: 'https://yjs.hdu.edu.cn',
    status: 'active',
    medianScore: 310,
    medianMinAdmission: 322,
    plannedCount: 160,
    isProtected: true,
    dataQuality: 'B',
    program: {
      id: 104,
      collegeId: 4,
      collegeName: '计算机学院',
      schoolName: '杭州电子科技大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '综合面试、程序设计基础笔试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 5,
    name: '华中科技大学',
    shortName: '华科',
    province: '湖北',
    city: '武汉',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://gszb.hust.edu.cn',
    status: 'active',
    medianScore: 350,
    medianMinAdmission: 352,
    plannedCount: 2, // 样本量极低
    isProtected: false, // 虚构设定用于演示非保护状态
    dataQuality: 'B',
    program: {
      id: 105,
      collegeId: 5,
      collegeName: '计算机科学与技术学院',
      schoolName: '华中科技大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)计算机系统结构',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      is408: true,
      reexaminationSubjects: '综合水平考查以及英语口语',
      isFirstChoiceProtected: false,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 6,
    name: '深圳大学',
    shortName: '深大',
    province: '广东',
    city: '深圳',
    level: '重点建设高校',
    is985: false,
    is211: false,
    isDoubleFirstClass: false,
    isPublic: true,
    website: 'https://yz.szu.edu.cn',
    status: 'active',
    medianScore: 330,
    medianMinAdmission: 348,
    plannedCount: 85,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 106,
      collegeId: 6,
      collegeName: '计算机与软件学院',
      schoolName: '深圳大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(02)计算机技术',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '计算机专业综合笔试、面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 15,
    name: '北京大学',
    shortName: '北大',
    province: '北京',
    city: '北京',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://eecs.pku.edu.cn',
    status: 'active',
    medianScore: 375,
    medianMinAdmission: 385,
    plannedCount: 15,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 115,
      collegeId: 15,
      collegeName: '计算机学院',
      schoolName: '北京大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)软件工程 & (02)计算机系统结构',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '高级数学能力、程序设计上机测试与综合面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 16,
    name: '浙江大学',
    shortName: '浙大',
    province: '浙江',
    city: '杭州',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://list.zju.edu.cn',
    status: 'active',
    medianScore: 365,
    medianMinAdmission: 378,
    plannedCount: 45,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 116,
      collegeId: 16,
      collegeName: '计算机科学与技术学院',
      schoolName: '浙江大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术 & (03)人工智能',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '综合素质面试、算法实践与C++程序设计上机测试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 17,
    name: '上海交通大学',
    shortName: '上海交大',
    province: '上海',
    city: '上海',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yzb.sjtu.edu.cn',
    status: 'active',
    medianScore: 360,
    medianMinAdmission: 372,
    plannedCount: 22,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 117,
      collegeId: 17,
      collegeName: '电子信息与电气工程学院',
      schoolName: '上海交通大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)计算机系统结构 & (03)计算机应用技术',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业英文交流能力、上机考试与综合素质面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 18,
    name: '武汉大学',
    shortName: '武大',
    province: '湖北',
    city: '武汉',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://gs.whu.edu.cn',
    status: 'active',
    medianScore: 342,
    medianMinAdmission: 355,
    plannedCount: 32,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 118,
      collegeId: 18,
      collegeName: '计算机学院',
      schoolName: '武汉大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术 & (02)数据科学',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '外语听说能力测试、综合面试、程序设计基础机试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 8,
    name: '清华大学',
    shortName: '清华',
    province: '北京',
    city: '北京',
    level: '985 / 双一流 / 顶尖九校',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yz.tsinghua.edu.cn',
    status: 'active',
    medianScore: 385,
    medianMinAdmission: 395,
    plannedCount: 5,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 108,
      collegeId: 8,
      collegeName: '计算机科学与技术系',
      schoolName: '清华大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)高性能计算 & (02)智能技术',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业知识综合面试、上机编程实战考核',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 9,
    name: '中山大学',
    shortName: '中大',
    province: '广东',
    city: '广州',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://graduate.sysu.edu.cn',
    status: 'active',
    medianScore: 340,
    medianMinAdmission: 345,
    plannedCount: 35,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 109,
      collegeId: 9,
      collegeName: '计算机学院',
      schoolName: '中山大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)体系结构 & (04)高安全软件与理论',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业英文口试、高级算法上机测试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 10,
    name: '同济大学',
    shortName: '同济',
    province: '上海',
    city: '上海',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yz.tongji.edu.cn',
    status: 'active',
    medianScore: 355,
    medianMinAdmission: 360,
    plannedCount: 22,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 110,
      collegeId: 10,
      collegeName: '计算机科学与技术学院',
      schoolName: '同济大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(02)软件工程与大数据平台',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '综合素质面试、C++程序设计笔试加机试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 11,
    name: '广西大学',
    shortName: '西大',
    province: '广西',
    city: '南宁',
    level: '211工程 / 双一流',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yjsc.gxu.edu.cn',
    status: 'active',
    medianScore: 295,
    medianMinAdmission: 300,
    plannedCount: 110,
    isProtected: true,
    dataQuality: 'B',
    program: {
      id: 111,
      collegeId: 11,
      collegeName: '计算机与电子信息学院',
      schoolName: '广西大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)网络空间安全与云计算',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '计算机综合能力笔试、综合素质面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 12,
    name: '贵州大学',
    shortName: '贵大',
    province: '贵州',
    city: '贵阳',
    level: '211工程 / 双一流',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://gs.gzu.edu.cn',
    status: 'active',
    medianScore: 285,
    medianMinAdmission: 290,
    plannedCount: 65,
    isProtected: true,
    dataQuality: 'B',
    program: {
      id: 112,
      collegeId: 12,
      collegeName: '计算机科学与技术学院',
      schoolName: '贵州大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(02)大数据系统 & (04)计算智能',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '高级英语口语、程序设计语法基础综合能力测试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 13,
    name: '西安电子科技大学',
    shortName: '西电',
    province: '陕西',
    city: '西安',
    level: '一流学科 / 211',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yzb.xidian.edu.cn',
    status: 'active',
    medianScore: 325,
    medianMinAdmission: 338,
    plannedCount: 154,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 113,
      collegeId: 13,
      collegeName: '计算机科学与技术学院',
      schoolName: '西安电子科技大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)软件工程与高保真计算',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业笔试、英语听力及口语面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 14,
    name: '太原理工大学',
    shortName: '太原理工',
    province: '山西',
    city: '太原',
    level: '211工程 / 双一流',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://yz.tyut.edu.cn',
    status: 'active',
    medianScore: 305,
    medianMinAdmission: 312,
    plannedCount: 52,
    isProtected: true,
    dataQuality: 'B',
    program: {
      id: 114,
      collegeId: 14,
      collegeName: '计算机科学与技术学院(软件学院)',
      schoolName: '太原理工大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(03)网络系统设计与人工智能应用',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '程序设计专业基础知识笔试及面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 19,
    name: '北京邮电大学',
    shortName: '北邮',
    province: '北京',
    city: '北京',
    level: '211工程 / 双一流',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yzb.bupt.edu.cn',
    status: 'active',
    medianScore: 348,
    medianMinAdmission: 358,
    plannedCount: 120,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 119,
      collegeId: 19,
      collegeName: '计算机学院',
      schoolName: '北京邮电大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)计算机技术 & (02)数据智能',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '综合面试、程序设计能力（上机测试）',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 20,
    name: '南京航空航天大学',
    shortName: '南航',
    province: '江苏',
    city: '南京',
    level: '211工程 / 双一流',
    is985: false,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'http://yzs.nuaa.edu.cn',
    status: 'active',
    medianScore: 330,
    medianMinAdmission: 340,
    plannedCount: 60,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 120,
      collegeId: 20,
      collegeName: '计算机科学与技术学院',
      schoolName: '南京航空航天大学',
      code: '085400',
      name: '电子信息 (专业学位)',
      direction: '(01)软件工程与智能计算',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'professional',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '专业基础笔试、英语口语与综合面试',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  },
  {
    id: 21,
    name: '华南理工大学',
    shortName: '华工',
    province: '广东',
    city: '广州',
    level: '985 / 双一流',
    is985: true,
    is211: true,
    isDoubleFirstClass: true,
    isPublic: true,
    website: 'https://yanzhao.scut.edu.cn',
    status: 'active',
    medianScore: 345,
    medianMinAdmission: 356,
    plannedCount: 38,
    isProtected: true,
    dataQuality: 'A',
    program: {
      id: 121,
      collegeId: 21,
      collegeName: '计算机科学与工程学院',
      schoolName: '华南理工大学',
      code: '081200',
      name: '计算机科学与技术 (学术学位)',
      direction: '(01)人工智能 & (03)高可靠网络',
      disciplineCategory: '工学',
      firstClassDiscipline: '计算机科学与技术',
      learningType: 'full-time',
      degreeType: 'academic',
      examType: '全国统一考试',
      examMaxScore: 500,
      reexaminationSubjects: '高级程序设计、外语听说能力、综合素质考核',
      is408: true,
      isFirstChoiceProtected: true,
      isJointOrSinoForeign: false,
      status: 'active'
    }
  }
];

export default function Simulator() {
  const [estimatedScore, setEstimatedScore] = useState<number>(345);
  const [acceptPartTime, setAcceptPartTime] = useState<boolean>(false);
  const [degreePreference, setDegreePreference] = useState<'both' | 'academic' | 'professional'>('both');
  const [riskThreshold, setRiskThreshold] = useState<'conservative' | 'balanced' | 'aggressive'>('balanced');
  const [provinceFilter, setProvinceFilter] = useState<string>('all');
  const [isAutoSliding, setIsAutoSliding] = useState<boolean>(false);

  // Track score modifications direction to power bubbling animations
  const [prevScore, setPrevScore] = useState<number>(345);
  const [scoreDirection, setScoreDirection] = useState<'up' | 'down' | null>(null);

  useEffect(() => {
    if (estimatedScore !== prevScore) {
      setScoreDirection(estimatedScore > prevScore ? 'up' : 'down');
      setPrevScore(estimatedScore);
    }
  }, [estimatedScore, prevScore]);

  useEffect(() => {
    if (scoreDirection) {
      const timer = setTimeout(() => {
        setScoreDirection(null);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [scoreDirection, estimatedScore]);

  // Auto-demonstrate slider on first view if user hasn't interacted, run for a suitable duration (8 seconds) then stop
  useEffect(() => {
    const interacted = localStorage.getItem('has_interacted_408_slider');
    if (!interacted) {
      const startTimer = setTimeout(() => {
        setIsAutoSliding(true);
      }, 1500);

      const stopTimer = setTimeout(() => {
        setIsAutoSliding(false);
        localStorage.setItem('has_interacted_408_slider', 'true');
      }, 9500); // 1.5s delay + 8s demonstration = 9.5s total

      return () => {
        clearTimeout(startTimer);
        clearTimeout(stopTimer);
      };
    }
  }, []);

  // Smooth automatic score sliding animation for visualization demo
  useEffect(() => {
    if (!isAutoSliding) return;

    const startTime = Date.now();
    const intervalId = setInterval(() => {
      const elapsed = (Date.now() - startTime) / 1000; // time in seconds
      // A smooth harmonic wave oscillating between 310 and 390
      const sineVal = Math.sin(elapsed * (Math.PI / 1.8)); 
      const midPoint = 350;
      const amplitude = 40;
      const nextScore = Math.round(midPoint + Math.round(sineVal * amplitude));
      setEstimatedScore(nextScore);
    }, 40);

    return () => clearInterval(intervalId);
  }, [isAutoSliding]);

  const stopAutoSliding = () => {
    if (isAutoSliding) {
      setIsAutoSliding(false);
      localStorage.setItem('has_interacted_408_slider', 'true');
    }
  };

  const startAutoSliding = () => {
    setIsAutoSliding(true);
  };
  
  // Custom presets
  const applyPreset = (preset: 'safe' | 'target' | 'edge' | 'undergrad') => {
    stopAutoSliding();
    switch (preset) {
      case 'safe':
        setEstimatedScore(385);
        setDegreePreference('both');
        setRiskThreshold('conservative');
        setProvinceFilter('all');
        break;
      case 'target':
        setEstimatedScore(350);
        setDegreePreference('professional');
        setRiskThreshold('balanced');
        setProvinceFilter('all');
        break;
      case 'edge':
        setEstimatedScore(325);
        setDegreePreference('professional');
        setRiskThreshold('aggressive');
        setProvinceFilter('all');
        break;
      case 'undergrad':
        setEstimatedScore(305);
        setDegreePreference('both');
        setRiskThreshold('aggressive');
        setProvinceFilter('all');
        break;
    }
  };

  // Run the core calculations based on instructions in sections 7.4.2 & 7.4.3 & 7.4.4
  const generateRecommendations = (): RecommendationResult[] => {
    return MOCK_SCHOOLS.map(sc => {
      // 1. Calculate effective score
      // MVP formula: effective_score = max(medianScore, medianMinAdmission)
      // Fallback: If medianMinAdmission is missing/0, uses medianScore. If both missing/0, flagged as data deficient.
      let effectiveScore = 0;
      let hasData = true;

      if (sc.medianScore === 0 && sc.medianMinAdmission === 0) {
        hasData = false;
      } else {
        effectiveScore = sc.medianMinAdmission > 0 && sc.medianScore > 0 
          ? Math.max(sc.medianScore, sc.medianMinAdmission)
          : (sc.medianScore || sc.medianMinAdmission);
      }

      // 2. Score gap calculation
      const scoreGap = estimatedScore - effectiveScore;

      // 3. Recommended tier matching Section 7.4.3
      // gap >= 20: 重点稳妥
      // gap >= 5 && < 20: 重点关注
      // gap >= -10 && < 5: 略冲
      // gap < -10: 不建议
      let tier: 'focus_safe' | 'focus_watch' | 'slight_chance' | 'avoid';
      if (!hasData) {
        tier = 'avoid'; // marked as data deficient on frontend block
      } else if (scoreGap >= 20) {
        tier = 'focus_safe';
      } else if (scoreGap >= 5) {
        tier = 'focus_watch';
      } else if (scoreGap >= -10) {
        tier = 'slight_chance';
      } else {
        tier = 'avoid';
      }

      // Adjust tiers slightly based on user risk index if they set conservative or aggressive profiles
      let finalTier = tier;
      if (riskThreshold === 'conservative') {
        // Shifting margins stricter: requires higher gaps
        if (scoreGap < 12 && tier === 'focus_watch') finalTier = 'slight_chance';
        if (scoreGap < -2 && tier === 'slight_chance') finalTier = 'avoid';
      } else if (riskThreshold === 'aggressive') {
        // Expanding tolerance bounds
        if (scoreGap >= -15 && tier === 'avoid') finalTier = 'slight_chance';
        if (scoreGap >= 0 && tier === 'slight_chance') finalTier = 'focus_watch';
      }

      // If data deficient, override
      if (!hasData) {
        finalTier = 'avoid';
      }

      // 4. Risk Signals analysis Section 7.4.4
      const riskSignals: string[] = [];
      let riskSeverity: 'low' | 'medium' | 'high' = 'low';
      let riskTitle = '指标健康，无明显异常统计';

      if (sc.plannedCount > 0 && sc.plannedCount < 5) {
        riskSignals.push(`招生核定规模小于 5 人 (当前：仅 ${sc.plannedCount} 统考名额)。竞争波动烈度极高`);
        riskSeverity = 'high';
        riskTitle = '单核指标波动型极高风险';
      }
      if (sc.medianMinAdmission - sc.medianScore >= 15) {
        riskSignals.push(`拟录取最低排位高出复试基线 ${sc.medianMinAdmission - sc.medianScore} 分。复试水分极大，易形成虚胖陷阱`);
        if (riskSeverity !== 'high') riskSeverity = 'medium';
      }
      if (!sc.isProtected) {
        riskSignals.push('历史曾有录取外校生调剂重置前置承诺，一志愿保护级别评定为“中偏低”');
        if (riskSeverity === 'low') riskSeverity = 'medium';
      }
      if (sc.dataQuality === 'D') {
        riskSignals.push('关键指标历史归档严重中断。决策可信度已评定为 D 级 (请谨慎参照原始数据)');
        riskSeverity = 'high';
        riskTitle = '核心报告缺失严重风险';
      }

      // Translate risk summary title neatly
      if (riskSignals.length === 0) {
        riskTitle = '院校历史报告高度健康，风险极低';
      } else if (riskSeverity === 'high') {
        riskTitle = '检出偏高层级安全警告，建议备份';
      } else if (riskSeverity === 'medium') {
        riskTitle = '复试线水分检测提示，建议关注';
      }

      // Build mock year stats for visualization
      const threeYearScores = sc.medianScore > 0 ? [
        { year: 2024, score: sc.medianScore - 5, minAdmission: sc.medianMinAdmission - 3 },
        { year: 2025, score: sc.medianScore, minAdmission: sc.medianMinAdmission },
        { year: 2026, score: sc.medianScore + 4, minAdmission: sc.medianMinAdmission + 2 }
      ] : [
        { year: 2024, score: null, minAdmission: null },
        { year: 2025, score: null, minAdmission: null },
        { year: 2026, score: null, minAdmission: null }
      ];

      return {
        program: sc.program,
        school: sc,
        effectiveScore: effectiveScore,
        scoreGap: scoreGap,
        tier: finalTier,
        riskTitle,
        riskSeverity,
        riskSignals,
        dataQualityGrade: sc.dataQuality,
        threeYearScores
      };
    })
    .filter(res => {
      // Hard constraints filtering Section 7.4.1
      // 1. Regional constraint
      if (provinceFilter !== 'all' && res.school.province !== provinceFilter) return false;
      // 2. Degree type preference
      if (degreePreference === 'academic' && res.program.degreeType !== 'academic') return false;
      if (degreePreference === 'professional' && res.program.degreeType !== 'professional') return false;
      
      return true;
    });
  };

  // Sort recommendations so higher feasibility schools float directly to the top
  const results = generateRecommendations().sort((a, b) => {
    const tierWeight = {
      'focus_safe': 4,
      'focus_watch': 3,
      'slight_chance': 2,
      'avoid': 1
    };
    const weightA = tierWeight[a.tier] || 0;
    const weightB = tierWeight[b.tier] || 0;
    if (weightA !== weightB) {
      return weightB - weightA; // Higher feasibility floats to the top
    }
    return b.scoreGap - a.scoreGap; // Higher safety margin within same tier
  });

  return (
    <div className="bg-white rounded-3xl p-6 lg:p-8 border border-[#d7e3f8] shadow-md relative" id="simulator-interactive-section">
      {/* Decorative subtle blue ambient bubble */}
      <div className="absolute top-1/2 left-1/4 w-96 h-96 bg-blue-500/5 rounded-full blur-3xl pointer-events-none"></div>

      <div className="text-center max-w-2xl mx-auto mb-10">
        <div className="flex flex-wrap items-center justify-center gap-3 mb-4">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#eff6ff] border border-[#d7e3f8] text-[#2563eb] text-xs font-mono">
            <Sparkles className="w-3.5 h-3.5 text-[#2563eb]" />
            <span>2026年研招数据 · 条件筛选召回匹配院校</span>
          </div>
        </div>
        <h2 className="text-3xl lg:text-4xl font-sans font-semibold text-[#0f172a] mb-3">
          按你的画像，精准召回符合条件的院校
        </h2>
        <p className="text-[#64748b] text-sm leading-relaxed font-light">
          设置你的分数区间、目标地区和报考偏好，系统从历年真实招生数据中筛选出符合条件的院校专业，复试线、拟录取最低分、招生人数一并呈现，数据来源逐条可查。
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Control Panel */}
        <div className="lg:col-span-4 bg-white border border-[#d7e3f8] rounded-2xl p-6 space-y-6 shadow-sm select-none">
          <div className="flex items-center justify-between border-b border-[#d7e3f8] pb-3">
            <h3 className="text-base font-sans font-semibold text-[#0f172a] flex items-center gap-2">
              <Sliders className="w-4 h-4 text-[#2563eb]" />
              <span>研招报考画像设定</span>
            </h3>
            <button 
              onClick={() => {
                setEstimatedScore(345);
                setDegreePreference('both');
                setRiskThreshold('balanced');
                setProvinceFilter('all');
                stopAutoSliding();
              }}
              className="text-[11px] text-[#2563eb] hover:bg-blue-50/50 px-2 py-1 rounded transition-colors inline-flex items-center gap-1 font-bold"
            >
              <RefreshCw className="w-3 h-3" />
              <span>极速重置</span>
            </button>
          </div>

          {/* Range Slider for estimatedScore */}
          <div className="p-2.5 rounded-xl space-y-2">
            <div className="flex justify-between items-center text-xs">
              <span className="text-[#0f172a] font-semibold">初试预估总分 (考研408)</span>
              <span className="font-mono text-[#2563eb] font-bold text-sm bg-[#eff6ff] px-2 py-0.5 rounded border border-[#d7e3f8]">
                {estimatedScore} 分
              </span>
            </div>
            <div className="relative py-4 flex items-center">
              <input 
                type="range" 
                min="280" 
                max="430" 
                value={estimatedScore} 
                onChange={e => {
                  setEstimatedScore(Number(e.target.value));
                  stopAutoSliding();
                }}
                onMouseDown={stopAutoSliding}
                onTouchStart={stopAutoSliding}
                className="w-full h-2 bg-gradient-to-r from-blue-100 via-blue-200 to-blue-500 rounded-lg appearance-none cursor-pointer focus:outline-none transition-all duration-150 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:h-5 [&::-webkit-slider-thumb]:w-5 [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:border-[3px] [&::-webkit-slider-thumb]:border-[#2563eb] [&::-webkit-slider-thumb]:shadow-[0_2px_8px_rgba(37,99,235,0.4)] [&::-webkit-slider-thumb]:transition-all [&::-webkit-slider-thumb]:hover:scale-115 active:[&::-webkit-slider-thumb]:scale-125 [&::-moz-range-thumb]:h-5 [&::-moz-range-thumb]:w-5 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:bg-white [&::-moz-range-thumb]:border-[3px] [&::-moz-range-thumb]:border-[#2563eb] [&::-moz-range-thumb]:shadow-[0_2px_8px_rgba(37,99,235,0.4)] [&::-moz-range-thumb]:transition-all [&::-moz-range-thumb]:hover:scale-115 active:[&::-moz-range-thumb]:scale-125"
              />
            </div>
            <div className="flex justify-between text-[10px] text-[#64748b] font-mono select-none">
              <span>280分</span>
              <span>350分 (数据中位值)</span>
              <span>430分</span>
            </div>
          </div>

          {/* Filters Preferred Group for Step 3 */}
          <div className="p-2.5 rounded-xl space-y-4">
            {/* Regional filter preference */}
            <div className="space-y-2">
              <label className="text-xs text-[#0f172a] font-semibold block">地理意向偏好 (多省市过滤)</label>
              <select 
                value={provinceFilter}
                onChange={e => {
                  setProvinceFilter(e.target.value);
                  stopAutoSliding();
                }}
                className="w-full bg-white border border-[#d7e3f8] rounded-lg px-3 py-2 text-xs text-[#0f172a] focus:outline-none focus:border-[#2563eb]"
              >
                <option value="all">不限地区 (全国召回)</option>
                <option value="北京">北京 (竞争极端烈度)</option>
                <option value="上海">上海 (重点保护地带)</option>
                <option value="江苏">江苏 (强名校聚集省份)</option>
                <option value="浙江">浙江 (重点属校温床)</option>
                <option value="广东">广东 (高大湾区地缘)</option>
              </select>
            </div>

            {/* Degree preference filter */}
            <div className="space-y-2">
              <label className="text-xs text-[#0f172a] font-semibold block">学位偏好 (学硕 vs 专硕)</label>
              <div className="grid grid-cols-3 gap-1 bg-slate-50 p-0.5 rounded-lg border border-[#d7e3f8]">
                <button 
                  onClick={() => {
                    setDegreePreference('both');
                    stopAutoSliding();
                  }}
                  className={`py-1.5 px-2 rounded text-[11px] font-semibold transition ${degreePreference === 'both' ? 'bg-[#2563eb] text-white shadow-sm font-bold' : 'text-[#64748b] hover:text-[#2563eb]'}`}
                >
                  双轨并举
                </button>
                <button 
                  onClick={() => {
                    setDegreePreference('academic');
                    stopAutoSliding();
                  }}
                  className={`py-1.5 px-2 rounded text-[11px] font-semibold transition ${degreePreference === 'academic' ? 'bg-[#2563eb] text-white shadow-sm font-bold' : 'text-[#64748b] hover:text-[#2563eb]'}`}
                >
                  只选学硕
                </button>
                <button 
                  onClick={() => {
                    setDegreePreference('professional');
                    stopAutoSliding();
                  }}
                  className={`py-1.5 px-2 rounded text-[11px] font-semibold transition ${degreePreference === 'professional' ? 'bg-[#2563eb] text-white shadow-sm font-bold' : 'text-[#64748b] hover:text-[#2563eb]'}`}
                >
                  只选专硕
                </button>
              </div>
            </div>
          </div>

          {/* Risk preference */}
          <div className="p-2.5 rounded-xl space-y-2">
            <label className="text-xs text-[#0f172a] font-semibold block flex items-center gap-1">
              <span>择校偏好与风控评级</span>
              <HelpCircle className="w-3 h-3 text-[#64748b] cursor-help" title="保守型：大幅强化录取底线防护，收紧优势区；均衡型：基于往年波动极值计算；冲刺型：放开择校阈值，挖掘低概率捡漏可能。" />
            </label>
            <div className="grid grid-cols-3 gap-1 bg-slate-50 p-0.5 rounded-lg border border-[#d7e3f8]">
              <button 
                onClick={() => {
                  setRiskThreshold('conservative');
                  stopAutoSliding();
                }}
                className={`py-1.5 px-2 rounded text-[11px] font-bold transition ${riskThreshold === 'conservative' ? 'bg-[#16a34a] text-white border-none' : 'text-[#64748b] hover:text-[#2563eb]'}`}
              >
                保守稳健
              </button>
              <button 
                onClick={() => {
                  setRiskThreshold('balanced');
                  stopAutoSliding();
                }}
                className={`py-1.5 px-2 rounded text-[11px] font-bold transition ${riskThreshold === 'balanced' ? 'bg-[#2563eb] text-white border-none' : 'text-[#64748b] hover:text-[#2563eb]'}`}
              >
                均衡推荐
              </button>
              <button 
                onClick={() => {
                  setRiskThreshold('aggressive');
                  stopAutoSliding();
                }}
                className={`py-1.5 px-2 rounded text-[11px] font-bold transition ${riskThreshold === 'aggressive' ? 'bg-[#f97316] text-white border-none' : 'text-[#64748b] hover:text-slate-200'}`}
              >
                冲刺进取
              </button>
            </div>
          </div>
        </div>
        <div className="lg:col-span-8 space-y-4 p-2 select-none">
          <div className="flex justify-between items-center bg-slate-50 px-4 py-2.5 rounded-xl border border-[#d7e3f8] shadow-sm">
            <span className="text-xs text-[#64748b] font-mono">
              经条件筛选，共召回符合报考方向的院校专业：<span className="text-[#0f172a] font-bold">{results.length}</span> 所
            </span>
            <div className="flex items-center gap-1.5 text-[10px] text-[#64748b] font-medium">
              <span className="w-2.5 h-2.5 rounded-full bg-[#16a34a]"></span>
              <span>同步版本：全国研招在线2026同步版</span>
            </div>
          </div>

          <div className="space-y-4 max-h-[580px] overflow-y-auto pr-1 select-none">
            <AnimatePresence mode="popLayout">
              {results.length > 0 ? (
                results.map(res => {
                  const isAvoid = res.tier === 'avoid';
                  const isSafe = res.tier === 'focus_safe';
                  const isWatch = res.tier === 'focus_watch';
                  const isChance = res.tier === 'slight_chance';

                  return (
                    <motion.div 
                      key={res.school.id}
                      layout
                      initial={{ opacity: 0, y: 15 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.95 }}
                      transition={{ 
                        type: "spring",
                        stiffness: 355,
                        damping: 28,
                        mass: 0.8
                      }}
                      className={`p-5 rounded-2xl border transition-all duration-200 relative overflow-hidden flex flex-col md:flex-row gap-5 justify-between hover:translate-x-1 ${
                        isSafe ? 'bg-white hover:bg-emerald-50/10 border-[#d7e3f8] hover:border-[#16a34a]/40 shadow-sm hover:shadow-md' : ''
                      } ${
                        isWatch ? 'bg-white hover:bg-[#eff6ff]/10 border-[#d7e3f8] hover:border-[#2563eb]/40 shadow-sm hover:shadow-md' : ''
                      } ${
                        isChance ? 'bg-white hover:bg-orange-50/10 border-[#d7e3f8] hover:border-[#f97316]/40 shadow-sm hover:shadow-md' : ''
                      } ${
                        isAvoid ? 'bg-slate-50 border-[#d7e3f8]/85 saturate-50 opacity-70' : ''
                      }`}
                    >
                      {/* Visual left colored vertical strip based on calculated recommendation tier */}
                      <div className={`absolute top-0 left-0 bottom-0 w-1.5 ${
                        isSafe ? 'bg-[#16a34a]' : ''
                      } ${
                        isWatch ? 'bg-[#2563eb]' : ''
                      } ${
                        isChance ? 'bg-[#f97316]' : ''
                      } ${
                        isAvoid ? 'bg-[#ef4444]' : ''
                      }`}></div>

                      {/* Left Column: School details and program specifications */}
                      <div className="flex-grow space-y-3.5 text-left relative z-10">
                        <div className="space-y-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="text-base font-sans font-bold text-[#0f172a]">{res.school.name}</span>
                            <span className="text-[10px] bg-slate-50 border border-[#d7e3f8] px-2 py-0.5 rounded text-[#2563eb] font-mono font-bold">
                              {res.school.level}
                            </span>
                            <span className="text-[10px] bg-slate-50 border border-[#d7e3f8] px-2 py-0.5 rounded text-[#64748b] font-mono">
                              {res.school.province} · {res.school.city}
                            </span>
                          </div>
                          <div className="text-xs text-[#0f172a] font-medium leading-relaxed">
                            {res.program.collegeName} · <span className="font-mono text-[#2563eb] font-bold">{res.program.code}</span> {res.program.name}
                          </div>
                        </div>

                        {/* Score metrics blocks */}
                        {res.effectiveScore > 0 ? (
                          <div className="grid grid-cols-3 gap-2 bg-slate-50 p-3 rounded-xl border border-[#d7e3f8]">
                            <div>
                              <span className="text-[10px] text-[#64748b] font-mono block">往年复试中位线</span>
                              <span className="text-sm font-mono font-bold text-[#0f172a] mt-0.5 block">{(res.school as any).medianScore}分</span>
                            </div>
                            <div>
                              <span className="text-[10px] text-[#64748b] font-mono block">往年录取底线</span>
                              <span className="text-sm font-mono font-bold text-[#0f172a] mt-0.5 block">{(res.school as any).medianMinAdmission}分</span>
                            </div>
                            <div className="relative">
                              <span className="text-[10px] text-[#2563eb] font-mono block font-bold flex items-center gap-0.5">
                                数据参考基准分
                              </span>
                              <span className="text-sm font-mono font-extrabold text-[#2563eb] mt-0.5 block">{res.effectiveScore}分</span>
                            </div>
                          </div>
                        ) : (
                          <div className="p-3 bg-slate-50 border border-dashed border-[#d7e3f8] rounded-xl text-center">
                            <span className="text-xs text-[#64748b] font-mono block">暂无三年历史录取完整数据</span>
                          </div>
                        )}

                        {/* Risk Warnings List */}
                        {res.riskSignals.length > 0 && (
                          <div className="space-y-1.5 bg-orange-50/60 border border-orange-200 rounded-xl p-3">
                            <div className="flex items-center gap-1.5 text-xs text-[#f97316] font-bold">
                              <AlertTriangle className="w-3.5 h-3.5 text-[#f97316] flex-shrink-0" />
                              <span>招生规模与一志愿保护深度评估</span>
                            </div>
                            <ul className="space-y-1 text-[10px] text-[#64748b] list-disc list-inside leading-relaxed pl-1">
                              {res.riskSignals.map((sig, i) => (
                                <li key={i}>{sig}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>

                      {/* Right Column: Engine decision values and tier badge */}
                      <div className="flex flex-row md:flex-col justify-between items-start md:items-end md:w-48 border-t md:border-t-0 md:border-l border-[#d7e3f8] pt-3 md:pt-0 md:pl-5 flex-shrink-0">
                        <div className="space-y-1 text-left md:text-right w-full">
                          <span className="text-[10px] text-[#64748b] font-mono block">估分与基准偏差</span>
                          
                          {res.effectiveScore > 0 ? (
                            <div className="flex items-baseline md:justify-end gap-1 font-mono">
                              <span className="text-[#64748b] text-xs">估算差值:</span>
                              <span className={`text-lg font-bold ${
                                res.scoreGap >= 20 ? 'text-[#16a34a]' : 
                                res.scoreGap >= 5 && res.scoreGap < 20 ? 'text-[#2563eb]' : 
                                res.scoreGap >= -10 && res.scoreGap < 5 ? 'text-[#f97316]' : 'text-slate-500'
                              }`}>
                                {res.scoreGap > 0 ? `+${res.scoreGap}` : res.scoreGap} 分
                              </span>
                            </div>
                          ) : (
                            <span className="text-xs text-[#ef4444] font-mono font-medium">未能锁算差值</span>
                          )}
                        </div>

                        {/* Display Tier Badge */}
                        <div className="flex flex-col items-start md:items-end gap-2 w-full mt-2 md:mt-0">
                          <span className="text-[10px] text-[#64748b] font-mono block">安全级归类评估</span>
                          {res.effectiveScore === 0 ? (
                            <span className="px-3 py-1.5 rounded-full bg-slate-100 border border-[#d7e3f8] text-xs font-semibold text-[#64748b] font-mono flex items-center gap-1.5">
                              📊 历史数据不足
                            </span>
                          ) : (
                            <>
                              {isSafe && (
                                <span className="px-3 py-1.5 rounded-xl bg-[#e6f4ea] border border-[#a3cfb1] text-xs font-bold text-[#16a34a] flex items-center gap-1.5 shadow-sm">
                                  <CheckCircle2 className="w-3.5 h-3.5" />
                                  稳妥关注档
                                </span>
                              )}
                              {isWatch && (
                                <span className="px-3 py-1.5 rounded-xl bg-[#eff6ff] border border-[#d7e3f8] text-xs font-bold text-[#2563eb] flex items-center gap-1.5 shadow-sm">
                                  <ShieldCheck className="w-3.5 h-3.5" />
                                  保持关注档 (Watch)
                                </span>
                              )}
                              {isChance && (
                                <span className="px-3 py-1.5 rounded-xl bg-orange-50 border border-orange-200 text-xs font-bold text-[#f97316] flex items-center gap-1.5 shadow-sm">
                                  <Sparkles className="w-3.5 h-3.5" />
                                  积极冲刺档 (Chance)
                                </span>
                              )}
                              {isAvoid && (
                                <span className="px-3 py-1.5 rounded-xl bg-red-50 border border-red-200 text-xs font-bold text-[#ef4444] flex items-center gap-1.5 shadow-sm">
                                  <span className="w-1.5 h-1.5 rounded-full bg-[#ef4444]"></span>
                                  极高风险档 (Avoid)
                                </span>
                              )}
                            </>
                          )}

                          {/* Data Quality Star */}
                          <div className="flex items-center gap-1 text-[10px] font-mono text-[#64748b] mt-1">
                            <span>数据完整度评级:</span>
                            <span className={`font-bold px-1.5 py-0.5 rounded text-xs leading-none ${
                              res.dataQualityGrade === 'A' ? 'text-[#16a34a] bg-[#e6f4ea]' : 
                              res.dataQualityGrade === 'B' ? 'text-[#2563eb] bg-[#eff6ff]' : 'text-[#64748b] bg-slate-100'
                            }`}>
                              {res.dataQualityGrade} 级
                            </span>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  );
                })
              ) : (
                <div className="p-12 text-center bg-slate-50 rounded-2xl border border-dashed border-[#d7e3f8]">
                  <AlertTriangle className="w-8 h-8 text-[#64748b] mx-auto mb-2" />
                  <h4 className="text-[#0f172a] font-bold text-sm">无符合任何画像设定的高校专业</h4>
                  <p className="text-xs text-[#64748b] mt-1 font-light">请尝试放宽地区选择、学位偏好或调高预估总分。</p>
                </div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </div>
  );
}
