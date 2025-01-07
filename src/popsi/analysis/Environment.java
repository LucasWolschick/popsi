package popsi.analysis;

import java.util.HashMap;
import java.util.Optional;

public class Environment {
    private HashMap<String, EnvEntry> values;
    private HashMap<String, TypeEnvEntry> types;
    private Optional<Environment> enclosing;

    public static sealed interface EnvEntry {
        public record Function() implements EnvEntry {
        }

        public record Local() implements EnvEntry {
        }
    }

    public static sealed interface TypeEnvEntry {
        public record Function() implements TypeEnvEntry {
        }
    }

    public Environment() {
        this.values = new HashMap<>();
        this.enclosing = Optional.empty();
    }

    public Environment(Environment enclosing) {
        this.values = new HashMap<>();
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
