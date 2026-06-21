package io.kelta.worker.dependency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetadataDependencyGraph")
class MetadataDependencyGraphTest {

    private static MetadataNode collection(String id) {
        return MetadataNode.of(MetadataType.COLLECTION, id, "Collection " + id);
    }

    private static MetadataNode field(String id) {
        return MetadataNode.of(MetadataType.FIELD, id, "Field " + id);
    }

    @Test
    @DisplayName("records direct dependents and dependencies with edge kinds")
    void directEdges() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode account = collection("account");
        MetadataNode contactAccountFk = field("contact.account");
        // contact.account (a lookup field) depends on the account collection
        g.addEdge(contactAccountFk, account, DependencyKind.LOOKUP);

        assertThat(g.directDependents(account)).singleElement()
                .satisfies(e -> {
                    assertThat(e.from()).isEqualTo(contactAccountFk);
                    assertThat(e.kind()).isEqualTo(DependencyKind.LOOKUP);
                });
        assertThat(g.directDependencies(contactAccountFk)).singleElement()
                .satisfies(e -> assertThat(e.to()).isEqualTo(account));
    }

    @Test
    @DisplayName("transitive dependents answer 'what breaks if this changes'")
    void transitiveDependents() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode account = collection("account");
        MetadataNode lookup = field("contact.account");
        MetadataNode layout = MetadataNode.of(MetadataType.LAYOUT, "contact-layout", "Contact Layout");

        // account <- lookup field <- a layout that places that field
        g.addEdge(lookup, account, DependencyKind.LOOKUP);
        g.addEdge(layout, lookup, DependencyKind.LAYOUT_USES_FIELD);

        Set<MetadataNode> impacted = g.transitiveDependents(account);

        assertThat(impacted).containsExactlyInAnyOrder(lookup, layout);
        // The node itself is never in its own impact set.
        assertThat(impacted).doesNotContain(account);
    }

    @Test
    @DisplayName("transitive dependencies answer 'what does this rely on'")
    void transitiveDependencies() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode order = collection("order");
        MetadataNode customer = collection("customer");
        MetadataNode orderCustomerFk = field("order.customer");

        g.addEdge(orderCustomerFk, order, DependencyKind.FIELD_OF_COLLECTION);
        g.addEdge(orderCustomerFk, customer, DependencyKind.LOOKUP);

        assertThat(g.transitiveDependencies(orderCustomerFk))
                .containsExactlyInAnyOrder(order, customer);
    }

    @Test
    @DisplayName("ignores self-edges")
    void ignoresSelfEdges() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode c = collection("a");
        g.addEdge(c, c, DependencyKind.MASTER_DETAIL);
        assertThat(g.edges()).isEmpty();
        assertThat(g.hasCycle()).isFalse();
    }

    @Test
    @DisplayName("detects circular master-detail at the collection level")
    void detectsCircularMasterDetail() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode a = collection("a");
        MetadataNode b = collection("b");
        // a is a detail of b, and b is a detail of a — a cascade cycle
        g.addEdge(a, b, DependencyKind.MASTER_DETAIL);
        g.addEdge(b, a, DependencyKind.MASTER_DETAIL);

        List<List<MetadataNode>> cycles = g.findCycles();

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0)).containsExactlyInAnyOrder(a, b);
        assertThat(g.hasCycle()).isTrue();
    }

    @Test
    @DisplayName("acyclic graph reports no cycles")
    void acyclicHasNoCycles() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode a = collection("a");
        MetadataNode b = collection("b");
        MetadataNode c = collection("c");
        g.addEdge(a, b, DependencyKind.LOOKUP);
        g.addEdge(b, c, DependencyKind.LOOKUP);

        assertThat(g.findCycles()).isEmpty();
        assertThat(g.hasCycle()).isFalse();
    }

    @Test
    @DisplayName("merges a missing node name when a later edge supplies it")
    void mergesNodeName() {
        MetadataDependencyGraph g = new MetadataDependencyGraph();
        MetadataNode namelessAccount = MetadataNode.of(MetadataType.COLLECTION, "account", null);
        MetadataNode namedAccount = collection("account");
        g.addNode(namelessAccount);
        g.addNode(namedAccount);

        assertThat(g.resolve(MetadataType.COLLECTION, "account").name()).isEqualTo("Collection account");
    }
}
