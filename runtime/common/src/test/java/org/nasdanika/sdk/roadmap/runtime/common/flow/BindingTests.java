package org.nasdanika.sdk.roadmap.runtime.common.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.junit.jupiter.api.Test;
import org.nasdanika.sdk.runtime.common.flow.Pump;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

/**
 * What a binding promises, one promise per test. The roadmap book's chapter on bindings includes
 * its listings from here.
 */
class BindingTests {

	/** A subscriber that keeps what it receives, with as much demand as the test gives it. */
	// tag::collector[]
	static class Collector<T> implements Flow.Subscriber<T> {

		final List<T> items = new ArrayList<>();
		final long demand;
		Flow.Subscription subscription;
		boolean completed;

		Collector(long demand) {
			this.demand = demand;
		}

		@Override
		public void onSubscribe(Flow.Subscription subscription) {
			this.subscription = subscription;
			subscription.request(demand);
		}

		@Override
		public void onNext(T item) {
			items.add(item);
		}

		@Override
		public void onError(Throwable throwable) {
			throw new AssertionError(throwable);
		}

		@Override
		public void onComplete() {
			completed = true;
		}

	}
	// end::collector[]

	/** A subscriber that takes everything and passes it on. */
	static <T> Flow.Subscriber<T> each(Consumer<T> action) {
		return new Collector<>(Long.MAX_VALUE) {

			@Override
			public void onNext(T item) {
				action.accept(item);
			}

		};
	}

	@Test
	void theFirstItemIsTheValueAtSubscription() {
		// tag::first[]
		ValueBinding<String> account = Bindings.value("Checking");

		Collector<String> collector = new Collector<>(Long.MAX_VALUE);
		account.subscribe(collector); // <1>
		account.set("Savings");
		account.set("Savings"); // <2>

		assertThat(account.get()).isEqualTo("Savings"); // <3>
		assertThat(collector.items).containsExactly("Checking", "Savings");
		// end::first[]
	}

	@Test
	void aSlowSubscriberGetsTheLatestValueNotABacklog() {
		// tag::coalescing[]
		ValueBinding<Integer> progress = Bindings.value(0);

		Collector<Integer> frame = new Collector<>(1); // <1>
		progress.subscribe(frame);
		for (int done = 1; done <= 1000; ++done) {
			progress.set(done); // <2>
		}
		assertThat(frame.items).containsExactly(0);

		frame.subscription.request(1); // <3>
		assertThat(frame.items).containsExactly(0, 1000);
		// end::coalescing[]
	}

	@Test
	void onAPumpNothingIsDeliveredUntilSomebodyPumps() {
		// tag::pump[]
		Pump pump = new Pump();
		ValueBinding<Integer> progress = Bindings.value(0, pump); // <1>

		Collector<Integer> collector = new Collector<>(Long.MAX_VALUE);
		progress.subscribe(collector);
		progress.set(1);
		progress.set(2);
		progress.set(3);
		assertThat(collector.items).isEmpty(); // <2>

		pump.settle(); // <3>
		assertThat(collector.items).containsExactly(3);
		// end::pump[]
	}

	// tag::provenance[]
	static final ContextKey<String> CONTROL = ContextKey.named("control");

	@Test
	void aChangeIsDeliveredInTheContextItWasMadeIn() {
		Pump pump = new Pump();
		ValueBinding<String> amount = Bindings.value("0", pump);

		List<String> seen = new ArrayList<>();
		amount.subscribe(each(value -> seen.add(value + " from " + Context.current().get(CONTROL)))); // <1>
		pump.settle();

		try (Scope scope = Context.current().with(CONTROL, "transfer/amount").makeCurrent()) {
			amount.set("100"); // <2>
		}
		pump.settle(); // <3>

		assertThat(seen).containsExactly("0 from null", "100 from transfer/amount");
	}
	// end::provenance[]

	@Test
	void aMappedBindingMapsBothSides() {
		// tag::map[]
		ValueBinding<Integer> cents = Bindings.value(1999);
		Binding<String> label = Bindings.map(cents, value -> "$%d.%02d".formatted(value / 100, value % 100));

		assertThat(label.get()).isEqualTo("$19.99"); // <1>

		Collector<String> collector = new Collector<>(Long.MAX_VALUE);
		label.subscribe(collector);
		cents.set(2500);
		assertThat(collector.items).containsExactly("$19.99", "$25.00"); // <2>

		label.close(); // <3>
		assertThat(collector.completed).isTrue();
		assertThat(cents.isClosed()).isFalse();
		assertThat(cents.getSubscriberCount()).isZero();
		// end::map[]
	}

	@Test
	void aComputedBindingFollowsEveryBindingItReads() {
		// tag::combine[]
		ValueBinding<Integer> quantity = Bindings.value(2);
		ValueBinding<Integer> price = Bindings.value(5);
		Binding<Integer> total = Bindings.combine(quantity, price, (q, p) -> q * p);

		Collector<Integer> collector = new Collector<>(Long.MAX_VALUE);
		total.subscribe(collector);
		quantity.set(3);
		price.set(10);

		assertThat(collector.items).containsExactly(10, 15, 30);
		// end::combine[]
	}

	@Test
	void aMappedBindingEndsWhenItsSourceEnds() {
		ValueBinding<Integer> cents = Bindings.value(1999);
		Binding<String> label = Bindings.map(cents, String::valueOf);
		Collector<String> collector = new Collector<>(Long.MAX_VALUE);
		label.subscribe(collector);

		cents.close();

		assertThat(label.isClosed()).isTrue();
		assertThat(collector.completed).isTrue();
		assertThat(label.get()).isEqualTo("1999");
	}

	@Test
	void closingEndsSubscribersAndKeepsTheLastValue() {
		// tag::close[]
		ValueBinding<String> status = Bindings.value("running");
		Collector<String> collector = new Collector<>(Long.MAX_VALUE);
		status.subscribe(collector);

		status.close();
		status.close(); // <1>

		assertThat(collector.completed).isTrue();
		assertThat(status.get()).isEqualTo("running"); // <2>
		assertThatThrownBy(() -> status.set("done")).isInstanceOf(IllegalStateException.class);

		Collector<String> late = new Collector<>(Long.MAX_VALUE);
		status.subscribe(late); // <3>
		assertThat(late.items).containsExactly("running");
		assertThat(late.completed).isTrue();
		// end::close[]
	}

	@Test
	void aSubscriberThatCancelsLeavesTheBindingOpenForTheOthers() {
		ValueBinding<String> status = Bindings.value("running");
		Collector<String> first = new Collector<>(Long.MAX_VALUE);
		Collector<String> second = new Collector<>(Long.MAX_VALUE);
		status.subscribe(first);
		status.subscribe(second);

		first.subscription.cancel();
		status.set("done");

		assertThat(first.items).containsExactly("running");
		assertThat(second.items).containsExactly("running", "done");
		assertThat(status.isClosed()).isFalse();
	}

	@Test
	void twoBindingsThatFollowEachOtherSettle() {
		// tag::two-way[]
		ValueBinding<Integer> celsius = Bindings.value(0);
		ValueBinding<Integer> kelvin = Bindings.value(273);
		celsius.subscribe(each(value -> kelvin.set(value + 273)));
		kelvin.subscribe(each(value -> celsius.set(value - 273))); // <1>

		celsius.set(100);
		assertThat(kelvin.get()).isEqualTo(373);

		kelvin.set(0);
		assertThat(celsius.get()).isEqualTo(-273);
		// end::two-way[]
	}

	@Test
	void aNullValueIsRefused() {
		assertThatThrownBy(() -> Bindings.value(null)).isInstanceOf(NullPointerException.class);
		ValueBinding<String> binding = Bindings.value("a");
		assertThatThrownBy(() -> binding.set(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void anEmfFeatureIsABinding() {
		// tag::emf[]
		EClass account = EcoreFactory.eINSTANCE.createEClass(); // <1>
		account.setName("Account");

		Binding<Optional<String>> name = Bindings.of(account, EcorePackage.Literals.ENAMED_ELEMENT__NAME, String.class);
		Collector<Optional<String>> collector = new Collector<>(Long.MAX_VALUE);
		name.subscribe(collector);

		account.setName("CheckingAccount"); // <2>
		account.setName(null);

		assertThat(collector.items).containsExactly(Optional.of("Account"), Optional.of("CheckingAccount"), Optional.empty());

		name.close(); // <3>
		assertThat(account.eAdapters()).isEmpty();
		// end::emf[]
	}

	@Test
	@SuppressWarnings("rawtypes")
	void aManyValuedFeatureIsReadAgainWhenItChanges() {
		EClass account = EcoreFactory.eINSTANCE.createEClass();
		Binding<Optional<List>> features = Bindings.of(account, EcorePackage.Literals.ECLASS__ESTRUCTURAL_FEATURES, List.class);
		Collector<Optional<List>> frame = new Collector<>(1);
		features.subscribe(frame);

		account.getEStructuralFeatures().add(EcoreFactory.eINSTANCE.createEAttribute());
		account.getEStructuralFeatures().add(EcoreFactory.eINSTANCE.createEAttribute());
		frame.subscription.request(1);

		assertThat(frame.items).hasSize(2);
		assertThat(frame.items.get(0).orElseThrow()).isEmpty();
		assertThat(frame.items.get(1).orElseThrow()).hasSize(2);
		features.close();
	}

}
