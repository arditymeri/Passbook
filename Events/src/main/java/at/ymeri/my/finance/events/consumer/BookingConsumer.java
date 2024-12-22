package at.ymeri.my.finance.events.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class BookingConsumer {

        @KafkaListener(topics = "booking.topic", groupId = "booking.group.id")
        public void consume(String message) {
            System.out.println("Consumed message: " + message);
        }
}
