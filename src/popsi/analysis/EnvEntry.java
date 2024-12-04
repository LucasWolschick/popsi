package popsi.analysis;

public sealed interface EnvEntry {
    public record Function() implements EnvEntry {
    }

    public record Local() implements EnvEntry {
    }
}
