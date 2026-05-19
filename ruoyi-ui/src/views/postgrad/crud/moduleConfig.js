const commonOptions = {
  status: [
    { label: '正常', value: 'active' },
    { label: '停用', value: 'inactive' },
    { label: '暂停', value: 'suspended' },
    { label: '待确认', value: 'pending' }
  ],
  subjectType: [
    { label: '公共课', value: 'public' },
    { label: '专业课', value: 'professional' }
  ],
  studyMode: [
    { label: '全日制', value: 'full_time' },
    { label: '非全日制', value: 'part_time' }
  ],
  degreeType: [
    { label: '学硕', value: 'academic' },
    { label: '专硕', value: 'professional' }
  ],
  examType: [
    { label: '统考500', value: 'GENERAL_500' },
    { label: '管理类300', value: 'MANAGEMENT_300' },
    { label: '法硕500', value: 'LAW_MASTER_500' },
    { label: '医学500', value: 'MEDICAL_500' },
    { label: '艺术500', value: 'ART_500' },
    { label: '其他', value: 'OTHER' }
  ],
  sourceType: [
    { label: '官网', value: 'OFFICIAL' },
    { label: '第三方', value: 'THIRD_PARTY' },
    { label: '手工', value: 'MANUAL' },
    { label: 'OCR', value: 'OCR' }
  ],
  verifyStatus: [
    { label: '官网已核验', value: 'OFFICIAL_VERIFIED' },
    { label: '人工已核验', value: 'MANUAL_VERIFIED' },
    { label: '第三方', value: 'THIRD_PARTY' },
    { label: 'OCR待核验', value: 'OCR_PENDING' },
    { label: '冲突', value: 'CONFLICT' },
    { label: '缺失', value: 'MISSING' }
  ],
  risk: [
    { label: '低', value: 'low' },
    { label: '中', value: 'medium' },
    { label: '高', value: 'high' },
    { label: '未知', value: 'unknown' }
  ],
  completenessLevel: [
    { label: 'A', value: 'A' },
    { label: 'B', value: 'B' },
    { label: 'C', value: 'C' },
    { label: 'D', value: 'D' },
    { label: 'E', value: 'E' }
  ],
  taskType: [
    { label: '复试线', value: 'SCORE' },
    { label: '招生计划', value: 'PLAN' },
    { label: '录取结果', value: 'RESULT' },
    { label: '复试信息', value: 'RETEST' },
    { label: '来源', value: 'SOURCE' }
  ],
  taskStatus: [
    { label: '待处理', value: 'PENDING' },
    { label: '运行中', value: 'RUNNING' },
    { label: '待审核', value: 'STAGING' },
    { label: '已审核', value: 'REVIEWED' },
    { label: '失败', value: 'FAILED' },
    { label: '取消', value: 'CANCELLED' }
  ],
  createdBy: [
    { label: '系统', value: 'SYSTEM' },
    { label: '管理员', value: 'ADMIN' },
    { label: '用户', value: 'USER' }
  ],
  stagingSourceType: [
    { label: '研招网', value: 'YZ_CHSI' },
    { label: '官网HTML', value: 'OFFICIAL_HTML' },
    { label: '官网PDF', value: 'OFFICIAL_PDF' },
    { label: '图片', value: 'IMAGE' },
    { label: '第三方', value: 'THIRD_PARTY' },
    { label: '手工', value: 'MANUAL' },
    { label: '其他', value: 'OTHER' }
  ],
  confidence: [
    { label: '高', value: 'high' },
    { label: '中', value: 'medium' },
    { label: '低', value: 'low' },
    { label: '未知', value: 'unknown' }
  ],
  stagingStatus: [
    { label: '种子', value: 'seed' },
    { label: '待审核', value: 'pending' },
    { label: '已通过', value: 'approved' },
    { label: '已拒绝', value: 'rejected' },
    { label: '已跳过', value: 'skipped' }
  ]
}

const boolColumn = (prop, label) => ({ prop, label, type: 'switch', width: 90, default: 0 })
const numberColumn = (prop, label, required = false) => ({ prop, label, type: 'number', width: 110, required })
const textColumn = (prop, label, required = false, minWidth = 140) => ({ prop, label, type: 'text', required, minWidth, showOverflow: true })
const textareaColumn = (prop, label, required = false) => ({ prop, label, type: 'textarea', required, minWidth: 180, showOverflow: true })
const selectColumn = (prop, label, optionsKey, required = false, width = 130) => ({ prop, label, type: 'select', optionsKey, required, width })
const remoteColumn = (prop, label, optionModule, required = false, minWidth = 160) => ({ prop, label, type: 'remoteSelect', optionModule, required, minWidth, showOverflow: true })
const dateColumn = (prop, label) => ({ prop, label, type: 'date', width: 130 })
const readonlyColumn = (prop, label, minWidth = 180) => ({ prop, label, type: 'readonly', readonly: true, minWidth, showOverflow: true })

const admissionBase = [
  remoteColumn('programId', '专业方向', 'program', true, 220),
  readonlyColumn('programLabel', '专业方向', 260),
  numberColumn('year', '年份', true)
]

const admissionTail = [
  selectColumn('verifyStatus', '可信状态', 'verifyStatus', true, 130),
  remoteColumn('sourceId', '来源', 'dataSource'),
  readonlyColumn('sourceLabel', '来源标题', 180)
]

export const modules = {
  college: {
    title: '学院管理',
    idField: 'id',
    query: ['schoolId', 'name'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      remoteColumn('schoolId', '所属学校', 'school', true, 180),
      readonlyColumn('schoolName', '学校名称', 160),
      textColumn('name', '学院名称', true, 180),
      textColumn('website', '学院官网', false, 180),
      textColumn('graduateUrl', '研招信息页', false, 200)
    ]
  },
  subject: {
    title: '初试科目管理',
    idField: 'id',
    query: ['code', 'name', 'subjectType'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      textColumn('code', '科目代码', true, 120),
      textColumn('name', '科目名称', true, 180),
      selectColumn('subjectType', '科目类型', 'subjectType', true),
      textColumn('examCategory', '考试类别', false, 130)
    ]
  },
  program: {
    title: '专业方向管理',
    idField: 'id',
    query: ['collegeId', 'programCode', 'programName', 'is408', 'status'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      remoteColumn('collegeId', '所属学院', 'college', true, 220),
      readonlyColumn('schoolName', '学校', 140),
      readonlyColumn('collegeName', '学院', 140),
      textColumn('programCode', '专业代码', true, 120),
      textColumn('programName', '专业名称', true, 180),
      textColumn('researchDirection', '研究方向', false, 200),
      textColumn('disciplineCategory', '学科门类', false, 120),
      textColumn('firstDiscipline', '一级学科', false, 120),
      selectColumn('studyMode', '学习方式', 'studyMode', true),
      selectColumn('degreeType', '学位类型', 'degreeType', true),
      selectColumn('examType', '考试类型', 'examType', true),
      numberColumn('scoreScale', '满分', true),
      textColumn('retestSubjects', '复试科目', false, 200),
      boolColumn('is408', '408'),
      boolColumn('protectsFirstChoice', '保护一志愿'),
      boolColumn('isJointProgram', '联培'),
      selectColumn('status', '状态', 'status', true)
    ]
  },
  programSubject: {
    title: '专业科目管理',
    idField: '__key',
    query: ['programId', 'subjectId'],
    columns: [
      remoteColumn('programId', '专业方向', 'program', true, 240),
      readonlyColumn('programLabel', '专业方向', 260),
      remoteColumn('subjectId', '考试科目', 'subject', true, 160),
      readonlyColumn('subjectLabel', '考试科目', 160),
      numberColumn('subjectOrder', '科目顺序', true)
    ]
  },
  dataSource: {
    title: '数据来源管理',
    idField: 'id',
    query: ['sourceType', 'title', 'sourceOwner'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      selectColumn('sourceType', '来源类型', 'sourceType', true),
      textareaColumn('url', '来源URL'),
      textColumn('title', '来源标题', false, 220),
      textColumn('sourceOwner', '来源主体'),
      dateColumn('publishDate', '发布日期'),
      textColumn('localFilePath', '本地文件', false, 180),
      textColumn('fileHash', '文件Hash', false, 180),
      textColumn('pageHash', '页面Hash', false, 180),
      textColumn('fetchedAt', '抓取时间', false, 160),
      boolColumn('robotsChecked', '查robots'),
      boolColumn('robotsAllowed', '允许'),
      boolColumn('termsChecked', '查条款'),
      selectColumn('commercialUseRisk', '商用风险', 'risk'),
      selectColumn('copyrightRisk', '版权风险', 'risk')
    ]
  },
  admissionScore: {
    title: '历年复试线',
    idField: 'id',
    query: ['programId', 'year', 'verifyStatus'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      ...admissionBase,
      numberColumn('scoreLine', '复试线'),
      numberColumn('singleMath', '数学线'),
      numberColumn('singleEnglish', '英语线'),
      numberColumn('singlePolitics', '政治线'),
      numberColumn('singleProfessional', '专业课线'),
      ...admissionTail
    ]
  },
  admissionPlan: {
    title: '历年招生计划',
    idField: 'id',
    query: ['programId', 'year', 'verifyStatus'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      ...admissionBase,
      numberColumn('totalPlan', '总计划'),
      numberColumn('recommendedExemptionPlan', '推免计划'),
      numberColumn('unifiedExamQuota', '统考名额'),
      numberColumn('retestCount', '复试人数'),
      ...admissionTail
    ]
  },
  admissionResult: {
    title: '历年拟录取',
    idField: 'id',
    query: ['programId', 'year', 'verifyStatus'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      ...admissionBase,
      numberColumn('admittedCount', '录取人数'),
      numberColumn('firstChoiceAdmittedCount', '一志愿录取'),
      numberColumn('minAdmittedScore', '最低分'),
      numberColumn('avgAdmittedScore', '平均分'),
      numberColumn('maxAdmittedScore', '最高分'),
      boolColumn('hasTransfer', '有调剂'),
      numberColumn('transferCount', '调剂人数'),
      ...admissionTail
    ]
  },
  programYearDataQuality: {
    title: '数据完整度',
    idField: 'id',
    query: ['programId', 'year', 'completenessLevel'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      remoteColumn('programId', '专业方向', 'program', true, 220),
      readonlyColumn('programLabel', '专业方向', 260),
      numberColumn('year', '年份', true),
      boolColumn('hasScore', '复试线'),
      boolColumn('hasPlan', '招生计划'),
      boolColumn('hasResult', '录取结果'),
      boolColumn('hasOfficialSource', '官网来源'),
      boolColumn('hasConflict', '冲突'),
      selectColumn('completenessLevel', '完整度', 'completenessLevel', true),
      textareaColumn('missingFields', '缺失字段JSON'),
      textColumn('lastCheckedAt', '检查时间', false, 160)
    ]
  },
  dataCollectionTask: {
    title: '采集任务管理',
    idField: 'id',
    query: ['programId', 'targetYear', 'taskType', 'status'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      remoteColumn('programId', '专业方向', 'program', true, 220),
      readonlyColumn('programLabel', '专业方向', 260),
      selectColumn('taskType', '任务类型', 'taskType', true),
      numberColumn('targetYear', '目标年份', true),
      numberColumn('priority', '优先级'),
      selectColumn('status', '状态', 'taskStatus', true),
      textareaColumn('sourceHintUrl', '来源提示URL'),
      textareaColumn('failureReason', '失败原因'),
      selectColumn('createdBy', '创建来源', 'createdBy'),
      numberColumn('assignedTo', '处理人'),
      textColumn('startedAt', '开始时间', false, 160),
      textColumn('finishedAt', '完成时间', false, 160)
    ]
  },
  staging: {
    title: '抽取暂存审核',
    idField: 'id',
    query: ['schoolName', 'programCode', 'year', 'status', 'confidence'],
    columns: [
      { prop: 'id', label: 'ID', type: 'readonly', width: 80, readonly: true },
      numberColumn('taskId', '任务ID'),
      remoteColumn('sourceId', '数据来源', 'dataSource'),
      remoteColumn('matchedProgramId', '匹配专业', 'program'),
      readonlyColumn('matchedProgramLabel', '匹配专业方向', 260),
      selectColumn('sourceType', '采集来源', 'stagingSourceType', true),
      textColumn('schoolName', '学校名称', true, 160),
      textColumn('collegeName', '学院名称', false, 160),
      textColumn('city', '城市', false, 100),
      textColumn('programCode', '专业代码', false, 120),
      textColumn('programName', '专业名称', false, 160),
      textColumn('examSubjects', '初试科目', false, 180),
      numberColumn('year', '年份'),
      numberColumn('scoreLine', '复试线'),
      numberColumn('singleMath', '数学线'),
      numberColumn('singleEnglish', '英语线'),
      numberColumn('singlePolitics', '政治线'),
      numberColumn('singleProfessional', '专业课线'),
      numberColumn('minAdmitted', '最低录取'),
      numberColumn('avgAdmitted', '平均录取'),
      numberColumn('planCount', '计划数'),
      numberColumn('retestCount', '复试数'),
      numberColumn('admittedCount', '录取数'),
      selectColumn('confidence', '置信度', 'confidence'),
      textareaColumn('sourceUrl', '来源URL'),
      textareaColumn('rawText', '原始文本'),
      textareaColumn('extractJson', '抽取JSON'),
      selectColumn('status', '审核状态', 'stagingStatus', true),
      textareaColumn('errorMessage', '错误信息'),
      numberColumn('reviewerId', '审核人'),
      textColumn('reviewedAt', '审核时间', false, 160),
      textareaColumn('reviewNote', '审核备注')
    ]
  }
}

export function getModuleConfig(module) {
  return modules[module]
}

export function getOptions(key) {
  return commonOptions[key] || []
}
