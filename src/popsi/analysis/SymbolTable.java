package popsi.analysis;

import java.util.HashMap;
import java.util.Optional;

public class SymbolTable {
    public record Id<T>(long id) {
    }

    public record FunctionInfo(String name, Id<TypeInfo> type) {
    }

    public record RecordInfo(String name, Id<TypeInfo> type) {
    }

    public record LocalInfo(String name, Id<TypeInfo> type) {
    }

    public record TypeInfo(Type type) {
    }

    public static class Store<T> {
        private long gen = 0;
        private HashMap<Id<T>, T> map = new HashMap<>();
        private HashMap<T, Id<T>> reverseMap = new HashMap<>();

        public Id<T> nextId() {
            return new Id<>(gen++);
        }

        public Id<T> insert(T elem) {
            var id = nextId();
            insert(id, elem);
            return id;
        }

        public void insert(Id<T> id, T elem) {
            map.put(id, elem);
            reverseMap.put(elem, id);
        }

        public Optional<T> get(Id<T> id) {
            return Optional.ofNullable(map.get(id));
        }

        public Optional<Id<T>> getId(T elem) {
            return Optional.ofNullable(reverseMap.get(elem));
        }
    }

    private Store<FunctionInfo> functions;
    private Store<RecordInfo> records;
    private Store<LocalInfo> locals;
    private Store<TypeInfo> types;

    public SymbolTable() {
        functions = new Store<>();
        records = new Store<>();
        locals = new Store<>();
        types = new Store<>();
    }

    public Store<FunctionInfo> functions() {
        return functions;
    }

    public Store<RecordInfo> records() {
        return records;
    }

    public Store<LocalInfo> locals() {
        return locals;
    }

    public Store<TypeInfo> types() {
        return types;
    }

    // funções especializadas
    public Id<TypeInfo> typeId(Type t) {
        var entry = types().getId(new TypeInfo(t));
        if (entry.isPresent()) {
            return entry.get();
        } else {
            return types().insert(new TypeInfo(t));
        }
    }

    public Type typeDefinition(Id<TypeInfo> id) {
        return types().get(id).get().type();
    }

    public void printSymbolTable() {
        System.out.println("Tabela de Símbolos:");
        System.out.println("Funções:");
        functions.map.forEach((id, functionInfo) -> System.out
                .println("Nome: " + functionInfo.name() + ", Tipo: " + typeDefinition(functionInfo.type())));

        System.out.println("\nRegistros:");
        records.map.forEach((id, recordInfo) -> System.out
                .println("Nome: " + recordInfo.name() + ", Tipo: " + typeDefinition(recordInfo.type())));

        System.out.println("\nVariáveis Locais:");
        locals.map.forEach((id, localInfo) -> System.out
                .println("Nome: " + localInfo.name() + ", Tipo: " + typeDefinition(localInfo.type())));

        System.out.println("\nTipos:");
        types.map.forEach((id, typeInfo) -> System.out.println("ID: " + id.id() + ", Tipo: " + typeInfo.type()));

        System.out.println("Fim da Tabela de Símbolos");
    }

    // public void printSymbolTable() {
    // System.out.println("Tabela de Símbolos:");
    // System.out.println("Funções:");
    // function.forEach((id, functionInfo) -> System.out
    // .println("Nome: " + id.id() + ", Tipo: " +
    // functionInfo.returnType().toString()));

    // System.out.println("\nTipos:");
    // types.forEach((id, typeInfo) -> System.out.println("Nome: " + id.id() + ",
    // Info: " + typeInfo));

    // System.out.println("\nVariáveis:");
    // locals.forEach((name, type) -> System.out.println("Nome: " + name + ", Tipo:
    // " + type.toString()));
    // System.out.println("Fim da Tabela de Símbolos");
    // }
}
