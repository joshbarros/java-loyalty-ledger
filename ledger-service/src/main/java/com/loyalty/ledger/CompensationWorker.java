package com.loyalty.ledger;

import com.loyalty.common.Channels;
import com.loyalty.common.CompensationEvent;
import com.loyalty.common.PointsPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CompensationWorker {

    private static final Logger log = LoggerFactory.getLogger(CompensationWorker.class);

    private final MemberRepository members;
    private final AppliedCommandRepository appliedCommands;

    public CompensationWorker(MemberRepository members, AppliedCommandRepository appliedCommands) {
        this.members = members;
        this.appliedCommands = appliedCommands;
    }

    @KafkaListener(topics = Channels.COMPENSATIONS, groupId = "ledger-compensation")
    @Transactional
    public void onCompensation(CompensationEvent event) {
        if (appliedCommands.existsById(event.commandId())) {
            return;
        }
        Member member = members.findById(event.memberId())
                .orElseThrow(() -> new IllegalStateException("member missing for " + event.commandId()));
        member.setBalance(PointsPolicy.credit(member.getBalance(), event.points()));
        members.save(member);
        AppliedCommand applied = new AppliedCommand();
        applied.setCommandId(event.commandId());
        appliedCommands.save(applied);
        log.info("credited {} points back to {} for {}", event.points(), event.memberId(), event.commandId());
    }
}
