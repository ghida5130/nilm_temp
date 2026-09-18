#!/usr/bin/env bash
set -euo pipefail

hdfs dfs -mkdir -p \
  /nilm/bronze/power \
  /nilm/quarantine/power \
  /nilm/manifests \
  /nilm/silver \
  /nilm/gold

if [[ "${HDFS_QUOTA_ENABLED:-true}" == "true" ]]; then
  hdfs dfsadmin -setSpaceQuota "${HDFS_BRONZE_QUOTA:-32g}" /nilm/bronze/power
  hdfs dfsadmin -setSpaceQuota "${HDFS_QUARANTINE_QUOTA:-8g}" /nilm/quarantine/power
  hdfs dfsadmin -setSpaceQuota "${HDFS_MANIFEST_QUOTA:-2g}" /nilm/manifests
  hdfs dfsadmin -setSpaceQuota "${HDFS_SILVER_QUOTA:-24g}" /nilm/silver
  hdfs dfsadmin -setSpaceQuota "${HDFS_GOLD_QUOTA:-2g}" /nilm/gold
  hdfs dfsadmin -setSpaceQuota "${HDFS_TOTAL_QUOTA:-80g}" /nilm
fi

hdfs dfs -count -q -h /nilm
