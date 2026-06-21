package io.kelta.worker.health.rule;

import io.kelta.worker.dependency.DependencyKind;
import io.kelta.worker.dependency.MetadataDependencyGraph;
import io.kelta.worker.dependency.MetadataNode;
import io.kelta.worker.dependency.MetadataType;
import io.kelta.worker.health.HealthContext;
import io.kelta.worker.health.HealthFinding;
import io.kelta.worker.health.HealthSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CircularDependencyRule")
class CircularDependencyRuleTest {

    private final CircularDependencyRule rule = new CircularDependencyRule();

    private static MetadataNode collection(String id) {
        return MetadataNode.of(MetadataType.COLLECTION, id, "Collection " + id);
    }

    private static MetadataNode flow(String id) {
        return MetadataNode.of(MetadataType.FLOW, id, "Flow " + id);
    }

    @Test
    @DisplayName("reports a circular master-detail chain as an ERROR")
    void masterDetailCycle() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        g.addEdge(collection("a"), collection("b"), DependencyKind.MASTER_DETAIL);
        g.addEdge(collection("b"), collection("a"), DependencyKind.MASTER_DETAIL);

        List<HealthFinding> findings = rule.evaluate(new HealthContext("t1", g, null));

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(HealthSeverity.ERROR);
        assertThat(findings.get(0).title()).isEqualTo("Circular master-detail relationship");
        assertThat(findings.get(0).ruleKey()).isEqualTo("CIRCULAR_DEPENDENCY");
    }

    @Test
    @DisplayName("reports a circular flow invocation distinctly")
    void flowCycle() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        g.addEdge(flow("f1"), flow("f2"), DependencyKind.FLOW_INVOKES_FLOW);
        g.addEdge(flow("f2"), flow("f1"), DependencyKind.FLOW_INVOKES_FLOW);

        List<HealthFinding> findings = rule.evaluate(new HealthContext("t1", g, null));

        assertThat(findings).singleElement()
                .satisfies(f -> assertThat(f.title()).isEqualTo("Circular flow invocation"));
    }

    @Test
    @DisplayName("acyclic graph yields no findings")
    void acyclic() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        g.addEdge(collection("a"), collection("b"), DependencyKind.LOOKUP);
        assertThat(rule.evaluate(new HealthContext("t1", g, null))).isEmpty();
    }
}
