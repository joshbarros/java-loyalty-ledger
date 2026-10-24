package com.loyalty.ledger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.loyalty.common.Channels;
import com.loyalty.common.FulfillmentCommand;
import com.loyalty.common.LedgerEvent;
import com.loyalty.common.PointsPolicy;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class LedgerController {

    private final MemberRepository members;
    private final KafkaTemplate<String, Object> kafka;
    private final RabbitTemplate rabbit;

    public LedgerController(MemberRepository members, KafkaTemplate<String, Object> kafka, RabbitTemplate rabbit) {
        this.members = members;
        this.kafka = kafka;
        this.rabbit = rabbit;
    }

    @PostMapping("/members")
    public ResponseEntity<Member> create(@RequestBody CreateMemberRequest request) {
        if (request == null || blank(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Member member = new Member();
        member.setId(UUID.randomUUID().toString());
        member.setName(request.name().trim());
        member.setBalance(0);
        return ResponseEntity.status(HttpStatus.CREATED).body(members.save(member));
    }

    @GetMapping("/members/{id}")
    public Member member(@PathVariable String id) {
        return load(id);
    }

    @PostMapping("/members/{id}/earn")
    @Transactional
    public Member earn(@PathVariable String id, @RequestBody PointsRequest request) {
        int points = request == null ? 0 : request.points();
        requirePositive(points);
        if (members.credit(id, points) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }
        Member member = load(id);
        publish(new LedgerEvent(UUID.randomUUID().toString(), id, "EARN", points, member.getBalance()));
        return member;
    }

    @PostMapping("/members/{id}/redeem")
    @Transactional
    public RedeemResponse redeem(@PathVariable String id, @RequestBody RedeemRequest request) {
        if (request == null || blank(request.rewardCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rewardCode is required");
        }
        int points = request.points();
        requirePositive(points);
        if (members.debit(id, points) == 0) {
            load(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "insufficient balance");
        }
        Member member = load(id);
        String commandId = UUID.randomUUID().toString();
        String rewardCode = request.rewardCode().trim();
        publish(new LedgerEvent(commandId, id, "REDEEM", -points, member.getBalance()));
        rabbit.convertAndSend(Channels.FULFILL, new FulfillmentCommand(commandId, id, rewardCode, points));
        return new RedeemResponse(id, member.getBalance(), commandId, rewardCode);
    }

    private Member load(String id) {
        return members.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found"));
    }

    private static void requirePositive(int points) {
        try {
            PointsPolicy.credit(0, points);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void publish(LedgerEvent event) {
        try {
            kafka.send(Channels.ENTRIES, event.memberId(), event).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "event log unavailable", ex);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateMemberRequest(String name) {
    }

    public record PointsRequest(int points) {
    }

    public record RedeemRequest(int points, String rewardCode) {
    }

    public record RedeemResponse(String memberId, int balance, String commandId, String rewardCode) {
    }
}
