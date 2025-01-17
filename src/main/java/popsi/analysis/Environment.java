package popsi.analysis;

import java.util.HashMap;
import java.util.Optional;

import popsi.analysis.SymbolTable.FunctionInfo;
import popsi.analysis.SymbolTable.Id;
import popsi.analysis.SymbolTable.LocalInfo;
import popsi.analysis.SymbolTable.RecordInfo;
import popsi.analysis.SymbolTable.TypeInfo;

public class Environment {
    private HashMap<String, EnvEntry> values;
    private HashMap<String, TypeEnvEntry> types;
    private Optional<Environment> enclosing;

    public static sealed interface EnvEntry {
        public record Function(Id<FunctionInfo> functionId) implements EnvEntry {
        }

        public record Local(Id<LocalInfo> localId) implements EnvEntry {
        }
    }

    public static sealed interface TypeEnvEntry {
        public record Type(Id<TypeInfo> typeId) implements TypeEnvEntry {
        }

        public record Record(Id<RecordInfo> recordId) implements TypeEnvEntry {
        }
    }

    public Environment() {
        this.values = new HashMap<>();
        this.types = new HashMap<>();
        this.enclosing = Optional.empty();
    }

    public Environment(Environment enclosing) {
        this.values = new HashMap<>();
        this.types = new HashMap<>();
        this.enclosing = Optional.of(enclosing);
    }

    public Optional<EnvEntry> get(String key) {
        if (values.containsKey(key)) {
            return Optional.of(values.get(key));
        } else if (enclosing.isPresent()) {
            return enclosing.get().get(key);
        } else {
            return Optional.empty();
        }
    }

    public Optional<TypeEnvEntry> getType(String key) {
        if (types.containsKey(key)) {
            return Optional.of(types.get(key));
        } else if (enclosing.isPresent()) {
            return enclosing.get().getType(key);
        } else {
            return Optional.empty();
        }
    }

    public void put(String key, EnvEntry entry) {
        values.put(key, entry);
    }

    public void putType(String key, TypeEnvEntry entry) {
        types.put(key, entry);
    }

    public Optional<Environment> enclosing() {
        return enclosing;
    }
}
