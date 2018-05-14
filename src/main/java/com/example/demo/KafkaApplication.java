package com.example.demo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

public class KafkaApplication {

	public static void main(String[] args) {
		KafkaApplication app = new KafkaApplication();
		app.send();
		app.run();
	}

	private String group = "mygroup";

	private int count = 0;

	private void send() {
		KafkaProducer<byte[], byte[]> producer = createKafkaProducer("bootstrap.servers",
				"localhost:9092");
		CountDownLatch latch = new CountDownLatch(5);
		for (int i = 0; i < 5; i++) {
			System.err.println("***** Send foo" + i);
			producer.send(
					new ProducerRecord<byte[], byte[]>("input", ("foo" + i).getBytes()),
					(m, e) -> latch.countDown());
		}
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void run() {
		KafkaProducer<byte[], byte[]> producer = createKafkaProducer("bootstrap.servers",
				"localhost:9092", "transactional.id", "mytx");

		producer.initTransactions();

		while (true) {
			KafkaConsumer<byte[], byte[]> consumer = createKafkaConsumer(
					"bootstrap.servers", "localhost:9092", "group.id", group,
					"isolation.level", "read_committed");

			consumer.subscribe(Arrays.asList("input"));

			while (true) {
				ConsumerRecords<byte[], byte[]> records = consumer.poll(Long.MAX_VALUE);
				System.err.println("***** Begin");
				producer.beginTransaction();
				try {
					Map<TopicPartition, OffsetAndMetadata> currentOffsets = new LinkedHashMap<>();
					for (ConsumerRecord<byte[], byte[]> record : records) {
						currentOffsets.put(
								new TopicPartition(record.topic(), record.partition()),
								new OffsetAndMetadata(record.offset()));
						System.err.println("***** " + new String(record.value()));
						producer.send(new ProducerRecord<byte[], byte[]>("output",
								record.key(), transform(record.value())));
					}
					producer.sendOffsetsToTransaction(currentOffsets, group);
					System.err.println("***** Commit");
					producer.commitTransaction();
				}
				catch (Exception e) {
					try {
						System.err.println("***** Rollback");
						producer.abortTransaction();
					}
					catch (Exception ex) {
					}
					break;
				}
			}

		}
	}

	private byte[] transform(byte[] value) {
		if (count++ == 2) {
			throw new RuntimeException("Planned!");
		}
		return value;
	}

	private KafkaConsumer<byte[], byte[]> createKafkaConsumer(String... args) {
		Properties properties = new Properties();
		for (int i = 0; i < args.length; i += 2) {
			properties.setProperty(args[i], args[i + 1]);
		}
		properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
				ByteArrayDeserializer.class.getName());
		properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
				ByteArrayDeserializer.class.getName());
		properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		return new KafkaConsumer<>(properties);
	}

	private KafkaProducer<byte[], byte[]> createKafkaProducer(String... args) {
		Properties properties = new Properties();
		for (int i = 0; i < args.length; i += 2) {
			properties.setProperty(args[i], args[i + 1]);
		}
		properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
				ByteArraySerializer.class.getName());
		properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
				ByteArraySerializer.class.getName());
		return new KafkaProducer<>(properties);
	}

}
