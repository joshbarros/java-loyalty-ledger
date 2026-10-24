package com.loyalty.fulfillment;

import java.util.concurrent.TimeUnit;

import com.loyalty.common.Channels;
import com.loyalty.common.CompensationEvent;
import com.loyalty.common.FulfillmentCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FulfillmentWorker {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentWorker.class);

    private final FulfillmentRepository fulfillments;
    private final KafkaTemplate<String, Object> kafka;

    public FulfillmentWorker(FulfillmentRepository fulfillments, KafkaTemplate<String, Object> kafka) {
        this.fulfillments = fulfillments;
        this.kafka = kafka;
    }

    @RabbitListener(queues = Channels.FULFILL)
    @Transactional
    public void onFulfill(FulfillmentCommand command) {
        if ("OUT_OF_STOCK".equals(command.rewardCode())) {
            try {
                kafka.send(Channels.COMPENSATIONS, command.commandId(),
                        new CompensationEvent(command.commandId(), command.memberId(), command.points()))
                        .get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new AmqpRejectAndDontRequeueException("compensation publish failed", ex);
            }
            log.info("reward {} is out of stock for {}", command.rewardCode(), command.commandId());
            throw new AmqpRejectAndDontRequeueException("out of stock");
        }
        if (fulfillments.existsById(command.commandId())) {
            return;
        }
        FulfillmentEntity row = new FulfillmentEntity();
        row.setCommandId(command.commandId());
        row.setRewardCode(command.rewardCode());
        row.setStatus("SUCCESS");
        fulfillments.save(row);
        log.info("fulfilled {} for {}", command.rewardCode(), command.commandId());
    }
}
