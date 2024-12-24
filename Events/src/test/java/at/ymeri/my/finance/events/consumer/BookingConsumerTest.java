package at.ymeri.my.finance.events.consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BookingConsumerTest {

    @InjectMocks
    private BookingConsumer bookingConsumer;

    @Test
    void consume() {
        assertDoesNotThrow(() -> bookingConsumer.consume("test"));
    }

}