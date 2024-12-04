package popsi.analysis;

public sealed interface TypeEnvEntry {
    public record Function() implements TypeEnvEntry {
    }
}
