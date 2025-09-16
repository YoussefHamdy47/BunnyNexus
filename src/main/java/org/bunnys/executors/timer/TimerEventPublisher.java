package org.bunnys.executors.timer;

import org.bunnys.executors.timer.engine.TimerHelper.*;
import org.bunnys.handler.utils.handler.logging.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class TimerEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public TimerEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publishButtonStateChange(TimerEvents.TimerContext context,
            List<TimerButtonID> enabledButtons,
            boolean isPaused) {
        Objects.requireNonNull(context, "context cannot be null");
        ButtonFixerOptions options = new ButtonFixerOptions(enabledButtons, isPaused);
        eventPublisher.publishEvent(new ButtonStateChangeEvent(context, options));
        Logger.debug(() -> "Published ButtonStateChangeEvent for user: " + context.userId()
                + " enabledButtons=" + enabledButtons + " isPaused=" + isPaused);
    }

    public void publishLevelUp(TimerEvents.TimerContext context, int levelUps, int carryOverXP) {
        Objects.requireNonNull(context, "context cannot be null");
        LevelUpOptions options = new LevelUpOptions(null, levelUps, carryOverXP,
                context.timerData(), context.userData());
        eventPublisher.publishEvent(new LevelUpEvent(context, options));
        Logger.info("Published LevelUpEvent for user: " + context.userId() + " levelUps=" + levelUps);
    }

    public void publishRankUp(TimerEvents.TimerContext context, int rankUps, int carryOverRP) {
        Objects.requireNonNull(context, "context cannot be null");
        LevelUpOptions options = new LevelUpOptions(null, rankUps, carryOverRP,
                context.timerData(), context.userData());
        eventPublisher.publishEvent(new RankUpEvent(context, options));
        Logger.info("Published RankUpEvent for user: " + context.userId() + " rankUps=" + rankUps);
    }

    public void publishRecordBroken(TimerEvents.TimerContext context, ActivityType type, long sessionTime) {
        Objects.requireNonNull(context, "context cannot be null");
        RecordBrokenOptions options = new RecordBrokenOptions(type, null, sessionTime,
                context.timerData().getCurrentSemester());
        eventPublisher.publishEvent(new RecordBrokenEvent(context, options));
        Logger.info("Published RecordBrokenEvent for user: " + context.userId() + " type=" + type
                + " sessionTime=" + sessionTime);
    }

    // Event classes
    public record ButtonStateChangeEvent(TimerEvents.TimerContext context, ButtonFixerOptions options) {
    }

    public record LevelUpEvent(TimerEvents.TimerContext context, LevelUpOptions options) {
    }

    public record RankUpEvent(TimerEvents.TimerContext context, LevelUpOptions options) {
    }

    public record RecordBrokenEvent(TimerEvents.TimerContext context, RecordBrokenOptions options) {
    }
}
