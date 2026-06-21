package io.kelta.worker.health.rule;

import io.kelta.worker.dependency.MetadataNode;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthRule;
import io.kelta.worker.health.HealthSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Flags cycles in the metadata dependency graph — most importantly circular master-detail chains
 * (a cascade-delete loop) and circular flow invocation (an InvokeFlow loop). Reuses Rec 5's
 * {@code MetadataDependencyGraph.findCycles()}.
 *
 * @since 1.0.0
 */
@Component
public class CircularDependencyRule implements HealthRule {

    @Override
    public String key() {
        return "CIRCULAR_DEPENDENCY";
    }

    @Override
    public List<HealthFinding> evaluate(HealthContext context) {
        List<HealthFinding> findings = new ArrayList<>();
        for (List<MetadataNode> cycle : context.graph().findCycles()) {
            if (cycle.isEmpty()) {
                continue;
            }
            boolean allCollections = cycle.stream().allMatch(n -> n.type() == MetadataType.COLLECTION);
            boolean allFlows = cycle.stream().allMatch(n -> n.type() == MetadataType.FLOW);
            String chain = cycle.stream().map(this::label).collect(Collectors.joining(" → "));
            MetadataNode anchor = cycle.get(0);

            String title;
            String detail;
            if (allCollections) {
                title = "Circular master-detail relationship";
                detail = "Collections form a cascade-delete cycle: " + chain
                        + ". Deleting any one cascades around the loop. Break the cycle by changing one"
                        + " master-detail field to a lookup.";
            } else if (allFlows) {
                title = "Circular flow invocation";
                detail = "Flows invoke each other in a cycle: " + chain
                        + ". This risks unbounded recursion; remove one InvokeFlow edge.";
            } else {
                title = "Circular metadata dependency";
                detail = "Configuration forms a dependency cycle: " + chain + ". Break the loop.";
            }
            findings.add(HealthFinding.of(key(), HealthSeverity.ERROR, title, detail,
                    anchor.type(), anchor.id(), anchor.name()));
        }
        return findings;
    }

    private String label(MetadataNode node) {
        return node.name() != null ? node.name() : node.id();
    }
}
