package popsi.analysis;

import java.util.HashMap;
import java.util.Optional;

public class SymbolTable {
    public record Id(int id) {
    }

    public record FunctionInfo(Id id, Type returnType) {
    }

    public record TypeInfo(Id id) {
    }

    private HashMap<Id, FunctionInfo> function;
    private HashMap<Id, TypeInfo> types;
    private HashMap<String, Type> variables;

    public SymbolTable() {
        function = new HashMap<>();
        types = new HashMap<>();
        variables = new HashMap<>();
    }

    public void insert(String name, Type type) {
        variables.put(name, type);
    }

    public Optional<Type> lookup(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    public void insertFunction(String name, FunctionInfo functionInfo) {
        function.put(new Id(name.hashCode()), functionInfo);
    }

    public Optional<FunctionInfo> lookupFunction(String name) {
        return Optional.ofNullable(function.get(new Id(name.hashCode())));
    }

    public void insertType(String name, TypeInfo typeInfo) {
        types.put(new Id(name.hashCode()), typeInfo);
    }

    public Optional<TypeInfo> lookupType(String name) {
        return Optional.ofNullable(types.get(new Id(name.hashCode())));
    }

    public void printSymbolTable() {
        System.out.println("Tabela de Símbolos:");
        System.out.println("Funções:");
        function.forEach((id, functionInfo) -> System.out
                .println("Nome: " + id.id() + ", Tipo: " + functionInfo.returnType().toString()));

        System.out.println("\nTipos:");
        types.forEach((id, typeInfo) -> System.out.println("Nome: " + id.id() + ", Info: " + typeInfo));

        System.out.println("\nVariáveis:");
        variables.forEach((name, type) -> System.out.println("Nome: " + name + ", Tipo: " + type.toString()));
        System.out.println("Fim da Tabela de Símbolos");
    }

}
