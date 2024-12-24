package at.ymeri.my.finance.events.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class BookingConsumer implements ConsumerSeekAware {

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        // Reset offsets to the beginning of each assigned partition
        assignments.keySet()
                .forEach(tp -> callback.seekToBeginning(tp.topic(), tp.partition()));
    }

    @KafkaListener(topics = "booking.topic", groupId = "booking.group.id")
    public void consume(String message) {
        log.info("Consumed message: {}", message);
    }
}
