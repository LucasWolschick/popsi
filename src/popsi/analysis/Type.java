package popsi.analysis;

import java.util.List;

public sealed interface Type {
    public static record Named(String name, List<Type> args) implements Type {
    }

    public static record Function(List<Type> args, Type ret) implements Type {
    }

    /// Tipo Record, usado para representar registros.
    public static record Record(List<String> fields, List<Type> types) implements Type {
    }

    /// Inteiro de comprimento desconhecido.
    /// Pode ser convertido para qualquer tipo inteiro.
    public static final Type I_LITERAL = new Named("{integer}", List.of());

    /// Número de ponto flutuante de comprimento desconhecido.
    /// Pode ser convertido para qualquer tipo de ponto flutuante.
    public static final Type F_LITERAL = new Named("{float}", List.of());

    /// String.
    public static final Type STR = new Named("str", List.of());

    /// Caractere.
    public static final Type CHAR = new Named("char", List.of());

    /// Tipo desconhecido. Esse tipo não foi determinado ainda, mas ele existe.
    /// Pode ser convertido para qualquer tipo.
    public static final Type UNKNOWN = new Named("unknown", List.of());

    /// Tipo inválido. Produzido quando ocorre um erro de tipo.
    public static final Type INVALID = new Named("?", List.of());

    public static final Type UNIT = new Named("unit", List.of());

    /// Tipo booleano, usado para condições lógicas.
    public static final Type BOOLEAN = new Named("bool", List.of());

}
