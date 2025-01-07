package popsi.analysis.typed_ast;

import java.util.Optional;

import popsi.analysis.Type;
import popsi.lexer.Token;
import popsi.parser.ast.TypeAst;

public sealed interface TypedStmt {
        // Declaração -> "let" identificador : tipo = expressão
        public static record Declaration(Token name, TypeAst typeAst, Optional<TypedExpr> value, Type type)
                        implements TypedStmt {
        }

        // Expressão como comando
        public static record ExpressionStatement(TypedExpr expression, Type type) implements TypedStmt {
        }
}
