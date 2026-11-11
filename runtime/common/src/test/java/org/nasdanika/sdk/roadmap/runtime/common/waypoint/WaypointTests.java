package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.roadmap.runtime.common.waypoint.Realms.PumpRealm;
import org.nasdanika.sdk.runtime.common.flow.Pump;
import org.nasdanika.sdk.runtime.common.telemetry.PublishingSpan;
import org.nasdanika.sdk.runtime.common.telemetry.PublishingTracerProvider;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

/**
 * Waypoints by example. The roadmap book's chapter on waypoints includes its listings from here.
 */
class WaypointTests {

	@Test
	void anExecutionIsAGraphOfWaypoints() {
		// tag::lineage[]
		Waypoint<String> order = Waypoints.root("order");
		Waypoint<String> validate = order.commit("validate"); // <1>
		Waypoint<String> price = validate.commit("price"); // <2>
		Waypoint<String> stock = validate.commit("stock");
		Waypoint<String> ship = price.merge("ship", List.of(stock)); // <3>

		assertThat(ship.getParents()).isEqualTo(List.of(price, stock));
		assertThat(ship.lineage().map(Waypoint::getElement))
				.containsExactly("ship", "price", "stock", "validate", "order"); // <4>
		assertThat(validate.getParents()).isEqualTo(List.of(order)); // <5>
		// end::lineage[]
	}

	@Test
	void aRetryIsASiblingAndTheFailedAttemptStays() {
		// tag::retry[]
		StateWaypoint<String, String> validate = Waypoints.root("order", "received").commit("validate", "valid");
		StateWaypoint<String, String> failed = validate.commit("price", "timeout");

		StateWaypoint<String, String> retry = validate.commit("price", "priced"); // <1>

		assertThat(retry.getParents()).isEqualTo(List.of(validate));
		assertThat(failed.getState()).isEqualTo("timeout"); // <2>
		assertThat(validate.getState()).isEqualTo("valid");
		// end::retry[]
	}

	@Test
	void aJoinComputesItsStateFromTheWaypointsItJoins() {
		// tag::join[]
		StateWaypoint<String, String> order = Waypoints.root("order", "3 widgets");
		StateWaypoint<String, Integer> price = order.commit("price", 120);
		StateWaypoint<String, Boolean> stock = order.commit("stock", true);

		StateWaypoint<String, String> ship = price.map(
				"ship",
				joining -> "charge " + joining.get(0).getState() + ", in stock: " + joining.get(1).getState(), // <1>
				List.of(stock));

		assertThat(ship.getState()).isEqualTo("charge 120, in stock: true");
		assertThat(ship.getParents()).isEqualTo(List.of(price, stock));
		// end::join[]
	}

	/**
	 * The execution has settled when it comes back to an element with the state it had there the
	 * last time. The rule reads lineage, and the value knows nothing about it.
	 */
	// tag::settled[]
	static boolean settled(StateWaypoint<String, Integer> waypoint) {
		return waypoint.lineage()
				.skip(1) // <1>
				.filter(earlier -> earlier.getElement().equals(waypoint.getElement()))
				.findFirst() // <2>
				.map(earlier -> earlier.facet(StateWaypoint.class).orElseThrow().getState().equals(waypoint.getState()))
				.orElse(false);
	}

	@Test
	void settlingIsARuleOverLineage() {
		Map<String, String> ring = Map.of("a", "b", "b", "c", "c", "a"); // <3>

		StateWaypoint<String, Integer> waypoint = Waypoints.root("a", 64);
		while (!settled(waypoint)) {
			waypoint = waypoint.commit(ring.get(waypoint.getElement()), waypoint.getState() / 2); // <4>
		}

		assertThat(waypoint.getElement()).isEqualTo("b");
		assertThat(waypoint.getState()).isZero();
		assertThat(waypoint.visits("b")).isEqualTo(4); // <5>
		assertThat(waypoint.lineage()).hasSize(11);
	}
	// end::settled[]

	@Test
	void aMergeOfStateWaypointsRefusesAParentWithoutState() {
		StateWaypoint<String, Integer> price = Waypoints.root("price", 120);
		Waypoint<String> plain = Waypoints.root("stock");

		assertThatThrownBy(() -> price.merge("ship", List.of(plain))).isInstanceOf(IllegalArgumentException.class);
	}

	// tag::realm[]
	static final ContextKey<String> SCREEN = ContextKey.named("screen");

	@Test
	void aRealmOnAPumpRunsOneCommandAtATimeInTheContextItWasIssuedIn() {
		Pump pump = new Pump();
		List<String> ledger = new ArrayList<>(); // <1>
		PumpRealm<List<String>> realm = Realms.pump(ledger, pump);

		RealmWaypoint<String, List<String>> debit = Waypoints.realm("transfer", realm).commit("debit");
		try (Scope scope = Context.current().with(SCREEN, "transfer").makeCurrent()) {
			debit.execute(entries -> entries.add("debit 100 from " + Context.current().get(SCREEN))); // <2>
		}
		RealmWaypoint<String, List<String>> credit = debit.commit("credit");
		credit.execute(entries -> entries.add("credit 100"));

		int entries = credit.apply(List::size);
		assertThat(entries).isEqualTo(2);
		assertThat(ledger).containsExactly("debit 100 from transfer", "credit 100");

		CompletableFuture<Integer> size = realm.submit(List::size).toCompletableFuture(); // <3>
		assertThat(size).isNotDone();
		pump.settle();
		assertThat(size).isCompletedWithValue(2);
	}
	// end::realm[]

	@Test
	void aCommandThatFailsFailsItsCallerAndNotThePump() {
		Pump pump = new Pump();
		PumpRealm<List<String>> realm = Realms.pump(new ArrayList<>(), pump);

		assertThatThrownBy(() -> realm.execute(entries -> {
			throw new IllegalStateException("insufficient funds");
		})).isInstanceOf(IllegalStateException.class).hasMessage("insufficient funds");

		realm.execute(entries -> entries.add("still working"));
		int size = realm.apply(List::size);
		assertThat(size).isEqualTo(1);
	}

	@Test
	void aRealmCanBeSeenThroughALens() {
		Realm<List<String>> ledger = Realms.exclusive(new ArrayList<>(List.of("debit 100", "credit 100")));
		Realm<Integer> count = ledger.adapt(List::size);

		ledger.execute(entries -> entries.add("fee 1"));

		int entries = count.apply(size -> size);
		assertThat(entries).isEqualTo(3);
	}

	@Test
	void theTreeOfWaypointsIsTheTreeOfSpans() {
		// tag::telemetry[]
		try (PublishingTracerProvider provider = new PublishingTracerProvider(TracerProvider.noop())) { // <1>
			Tracer tracer = provider.get("orders");

			StateWaypoint<String, Integer> root = Waypoints.root("order", 0);
			TelemetryWaypoint<String> order = Waypoints.traced(root, tracer, element -> "step " + element); // <2>
			TelemetryWaypoint<String> price = order.follow(root.commit("price", 120)); // <3>
			TelemetryWaypoint<String> stock = order.commit("stock");
			TelemetryWaypoint<String> ship = price.merge("ship", List.of(stock)); // <4>

			PublishingSpan span = (PublishingSpan) ship.getSpan();
			assertThat(span.getName()).isEqualTo("step ship");
			assertThat(span.getParent()).isSameAs(price.getSpan());
			assertThat(span.getParent().getParent()).isSameAs(order.getSpan());
			assertThat(ship.getParents()).isEqualTo(List.of(price, stock));

			assertThat(ship.facet(StateWaypoint.class).orElseThrow().getState()).isEqualTo(120); // <5>
			assertThat(ship.call(Span::current)).isSameAs(span); // <6>

			for (TelemetryWaypoint<String> waypoint: List.of(ship, stock, price, order)) {
				waypoint.close();
			}
			assertThat(span.isEnded()).isTrue();
		}
		// end::telemetry[]
	}

}
