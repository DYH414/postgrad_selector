/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export interface School {
  id: number;
  name: string;
  shortName: string;
  province: string;
  city: string;
  level: string; // e.g., 985, 211, Dual-First-Class
  is985: boolean;
  is211: boolean;
  isDoubleFirstClass: boolean;
  isPublic: boolean;
  website: string;
  status: 'active' | 'inactive';
}

export interface College {
  id: number;
  schoolId: number;
  schoolName: string;
  name: string;
  website: string;
  infoPage: string;
}

export interface Program {
  id: number;
  collegeId: number;
  collegeName: string;
  schoolName: string;
  code: string; // e.g., 085400
  name: string; // e.g., 电子信息
  direction: string; // e.g., (01)计算机技术
  disciplineCategory: string; // e.g., 工学
  firstClassDiscipline: string; // e.g., 计算机科学与技术
  learningType: 'full-time' | 'part-time';
  degreeType: 'academic' | 'professional';
  examType: string; // e.g., 统考
  examMaxScore: number; // e.g., 500
  reexaminationSubjects: string;
  is408: boolean;
  isFirstChoiceProtected: boolean;
  isJointOrSinoForeign: boolean;
  status: 'active' | 'inactive';
}

export interface Subject {
  code: string; // e.g., 101, 201, 301, 408
  name: string; // e.g., 思想政治理论, 英语一, 数学一, 计算机学科专业基础
  type: 'public' | 'professional';
  maxScore: number;
}

export interface AdmissionScore {
  id: number;
  programId: number;
  year: number;
  totalScore: number;
  politicsScore: number;
  englishScore: number;
  mathScore: number;
  professionalScore: number;
  sourceId: number;
}

export interface AdmissionPlan {
  id: number;
  programId: number;
  year: number;
  plannedCount: number;
  unificationCount: number; // 统招名额
  recommendationCount: number; // 推免人数
  reexaminationCount: number; // 复试人数
  sourceId: number;
}

export interface AdmissionResult {
  id: number;
  programId: number;
  year: number;
  enrolledCount: number;
  minScore: number;
  avgScore: number;
  maxScore: number;
  firstChoiceEnrolledCount: number;
  adjustmentEnrolledCount: number; // 调剂录取数
  reexamAdmissionRatio: number; // 复录比
  sourceId: number;
}

export interface DataSource {
  id: number;
  type: 'official_document' | 'website_page' | 'third_party' | 'unverified';
  title: string;
  url: string;
  publishDate: string;
  fileHash: string;
  credibility: 'high' | 'medium' | 'pending_verification' | 'low';
  notes: string;
}

export interface DataQuality {
  id: number;
  programId: number;
  year: number;
  hasScore: boolean;
  hasPlan: boolean;
  hasResult: boolean;
  hasOfficialSource: boolean;
  grade: 'A' | 'B' | 'C' | 'D' | 'E'; // Complete, Minor gaps, Heavy gaps, Unusable, Null
}

export interface UserProfile {
  estimatedScore: number;
  preferredProvinces: string[];
  riskPreference: 'conservative' | 'balanced' | 'aggressive';
  acceptPartTime: boolean;
  acceptAdjustment: boolean;
  acceptAcademic: boolean;
  acceptJointOrSinoForeign: boolean;
  targetProgramCodes: string[];
  undergradLevel?: string;
  crossMajor: boolean;
}

// Structured recommendation output shape
export interface RecommendationResult {
  program: Program;
  school: School;
  effectiveScore: number;
  scoreGap: number;
  tier: 'focus_safe' | 'focus_watch' | 'slight_chance' | 'avoid';
  riskTitle: string;
  riskSeverity: 'low' | 'medium' | 'high';
  riskSignals: string[];
  dataQualityGrade: 'A' | 'B' | 'C' | 'D' | 'E';
  threeYearScores: { year: number; score: number | null; minAdmission: number | null }[];
}
