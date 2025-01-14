package popsi.analysis.typed_ast;

import java.util.Optional;

import popsi.analysis.SymbolTable.Id;
import popsi.analysis.SymbolTable.LocalInfo;
import popsi.analysis.SymbolTable.TypeInfo;
import popsi.lexer.Token;
import popsi.parser.ast.TypeAst;

public sealed interface TypedStmt {
        // Declaração -> "let" identificador : tipo = expressão
        public static record Declaration(Token name, TypeAst typeAst, Optional<TypedExpr> value, Id<LocalInfo> local)
                        implements TypedStmt {
        }

        // Expressão como comando
        public static record ExpressionStatement(TypedExpr expression, Id<TypeInfo> type) implements TypedStmt {
        }
}
