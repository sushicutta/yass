package ch.softappeal.yass.core.remote;

import ch.softappeal.yass.core.Interceptor;
import ch.softappeal.yass.core.Interceptors;
import ch.softappeal.yass.util.Check;

/**
 * Combines a contract with an id.
 * Factory for {@link Service} and {@link Invoker}.
 * @param <C> the contract type
 */
public final class ContractId<C> {

  static final ThreadLocal<ContractId<?>> INSTANCE = new ThreadLocal<>();

  /**
   * @return {@link ContractId} of the active invocation
   * @see Interceptors#getInvocation(ThreadLocal)
   */
  public static ContractId<?> get() {
    return Interceptors.getInvocation(INSTANCE);
  }

  public final Class<C> contract;
  public final Object id;

  private ContractId(final Class<C> contract, final Object id) {
    this.contract = Check.notNull(contract);
    this.id = Check.notNull(id);
  }

  public Service service(final C implementation, final Interceptor... interceptors) {
    return new Service(this, implementation, interceptors);
  }

  public Invoker<C> invoker(final Client client) {
    return client.invoker(this);
  }

  public static <C> ContractId<C> create(final Class<C> contract, final Object id) {
    return new ContractId<>(contract, id);
  }

}