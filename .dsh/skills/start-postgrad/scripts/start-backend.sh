#!/bin/bash
# Start the Spring Boot backend with the local runtime config.
set -u
RT="${POSTGRAD_RUNTIME:-$HOME/postgrad-runtime}"
source "$RT/env.sh"
export RAG_DATA_DIR="$RT/rag-data"
cd /opt/postgrad
exec java -Dlogback.configurationFile="$RT/logback.xml" -Dlogging.config="file:$RT/logback.xml" \
  -Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /opt/postgrad/ruoyi-admin.jar \
  --spring.config.additional-location=file:/opt/postgrad/config/ \
  --ruoyi.profile="$RT/uploadPath" \
  --logging.config="file:$RT/logback.xml" \
  >> "$RT/logs/backend.log" 2>&1
