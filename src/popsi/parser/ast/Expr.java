package popsi.parser.ast;

import java.util.List;
import java.util.Optional;
import popsi.FilePosition;
import popsi.lexer.Token;

public sealed interface Expr {
        public FilePosition position();

        // Literais: números, strings, caracteres
        public static record Literal(Token value) implements Expr {
                @Override
                public FilePosition position() {
                        return value.where();
                }
        }

        // Variáveis: identificador
        public static record VariableExpression(Token name) implements Expr {
                @Override
                public FilePosition position() {
                        return name.where();
                }
        }

        // Lista
        public static record ListExpression(FilePosition position, List<Expr> elements) implements Expr {
        }

        // Operação Binária -> expressão operador expressão
        public static record BinaryExpression(
                        Expr left,
                        Token operator,
                        Expr right) implements Expr {
                @Override
                public FilePosition position() {
                        return left.position();
                }
        }

        // Operação Unária -> operador expressão
        public static record UnaryExpression(Token operator, Expr operand) implements Expr {
                @Override
                public FilePosition position() {
                        return operator.where();
                }
        }

        // Chamada de Função -> identificador ( argumentos? )
        public static record FunctionCall(
                        Expr target,
                        List<Argument> arguments) implements Expr {
                @Override
                public FilePosition position() {
                        return target.position();
                }
        }

        // Argumento
        public static record Argument(
                        Optional<Token> label,
                        Expr value) implements Expr {
                @Override
                public FilePosition position() {
                        return value.position();
                }
        }

        // Acesso a lista
        public static record ListAccess(
                        Expr target,
                        Expr place) implements Expr {
                @Override
                public FilePosition position() {
                        return place.position();
                }
        }

        // Acesso a rec
        public static record RecAccess(
                        Expr target,
                        Token place) implements Expr {
                @Override
                public FilePosition position() {
                        return place.where();
                }
        }

        // Loop "for"
        public static record ForExpression(
                        Token start, // Token "for"
                        Token variable, // Variável do loop
                        TypeAst type, // Tipo da variável
                        Expr range, // Intervalo do loop
                        Block body // Corpo do loop
        ) implements Expr {
                @Override
                public FilePosition position() {
                        return variable.where();
                }
        }

        // Estrutura "if"
        public static record IfExpression(
                        Token start, // Token "if"
                        Expr condition, // Condição do `if`
                        Block thenBranch, // Bloco do `then`
                        Optional<Expr> elseBranch // Bloco do `else`, se houver
        ) implements Expr {
                @Override
                public FilePosition position() {
                        return start.where();
                }
        }

        // Loop "while"
        public static record WhileExpression(
                        Token start, // Token "while"
                        Expr condition, // Condição do `while`
                        Block body // Corpo do loop
        ) implements Expr {
                @Override
                public FilePosition position() {
                        return start.where();
                }
        }

        // Retorno
        public static record ReturnExpression(Token keyword, Optional<Expr> value) implements Expr {
                @Override
                public FilePosition position() {
                        return keyword.where();
                }
        }

        // Debug
        public static record DebugExpression(Token keyword, Expr value) implements Expr {
                @Override
                public FilePosition position() {
                        return keyword.where();
                }
        }

        // Read
        public static record ReadExpression(Token keyword, List<Expr> variables) implements Expr {
                @Override
                public FilePosition position() {
                        return keyword.where();
                }
        }

        // Bloco -> "{" comando (";" comando)* ";"? "}"
        public static record Block(FilePosition start, List<Stmt> statements, Optional<Stmt> lastStatement)
                        implements Expr {
                @Override
                public FilePosition position() {
                        return start;
                }
        }
}
