package popsi.ast;

import java.util.Optional;

import popsi.Token;

public sealed interface Statement {
        // Declaração -> "let" identificador : tipo = expressão
        public static record Declaration(Token name, Type type, Optional<Expression> value) implements Statement {
        }

        // Expressão como comando
        public static record ExpressionStatement(Expression expression) implements Statement {
        }
}
