// generics: generic method with bounded type param and nested generic return type
class GenericDemo {
  public <T extends Comparable<T>> T findMax(List<T> items) {
    T max = items.get(0);
    for (var item : items) {
      if (item.compareTo(max) > 0) {
        max = item;
      }
    }
    return max;
  }

  public Map<String, List<Integer>> buildIndex() {
    return new HashMap<>();
  }
}

// class-header-type-params: long type-param header with implements clause
abstract class AbstractGenericContainerNodeServiceBase<
  E extends AbstractContainerInterface,
  I
> implements AbstractGenericNodeService<E, I> {
  abstract List<E> findSelectedChildParts(NodeId nodeId, I id);
}

// class-header-type-params: short single type param
class ShortGeneric<T> implements Marker {
  void noop() {}
}

// class-header-type-params: long type-param header with extends clause
abstract class GenericExtends<
  E extends AbstractContainerInterface,
  I
> extends AbstractExplanationNotesServiceBaseImplementationClass<E, I> {
  abstract List<E> find(I id);
}

// interface-and-sealed: interface with default method
interface Printable {
  void print();

  default String asString() {
    return toString();
  }
}

// interface-and-sealed: sealed interface with permits
sealed interface Shape permits Circle, Rectangle {}

// interface-and-sealed: record implementing sealed interface
record Circle(double radius) implements Shape {}

// interface-and-sealed: record implementing sealed interface
record Rectangle(double width, double height) implements Shape {}

// class-with-fields-and-methods: fields, constructor, accessor methods
class UserService {
  private final List<String> names;
  private final Map<String, Integer> ages;

  public UserService(List<String> names, Map<String, Integer> ages) {
    this.names = names;
    this.ages = ages;
  }

  public List<String> getNames() {
    return names;
  }

  public Map<String, Integer> getAges() {
    return ages;
  }
}

// empty-body-with-long-extends: long extends/implements header, empty body
public interface SomeRepositoryWithLongName
  extends BaseRepositoryWithMoreLongNames<SomeEntityWithLongName, SomeEntityIdWithLongName>, AnotherInterfaceWithLongName<SomeEntity> {}

// empty-body-with-long-extends: short extends, empty body
public interface ShortInterface extends Foo {}

// empty-body-with-long-extends: long extends header with type params, empty body
public class EmptyClassLongExtends
  extends SomeAbstractBaseWithVeryLongNameAndExtraStuff<TypeParameterWithLongName, AnotherTypeParameterWithLongName, ThirdTypeParameter> {}

// params-multiple-final + var-keyword: method-level parameter and local-variable cases
class DeclarationMethodCases {

  // params-multiple-final: single final param
  public final String toView(final SomeQualifiedEntityType someEntity) {
    return someEntity.toString();
  }

  // params-multiple-final: multiple final params
  public void merge(final FirstEntity first, final SecondEntity second, final ThirdEntity third) {
    first.combine(second, third);
  }

  // var-keyword: var local variables and var in enhanced-for
  public void example() {
    var name = "Hello";
    var count = 42;
    var list = List.of(1, 2, 3);

    for (var item : list) {
      System.out.println(item);
    }

    var result = list
      .stream()
      .filter(x -> x > 1)
      .toList();
  }
}
