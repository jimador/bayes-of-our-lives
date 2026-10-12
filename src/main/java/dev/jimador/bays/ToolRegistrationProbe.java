package dev.jimador.bays;

import com.embabel.agent.core.AgentPlatform;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Inspect the actual Embabel platform registrations; role constants alone are not providers. */
@Component
@ConditionalOnProperty(name = "bays.inspect-tools", havingValue = "true")
public class ToolRegistrationProbe implements CommandLineRunner {
    private final AgentPlatform platform;
    public ToolRegistrationProbe(AgentPlatform platform) { this.platform = platform; }
    @Override public void run(String... args) {
        for (var metadata : platform.getToolGroupResolver().availableToolGroups())
            System.out.println("registeredToolGroup=" + metadata);
    }
}
