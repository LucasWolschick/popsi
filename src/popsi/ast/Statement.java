package popsi.ast;

import popsi.Token;

public sealed interface Statement {
        // Declaração -> "let" identificador : tipo = expressão
        public static record Declaration(Token name, Type type, Expression value) implements Statement {
        }

        // Expressão como comando
        public static record ExpressionStatement(Expression expression) implements Statement {
        }
}
