<template>
  <section class="logic-card">
    <h2>推荐逻辑</h2>
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
  padding: 20px;
  border: 1px solid rgba(215, 227, 245, .9);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(42, 84, 153, .08);
}

.logic-card h2 {
  margin: 0 0 16px;
  font-size: 18px;
  line-height: 24px;
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
  border: 1px solid #edf3fb;
  border-radius: 8px;
  background: #f8fbff;
}

.logic-step span {
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #1769f6;
  color: #fff;
  font-weight: 800;
  font-size: 13px;
}

.logic-step strong {
  display: block;
  color: #10213f;
  font-size: 14px;
  line-height: 20px;
}

.logic-step p {
  margin: 2px 0 0;
  color: #71829a;
  font-size: 13px;
  line-height: 18px;
}
</style>
