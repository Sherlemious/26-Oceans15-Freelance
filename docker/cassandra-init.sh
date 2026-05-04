#!/usr/bin/env bash

set -euo pipefail

docker-entrypoint.sh cassandra -f &

until cqlsh -e 'DESCRIBE KEYSPACES' >/dev/null 2>&1; do
  sleep 5
done

cqlsh -e "CREATE KEYSPACE IF NOT EXISTS ${CASSANDRA_KEYSPACE} WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
cqlsh -e "USE ${CASSANDRA_KEYSPACE}; CREATE TABLE IF NOT EXISTS contract_milestone_events (
  contract_id     bigint,
  timestamp       timestamp,
  milestone_order int,
  status          text,
  recorded_by     text,
  notes           text,
  PRIMARY KEY (contract_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);"

wait