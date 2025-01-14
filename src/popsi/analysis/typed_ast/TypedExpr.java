package popsi.analysis.typed_ast;

import java.util.List;
import java.util.Optional;
import popsi.FilePosition;
import popsi.analysis.SymbolTable.Id;
import popsi.analysis.SymbolTable.TypeInfo;
import popsi.lexer.Token;
import popsi.parser.ast.TypeAst;

public sealed interface TypedExpr {
        /// O tipo da expressão, quando avaliada.
        public Id<TypeInfo> type();

        // Literais: números, strings, caracteres
        public static record Literal(Token value, Id<TypeInfo> type) implements TypedExpr {
        }

        // Variáveis: identificador
        public static record VariableExpression(Token name, Id<TypeInfo> type) implements TypedExpr {
        }

        // Lista
        public static record ListExpression(FilePosition position, List<TypedExpr> elements, Id<TypeInfo> type)
                        implements TypedExpr {
        }

        // Operação Binária -> expressão operador expressão
        public static record BinaryExpression(
                        TypedExpr left,
                        Token operator,
                        TypedExpr right,
                        Id<TypeInfo> type) implements TypedExpr {
        }

        // Operação Unária -> operador expressão
        public static record UnaryExpression(Token operator, TypedExpr operand, Id<TypeInfo> type)
                        implements TypedExpr {
        }

        // Chamada de Função -> identificador ( argumentos? )
        public static record FunctionCall(
                        TypedExpr target,
                        List<Argument> arguments,
                        Id<TypeInfo> type) implements TypedExpr {
        }

        // Argumento
        public static record Argument(
                        Optional<Token> label,
                        TypedExpr value, Id<TypeInfo> type) implements TypedExpr {
        }

        // Acesso a lista
        public static record ListAccess(
                        TypedExpr target,
                        TypedExpr place, Id<TypeInfo> type) implements TypedExpr {
        }

        // Acesso a rec
        public static record RecAccess(
                        TypedExpr target,
                        Token place, Id<TypeInfo> type) implements TypedExpr {
        }

        // Loop "for"
        public static record ForExpression(
                        Token variable, // Variável do loop
                        TypeAst typeAst, // Tipo da variável
                        TypedExpr range, // Intervalo do loop
                        Block body, // Corpo do loop
                        Id<TypeInfo> type) implements TypedExpr {
        }

        // Estrutura "if"
        public static record IfExpression(
                        TypedExpr condition, // Condição do `if`
                        Block thenBranch, // Bloco do `then`
                        Optional<TypedExpr> elseBranch, // Bloco do `else`, se houver
                        Id<TypeInfo> type) implements TypedExpr {
        }

        // Loop "while"
        public static record WhileExpression(
                        TypedExpr condition, // Condição do `while`
                        Block body, // Corpo do loop
                        Id<TypeInfo> type) implements TypedExpr {
        }

        // Retorno
        public static record ReturnExpression(TypedExpr value, Id<TypeInfo> type) implements TypedExpr {
        }

        // Debug
        public static record DebugExpression(TypedExpr value, Id<TypeInfo> type) implements TypedExpr {
        }

        // Bloco -> "{" comando (";" comando)* ";"? "}"
        public static record Block(FilePosition start, List<TypedStmt> statements, Optional<TypedStmt> lastStatement,
                        Id<TypeInfo> type)
                        implements TypedExpr {
        }
}
