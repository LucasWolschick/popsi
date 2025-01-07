package popsi.analysis.typed_ast;

import java.util.List;
import java.util.Optional;

import popsi.FilePosition;
import popsi.analysis.Type;
import popsi.lexer.Token;
import popsi.parser.ast.TypeAst;

public sealed interface TypedExpr {
        /// O tipo da expressão, quando avaliada.
        public Type type();

        // Literais: números, strings, caracteres
        public static record Literal(Token value, Type type) implements TypedExpr {
        }

        // Variáveis: identificador
        public static record VariableExpression(Token name, Type type) implements TypedExpr {
        }

        // Lista
        public static record ListExpression(FilePosition position, List<TypedExpr> elements, Type type)
                        implements TypedExpr {
        }

        // Operação Binária -> expressão operador expressão
        public static record BinaryExpression(
                        TypedExpr left,
                        Token operator,
                        TypedExpr right,
                        Type type) implements TypedExpr {
        }

        // Operação Unária -> operador expressão
        public static record UnaryExpression(Token operator, TypedExpr operand, Type type) implements TypedExpr {
        }

        // Chamada de Função -> identificador ( argumentos? )
        public static record FunctionCall(
                        TypedExpr target,
                        List<Argument> arguments,
                        Type type) implements TypedExpr {
        }

        // Argumento
        public static record Argument(
                        Optional<Token> label,
                        TypedExpr value, Type type) implements TypedExpr {
        }

        // Acesso a lista
        public static record ListAccess(
                        TypedExpr target,
                        TypedExpr place, Type type) implements TypedExpr {
        }

        // Acesso a rec
        public static record RecAccess(
                        TypedExpr target,
                        Token place, Type type) implements TypedExpr {
        }

        // Loop "for"
        public static record ForExpression(
                        Token variable, // Variável do loop
                        TypeAst typeAst, // Tipo da variável
                        TypedExpr range, // Intervalo do loop
                        Block body, // Corpo do loop
                        Type type) implements TypedExpr {
        }

        // Estrutura "if"
        public static record IfExpression(
                        TypedExpr condition, // Condição do `if`
                        Block thenBranch, // Bloco do `then`
                        Optional<Block> elseBranch, // Bloco do `else`, se houver
                        Type type) implements TypedExpr {
        }

        // Loop "while"
        public static record WhileExpression(
                        TypedExpr condition, // Condição do `while`
                        Block body, // Corpo do loop
                        Type type) implements TypedExpr {
        }

        // Retorno
        public static record ReturnExpression(TypedExpr value, Type type) implements TypedExpr {
        }

        // Debug
        public static record DebugExpression(TypedExpr value, Type type) implements TypedExpr {
        }

        // Bloco -> "{" comando (";" comando)* ";"? "}"
        public static record Block(List<TypedStmt> statements, Optional<TypedStmt> lastStatement, Type type)
                        implements TypedExpr {
        }
}
