package popsi.analysis;

import java.util.HashMap;

public class SymbolTable {
    public record Id(int id) {
    }

    public record FunctionInfo(Id id) {
    }

    public record TypeInfo(Id id) {
    }

    private HashMap<Id, FunctionInfo> function;
    private HashMap<Id, TypeInfo> types;

    public SymbolTable() {
        function = new HashMap<>();
        types = new HashMap<>();
    }
}
