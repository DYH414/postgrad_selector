<template>
  <section class="logic-card">
    <div class="logic-head">
      <span>流程</span>
      <h2>推荐逻辑</h2>
    </div>
    <div class="logic-steps">
      <div class="logic-step">
        <span>1</span>
        <div>
          <strong>先看画像</strong>
          <p>理解你的目标与偏好</p>
        </div>
      </div>
      <div class="logic-step">
        <span>2</span>
        <div>
          <strong>再查事实</strong>
          <p>查询招生、分数和风险数据</p>
        </div>
      </div>
      <div class="logic-step">
        <span>3</span>
        <div>
          <strong>最后给建议</strong>
          <p>结合策略给出冲稳保推荐</p>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.logic-card {
  padding: 18px;
  border: 1px solid #dce7f6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 28px rgba(39, 86, 166, .07);
}

.logic-head {
  margin-bottom: 14px;
}

.logic-head span {
  display: block;
  color: #1769f6;
  font-size: 12px;
  line-height: 16px;
  font-weight: 900;
}

.logic-card h2 {
  margin: 4px 0 0;
  font-size: 17px;
  line-height: 23px;
}

.logic-steps {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.logic-step {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5edf8;
  border-radius: 8px;
  background: #fbfdff;
}

.logic-step span {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: #1769f6;
  color: #fff;
  font-weight: 800;
  font-size: 13px;
}

.logic-step strong {
  display: block;
  color: #10213f;
  font-size: 13px;
  line-height: 20px;
}

.logic-step p {
  margin: 2px 0 0;
  color: #71829a;
  font-size: 13px;
  line-height: 18px;
}
</style>
