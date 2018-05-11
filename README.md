Playground project for Kafka Java APIs.

Code copied from Kafka blog: https://www.confluent.io/blog/transactions-apache-kafka/. Had to fill in some blanks, mostly guessing, so might have screwed it up there.

Puzzle: why are messages not re-delivered after a transaction is aborted?

Run the app. It sends 5 messages, "foo0", "foo1", "foo2", "foo3" etc. Then it sets up a read-transform-write loop with a producer transaction, so if it aborts we expect that the messages will be re-delivered. The app intentionally fails on the 3rd record ("foo2" on the first time round the loop). But it never gets to the second time round the loop: it blocks on `consumer.poll()` - i.e. the records are never re-delivered:

```
...
2018-05-11 10:01:20.146 [main] INFO  o.a.kafka.common.utils.AppInfoParser - Kafka version : 1.0.1
2018-05-11 10:01:20.146 [main] INFO  o.a.kafka.common.utils.AppInfoParser - Kafka commitId : c0518aa65f25317e
2018-05-11 10:01:20.160 [main] INFO  o.a.k.c.c.i.AbstractCoordinator - [Consumer clientId=consumer-1, groupId=mygroup] Discovered group coordinator localhost:9092 (id: 2147482646 rack: null)
2018-05-11 10:01:20.162 [main] INFO  o.a.k.c.c.i.ConsumerCoordinator - [Consumer clientId=consumer-1, groupId=mygroup] Revoking previously assigned partitions []
2018-05-11 10:01:20.163 [main] INFO  o.a.k.c.c.i.AbstractCoordinator - [Consumer clientId=consumer-1, groupId=mygroup] (Re-)joining group
2018-05-11 10:01:20.175 [main] INFO  o.a.k.c.c.i.AbstractCoordinator - [Consumer clientId=consumer-1, groupId=mygroup] Successfully joined group with generation 12
2018-05-11 10:01:20.180 [main] INFO  o.a.k.c.c.i.ConsumerCoordinator - [Consumer clientId=consumer-1, groupId=mygroup] Setting newly assigned partitions [input-0]
***** Begin
***** foo0
***** foo1
***** foo2
***** Rollback
```

If you comment out the `app.send()` and restart the same thing happens - it blocks waiting for messages and the unconfirmed records are lost - so there is no output from the app.
