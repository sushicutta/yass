package ch.softappeal.yass.generate;

import ch.softappeal.yass.Version;
import ch.softappeal.yass.core.remote.ContractId;
import ch.softappeal.yass.core.remote.MethodMapper;
import ch.softappeal.yass.core.remote.Services;
import ch.softappeal.yass.core.remote.SimpleMethodMapper;
import ch.softappeal.yass.serialize.fast.BaseTypeHandler;
import ch.softappeal.yass.serialize.fast.BaseTypeHandlers;
import ch.softappeal.yass.serialize.fast.ClassTypeHandler;
import ch.softappeal.yass.serialize.fast.FastSerializer;
import ch.softappeal.yass.serialize.fast.FieldHandler;
import ch.softappeal.yass.serialize.fast.TypeDesc;
import ch.softappeal.yass.serialize.fast.TypeHandler;
import ch.softappeal.yass.util.Check;
import ch.softappeal.yass.util.Exceptions;
import ch.softappeal.yass.util.Nullable;
import ch.softappeal.yass.util.Reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * You must use the "-parameters" option for javac to get the real method parameter names.
 */
public final class Python3Generator { // todo: extract common code with TypeScriptGenerator

    public static final TypeDesc BOOLEAN_DESC = new TypeDesc(TypeDesc.FIRST_ID, BaseTypeHandlers.BOOLEAN);
    public static final TypeDesc DOUBLE_DESC = new TypeDesc(TypeDesc.FIRST_ID + 1, BaseTypeHandlers.DOUBLE);
    public static final TypeDesc STRING_DESC = new TypeDesc(TypeDesc.FIRST_ID + 2, BaseTypeHandlers.STRING);
    public static final TypeDesc BYTES_DESC = new TypeDesc(TypeDesc.FIRST_ID + 3, BaseTypeHandlers.BYTE_ARRAY);
    public static final int FIRST_DESC_ID = TypeDesc.FIRST_ID + 4;

    public static List<BaseTypeHandler<?>> baseTypeHandlers(final BaseTypeHandler<?>... handlers) {
        final List<BaseTypeHandler<?>> h = new ArrayList<>();
        h.add((BaseTypeHandler<?>)BOOLEAN_DESC.handler);
        h.add((BaseTypeHandler<?>)DOUBLE_DESC.handler);
        h.add((BaseTypeHandler<?>)STRING_DESC.handler);
        h.add((BaseTypeHandler<?>)BYTES_DESC.handler);
        h.addAll(Arrays.asList(handlers));
        return h;
    }

    public static Collection<TypeDesc> baseTypeDescs(final TypeDesc... descs) {
        final List<TypeDesc> d = new ArrayList<>();
        d.add(BOOLEAN_DESC);
        d.add(DOUBLE_DESC);
        d.add(STRING_DESC);
        d.add(BYTES_DESC);
        d.addAll(Arrays.asList(descs));
        return d;
    }

    private static final String INIT_PY = "/__init__.py";
    private static final String ROOT_MODULE = "contract";
    private static final Set<Type> ROOT_CLASSES = new HashSet<>(Arrays.asList(
        Object.class,
        Exception.class,
        RuntimeException.class,
        Error.class,
        Throwable.class
    ));

    private static boolean isRootClass(final Class<?> type) {
        return ROOT_CLASSES.contains(Check.notNull(type));
    }

    public static final class ExternalDesc {
        final String name;
        final String typeDesc; // todo: change also in TypeScript
        public ExternalDesc(final String name, final String typeDesc) {
            this.name = Check.notNull(name);
            this.typeDesc = Check.notNull(typeDesc);
        }
    }

    private final String rootPackage;
    private final SortedMap<Integer, TypeHandler> id2typeHandler;
    private final @Nullable Services initiator;
    private final @Nullable Services acceptor;
    private MethodMapper.@Nullable Factory methodMapperFactory;
    private final @Nullable String includeFileForEachModule;
    private final Map<String, String> module2includeFile = new HashMap<>();
    private final SortedMap<Class<?>, ExternalDesc> externalTypes = new TreeMap<>((c1, c2) -> c1.getCanonicalName().compareTo(c2.getCanonicalName()));
    private final Namespace rootNamespace = new Namespace(null, null, ROOT_MODULE, 0);
    private final LinkedHashMap<Class<?>, Namespace> type2namespace = new LinkedHashMap<>();
    private final Map<Class<?>, Integer> type2id = new HashMap<>();

    private void checkType(final Class<?> type) {
        if (!type.getCanonicalName().startsWith(rootPackage)) {
            throw new RuntimeException("type '" + type.getCanonicalName() + "' doesn't have root package '" + rootPackage + "'");
        }
    }

    private final class Namespace {
        final @Nullable Namespace parent;
        final @Nullable String name;
        final String moduleName;
        final int depth;
        final LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
        private final Map<String, Namespace> children = new HashMap<>();
        Namespace(final @Nullable Namespace parent, final @Nullable String name, final String moduleName, final int depth) {
            this.parent = parent;
            this.name = name;
            this.moduleName = Check.notNull(moduleName);
            this.depth = depth;
        }
        private void add(final String qualifiedName, final Class<?> type) {
            final int dot = qualifiedName.indexOf('.');
            if (dot < 0) { // leaf
                checkType(type);
                if (!type.isEnum() && !type.isInterface()) { // note: baseclasses must be before subclasses
                    final Class<?> superClass = type.getSuperclass();
                    if (!isRootClass(superClass)) {
                        rootNamespace.add(superClass);
                    }
                }
                types.add(type);
                type2namespace.put(type, this);
            } else { // intermediate
                final String name = qualifiedName.substring(0, dot);
                @Nullable Namespace namespace = children.get(name);
                if (namespace == null) {
                    namespace = new Namespace(this, name, moduleName + '_' + name, depth + 1);
                    children.put(name, namespace);
                }
                namespace.add(qualifiedName.substring(dot + 1), type);
            }
        }
        void add(final Class<?> type) {
            add(type.getCanonicalName().substring(rootPackage.length()), type);
        }
        void generate(final String path) {
            try {
                new PythonGenerator(Check.notNull(path) + INIT_PY, this);
            } catch (final Exception e) {
                throw Exceptions.wrap(e);
            }
            children.forEach((name, namespace) -> namespace.generate(path + '/' + name));
        }
    }

    private static final class ServiceDesc {
        final String name;
        final ContractId<?> contractId;
        ServiceDesc(final String name, final ContractId<?> contractId) {
            this.name = Check.notNull(name);
            this.contractId = Check.notNull(contractId);
        }
    }

    private static List<ServiceDesc> getServiceDescs(final Services services) throws Exception {
        final List<ServiceDesc> serviceDescs = new ArrayList<>();
        for (final Field field : services.getClass().getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && (field.getType() == ContractId.class)) {
                serviceDescs.add(new ServiceDesc(field.getName(), (ContractId<?>)field.get(services)));
            }
        }
        Collections.sort(serviceDescs, (s1, s2) -> ((Integer)s1.contractId.id).compareTo((Integer)s2.contractId.id));
        return serviceDescs;
    }

    private static Set<Class<?>> getInterfaces(final @Nullable Services services) throws Exception {
        if (services == null) {
            return new HashSet<>();
        }
        return getServiceDescs(services).stream().map(serviceDesc -> serviceDesc.contractId.contract).collect(Collectors.toSet());
    }

    public Python3Generator(
        final String rootPackage,
        final FastSerializer serializer,
        final @Nullable Services initiator,
        final @Nullable Services acceptor,
        final @Nullable String includeFileForEachModule,
        final @Nullable Map<String, String> module2includeFile,
        final @Nullable Map<Class<?>, ExternalDesc> externalTypes,
        final String generatedDir
    ) throws Exception {
        this.rootPackage = rootPackage.isEmpty() ? "" : rootPackage + '.';
        this.id2typeHandler = serializer.id2typeHandler();
        this.initiator = initiator;
        this.acceptor = acceptor;
        if ((initiator != null) && (acceptor != null) && (initiator.methodMapperFactory != acceptor.methodMapperFactory)) {
            throw new IllegalArgumentException("initiator and acceptor must have same methodMapperFactory");
        }
        if (initiator != null) {
            methodMapperFactory = initiator.methodMapperFactory;
        }
        if (acceptor != null) {
            methodMapperFactory = acceptor.methodMapperFactory;
        }
        this.includeFileForEachModule = includeFileForEachModule;

        if (module2includeFile != null) {
            module2includeFile.forEach((m, i) -> this.module2includeFile.put(Check.notNull(m), Check.notNull(i)));
        }
        if (externalTypes != null) {
            externalTypes.forEach((java, py) -> this.externalTypes.put(Check.notNull(java), Check.notNull(py)));
        }
        id2typeHandler.forEach((id, typeHandler) -> {
            if (id >= FIRST_DESC_ID) {
                final Class<?> type = typeHandler.type;
                type2id.put(type, id);
                if (!this.externalTypes.containsKey(type)) {
                    rootNamespace.add(type);
                }
            }
        });
        final Set<Class<?>> interfaceSet = getInterfaces(initiator);
        interfaceSet.addAll(getInterfaces(acceptor));
        final List<Class<?>> interfaceList = new ArrayList<>(interfaceSet);
        Collections.sort(interfaceList, (t1, t2) -> t1.getCanonicalName().compareTo(t2.getCanonicalName()));
        interfaceList.forEach(rootNamespace::add);
        rootNamespace.generate(generatedDir + '/' + ROOT_MODULE);
        new Generator(generatedDir + INIT_PY) {
            // empty
        }.close();
    }

    private TypeHandler typeHandler(final Class<?> type) {
        return Check.notNull(id2typeHandler.get(Check.notNull(type2id.get(Check.notNull(type)))));
    }

    private static String pyBool(final boolean value) {
        return value ? "True" : "False";
    }

    private boolean hasClassDesc(final Class<?> type) {
        return !type.isEnum() && !Modifier.isAbstract(type.getModifiers()) && (typeHandler(type) instanceof ClassTypeHandler);
    }

    private final class PythonGenerator extends Generator {
        private final Namespace namespace;
        private final SortedSet<Namespace> modules = new TreeSet<>((n1, n2) -> n1.moduleName.compareTo(n2.moduleName));
        private String getQualifiedName(final Class<?> type) {
            final Namespace ns = Check.notNull(type2namespace.get(Check.notNull(type)));
            modules.add(ns);
            return ((namespace != ns) ? ns.moduleName + '.' : "") + type.getSimpleName();
        }
        private void importModule(final Namespace module) {
            print("from ");
            for (int d = namespace.depth + 2; d > 0; d--) {
                print(".");
            }
            if (module == rootNamespace) {
                println(" import " + ROOT_MODULE);
            } else {
                println("%s import %s as %s", module.parent.moduleName.replace('_', '.'), module.name, module.moduleName);
            }
        }
        @SuppressWarnings("unchecked") PythonGenerator(final String file, final Namespace namespace) throws Exception {
            super(file);
            this.namespace = Check.notNull(namespace);
            println("from enum import Enum");
            println("from typing import List, Any");
            println();
            println("import yass");
            if (includeFileForEachModule != null) {
                includeFile(includeFileForEachModule);
            }
            final @Nullable String moduleIncludeFile = module2includeFile.get(
                (namespace == rootNamespace) ? "" : namespace.moduleName.substring(ROOT_MODULE.length() + 1).replace('_', '.')
            );
            if (moduleIncludeFile != null) {
                println2();
                includeFile(moduleIncludeFile);
            }
            final StringBuilder buffer = new StringBuilder();
            redirect(buffer);
            namespace.types.stream().filter(Class::isEnum).forEach(type -> generateEnum((Class<Enum<?>>)type));
            namespace.types.stream()
                .filter(t -> !t.isEnum() && !t.isInterface() && (Modifier.isAbstract(t.getModifiers()) || (typeHandler(t) instanceof ClassTypeHandler)))
                .forEach(this::generateClass);
            namespace.types.stream().filter(Class::isInterface).forEach(this::generateInterface);
            redirect(null);
            modules.stream().filter(module -> module != namespace).forEach(this::importModule);
            print(buffer);
            if (namespace == rootNamespace) {
                generateOnlyForRootNamespace();
            }
            close();
        }
        private void generateEnum(final Class<? extends Enum<?>> type) {
            println2();
            tabsln("class %s(Enum):", type.getSimpleName());
            for (final Enum<?> e : type.getEnumConstants()) {
                tab();
                tabsln("%s = %s", e.name(), e.ordinal());
            }
        }
        private String contractType(final Class<?> type, final boolean name) {
            final @Nullable ExternalDesc externalDesc = externalTypes.get(FieldHandler.primitiveWrapperType(type));
            if (externalDesc != null) {
                return name ? externalDesc.name : externalDesc.typeDesc;
            }
            checkType(type);
            if (type.isArray()) {
                throw new IllegalArgumentException("illegal type " + type.getCanonicalName() + " (use List instead [])");
            }
            return getQualifiedName(type);
        }
        private String pythonType(final Type type) {
            if (type instanceof ParameterizedType) {
                final ParameterizedType parameterizedType = (ParameterizedType)type;
                if (parameterizedType.getRawType() == List.class) {
                    return "List[" + pythonType(parameterizedType.getActualTypeArguments()[0]) + ']';
                } else {
                    throw new RuntimeException("unexpected type '" + parameterizedType + '\'');
                }
            } else if (type == void.class) {
                return "None";
            } else if ((type == double.class) || (type == Double.class)) {
                return "float";
            } else if ((type == boolean.class) || (type == Boolean.class)) {
                return "bool";
            } else if (type == String.class) {
                return "str";
            } else if (type == byte[].class) {
                return "bytes";
            } else if (type == Object.class) {
                return "Any";
            } else if (Throwable.class.isAssignableFrom((Class<?>)type)) {
                return "Exception";
            }
            return contractType((Class<?>)type, true);
        }
        private void generateClass(final Class<?> type) {
            println2();
            final Class<?> sc = type.getSuperclass();
            final @Nullable Class<?> superClass = isRootClass(sc) ? null : sc;
            if (Modifier.isAbstract(type.getModifiers())) {
                println("@yass.abstract");
            }
            tabs("class %s", type.getSimpleName());
            boolean hasSuper = false;
            if (superClass != null) {
                hasSuper = true;
                print("(%s)", getQualifiedName(superClass));
            } else if (Throwable.class.isAssignableFrom(sc)) {
                print("(Exception)");
            }
            println(":");
            inc();
            tabsln("def __init__(self) -> None:");
            inc();
            if (hasSuper) {
                tabsln("super().__init__()");
            }
            final List<Field> ownFields = Reflect.ownFields(type);
            if (ownFields.isEmpty() && !hasSuper) {
                tabsln("pass");
            } else {
                for (final Field field : ownFields) {
                    tabsln("self.%s = None  # type: %s", field.getName(), pythonType(field.getGenericType()));
                }
            }
            dec();
            dec();
        }
        private void generateInterface(final Class<?> type) {
            final Method[] methods = type.getMethods();
            Arrays.sort(methods, (method1, method2) -> method1.getName().compareTo(method2.getName()));
            SimpleMethodMapper.FACTORY.create(type); // checks for overloaded methods (Python restriction)
            final MethodMapper methodMapper = methodMapperFactory.create(type);
            println2();
            println("class %s:", type.getSimpleName());
            inc();
            tabsln("MAPPER = yass.MethodMapper([");
            for (final Method method : methods) {
                final MethodMapper.Mapping mapping = methodMapper.mapMethod(method);
                tab();
                tabsln("yass.MethodMapping(%s, '%s', %s),", mapping.id, mapping.method.getName(), pyBool(mapping.oneWay));
            }
            tabsln("])");
            for (final Method method : methods) {
                println();
                tabs("def %s(self", method.getName());
                for (final Parameter parameter : method.getParameters()) {
                    print(", %s: %s", parameter.getName(), pythonType(parameter.getParameterizedType()));
                }
                println(") -> %s:", methodMapper.mapMethod(method).oneWay ? "None" : pythonType(method.getGenericReturnType()));
                tab();
                tabsln("raise NotImplementedError()");
            }
            dec();
        }
        private void generateServices(final @Nullable Services services, final String role) throws Exception {
            if (services == null) {
                return;
            }
            println2();
            tabsln("class %s:", role);
            for (final ServiceDesc sd : getServiceDescs(services)) {
                final String qn = getQualifiedName(sd.contractId.contract);
                tab();
                tabsln("%s = yass.ContractId(%s, %s)  # type: yass.ContractId[%s]", sd.name, qn, sd.contractId.id, qn);
            }
        }
        private String typeDesc(final ClassTypeHandler.FieldDesc fieldDesc) {
            final Optional<TypeHandler> typeHandlerOptional = fieldDesc.handler.typeHandler();
            if (!typeHandlerOptional.isPresent()) {
                return "None";
            }
            final TypeHandler typeHandler = typeHandlerOptional.get();
            if (TypeDesc.LIST.handler == typeHandler) {
                return "yass.LIST_DESC";
            } else if (BOOLEAN_DESC.handler == typeHandler) {
                return "yass.BOOLEAN_DESC";
            } else if (DOUBLE_DESC.handler == typeHandler) {
                return "yass.DOUBLE_DESC";
            } else if (STRING_DESC.handler == typeHandler) {
                return "yass.STRING_DESC";
            } else if (BYTES_DESC.handler == typeHandler) {
                return "yass.BYTES_DESC";
            }
            return contractType(typeHandler.type, false);
        }
        private void generateOnlyForRootNamespace() throws Exception {
            println2();
            println("GENERATED_BY_YASS_VERSION = '%s'", Version.VALUE);
            println();
            new LinkedHashSet<>(type2namespace.values()).stream().filter(module -> module != rootNamespace).forEach(this::importModule);
            println();
            type2namespace.keySet().forEach(type -> {
                final String qn = getQualifiedName(type);
                if (type.isEnum()) {
                    println("yass.enumDesc(%s, %s)", type2id.get(type), qn);
                } else if (hasClassDesc(type)) {
                    println("yass.classDesc(%s, %s, %s)", type2id.get(type), qn, pyBool(((ClassTypeHandler)typeHandler(type)).referenceable));
                }
            });
            println();
            type2namespace.keySet().stream().filter(Python3Generator.this::hasClassDesc).forEach(type -> {
                tabsln("yass.fieldDescs(%s, [", getQualifiedName(type));
                for (final ClassTypeHandler.FieldDesc fieldDesc : ((ClassTypeHandler)typeHandler(type)).fieldDescs()) {
                    tab();
                    tabsln("yass.FieldDesc(%s, '%s', %s),", fieldDesc.id, fieldDesc.handler.field.getName(), typeDesc(fieldDesc));
                }
                tabsln("])");
            });
            println();
            println("SERIALIZER = yass.FastSerializer([");
            inc();
            externalTypes.values().forEach(externalDesc -> tabsln("%s,", externalDesc.typeDesc));
            type2namespace.keySet().stream().filter(t -> !Modifier.isAbstract(t.getModifiers())).forEach(t -> tabsln("%s,", getQualifiedName(t)));
            dec();
            println("])");
            generateServices(initiator, "initiator");
            generateServices(acceptor, "acceptor");
        }
    }

}
