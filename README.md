# Kafka Streaming

1. Consume from kafka topic `number-input`
1. Evaluate the numbers
1. Produce to kafka topics `even-numbers-output`, `odd-numbers-output` in a transactional way.

## Kafka topic Configuration
### Input
* Create input topic:
`./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic number-input --create --replication-factor 3 --partitions 3 --config "retention.ms=3600000" --config "message.timestamp.type=LogAppendTime" --config "compression.type=zstd"`
* Delete input topic:
`./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic number-input --delete`

### Output
Create output topics:
* `./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic even-numbers-output --create --replication-factor 3 --partitions 3 --config "retention.ms=3600000" --config "message.timestamp.type=LogAppendTime" --config "compression.type=zstd"`
* `./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic odd-numbers-output --create --replication-factor 3 --partitions 3 --config "retention.ms=3600000" --config "message.timestamp.type=LogAppendTime" --config "compression.type=zstd"`

Delete input topic:
* `./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic even-numbers-output --delete`
* `./kafka-topics.sh --zookeeper 127.0.0.1:9081 --topic odd-numbers-output --delete`
