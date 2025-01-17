package popsi.parser.ast;

import java.util.Optional;

import popsi.lexer.Token;

public sealed interface Stmt {
        // Declaração -> "let" identificador : tipo = expressão
        public static record Declaration(Token name, TypeAst type, Optional<Expr> value) implements Stmt {
        }

        // Expressão como comando
        public static record ExpressionStatement(Expr expression) implements Stmt {
        }
}
