<template>
  <div class="program-heading">
    <strong>{{ program.schoolName }}</strong>
    <span v-if="program.collegeName">{{ program.collegeName }}</span>
    <span>{{ program.programName }}</span>
    <span v-if="badges" class="tier-badge">{{ badges }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  program: { type: Object, required: true }
})

const badges = computed(() => [
  props.program.is985 && '985',
  props.program.is211 && '211',
  props.program.isDoubleFirst && '双一流'
].filter(Boolean).join(' '))
</script>

<style scoped>
.program-heading {
  display: grid;
  justify-items: center;
  gap: 4px;
  padding: 8px 0;
  overflow-wrap: anywhere;
}

.program-heading span {
  font-size: 13px;
}

.tier-badge {
  padding: 2px 8px;
  border-radius: 8px;
  color: #1769f6;
  background: #eef4ff;
}
</style>
