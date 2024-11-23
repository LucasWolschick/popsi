package popsi.ast;

public sealed interface Statement {
    // Declaração -> "let" identificador : tipo = expressão
    public static record Declaration(String name, String type, Expression value) implements Statement {
    }

    // Expressão como comando
    public static record ExpressionStatement(Expression expression) implements Statement {
    }
}
