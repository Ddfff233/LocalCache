package com.example.cache.domain.mq;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * 手动确认模式下提供给监听方法的幂等确认上下文。
 */
public final class CacheMqAckContext {

    private final String messageId;
    private final BooleanSupplier acknowledgeAction;
    private final Function<String, Boolean> rejectAction;
    private final AtomicBoolean completed = new AtomicBoolean();

    public CacheMqAckContext(String messageId, BooleanSupplier acknowledgeAction,
                             Function<String, Boolean> rejectAction) {
        this.messageId = Objects.requireNonNull(messageId, "消息标识不能为空");
        this.acknowledgeAction = Objects.requireNonNull(acknowledgeAction, "ACK 操作不能为空");
        this.rejectAction = Objects.requireNonNull(rejectAction, "NACK 操作不能为空");
    }

    public boolean ack() {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        return acknowledgeAction.getAsBoolean();
    }

    public boolean nack() {
        return nack("消费者主动拒绝消息");
    }

    public boolean nack(String reason) {
        if (!completed.compareAndSet(false, true)) {
            return false;
        }
        return rejectAction.apply(reason);
    }

    public String getMessageId() {
        return messageId;
    }

    public boolean isCompleted() {
        return completed.get();
    }
}
