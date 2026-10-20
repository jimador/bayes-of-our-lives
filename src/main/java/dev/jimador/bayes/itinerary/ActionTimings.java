package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.event.ActionExecutionResultEvent;
import com.embabel.agent.api.event.AgentProcessEvent;
import com.embabel.agent.api.event.AgenticEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs one line per finished action, using the timing Embabel already records. The live check
 * counts these lines. An action that throws never reaches here; the platform logs that failure
 * itself.
 */
@Component
class ActionTimings implements AgenticEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(ActionTimings.class);

    @Override
    public void onProcessEvent(AgentProcessEvent event) {
        if (event instanceof ActionExecutionResultEvent result) {
            String name = result.getAction().getName();
            LOG.info(
                    "stage={} durationMs={} outcome={}",
                    name.substring(name.lastIndexOf('.') + 1),
                    result.getRunningTime().toMillis(),
                    result.getActionStatus().getStatus());
        }
    }
}
